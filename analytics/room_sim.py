#!/usr/bin/env python3
"""
Simulador de salas: conecta DOS clientes y comprueba que la reproduccion queda
sincronizada. Es la prueba de la etapa 1 (backend + protocolo), sin tocar el
telefono.

No usa dependencias: trae su propio cliente WebSocket (la app usa OkHttp, pero
aqui interesa poder probar el protocolo desde cualquier maquina con Python).

Uso:
    python3 room_sim.py                        # contra un servidor local
    python3 room_sim.py https://...onrender.com

Verifica, en este orden:
  1. Crear sala por HTTP y entrar los dos (hello).
  2. Cambio de cancion en dos tiempos: `prepare` -> los dos avisan `ready` -> el
     servidor manda `go` con UNA hora para los dos. Se comprueba que el aviso de
     carga NO pone musica, que la senal de arranque llega a ambos y que los dos
     arrancarian con el mismo segundo (la diferencia real entre los dos).
  3. Latido `sync` a los 3 s -> la deriva se corrige sola.
  4. Pausa, seek y cambio de cancion -> B recibe cada accion.
  5. Chat en los dos sentidos.
  6. B se sale -> A ve la lista de miembros sin B (la sala sigue viva).
  7. ping/pong: mide el ida y vuelta (con eso el cliente corrige su reloj).
  8. Control desde administracion.
"""

import base64
import json
import os
import socket
import ssl
import struct
import sys
import time
import urllib.request
from urllib.parse import urlparse

# Deriva que ya se notaria al escuchar (por encima de esto, se corrige con seek).
DRIFT_NOTICEABLE_MS = 250


class WebSocketClient:
    """Cliente WebSocket minimo (RFC 6455) sobre sockets de la libreria estandar."""

    def __init__(self, url: str, timeout: float = 10.0):
        parsed = urlparse(url)
        self.secure = parsed.scheme == "wss"
        self.host = parsed.hostname or "localhost"
        self.port = parsed.port or (443 if self.secure else 80)
        self.path = (parsed.path or "/") + (f"?{parsed.query}" if parsed.query else "")
        self.timeout = timeout
        self.sock: socket.socket | None = None
        self.buffer = b""

    def connect(self) -> None:
        raw = socket.create_connection((self.host, self.port), timeout=self.timeout)
        if self.secure:
            context = ssl.create_default_context()
            raw = context.wrap_socket(raw, server_hostname=self.host)
        self.sock = raw
        key = base64.b64encode(os.urandom(16)).decode()
        handshake = (
            f"GET {self.path} HTTP/1.1\r\n"
            f"Host: {self.host}:{self.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        )
        self.sock.sendall(handshake.encode())
        header = self._read_until(b"\r\n\r\n")
        if b"101" not in header.split(b"\r\n")[0]:
            raise RuntimeError(f"el servidor rechazo el WebSocket: {header.decode(errors='replace')[:200]}")

    def _read_until(self, marker: bytes) -> bytes:
        while marker not in self.buffer:
            chunk = self.sock.recv(4096) if self.sock else b""
            if not chunk:
                raise RuntimeError("conexion cerrada durante el saludo")
            self.buffer += chunk
        head, _, rest = self.buffer.partition(marker)
        self.buffer = rest
        return head + marker

    # Centinelas del parser: "falta informacion" y "frame de control".
    NEED_MORE = object()
    CONTROL = object()

    def _parse_buffered(self):
        """Lee un frame COMPLETO del buffer, o pide mas datos sin consumir nada.

        Importante: si un frame se corta a medias (porque vencio el timeout)
        no se consume ni un byte. Consumirlo desincronizaba el cliente de prueba
        y hacia que despues "no llegara" un mensaje que si habia llegado.
        """
        buf = self.buffer
        if len(buf) < 2:
            return self.NEED_MORE
        opcode = buf[0] & 0x0F
        masked = bool(buf[1] & 0x80)
        length = buf[1] & 0x7F
        offset = 2
        if length == 126:
            if len(buf) < 4:
                return self.NEED_MORE
            length = struct.unpack("!H", buf[2:4])[0]
            offset = 4
        elif length == 127:
            if len(buf) < 10:
                return self.NEED_MORE
            length = struct.unpack("!Q", buf[2:10])[0]
            offset = 10
        mask_length = 4 if masked else 0
        if len(buf) < offset + mask_length + length:
            return self.NEED_MORE
        mask = buf[offset:offset + mask_length]
        payload = buf[offset + mask_length:offset + mask_length + length]
        self.buffer = buf[offset + mask_length + length:]
        if mask:
            payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
        if opcode == 0x8:
            return None
        if opcode == 0x9:
            self._send_frame(payload, opcode=0xA)
            return self.CONTROL
        if opcode in (0xA, 0x0):
            return self.CONTROL
        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return self.CONTROL

    def send_json(self, payload: dict) -> None:
        self._send_frame(json.dumps(payload).encode())

    def _send_frame(self, data: bytes, opcode: int = 0x1) -> None:
        # Los frames del cliente van enmascarados (obligatorio por RFC).
        mask = os.urandom(4)
        header = bytes([0x80 | opcode])
        length = len(data)
        if length < 126:
            header += bytes([0x80 | length])
        elif length < 65536:
            header += bytes([0x80 | 126]) + struct.pack("!H", length)
        else:
            header += bytes([0x80 | 127]) + struct.pack("!Q", length)
        masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(data))
        if self.sock:
            self.sock.sendall(header + mask + masked)

    def recv_json(self, timeout: float = 10.0) -> dict | None:
        if self.sock:
            self.sock.settimeout(timeout)
        while True:
            frame = self._parse_buffered()
            if frame is self.NEED_MORE:
                chunk = self.sock.recv(65536) if self.sock else b""
                if not chunk:
                    raise RuntimeError("conexion cerrada")
                self.buffer += chunk
                continue
            if frame is self.CONTROL:
                continue
            return frame

    def close(self, say_bye: bool = True) -> None:
        """Cierra el socket y, antes, avisa a la sala para que no quede fantasma."""
        try:
            if not self.sock:
                return
            if say_bye:
                self.send_json({"t": "bye"})
            self._send_frame(b"", opcode=0x8)
            self.sock.close()
        except OSError:
            pass


