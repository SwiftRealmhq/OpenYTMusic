"""
Salas de escucha compartida (escuchar musica con alguien mas).

Idea
----
No es una llamada de audio: cada dispositivo reproduce su PROPIO stream desde
YouTube Music y entre los dos solo viaja el estado de reproduccion (~50 bytes):
que cancion, en que segundo y si esta sonando. El servidor nunca toca el audio.

El servidor es el reloj de la sala
----------------------------------
Todo mensaje que sale lleva `server_ms` (hora del servidor). El cliente mide su
desfase con un `ping` y asi el invitado arranca en `posicion + (ahora - server_ms)`
en vez de en la posicion cruda. Sin eso, el invitado siempre va un poco atras.

Protocolo (JSON, un mensaje por frame)
--------------------------------------
Cliente -> servidor:
  {"t":"hello","name":"Leo","install":"<uuid>"}    primer mensaje, obligatorio
  {"t":"state","track":{...},"position_ms":42000,"playing":true,"action":"play"}
  {"t":"sync","position_ms":..,"playing":..}       latido para corregir deriva
  {"t":"chat","text":"esto suena brutal"}
  {"t":"ping","at":<ms del cliente>}      sirve de latido: mantener vivo y medir relojes
  {"t":"bye"}                              salida limpia (avisa al instante)

Servidor -> cliente:
  {"t":"joined","code":..,"me":{..},"members":[..],"state":{..},"server_ms":..}
  {"t":"presence","members":[..],"server_ms":..}
  {"t":"state","from":..,"name":.., ...estado, "server_ms":..}
  {"t":"sync","from":..,"position_ms":..,"playing":..,"server_ms":..}
  {"t":"chat","from":..,"name":..,"text":..,"server_ms":..}
  {"t":"pong","at":<eco>,"server_ms":..}
  {"t":"left","from":..,"name":..,"members":[..],"server_ms":..}
  {"t":"closed","reason":..}
  {"t":"error","msg":..}

Vida de una sala
----------------
- Se crea con un codigo de 6 caracteres (POST /v1/room) y dura 24 h como maximo.
- Si uno se va, la sala sigue en pie para el otro.
- Cuando se van todos queda un margen de reconexion (ROOM_EMPTY_GRACE_SECONDS)
  y despues se borra.
- El estado vive en memoria del proceso; si Render reinicia el servicio, la sala
  se recarga desde Postgres EN PAUSA (si el servicio estuvo caido 10 minutos,
  seguir corriendo el reloj daria un salto falso) y los que se reconectan siguen
  con la misma cancion.

Privacidad
----------
El chat NO se guarda: solo se retransmite a quien esta conectado. En la base
queda unicamente el estado minimo de reproduccion para sobrevivir a un reinicio,
sin usuarios ni mensajes, y se borra al expirar la sala.
"""

import asyncio
import json
import os
import random
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any, Callable, Optional

from fastapi import APIRouter, Header, HTTPException, Request, WebSocket, WebSocketDisconnect

# --------------------------------------------------------------------------- #
# Configuracion
# --------------------------------------------------------------------------- #
# Sin letras ni numeros que se confunden al dictarlos (I/O/0/1). 32^6 = mil
# millones de combinaciones: adivinar un codigo a mano es inviable.
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 6

ROOM_TTL_HOURS = int(os.environ.get("ROOM_TTL_HOURS", "24"))
# Margen en el que una sala vacia sigue existiendo: cubre que a uno se le caiga
# la red y vuelva a entrar.
ROOM_EMPTY_GRACE_SECONDS = int(os.environ.get("ROOM_EMPTY_GRACE_SECONDS", "300"))
MAX_MEMBERS = int(os.environ.get("ROOM_MAX_MEMBERS", "4"))

