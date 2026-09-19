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
        (install es el UUID de la instalacion: si vuelve a entrar, reemplaza su
         conexion anterior en vez de aparecer dos veces en la sala)
  {"t":"state","track":{...},"position_ms":42000,"playing":true,"action":"play"}
  {"t":"sync","position_ms":..,"playing":..}       latido para corregir deriva
  {"t":"prepare","seq":<ms>,"track":{..},"position_ms":0,"playing":true}
  {"t":"ready","seq":<ms>}                "ya tengo la cancion cargada y lista"
  {"t":"go","seq":<ms>}                   arranque manual (respaldo del servidor)
  {"t":"rename","name":"Leo"}             cambia tu apodo en la sala
  {"t":"typing"}                           "estoy escribiendo" (no se guarda)
  {"t":"chat","text":"esto suena brutal"}
  {"t":"ping","at":<ms del cliente>}      sirve de latido: mantener vivo y medir relojes
  {"t":"bye"}                              salida limpia (avisa al instante)

Servidor -> cliente:
  {"t":"joined","code":..,"me":{..},"members":[..],"state":{..},"server_ms":..}
  {"t":"presence","members":[..],"server_ms":..}
  {"t":"state","from":..,"name":.., ...estado, "server_ms":..}
  {"t":"sync","from":..,"position_ms":..,"playing":..,"server_ms":..}
  {"t":"prepare","from":..,"name":..,"seq":..,"track":{..},"position_ms":..,"playing":..}
  {"t":"ready","from":..,"name":..,"seq":..}
  {"t":"go","seq":..,"track":{..},"position_ms":..,"playing":..,"server_ms":..}
  {"t":"renamed","name":..}
  {"t":"typing","from":..,"name":..,"server_ms":..}
  {"t":"chat","from":..,"name":..,"text":..,"server_ms":..}
  {"t":"pong","at":<eco>,"server_ms":..}
  {"t":"left","from":..,"name":..,"members":[..],"server_ms":..}
  {"t":"closed","reason":..}
  {"t":"error","msg":..}

Arranque en dos tiempos (prepare -> ready -> go)
------------------------------------------------
Cambiar de cancion no se manda como una orden cualquiera. Si cada uno empieza
cuando su stream termina de cargar, el que carga primero suena solo y el otro
lo pisa al entrar con un seek: se oye un reinicio. Por eso:

  1. Quien cambia la cancion manda `prepare` y su propio reproductor queda EN
     PAUSA.
  2. Todos cargan esa cancion (tambien en pausa) y avisan `ready`.
  3. Solo cuando el ultimo avisa, el servidor manda `go` A TODOS con la MISMA
     hora del servidor. Cada cliente arranca en `posicion + (ahora - server_ms)`,
     asi los dos empiezan en el mismo instante aunque tarden distinto en cargar.

Nada suena hasta ese `go`: ese es el punto. Si alguien no contesta (app matada,
red caida), el servidor suelta el `go` igual pasado ROOM_PREPARE_TIMEOUT_SECONDS
para que una sala nunca se quede muda esperando a un fantasma.

Vida de una sala
----------------
- Se crea con un codigo de 6 caracteres (POST /v1/room) y dura 24 h como maximo.
- Si uno se va, la sala sigue en pie para el otro.
- Un mismo `install` (misma instalacion de la app) nunca ocupa dos veces: al
  reconectar reemplaza su conexion anterior, asi que no aparecen "oyentes
  fantasma" ni queda frenando el arranque de la musica.
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
# Si alguien no avisa `ready` (app matada a media carga, red caida), el servidor
# suelta el `go` igual: mas vale empezar con un poco de desfase que quedarse
# mudos para siempre esperando a un fantasma.
PREPARE_TIMEOUT_SECONDS = float(os.environ.get("ROOM_PREPARE_TIMEOUT_SECONDS", "8"))
# Un miembro que lleva este tiempo sin dar señales de vida NO frena el arranque
# de los demas. La app manda `ping` cada 20 s incluso mientras carga, asi que
# quien calla mas de esto esta muerto y esperarlo solo alarga la pausa.
MEMBER_STALE_BLOCK_SECONDS = int(os.environ.get("ROOM_MEMBER_STALE_BLOCK_SECONDS", "12"))
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