class SimClient:
    """Un cliente de la sala con el mismo reloj que usaria la app."""

    def __init__(self, ws_url: str, name: str):
        self.name = name
        self.ws = WebSocketClient(ws_url)
        self.offset_ms = 0.0          # server_ms - local_ms (relojes distintos)
        self.rtt_ms = 0.0
        self.state = {}               # ultima orden de reproduccion recibida
        self.chat: list[tuple[str, str]] = []
        self.members: list[dict] = []
        self.played_at: float | None = None
        self.left: dict | None = None
        self.prepare: dict | None = None      # aviso "carga esta cancion"
        self.go: dict | None = None           # senal de arranque comun
        self.ready_from: list[str] = []       # quienes avisaron

    def connect(self) -> dict:
        self.ws.connect()
        self.ws.send_json({"t": "hello", "name": self.name})
        joined = self.ws.recv_json()
        self._absorb(joined)
        self.calibrate()
        return joined

    def calibrate(self) -> None:
        """ping/pong: mide ida y vuelta y el desfase contra el reloj del servidor."""
        sent = time.monotonic() * 1000
        self.ws.send_json({"t": "ping", "at": sent})
        while True:
            message = self.ws.recv_json()
            if message is None:
                return
            if message.get("t") == "pong":
                now = time.monotonic() * 1000
                self.rtt_ms = now - sent
                # Desfase estimado en el instante del pong: se le resta media ida.
                self.offset_ms = message["server_ms"] - (now - self.rtt_ms / 2)
                return

    def server_now(self) -> float:
        return time.monotonic() * 1000 + self.offset_ms

    def _absorb(self, message: dict) -> None:
        if not message:
            return
        kind = message.get("t")
        if kind == "joined":
            self.members = message.get("members", [])
        elif kind == "presence":
            self.members = message.get("members", [])
        elif kind == "left":
            self.members = message.get("members", [])
            self.left = message
        elif kind == "state":
            self.state = message
            self.played_at = time.monotonic()
        elif kind == "sync":
            self.state = {**self.state, **message}
            self.played_at = time.monotonic()
        elif kind == "prepare":
            # Cargar la cancion, EN PAUSA: todavia no suena nada.
            self.prepare = message
            self.state = {
                **self.state,
                "track": message.get("track"),
                "position_ms": message.get("position_ms", 0),
                "playing": False,
            }
        elif kind == "ready":
            self.ready_from.append(message.get("name", "?"))
        elif kind == "go":
            # Arranque comun: aqui (y solo aqui) empieza a sonar.
            self.go = message
            self.state = {**self.state, **message}
            self.played_at = time.monotonic()
        elif kind == "chat":
            self.chat.append((message.get("name", "?"), message.get("text", "")))
        elif kind == "ping":
            # La app responde a los latidos del servidor para no caer por inactivo.
            self.ws.send_json({"t": "pong"})

    def listen(self, timeout: float = 6.0) -> dict | None:
        """Espera un mensaje y lo aplica (como haria la app)."""
        try:
            message = self.ws.recv_json(timeout)
        except (socket.timeout, TimeoutError):
            return None
        self._absorb(message)
        return message

    def drain(self, seconds: float = 1.0) -> list[dict]:
        received = []
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                message = self.ws.recv_json(timeout=0.3)
            except (socket.timeout, TimeoutError):
                continue
            if message is None:
                break
            self._absorb(message)
            received.append(message)
        return received

    def go_target_ms(self) -> float | None:
        """En qué segundo arranca con la señal común (lo que hará la app al oír `go`)."""
        go = self.go
        if not go:
            return None
        base = float(go.get("position_ms") or 0)
        if not go.get("playing"):
            return base
        return base + max(self.server_now() - go.get("server_ms", self.server_now()), 0.0)

    def target_position_ms(self) -> float | None:
        """En que segundo deberia ir la reproduccion AHORA mismo.

        Es exactamente la cuenta que hara la app: la posicion que mando el otro
        mas el tiempo que paso desde que el servidor lo selló.
        """
        state = self.state
        if not state or "position_ms" not in state:
            return None
        elapsed = self.server_now() - state.get("server_ms", self.server_now())
        if not state.get("playing"):
            return float(state["position_ms"])
        return float(state["position_ms"]) + max(elapsed, 0.0)