# Limites anti-abuso
MAX_CONNECTIONS_PER_IP = int(os.environ.get("ROOM_MAX_CONNECTIONS_PER_IP", "8"))
MAX_ROOMS_PER_IP_HOUR = int(os.environ.get("ROOM_MAX_PER_IP_HOUR", "20"))
MESSAGES_PER_WINDOW = int(os.environ.get("ROOM_MESSAGES_PER_10S", "40"))
MESSAGE_WINDOW_SECONDS = 10
# Si un miembro no da señales de vida en este tiempo, se le saca de la sala y se
# avisa al otro. La app manda un `ping` cada 20 s, asi que 75 s solo se cumplen
# si de verdad desaparecio (app cerrada de golpe, red caida, telefono apagado).
MEMBER_IDLE_SECONDS = int(os.environ.get("ROOM_MEMBER_IDLE_SECONDS", "75"))
MAX_MESSAGE_BYTES = 8192
MAX_CHAT_CHARS = 400
MAX_NAME_CHARS = 24

ALLOWED_ACTIONS = {"play", "pause", "seek", "track", "queue", "stop"}

# Lo que main.py nos presta. Se inyecta con configure() para mantener ahi el
# manejo de la base de datos y de la IP real (y no importar main desde aqui, que
# seria un ciclo).
_pool_getter: Optional[Callable[[], Any]] = None
_ban_checker: Optional[Callable[[Any, str], bool]] = None
_ip_of: Optional[Callable[[Any], str]] = None
_hash_ip: Optional[Callable[[str], str]] = None
_admin_check: Optional[Callable[[Optional[str]], None]] = None
_cleanup_task: Optional[asyncio.Task] = None


def configure(
    *,
    pool_getter: Callable[[], Any],
    ban_checker: Callable[[Any, str], bool],
    ip_of: Callable[[Any], str],
    hash_ip: Callable[[str], str],
    admin_check: Callable[[Optional[str]], None],
) -> None:
    global _pool_getter, _ban_checker, _ip_of, _hash_ip, _admin_check
    _pool_getter = pool_getter
    _ban_checker = ban_checker
    _ip_of = ip_of
    _hash_ip = hash_ip
    _admin_check = admin_check


def _not_allowed(message: str) -> None:
    """admin_check no devuelve nada: si el token falla, levanta HTTPException."""
    raise HTTPException(status_code=403, detail=message)


def _now_ms() -> int:
    return int(time.time() * 1000)


def _clean_name(name: Any) -> str:
    if not isinstance(name, str) or not name.strip():
        return "Alguien"
    return name.strip()[:MAX_NAME_CHARS]


def _clean_text(value: Any, limit: int) -> Optional[str]:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text[:limit] if text else None


