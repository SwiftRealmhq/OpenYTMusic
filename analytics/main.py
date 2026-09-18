"""
Backend de estadisticas y control de OpenYTMusic.

Que hace
--------
Recibe tres tipos de evento desde la app y responde siempre con el estado de
acceso de la red:

  heartbeat  -> usuarios activos (un latido por instalacion por dia)
  search     -> texto que busco el usuario
  play       -> cancion que empezo a sonar

Ademas expone endpoints de administracion (con token) para consultar todo y
gestionar la lista de baneos. El CLI `oymctl.py` usa esos endpoints.

Privacidad
----------
- La IP nunca la manda la app: la vemos por la conexion.
- En la base NO se guarda la IP en claro: se guarda su SHA-256 con un salt
  secreto (IP_SALT). Alcanza para contar abuso por red y para banear, pero no
  deja un registro de "quien busco que" con IP real.
- Los eventos guardan solo el ID de instalacion aleatorio (UUID), la version de
  la app y el dato en si.

Comportamiento a prueba de fallos
---------------------------------
Si el backend esta dormido o caido, la app sigue funcionando: el cliente es
fire-and-forget y su chequeo de acceso es fail-open (ver Telemetry.kt).
"""

import hashlib
import os
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

import psycopg
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool
from pydantic import BaseModel, Field

DATABASE_URL = os.environ.get("DATABASE_URL", "")
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")
IP_SALT = os.environ.get("IP_SALT", "openytmusic")
# Umbral opcional de auto-baneo por eventos en la ultima hora. 0 = desactivado
# (por defecto solo se registra: el baneo lo decides tu desde el CLI).
AUTO_BAN_EVENTS_PER_HOUR = int(os.environ.get("AUTO_BAN_EVENTS_PER_HOUR", "0"))

SCHEMA_FILE = Path(__file__).with_name("schema.sql")

pool: Optional[ConnectionPool] = None


def db() -> ConnectionPool:
    if pool is None:
        raise HTTPException(status_code=503, detail="base de datos no disponible")
    return pool


def hash_ip(ip: str) -> str:
    return hashlib.sha256(f"{IP_SALT}:{ip}".encode()).hexdigest()


def client_ip(request: Request) -> str:
    # Render pone la IP real en X-Forwarded-For (la primera de la lista).
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "desconocida"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global pool
    if DATABASE_URL:
        # dict_row: todas las consultas devuelven diccionarios, que es lo que
        # espera el CLI (y serializa directo a JSON).
        pool = ConnectionPool(
            DATABASE_URL,
            min_size=1,
            max_size=4,
            open=True,
            kwargs={"row_factory": dict_row},
        )
        with pool.connection() as connection:
            connection.execute(SCHEMA_FILE.read_text())
            connection.commit()
    yield
    if pool is not None:
        pool.close()


app = FastAPI(title="OpenYTMusic analytics", version="1.0", lifespan=lifespan)


# --------------------------------------------------------------------------- #
# Modelos
# --------------------------------------------------------------------------- #
class EventPayload(BaseModel):
    query: Optional[str] = None
    title: Optional[str] = None
    artist: Optional[str] = None


class Event(BaseModel):
    type: str = Field(pattern="^(heartbeat|search|play)$")
    install_id: str = Field(min_length=8, max_length=64)
    app_version: Optional[str] = None
    payload: EventPayload = EventPayload()


class BanRequest(BaseModel):
    ip: Optional[str] = None
    ip_hash: Optional[str] = None
    reason: Optional[str] = None


def require_admin(x_admin_token: Optional[str] = Header(default=None)) -> None:
    if not ADMIN_TOKEN or x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="token de administracion invalido")


# --------------------------------------------------------------------------- #
# API publica (la que usa la app)
# --------------------------------------------------------------------------- #
@app.get("/healthz")
def healthz():
    """Latido del propio servicio: lo usa el cronjob que evita que se duerma."""
    return {"ok": True, "time": datetime.now(timezone.utc).isoformat()}


def is_banned(connection, ip_hash: str) -> bool:
    row = connection.execute("SELECT 1 FROM bans WHERE ip_hash = %s", (ip_hash,)).fetchone()
    return row is not None