def check_prepare_timeout(base: str, ws_base: str) -> int:
    """Red de seguridad: si uno nunca avisa `ready`, la sala NO se queda muda."""
    print("\n9) Nadie avisa `ready`: el servidor arranca solo (prueba lenta)")
    code = api(base, "POST", "/v1/room")["code"]
    url = f"{ws_base}/v1/room/{code}/ws"
    host = SimClient(url, "Anfitrión")
    guest = SimClient(url, "Invitado")
    host.connect()
    guest.connect()
    seq = int(time.time() * 1000)
    track = {"id": "nadieAvisa1", "title": "Nadie avisa", "artist": "Prueba", "duration_ms": 120000}
    host.ws.send_json({"t": "prepare", "seq": seq, "track": track, "position_ms": 0, "playing": True})
    guest.listen()
    host.ws.send_json({"t": "ready", "seq": seq})   # el invitado nunca avisa
    started = time.monotonic()
    go = wait_for(host, "go", timeout=20)
    elapsed = time.monotonic() - started
    failures = 0
    failures += not check(f"el arranque sale solo en {elapsed:.1f} s", bool(go))
    failures += not check("y sale sonando (no en pausa)", bool(go) and go.get("playing") is True)
    host.ws.close()
    guest.ws.close()
    token = load_token()
    if token:
        api(base, "DELETE", f"/admin/room/{code}", token)
    return failures


def load_token() -> str:
    """Token de administracion: de las variables de entorno o del config del CLI."""
    token = os.environ.get("OYM_ADMIN_TOKEN", "").strip()
    if token:
        return token
    config = os.path.join(os.path.expanduser("~"), ".oym-analytics.json")
    if os.path.exists(config):
        try:
            with open(config) as handle:
                return str(json.load(handle).get("token", "")).strip()
        except (OSError, json.JSONDecodeError):
            return ""
    return ""