def _bounded_int(value: Any, low: int, high: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        return low
    return max(low, min(number, high))


# --------------------------------------------------------------------------- #
# Estado en memoria
# --------------------------------------------------------------------------- #
@dataclass
class Member:
    id: str
    name: str
    socket: WebSocket
    window_start: float = field(default_factory=time.monotonic)
    window_count: int = 0
    last_seen: float = field(default_factory=time.monotonic)

    def touch(self) -> None:
        self.last_seen = time.monotonic()

    def allow_message(self) -> bool:
        """Tope simple por ventana: frena a quien quiera inundar la sala."""
        now = time.monotonic()
        if now - self.window_start >= MESSAGE_WINDOW_SECONDS:
            self.window_start = now
            self.window_count = 0
        self.window_count += 1
        return self.window_count <= MESSAGES_PER_WINDOW


@dataclass
class Room:
    code: str
    created_at: datetime
    expires_at: datetime
    members: dict[str, Member] = field(default_factory=dict)
    state: dict[str, Any] = field(default_factory=dict)
    state_ms: int = 0
    empty_since: Optional[float] = None
    restored: bool = False

    def public_members(self) -> list[dict[str, Any]]:
        return [{"id": member.id, "name": member.name} for member in self.members.values()]

    def projected_state(self) -> dict[str, Any]:
        """Estado con la posicion ya proyectada al momento de entrar.

        Asi el que entra se sincroniza sin tener que calcular desfases: se le da
        el segundo exacto en el que deberia estar sonando.
        """
        state = dict(self.state or {})
        if not state:
            return {}
        position = state.get("position_ms") or 0
        if state.get("playing") and self.state_ms:
            elapsed = max(_now_ms() - self.state_ms, 0)
            position += elapsed
            track = state.get("track") or {}
            duration = track.get("duration_ms")
            if isinstance(duration, (int, float)) and duration > 0 and position > duration:
                position = duration
        return {**state, "position_ms": int(position), "projected": True}


class RoomManager:
    def __init__(self) -> None:
        self.rooms: dict[str, Room] = {}
        self.connections_by_ip: dict[str, int] = {}
        self.rooms_created_by_ip: dict[str, list[float]] = {}
        self.lock = asyncio.Lock()

    # ----------------------------------------------------------------- codigos
    def _new_code(self) -> str:
        while True:
            code = "".join(random.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))
            if code not in self.rooms:
                return code

    def _check_quota(self, ip_hash: str) -> None:
        now = time.time()
        recent = [stamp for stamp in self.rooms_created_by_ip.get(ip_hash, []) if now - stamp < 3600]
        self.rooms_created_by_ip[ip_hash] = recent
        if len(recent) >= MAX_ROOMS_PER_IP_HOUR:
            raise HTTPException(status_code=429, detail="demasiadas salas creadas desde esta red")
        if self.connections_by_ip.get(ip_hash, 0) >= MAX_CONNECTIONS_PER_IP:
            raise HTTPException(status_code=429, detail="demasiadas conexiones desde esta red")

    # ------------------------------------------------------------------- salas
    async def create(self, ip_hash: str) -> Room:
        async with self.lock:
            self._check_quota(ip_hash)
            now = datetime.now(timezone.utc)
            room = Room(
                code=self._new_code(),
                created_at=now,
                expires_at=now + timedelta(hours=ROOM_TTL_HOURS),
            )
            self.rooms[room.code] = room
            self.rooms_created_by_ip.setdefault(ip_hash, []).append(time.time())
        await self.persist(room)
        return room

    def get(self, code: str) -> Optional[Room]:
        return self.rooms.get(code.upper())

    async def get_or_restore(self, code: str) -> Optional[Room]:
        room = self.get(code)
        return room if room is not None else await self.restore(code)

    async def restore(self, code: str) -> Optional[Room]:
        """Recarga la sala desde Postgres si el proceso se reinicio."""
        pool = self.pool()
        if pool is None:
            return None

        def load():
            with pool.connection() as connection:
                return connection.execute(
                    """
                    SELECT code, state, EXTRACT(EPOCH FROM created_at) AS created_at,
                           EXTRACT(EPOCH FROM expires_at) AS expires_at
                    FROM rooms WHERE code = %s AND expires_at > now()
                    """,
                    (code.upper(),),
                ).fetchone()

        try:
            row = await asyncio.to_thread(load)
        except Exception:
            return None
        if row is None:
            return None

        created = datetime.fromtimestamp(float(row["created_at"]), tz=timezone.utc)
        expires = datetime.fromtimestamp(float(row["expires_at"]), tz=timezone.utc)
        state = row["state"] or {}
        room = Room(
            code=row["code"],
            created_at=created,
            expires_at=expires,
            state={**state, "playing": False, "action": "restore"},
            state_ms=0,
            restored=True,
        )
        async with self.lock:
            self.rooms[room.code] = room
        return room

    async def join(self, room: Room, socket: WebSocket, name: str, ip_hash: str) -> Member:
        member = Member(id=uuid.uuid4().hex[:6], name=name, socket=socket)
        member_ips[member.id] = ip_hash
        async with self.lock:
            if len(room.members) >= MAX_MEMBERS:
                raise HTTPException(status_code=409, detail="la sala esta llena")
            room.members[member.id] = member
            room.empty_since = None
            self.connections_by_ip[ip_hash] = self.connections_by_ip.get(ip_hash, 0) + 1
        return member

    async def leave(self, room: Room, member: Member, ip_hash: str = "") -> None:
        ip_hash = ip_hash or member_ips.pop(member.id, "")
        async with self.lock:
            room.members.pop(member.id, None)
            if not room.members:
                room.empty_since = time.monotonic()
            remaining = self.connections_by_ip.get(ip_hash, 1) - 1
            if remaining > 0:
                self.connections_by_ip[ip_hash] = remaining
            else:
                self.connections_by_ip.pop(ip_hash, None)

    async def broadcast(self, room: Room, payload: dict[str, Any], skip: Optional[str] = None) -> None:
        message = {**payload, "server_ms": _now_ms()}
        for member in list(room.members.values()):
            if skip is not None and member.id == skip:
                continue
            try:
                await member.socket.send_json(message)
            except Exception:
                # El socket ya no responde: su propio finally lo saca de la sala.
                room.members.pop(member.id, None)

    async def set_state(self, room: Room, state: dict[str, Any]) -> None:
        room.state = state
        room.state_ms = _now_ms()
        room.restored = False
        await self.persist(room)

    async def kill(self, code: str, reason: str = "la sala se cerro") -> Optional[Room]:
        async with self.lock:
            room = self.rooms.pop(code.upper(), None)
        await self.delete(code)
        if room is None:
            return None
        await self.broadcast(room, {"t": "closed", "reason": reason})
        for member in list(room.members.values()):
            try:
                await member.socket.close(code=1000)
            except Exception:
                pass
        room.members.clear()
        return room

    # ------------------------------------------------------------ persistencia
    def pool(self):
        return _pool_getter() if _pool_getter else None

    async def persist(self, room: Room) -> None:
        """Respaldo minimo: solo para sobrevivir a un reinicio del servicio."""
        pool = self.pool()
        if pool is None:
            return

        def save():
            with pool.connection() as connection:
                connection.execute(
                    """
                    INSERT INTO rooms (code, state, created_at, updated_at, expires_at)
                    VALUES (%s, %s::jsonb, %s, now(), %s)
                    ON CONFLICT (code) DO UPDATE
                        SET state = EXCLUDED.state, updated_at = now(), expires_at = EXCLUDED.expires_at
                    """,
                    (room.code, json.dumps(room.state), room.created_at, room.expires_at),
                )
                connection.commit()

        try:
            await asyncio.to_thread(save)
        except Exception:
            # Sin base la sala sigue viva en memoria: el respaldo es una
            # comodidad, no un requisito.
            pass

    async def delete(self, code: str) -> None:
        pool = self.pool()
        if pool is None:
            return

        def drop():
            with pool.connection() as connection:
                connection.execute("DELETE FROM rooms WHERE code = %s", (code.upper(),))
                connection.commit()

        try:
            await asyncio.to_thread(drop)
        except Exception:
            pass

    async def sweep_idle_members(self) -> None:
        """Saca a quien desaparecio sin despedirse.

        Sin esto, si a alguien se le cierra la app de golpe, el servidor tarda
        en notar el socket muerto y el otro se queda esperando a un fantasma.
        """
        monotonic_now = time.monotonic()
        for room in list(self.rooms.values()):
            for member in list(room.members.values()):
                if monotonic_now - member.last_seen <= MEMBER_IDLE_SECONDS:
                    continue
                await self.leave(room, member)
                try:
                    await member.socket.close(code=1001)
                except Exception:
                    pass
                await self.broadcast(
                    room,
                    {"t": "left", "from": member.id, "name": member.name, "members": room.public_members()},
                )

    async def cleanup(self) -> None:
        """Cierra salas expiradas o vacias de mas (memoria y base)."""
        wall_now = datetime.now(timezone.utc)
        monotonic_now = time.monotonic()
        for code, room in list(self.rooms.items()):
            expired = room.expires_at <= wall_now
            empty_too_long = (
                room.empty_since is not None
                and monotonic_now - room.empty_since > ROOM_EMPTY_GRACE_SECONDS
            )
            if (expired or empty_too_long) and not room.members:
                self.rooms.pop(code, None)
                await self.delete(code)

        pool = self.pool()
        if pool is None:
            return

        def purge():
            with pool.connection() as connection:
                connection.execute("DELETE FROM rooms WHERE expires_at <= now()")
                connection.commit()

        try:
            await asyncio.to_thread(purge)
        except Exception:
            pass

    def snapshot(self) -> list[dict[str, Any]]:
        """Vista para el CLI de administracion."""
        rows = []
        for room in self.rooms.values():
            state = room.state or {}
            track = state.get("track") or {}
            rows.append(
                {
                    "code": room.code,
                    "personas": len(room.members),
                    "nombres": ", ".join(member.name for member in room.members.values()),
                    "cancion": track.get("title"),
                    "artista": track.get("artist"),
                    "segundo": round((state.get("position_ms") or 0) / 1000),
                    "sonando": "SI" if state.get("playing") else "no",
                    "creada": room.created_at.isoformat()[:19],
                    "expira": room.expires_at.isoformat()[:19],
                }
            )
        return sorted(rows, key=lambda row: row["creada"], reverse=True)