@app.post("/v1/event")
def receive_event(event: Event, request: Request):
    ip_hash = hash_ip(client_ip(request))
    with db().connection() as connection:
        connection.execute(
            """
            INSERT INTO installs (install_id, app_version)
            VALUES (%s, %s)
            ON CONFLICT (install_id) DO UPDATE
                SET last_seen = now(),
                    app_version = COALESCE(EXCLUDED.app_version, installs.app_version)
            """,
            (event.install_id, event.app_version),
        )

        if event.type == "search" and event.payload.query:
            connection.execute(
                "INSERT INTO searches (install_id, query, ip_hash) VALUES (%s, %s, %s)",
                (event.install_id, event.payload.query[:300], ip_hash),
            )
        elif event.type == "play" and event.payload.title:
            connection.execute(
                "INSERT INTO plays (install_id, title, artist, ip_hash) VALUES (%s, %s, %s, %s)",
                (
                    event.install_id,
                    event.payload.title[:300],
                    (event.payload.artist or "")[:300],
                    ip_hash,
                ),
            )

        connection.execute(
            """
            INSERT INTO ip_counters (ip_hash, events, events_hour, hour_bucket)
            VALUES (%s, 1, 1, date_trunc('hour', now()))
            ON CONFLICT (ip_hash) DO UPDATE
                SET last_seen = now(),
                    events = ip_counters.events + 1,
                    events_hour = CASE
                        WHEN ip_counters.hour_bucket = date_trunc('hour', now())
                        THEN ip_counters.events_hour + 1
                        ELSE 1
                    END,
                    hour_bucket = date_trunc('hour', now())
            """,
            (ip_hash,),
        )

        banned = is_banned(connection, ip_hash)

        if AUTO_BAN_EVENTS_PER_HOUR > 0 and not banned:
            row = connection.execute(
                "SELECT events_hour FROM ip_counters WHERE ip_hash = %s", (ip_hash,)
            ).fetchone()
            if row and row["events_hour"] > AUTO_BAN_EVENTS_PER_HOUR:
                connection.execute(
                    "INSERT INTO bans (ip_hash, reason) VALUES (%s, %s) "
                    "ON CONFLICT (ip_hash) DO NOTHING",
                    (ip_hash, f"auto: mas de {AUTO_BAN_EVENTS_PER_HOUR} eventos en una hora"),
                )
                banned = True

        connection.commit()

    return {"ok": True, "banned": banned}


@app.get("/v1/status")
def status(request: Request, install_id: str = "-"):
    ip_hash = hash_ip(client_ip(request))
    with db().connection() as connection:
        row = connection.execute(
            "SELECT reason FROM bans WHERE ip_hash = %s", (ip_hash,)
        ).fetchone()
    return {"banned": row is not None, "reason": row["reason"] if row else None}


# --------------------------------------------------------------------------- #
# API de administracion (CLI)
# --------------------------------------------------------------------------- #
@app.get("/admin/stats", dependencies=[Depends(require_admin)])
def stats():
    with db().connection() as connection:
        def one(sql, params=()):
            return connection.execute(sql, params).fetchone()["n"]

        return {
            "instalaciones_totales": one("SELECT count(*) AS n FROM installs"),
            "activos_hoy": one(
                "SELECT count(*) AS n FROM installs WHERE last_seen >= current_date"
            ),
            "activos_7d": one(
                "SELECT count(*) AS n FROM installs WHERE last_seen >= current_date - 7"
            ),
            "activos_30d": one(
                "SELECT count(*) AS n FROM installs WHERE last_seen >= current_date - 30"
            ),
            "busquedas_totales": one("SELECT count(*) AS n FROM searches"),
            "busquedas_hoy": one(
                "SELECT count(*) AS n FROM searches WHERE created_at >= current_date"
            ),
            "reproducciones_totales": one("SELECT count(*) AS n FROM plays"),
            "reproducciones_hoy": one(
                "SELECT count(*) AS n FROM plays WHERE created_at >= current_date"
            ),
            "ips_vistas": one("SELECT count(*) AS n FROM ip_counters"),
            "baneos_activos": one("SELECT count(*) AS n FROM bans"),
            "version_mas_usada": (
                connection.execute(
                    "SELECT app_version AS v FROM installs WHERE app_version IS NOT NULL "
                    "GROUP BY app_version ORDER BY count(*) DESC LIMIT 1"
                ).fetchone() or {"v": None}
            )["v"],
        }


@app.get("/admin/searches", dependencies=[Depends(require_admin)])
def list_searches(limit: int = 100, install_id: Optional[str] = None):
    with db().connection() as connection:
        if install_id:
            rows = connection.execute(
                "SELECT install_id, query, created_at FROM searches "
                "WHERE install_id = %s ORDER BY id DESC LIMIT %s",
                (install_id, min(limit, 1000)),
            ).fetchall()
        else:
            rows = connection.execute(
                "SELECT install_id, query, created_at FROM searches "
                "ORDER BY id DESC LIMIT %s",
                (min(limit, 1000),),
            ).fetchall()
    return {"items": rows}


@app.get("/admin/top-searches", dependencies=[Depends(require_admin)])
def top_searches(days: int = 7, limit: int = 30):
    with db().connection() as connection:
        rows = connection.execute(
            "SELECT lower(query) AS query, count(*) AS veces FROM searches "
            "WHERE created_at >= now() - make_interval(days => %s) "
            "GROUP BY lower(query) ORDER BY veces DESC LIMIT %s",
            (days, min(limit, 200)),
        ).fetchall()
    return {"items": rows}