def api(base_url: str, method: str, path: str, token: str = "") -> dict:
    headers = {"Accept": "application/json"}
    if token:
        headers["X-Admin-Token"] = token
    request = urllib.request.Request(f"{base_url}{path}", method=method, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read().decode()
    return json.loads(body) if body else {}


def check(label: str, ok: bool, detail: str = "") -> bool:
    print(f"  [{'OK ' if ok else 'FALLA'}] {label}{(' — ' + detail) if detail else ''}")
    return ok


def wait_for(client: "SimClient", kind: str, timeout: float = 8.0) -> dict | None:
    """Espera un mensaje de cierto tipo (lo demas se absorbe, como en la app)."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        message = client.listen(timeout=1.0)
        if message and message.get("t") == kind:
            return message
    return None


def start_together(a: "SimClient", b: "SimClient", track: dict) -> tuple[dict | None, dict | None]:
    """Reproduce el cambio de cancion en dos tiempos, tal como lo haria la app.

    Nadie suena hasta el `go`: es exactamente lo que evita que uno arranque solo
    y el otro lo pise al entrar.
    """
    seq = int(time.time() * 1000)
    a.ws.send_json({"t": "prepare", "seq": seq, "track": track, "position_ms": 0, "playing": True})
    b.listen()                                     # a B le llega el aviso
    b.ws.send_json({"t": "ready", "seq": seq})    # B la cargo (en la app: buffering)
    wait_for(a, "ready")                           # a A le llega el ready
    a.ws.send_json({"t": "ready", "seq": seq})    # A tambien estaba listo
    return wait_for(a, "go"), wait_for(b, "go")


def main() -> int:
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8000").rstrip("/")
    ws_base = base.replace("https://", "wss://").replace("http://", "ws://")
    print(f"Servidor: {base}\n")

    failures = 0
    created = api(base, "POST", "/v1/room")
    code = created["code"]
    print(f"Sala creada: {code} (expira {created['expires_at'][:19]}, máx {created['max_members']} personas)\n")

    url = f"{ws_base}/v1/room/{code}/ws"
    host = SimClient(url, "Anfitrión")
    guest = SimClient(url, "Invitado")
    joined = host.connect()
    guest.connect()

    print("1) Entrada a la sala")
    failures += not check("el anfitrión recibe su identidad", bool(joined.get("me", {}).get("id")), joined.get("me", {}).get("name", ""))
    failures += not check("el invitado ve 2 personas", len(guest.members) == 2, str([m["name"] for m in guest.members]))
    failures += not check("la sala reporta 2 conectados", api(base, "GET", f"/v1/room/{code}")["personas"] == 2)
    failures += not check("el reloj se calibró (ping/pong)", host.rtt_ms < 1000, f"ida y vuelta {host.rtt_ms:.0f} ms")

    print("\n2) Cambio de canción en dos tiempos: prepare -> ready -> go")
    track = {"id": "dQw4w9WgXcQ", "title": "Prueba de sincronía", "artist": "OpenYTMusic", "duration_ms": 213000}
    seq = int(time.time() * 1000)
    host.ws.send_json({"t": "prepare", "seq": seq, "track": track, "position_ms": 0, "playing": True})
    message = guest.listen()
    failures += not check("el invitado recibe el aviso de carga", (message or {}).get("t") == "prepare")
    failures += not check("llega la canción a cargar", (guest.prepare or {}).get("track", {}).get("id") == track["id"])
    failures += not check("el aviso NO pone música (nada suena todavía)", guest.state.get("playing") is False)
    failures += not check("el aviso guarda la intención (sonar al arrancar)", (guest.prepare or {}).get("playing") is True)

    guest.ws.send_json({"t": "ready", "seq": seq})
    message = wait_for(host, "ready")
    failures += not check("el anfitrión ve que el invitado está listo", bool(message))
    failures += not check("con uno solo todavía no arranca", host.go is None)
    host.ws.send_json({"t": "ready", "seq": seq})

    go_host = wait_for(host, "go")
    go_guest = wait_for(guest, "go")
    failures += not check("la señal de arranque llega a los dos", bool(go_host) and bool(go_guest))
    failures += not check("soy el anfitrión, no me pierdo el arranque", bool(go_host))
    failures += not check(
        "los dos arrancan con la MISMA hora del servidor",
        bool(go_host) and bool(go_guest) and go_host.get("server_ms") == go_guest.get("server_ms"),
    )
    failures += not check("misma canción en los dos", (guest.state.get("track") or {}).get("id") == track["id"])
    failures += not check("ahora sí suena", guest.state.get("playing") is True)
    skew = abs((host.go_target_ms() or 0) - (guest.go_target_ms() or 0))
    failures += not check(
        f"los dos empezarían con {skew:.0f} ms de diferencia",
        skew < DRIFT_NOTICEABLE_MS,
        "imperceptible" if skew < DRIFT_NOTICEABLE_MS else "se notaría al escuchar",
    )
    drift = abs(guest.target_position_ms() or 0)
    failures += not check(
        f"el invitado calcularía el segundo {drift / 1000:.2f} (deriva {drift:.0f} ms)",
        drift < DRIFT_NOTICEABLE_MS,
        "dentro del margen audible" if drift < DRIFT_NOTICEABLE_MS else "se notaría al escuchar",
    )

    print("\n3) Latido de sincronía a los 3 s")
    time.sleep(3.0)
    host.ws.send_json({"t": "sync", "position_ms": 3000, "playing": True})
    guest.listen()
    drift = abs((guest.target_position_ms() or 0) - 3000)
    failures += not check(f"la deriva queda en {drift:.0f} ms", drift < DRIFT_NOTICEABLE_MS)

    print("\n4) Pausa, adelanto de la canción y cambio de tema")
    host.ws.send_json({"t": "state", "position_ms": 5000, "playing": False, "action": "pause"})
    guest.listen()
    failures += not check("la pausa también llega al invitado", guest.state.get("playing") is False)

    host.ws.send_json({"t": "state", "position_ms": 90000, "playing": False, "action": "seek"})
    guest.listen()
    failures += not check("el invitado salta al segundo 90", abs((guest.state.get("position_ms") or 0) - 90000) < 500)

    new_track = {"id": "otraCancion1", "title": "Otra canción", "artist": "Otro", "duration_ms": 180000}
    go_host, go_guest = start_together(host, guest, new_track)
    failures += not check("el invitado cambia de canción con el mismo arranque", (guest.state.get("track") or {}).get("id") == new_track["id"])
    failures += not check("y sin quedarse sin señal de arranque", bool(go_host) and bool(go_guest))

    print("\n5) El invitado también controla (los dos mandan)")
    guest.ws.send_json({"t": "state", "position_ms": 0, "playing": False, "action": "pause"})
    received = host.drain(1.5)
    failures += not check(
        "el anfitrión recibe la pausa del invitado",
        any(m.get("t") == "state" and m.get("playing") is False for m in received),
    )

    print("\n6) Chat")
    host.ws.send_json({"t": "chat", "text": "esto suena brutal"})
    guest.listen()
    guest.ws.send_json({"t": "chat", "text": "buenísima elección"})
    received = host.drain(1.5)
    failures += not check(
        "va y viene en los dos sentidos",
        any("brutal" in text for _name, text in guest.chat)
        and any("elección" in (m.get("text") or "") for m in received),
    )

    print("\n7) El invitado sale: la sala sigue viva")
    guest.ws.close()
    # La salida debe verse al instante (gracias al "bye"): se le dan 6 s de
    # margen para no confundir un aviso lento con un aviso que no llega.
    left = wait_for(host, "left", timeout=6)
    failures += not check("el anfitrión ve que se fue", left is not None, (left or {}).get("name", ""))
    failures += not check("queda 1 persona en la sala", len((left or {}).get("members", [])) == 1)
    for _ in range(10):
        if api(base, "GET", f"/v1/room/{code}")["personas"] == 1:
            break
        time.sleep(0.5)
    failures += not check("la sala sigue existiendo", api(base, "GET", f"/v1/room/{code}")["personas"] == 1)

    print("\n8) Control desde administración")
    token = load_token()
    if token:
        rooms = api(base, "GET", "/admin/rooms", token)
        failures += not check("la sala aparece en la lista de admin", any(r["code"] == code for r in rooms.get("items", [])))
    else:
        print("  [salta] sin token de administración (OYM_ADMIN_TOKEN o ~/.oym-analytics.json)")

    host.ws.close()

    # La prueba del temporizador tarda 10 s: se pide aparte con OYM_TEST_SLOW=1.
    if os.environ.get("OYM_TEST_SLOW"):
        failures += check_prepare_timeout(base, ws_base)

    print(f"\n{'TODO CORRECTO' if failures == 0 else f'{failures} COMPROBACIONES FALLIDAS'}")
    print(f"(ida y vuelta del servidor: {host.rtt_ms:.0f} ms)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