manager = RoomManager()
router = APIRouter()

# ip_hash de cada miembro: hace falta para descontar el cupo por red cuando se
# le saca por inactividad (el finally ya no esta para pasarlo).
member_ips: dict[str, str] = {}


async def _cleanup_loop() -> None:
    while True:
        try:
            await asyncio.sleep(30)
            await manager.sweep_idle_members()
            await manager.cleanup()
        except asyncio.CancelledError:
            return
        except Exception:
            continue


def start_background_tasks() -> None:
    global _cleanup_task
    if _cleanup_task is None:
        _cleanup_task = asyncio.create_task(_cleanup_loop())


async def stop_background_tasks() -> None:
    global _cleanup_task
    if _cleanup_task is not None:
        _cleanup_task.cancel()
        _cleanup_task = None


# --------------------------------------------------------------------------- #
# API
# --------------------------------------------------------------------------- #
def _requester(request: Request) -> str:
    """Hash de la IP (nunca en claro) usando el mismo salt que las estadisticas."""
    if _ip_of is None or _hash_ip is None:
        return "sin-ip"
    return _hash_ip(_ip_of(request))


def _is_banned(ip_hash: str) -> bool:
    pool = manager.pool()
    if pool is None or _ban_checker is None:
        return False
    try:
        with pool.connection() as connection:
            return bool(_ban_checker(connection, ip_hash))
    except Exception:
        # Fail-open: si la base falla, la sala funciona igual.
        return False