@app.get("/admin/plays", dependencies=[Depends(require_admin)])
def list_plays(limit: int = 100):
    with db().connection() as connection:
        rows = connection.execute(
            "SELECT install_id, title, artist, created_at FROM plays "
            "ORDER BY id DESC LIMIT %s",
            (min(limit, 1000),),
        ).fetchall()
    return {"items": rows}


@app.get("/admin/top-songs", dependencies=[Depends(require_admin)])
def top_songs(days: int = 7, limit: int = 30):
    with db().connection() as connection:
        rows = connection.execute(
            "SELECT title, artist, count(*) AS veces FROM plays "
            "WHERE created_at >= now() - make_interval(days => %s) "
            "GROUP BY title, artist ORDER BY veces DESC LIMIT %s",
            (days, min(limit, 200)),
        ).fetchall()
    return {"items": rows}


@app.get("/admin/installs", dependencies=[Depends(require_admin)])
def list_installs(limit: int = 200):
    with db().connection() as connection:
        rows = connection.execute(
            "SELECT install_id, first_seen, last_seen, app_version FROM installs "
            "ORDER BY last_seen DESC LIMIT %s",
            (min(limit, 1000),),
        ).fetchall()
    return {"items": rows}


@app.get("/admin/ips", dependencies=[Depends(require_admin)])
def list_ips(limit: int = 100):
    """IPs (hasheadas) ordenadas por trafico, con su estado de baneo."""
    with db().connection() as connection:
        rows = connection.execute(
            """
            SELECT c.ip_hash, c.events, c.events_hour, c.first_seen, c.last_seen,
                   (b.ip_hash IS NOT NULL) AS banned, b.reason
            FROM ip_counters c
            LEFT JOIN bans b ON b.ip_hash = c.ip_hash
            ORDER BY c.last_seen DESC
            LIMIT %s
            """,
            (min(limit, 1000),),
        ).fetchall()
    return {"items": rows}


@app.post("/admin/ban", dependencies=[Depends(require_admin)])
def ban(body: BanRequest):
    if not body.ip and not body.ip_hash:
        raise HTTPException(status_code=400, detail="manda ip o ip_hash")
    ip_hash = body.ip_hash or hash_ip(body.ip or "")
    with db().connection() as connection:
        connection.execute(
            "INSERT INTO bans (ip_hash, reason) VALUES (%s, %s) "
            "ON CONFLICT (ip_hash) DO UPDATE SET reason = EXCLUDED.reason",
            (ip_hash, body.reason),
        )
        connection.commit()
    return {"ok": True, "ip_hash": ip_hash}


@app.delete("/admin/ban/{ip_hash}", dependencies=[Depends(require_admin)])
def unban(ip_hash: str):
    with db().connection() as connection:
        connection.execute("DELETE FROM bans WHERE ip_hash = %s", (ip_hash,))
        connection.commit()
    return {"ok": True, "ip_hash": ip_hash}


@app.get("/admin/bans", dependencies=[Depends(require_admin)])
def list_bans():
    with db().connection() as connection:
        rows = connection.execute(
            "SELECT ip_hash, reason, banned_at FROM bans ORDER BY banned_at DESC"
        ).fetchall()
    return {"items": rows}


@app.get("/admin/export", dependencies=[Depends(require_admin)])
def export_all():
    """
    Volcado completo de la base. Es el respaldo que hay que correr antes de que
    el Postgres gratis de Render cumpla los 30 dias (despues se puede recrear y
    reimportar lo que interese).
    """
    with db().connection() as connection:
        return {
            "exportado": datetime.now(timezone.utc).isoformat(),
            "installs": connection.execute("SELECT * FROM installs").fetchall(),
            "searches": connection.execute("SELECT * FROM searches ORDER BY id").fetchall(),
            "plays": connection.execute("SELECT * FROM plays ORDER BY id").fetchall(),
            "ip_counters": connection.execute("SELECT * FROM ip_counters").fetchall(),
            "bans": connection.execute("SELECT * FROM bans").fetchall(),
        }


@app.get("/admin/counts-by-day", dependencies=[Depends(require_admin)])
def counts_by_day(days: int = 30):
    """Serie diaria para ver si el uso crece o cae."""
    with db().connection() as connection:
        rows = connection.execute(
            """
            SELECT d::date AS dia,
                   (SELECT count(DISTINCT install_id) FROM installs i WHERE i.last_seen::date = d::date) AS activos,
                   (SELECT count(*) FROM searches s WHERE s.created_at::date = d::date) AS busquedas,
                   (SELECT count(*) FROM plays p WHERE p.created_at::date = d::date) AS reproducciones
            FROM generate_series(current_date - make_interval(days => %s), current_date, '1 day') AS d
            ORDER BY dia DESC
            """,
            (days,),
        ).fetchall()
    return {"items": rows}


@app.exception_handler(Exception)
async def on_error(request: Request, exc: Exception):
    # Nunca devolvemos un 500 crudo: la app solo necesita saber que no hay baneo.
    return JSONResponse(status_code=200, content={"ok": False, "banned": False})