def _clean_install(value: Any) -> str:
    """ID de instalacion (UUID aleatorio de la app).

    No identifica a nadie: solo sirve para reconocer que una conexion nueva es
    la MISMA instalacion que ya estaba en la sala. Con eso, cuando el socket se
    cae y la app se reconecta, el miembro viejo se reemplaza en el acto en vez
    de quedarse de fantasma en la lista del otro (el bug de "entran mas
    oyentes": el mismo telefono aparecia dos veces).
    """
    if not isinstance(value, str):
        return ""
    return value.strip()[:64]


def _clean_text(value: Any, limit: int) -> Optional[str]:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text[:limit] if text else None


def _clean_track(track: Any) -> Optional[dict[str, Any]]:
    """Deja de la cancion solo los campos que viajan entre los dos telefonos."""
    if not isinstance(track, dict):
        return None
    clean = {
        key: track.get(key)
        for key in ("id", "title", "artist", "artwork", "duration_ms", "album")
        if key in track
    }
    track_id = clean.get("id")
    if not isinstance(track_id, str) or not track_id.strip():
        return None
    clean["id"] = track_id.strip()[:64]
    for text_key, limit in (("title", 200), ("artist", 200), ("album", 200)):
        if isinstance(clean.get(text_key), str):
            clean[text_key] = clean[text_key][:limit]
    if isinstance(clean.get("artwork"), str):
        clean["artwork"] = clean["artwork"][:600]
    try:
        clean["duration_ms"] = max(0, min(int(clean.get("duration_ms") or 0), 24 * 3600 * 1000))
    except (TypeError, ValueError):
        clean["duration_ms"] = 0
    return clean


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
    # ID de la instalacion (no de la persona): permite reemplazar la conexion
    # vieja cuando la misma app vuelve a entrar.
    install: str = ""
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
class Prepare:
    """Un cambio de cancion en dos tiempos: falta que todos digan `ready`."""

    seq: int
    by: str
    track: dict[str, Any]
    position_ms: int
    playing: bool
    ready: set[str] = field(default_factory=set)
    # Quienes estaban en la sala cuando se pidio el cambio: alguien que entra
    # despues no puede dejar a los demas esperando.
    waiting: set[str] = field(default_factory=set)


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
    prepare: Optional[Prepare] = None
    prepare_task: Optional[asyncio.Task] = None

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

    def _release_ip(self, ip_hash: str) -> None:
        """Devuelve el cupo de conexiones de esa red (nunca deja contadores sueltos)."""
        if not ip_hash:
            return
        remaining = self.connections_by_ip.get(ip_hash, 1) - 1
        if remaining > 0:
            self.connections_by_ip[ip_hash] = remaining
        else:
            self.connections_by_ip.pop(ip_hash, None)

    async def join(
        self,
        room: Room,
        socket: WebSocket,
        name: str,
        ip_hash: str,
        install: str = "",
    ) -> Member:
        member = Member(id=uuid.uuid4().hex[:6], name=name, socket=socket, install=install)
        member_ips[member.id] = ip_hash
        replaced: list[Member] = []
        async with self.lock:
            # La misma instalacion vuelve a entrar: la conexion vieja ya no sirve
            # (quedo a medias tras un corte de red), asi que se reemplaza AQUI.
            # Sin esto el mismo telefono aparecia dos veces y el fantasma frenaba
            # el arranque de las canciones hasta que el barrido lo sacaba.
            if install:
                for other in list(room.members.values()):
                    if other.install and other.install == install:
                        room.members.pop(other.id, None)
                        self._release_ip(member_ips.pop(other.id, ""))
                        replaced.append(other)
            if len(room.members) >= MAX_MEMBERS:
                raise HTTPException(status_code=409, detail="la sala esta llena")
            room.members[member.id] = member
            room.empty_since = None
            self.connections_by_ip[ip_hash] = self.connections_by_ip.get(ip_hash, 0) + 1
        for other in replaced:
            try:
                await other.socket.close(code=4000, reason="reconectado")
            except Exception:
                pass
        return member

    async def leave(self, room: Room, member: Member, ip_hash: str = "") -> None:
        ip_hash = ip_hash or member_ips.pop(member.id, "")
        async with self.lock:
            room.members.pop(member.id, None)
            if not room.members:
                room.empty_since = time.monotonic()
            self._release_ip(ip_hash)
        # Si el que se fue era el que faltaba por avisar, la sala ya puede
        # arrancar: nadie debe quedarse esperando a alguien que ya no esta.
        await self.maybe_go(room)

    # ------------------------------------------------- arranque en dos tiempos
    async def start_prepare(
        self,
        room: Room,
        member: Member,
        seq: int,
        track: dict[str, Any],
        position_ms: int,
        playing: bool,
    ) -> None:
        """Anota el cambio de cancion y avisa a los demas para que la carguen."""
        if not track or not track.get("id"):
            return
        previous = room.prepare_task
        if previous is not None and not previous.done():
            previous.cancel()
        async with self.lock:
            room.prepare = Prepare(
                seq=seq,
                by=member.id,
                track=track,
                position_ms=position_ms,
                playing=playing,
                waiting=set(room.members),
            )
        # Mientras se prepara NADA suena: el estado guardado queda en pausa para
        # que quien entre a media preparacion no se ponga a sonar solo.
        await self.set_state(
            room,
            {
                "track": track,
                "position_ms": position_ms,
                "playing": False,
                "action": "prepare",
                "seq": seq,
                # La intencion (sonar o quedarse en pausa) viaja aparte porque el
                # estado en si queda en pausa mientras se prepara.
                "intent": playing,
                "by": member.name,
            },
        )
        room.prepare_task = asyncio.create_task(self._prepare_timeout(room, seq))
        await self.broadcast(
            room,
            {
                "t": "prepare",
                "from": member.id,
                "name": member.name,
                "seq": seq,
                "track": track,
                "position_ms": position_ms,
                "playing": playing,
            },
            skip=member.id,
        )

    async def _prepare_timeout(self, room: Room, seq: int) -> None:
        try:
            await asyncio.sleep(PREPARE_TIMEOUT_SECONDS)
        except asyncio.CancelledError:
            return
        await self.fire_go(room, seq)

    async def mark_ready(self, room: Room, member: Member, seq: int) -> None:
        prepare = room.prepare
        if prepare is None or prepare.seq != seq:
            return
        prepare.ready.add(member.id)
        await self.broadcast(
            room,
            {"t": "ready", "from": member.id, "name": member.name, "seq": seq},
            skip=member.id,
        )
        await self.maybe_go(room)

    async def maybe_go(self, room: Room) -> None:
        """Arranca si ya avisaron todos los que estaban cuando se pidio el cambio.

        Solo cuentan los que siguen DANDO SEÑALES DE VIDA: un miembro que lleva
        callado mas de [MEMBER_STALE_BLOCK_SECONDS] no puede dejar a la sala en
        pausa esperandolo (esa era la pausa larga que se sentia al cambiar de
        cancion).
        """
        prepare = room.prepare
        if prepare is None:
            return
        now = time.monotonic()
        pending = {
            member
            for member in prepare.waiting
            if member in room.members
            and (now - room.members[member].last_seen) <= MEMBER_STALE_BLOCK_SECONDS
        } - prepare.ready
        if pending:
            return
        await self.fire_go(room, prepare.seq)

    async def fire_go(self, room: Room, seq: int) -> None:
        """GO para TODOS (tambien quien lo pidio) con la misma hora del servidor.

        Esa hora comun es lo que hace que los dos empiecen en el mismo instante:
        cada uno calcula `posicion + (ahora - server_ms)` con su propio reloj
        corregido, y esa resta da el mismo momento en los dos telefonos.
        """
        prepare = room.prepare
        if prepare is None or prepare.seq != seq:
            return
        room.prepare = None
        task = room.prepare_task
        room.prepare_task = None
        # Nunca cancelarse a si misma: cuando el arranque lo suelta el propio
        # temporizador de respaldo, esta corrutina ES room.prepare_task, y
        # cancelarla cortaba el aviso justo antes de enviarlo (la sala se
        # quedaba muda para siempre si alguien no avisaba `ready`).
        if task is not None and task is not asyncio.current_task() and not task.done():
            task.cancel()

        at_ms = _now_ms()
        await self.set_state_at(
            room,
            {
                "track": prepare.track,
                "position_ms": prepare.position_ms,
                "playing": prepare.playing,
                "action": "go",
                "seq": seq,
                "by": prepare.by,
            },
            at_ms,
        )
        await self.broadcast(
            room,
            {
                "t": "go",
                "seq": seq,
                "track": prepare.track,
                "position_ms": prepare.position_ms,
                "playing": prepare.playing,
            },
            at_ms=at_ms,
        )

    async def _send(self, member: Member, message: dict[str, Any]) -> bool:
        try:
            await member.socket.send_json(message)
            return True
        except Exception:
            return False

    async def _drop_dead(self, room: Room, dead: list[Member]) -> None:
        """Saca de la sala a los sockets que ya no responden y avisa a los demas."""
        for member in dead:
            if room.members.pop(member.id, None) is None:
                continue
            self._release_ip(member_ips.pop(member.id, ""))
            try:
                await member.socket.close(code=1001)
            except Exception:
                pass
        if not room.members:
            return
        await self.maybe_go(room)

    async def _announce_presence(self, room: Room, depth: int = 0) -> None:
        """Manda la lista de miembros actualizada a los que quedan.

        Hace falta SIEMPRE que alguien sale por la puerta de atras (socket muerto):
        el aviso que disparo la limpieza llevaba la lista vieja, asi que sin esto
        el otro se quedaba viendo a alguien que ya no estaba hasta el siguiente
        aviso. La profundidad corta cualquier reenvio en cadena.
        """
        if depth > 3 or not room.members:
            return
        message = {"t": "presence", "members": room.public_members(), "server_ms": _now_ms()}
        again: list[Member] = []
        for member in list(room.members.values()):
            if not await self._send(member, message):
                again.append(member)
        if again:
            await self._drop_dead(room, again)
            await self._announce_presence(room, depth + 1)

    async def broadcast(
        self,
        room: Room,
        payload: dict[str, Any],
        skip: Optional[str] = None,
        at_ms: Optional[int] = None,
    ) -> None:
        message = {**payload, "server_ms": at_ms or _now_ms()}
        dead: list[Member] = []
        for member in list(room.members.values()):
            if skip is not None and member.id == skip:
                continue
            if not await self._send(member, message):
                dead.append(member)
        if not dead:
            return
        # Un socket muerto no puede quedarse en la lista de la sala: antes se
        # borraba en silencio y el otro seguia viendo a alguien que ya no estaba
        # (y su cupo de red se quedaba contado).
        await self._drop_dead(room, dead)
        await self._announce_presence(room)

    async def set_state(self, room: Room, state: dict[str, Any]) -> None:
        await self.set_state_at(room, state, _now_ms())

    async def set_state_at(self, room: Room, state: dict[str, Any], at_ms: int) -> None:
        room.state = state
        room.state_ms = at_ms
        room.restored = False
        await self.persist(room)

    async def kill(self, code: str, reason: str = "la sala se cerro") -> Optional[Room]:
        async with self.lock:
            room = self.rooms.pop(code.upper(), None)
        await self.delete(code)
        if room is not None and room.prepare_task is not None:
            room.prepare_task.cancel()
            room.prepare_task = None
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
        member = await manager.join(
            room,
            socket,
            _clean_name(hello.get("name")),
            ip_hash,
            _clean_install(hello.get("install")),
        )
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
            # Si ya no estoy en la sala es que una conexion mas nueva de mi misma
            # instalacion me reemplazo: esta conexion vieja termina aqui y no
            # puede volver a hablar (era lo que duplicaba las ordenes).
            if member.id not in room.members:
                return
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

            if kind == "rename":
                # Cambiar el apodo dentro de la sala: sin cuentas ni nada guardado.
                member.name = _clean_name(message.get("name"))
                await socket.send_json({"t": "renamed", "name": member.name, "server_ms": _now_ms()})
                await manager.broadcast(room, {"t": "presence", "members": room.public_members()})
                continue

            if kind == "prepare":
                # Cambio de cancion en dos tiempos: todos la cargan y solo cuando
                # el ultimo avisa, sale el `go` con una hora comun para todos.
                track = message.get("track") if isinstance(message.get("track"), dict) else None
                track = _clean_track(track)
                if not track:
                    continue
                await manager.start_prepare(
                    room,
                    member,
                    seq=_bounded_int(message.get("seq"), 1, 1 << 62),
                    track=track,
                    position_ms=_bounded_int(message.get("position_ms"), 0, 24 * 3600 * 1000),
                    playing=bool(message.get("playing", True)),
                )
                continue

            if kind == "ready":
                await manager.mark_ready(room, member, _bounded_int(message.get("seq"), 1, 1 << 62))
                continue

            if kind == "go":
                # Respaldo: si el cliente no ve el arranque, lo pide el mismo.
                await manager.fire_go(room, _bounded_int(message.get("seq"), 1, 1 << 62))
                continue

            if kind == "typing":
                # "esta escribiendo": solo se retransmite, no se guarda ni se
                # convierte en estado (el otro lo muestra unos segundos y ya).
                await manager.broadcast(
                    room,
                    {"t": "typing", "from": member.id, "name": member.name},
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
                track = _clean_track(message.get("track"))
                if action in {"play", "track"} and not (track and track.get("id")):
                    continue
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