def _websocket_ip(socket: WebSocket) -> str:
    if _ip_of is None or _hash_ip is None:
        return "sin-ip"
    return _hash_ip(_ip_of(socket))


@router.post("/v1/room")
async def create_room(request: Request):
    """Crea una sala y devuelve el codigo que se comparte."""
    ip_hash = _requester(request)
    if _is_banned(ip_hash):
        raise HTTPException(status_code=403, detail="acceso restringido desde esta red")
    room = await manager.create(ip_hash)
    return {
        "code": room.code,
        "expires_at": room.expires_at.isoformat(),
        "max_members": MAX_MEMBERS,
    }


@router.get("/v1/room/{code}")
async def room_exists(code: str):
    room = manager.get(code)
    if room is None:
        raise HTTPException(status_code=404, detail="esa sala no existe o ya expiro")
    return {
        "code": room.code,
        "personas": len(room.members),
        "max_members": MAX_MEMBERS,
        "expires_at": room.expires_at.isoformat(),
    }


@router.websocket("/v1/room/{code}/ws")
async def room_socket(socket: WebSocket, code: str):
    code = code.upper()
    await socket.accept()

    async def refuse(message: str, close_code: int = 1008) -> None:
        try:
            await socket.send_json({"t": "error", "msg": message})
            await socket.close(code=close_code)
        except Exception:
            pass

    # Primer mensaje obligatorio: identidad. Da 15 s o se corta.
    try:
        hello = json.loads(await asyncio.wait_for(socket.receive_text(), timeout=15))
    except Exception:
        await refuse("esperaba un mensaje de entrada")
        return
    if not isinstance(hello, dict) or hello.get("t") != "hello":
        await refuse("el primer mensaje debe ser hello")
        return

    ip_hash = _websocket_ip(socket)
    if _is_banned(ip_hash):
        await refuse("acceso restringido desde esta red", close_code=1011)
        return

    room = await manager.get_or_restore(code)
    if room is None:
        await refuse("esa sala no existe o ya expiro", close_code=1001)
        return

    try:
        member = await manager.join(room, socket, _clean_name(hello.get("name")), ip_hash)
    except HTTPException as error:
        await refuse(str(error.detail), close_code=1008)
        return

    await socket.send_json(
        {
            "t": "joined",
            "code": room.code,
            "me": {"id": member.id, "name": member.name},
            "members": room.public_members(),
            "state": room.projected_state(),
            "restored": room.restored,
            "server_ms": _now_ms(),
        }
    )
    await manager.broadcast(room, {"t": "presence", "members": room.public_members()}, skip=member.id)

    try:
        while True:
            raw = await socket.receive_text()
            if len(raw) > MAX_MESSAGE_BYTES:
                await refuse("mensaje demasiado largo")
                return
            member.touch()
            if not member.allow_message():
                await refuse("demasiados mensajes seguidos, espera unos segundos")
                return
            try:
                message = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if not isinstance(message, dict):
                continue

            kind = message.get("t")

            if kind == "bye":
                # Salida limpia: el aviso a la sala sale del finally de abajo, al
                # instante, sin esperar a que el socket se de cuenta.
                return

            if kind == "ping":
                # Sirve para medir el desfase de relojes (ida y vuelta).
                await socket.send_json({"t": "pong", "at": message.get("at"), "server_ms": _now_ms()})
                continue

            if kind == "sync":
                # Latido del actor: permite corregir deriva sin cambiar cancion.
                await manager.broadcast(
                    room,
                    {
                        "t": "sync",
                        "from": member.id,
                        "name": member.name,
                        "position_ms": _bounded_int(message.get("position_ms"), 0, 24 * 3600 * 1000),
                        "playing": bool(message.get("playing")),
                    },
                    skip=member.id,
                )
                continue

            if kind == "chat":
                text = _clean_text(message.get("text"), MAX_CHAT_CHARS)
                if text is None:
                    continue
                await manager.broadcast(
                    room,
                    {"t": "chat", "from": member.id, "name": member.name, "text": text},
                )
                continue

            if kind == "state":
                action = message.get("action")
                if action not in ALLOWED_ACTIONS:
                    continue
                track = message.get("track") if isinstance(message.get("track"), dict) else None
                if action in {"play", "track"} and not (track and track.get("id")):
                    continue
                if track is not None:
                    track = {
                        key: value
                        for key, value in track.items()
                        if key in {"id", "title", "artist", "artwork", "duration_ms", "album"}
                    }
                state = {
                    "track": track if track is not None else (room.state or {}).get("track"),
                    "position_ms": _bounded_int(message.get("position_ms"), 0, 24 * 3600 * 1000),
                    "playing": bool(message.get("playing")),
                    "action": action,
                    "by": member.name,
                }
                await manager.set_state(room, state)
                await manager.broadcast(
                    room,
                    {"t": "state", "from": member.id, "name": member.name, **state},
                    skip=member.id,
                )
                continue

    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        # Solo se avisa si sigue dentro: si el barrido de inactivos ya lo saco,
        # el aviso salio de ahi y repetirlo confundiria a los que quedan.
        if member.id in room.members:
            await manager.leave(room, member, ip_hash)
            await manager.broadcast(
                room,
                {"t": "left", "from": member.id, "name": member.name, "members": room.public_members()},
            )


# --------------------------------------------------------------------------- #
# Administracion (lo que ve el CLI)
# --------------------------------------------------------------------------- #
@router.get("/admin/rooms")
async def list_rooms(x_admin_token: Optional[str] = Header(default=None)):
    if _admin_check is None:
        _not_allowed("administracion no configurada")
    _admin_check(x_admin_token)
    return {
        "items": manager.snapshot(),
        "max_members": MAX_MEMBERS,
        "ttl_hours": ROOM_TTL_HOURS,
    }


@router.delete("/admin/room/{code}")
async def kill_room(code: str, x_admin_token: Optional[str] = Header(default=None)):
    if _admin_check is None:
        _not_allowed("administracion no configurada")
    _admin_check(x_admin_token)
    room = await manager.kill(code, "la sala se cerro desde administracion")
    if room is None:
        raise HTTPException(status_code=404, detail="no hay una sala activa con ese codigo")
    return {"code": room.code, "cerrada": True}
