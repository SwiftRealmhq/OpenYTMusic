#!/usr/bin/env python3
"""
oymctl — CLI de OpenYTMusic para ver las estadisticas y manejar los baneos.

Solo usa la libreria estandar de Python (no hay que instalar nada) y habla con
el backend por HTTP.

Configuracion (una de las dos):

  1) Variables de entorno:
       export OYM_BACKEND_URL="https://openytmusic-analytics.onrender.com"
       export OYM_ADMIN_TOKEN="el token del servicio"

  2) Archivo ~/.oym-analytics.json:
       {"url": "https://...", "token": "..."}

El token sale de Render: servicio -> Environment -> ADMIN_TOKEN.

Modo interactivo (el comando por defecto): todas las vistas en una pantalla con
scroll. Teclas:
    ↑↓ / j k        mover fila a fila
    PgUp / PgDn     página arriba/abajo
    g / G           inicio / fin
    ← →  o  1-9     cambiar de vista
    d               cambiar los días (vistas por rango)
    r               recargar
    b               banear (la fila seleccionada en IPs/Baneos, o una IP a mano)
    u               desbanear (fila seleccionada, o ip_hash a mano)
    x               cerrar la sala seleccionada (vista Salas)
    q / Esc         salir

Uso:
    python3 oymctl.py            # registro navegable (modo interactivo)
    python3 oymctl.py ui         # lo mismo, forzado
    python3 oymctl.py stats
    python3 oymctl.py searches 50
    python3 oymctl.py top-searches 7
    python3 oymctl.py plays 50
    python3 oymctl.py top-songs 7
    python3 oymctl.py installs 100
    python3 oymctl.py ips 50
    python3 oymctl.py days 14
    python3 oymctl.py bans
    python3 oymctl.py rooms
    python3 oymctl.py close-room ABC234
    python3 oymctl.py ban 190.12.34.56 "abuso de busquedas"
    python3 oymctl.py unban <ip_hash>
    python3 oymctl.py export
    python3 oymctl.py health
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

CONFIG_FILE = Path.home() / ".oym-analytics.json"


def load_config() -> tuple[str, str]:
    url = os.environ.get("OYM_BACKEND_URL", "").strip()
    token = os.environ.get("OYM_ADMIN_TOKEN", "").strip()
    if (not url or not token) and CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text())
            url = url or str(data.get("url", "")).strip()
            token = token or str(data.get("token", "")).strip()
        except (OSError, json.JSONDecodeError) as error:
            print(f"no pude leer {CONFIG_FILE}: {error}", file=sys.stderr)
    if not url:
        sys.exit(
            "Falta la URL del backend.\n"
            f"Define OYM_BACKEND_URL o crea {CONFIG_FILE} con "
            '{"url": "...", "token": "..."}'
        )
    return url.rstrip("/"), token


class ApiError(Exception):
    """Fallo hablando con el backend (red, HTTP o timeout)."""


def api(url: str, token: str, method: str, path: str, body: dict | None = None, timeout: int = 45):
    """Peticion al backend. Levanta ApiError en vez de salir del proceso: la TUI
    tiene que seguir viva aunque una consulta falle."""
    request = urllib.request.Request(
        f"{url}{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "X-Admin-Token": token,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "oymctl/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:300]
        raise ApiError(f"el backend respondio {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise ApiError(
            f"no pude conectar con {url}: {error.reason}. "
            "Si el servicio estaba dormido, espera un minuto y reintenta."
        ) from error
    except (TimeoutError, OSError) as error:
        raise ApiError(f"el backend tardo demasiado en responder: {error}") from error


def call(url: str, token: str, method: str, path: str, body: dict | None = None):
    """Version de api() para los subcomandos: si falla, termina con el mensaje."""
    try:
        return api(url, token, method, path, body)
    except ApiError as error:
        sys.exit(str(error))


def table(rows: list[dict], columns: list[str] | None = None) -> None:
    if not rows:
        print("  (sin datos)")
        return
    columns = columns or list(rows[0].keys())
    widths = {
        column: max(len(column), *(len(str(row.get(column, ""))) for row in rows))
        for column in columns
    }
    header = "  ".join(column.ljust(widths[column]) for column in columns)
    print(header)
    print("-" * len(header))
    for row in rows:
        print("  ".join(str(row.get(column, "")).ljust(widths[column]) for column in columns))


def shorten(value, limit: int = 48) -> str:
    text = str(value if value is not None else "")
    return text if len(text) <= limit else text[: limit - 1] + "…"


# --------------------------------------------------------------------- TUI --
#
# Modo interactivo (por defecto al abrir el CLI sin argumentos): todas las
# vistas en una sola pantalla, con scroll. Pensado para revisar el registro a
# gusto en vez de escribir un subcomando por consulta.

VIEWS = [
    "Resumen",
    "Por día",
    "Búsquedas",
    "Top búsquedas",
    "Reproducciones",
    "Top canciones",
    "Instalaciones",
    "IPs / red",
    "Baneos",
    "Salas",
]

# Vistas que aceptan el parametro de dias (tecla d)
DAYS_VIEWS = {1, 3, 5}
IP_VIEW = 7
BAN_VIEW = 8
ROOM_VIEW = 9

KEY_HELP = (
    "↑↓ j/k: mover   PgUp/PgDn: página   g/G: inicio/fin   ←→ 1-9/0: vista   "
    "d: días   r: recargar   b: banear   u: desbanear   x: cerrar sala   q: salir"
)


def build_view(url: str, token: str, index: int, days: int):
    """Devuelve (titulo, columnas, filas, raw, permite_banear).

    raw conserva los registros completos (hash sin recortar) para poder banear
    la fila que el usuario tiene seleccionada.
    """
    if index == 0:
        data = api(url, token, "GET", "/admin/stats", timeout=25)
        rows = [[key.replace("_", " ").capitalize(), str(value)] for key, value in data.items()]
        return "Resumen general", ["métrica", "valor"], rows, [], False

    if index == 1:
        items = api(url, token, "GET", f"/admin/counts-by-day?days={days}", timeout=25)["items"]
        rows = [
            [str(i.get("dia"))[:10], str(i.get("activos")), str(i.get("busquedas")), str(i.get("reproducciones"))]
            for i in items
        ]
        return (
            f"Actividad por día · últimos {days} días",
            ["día", "activos", "búsquedas", "reproducciones"],
            rows,
            items,
            False,
        )

    if index == 2:
        items = api(url, token, "GET", "/admin/searches?limit=400", timeout=25)["items"]
        rows = [
            [str(i["created_at"])[:19], str(i["install_id"])[:8], shorten(i["query"], 70)]
            for i in items
        ]
        return "Búsquedas (más recientes primero)", ["cuándo", "instalación", "búsqueda"], rows, items, False

    if index == 3:
        items = api(url, token, "GET", f"/admin/top-searches?days={days}", timeout=25)["items"]
        rows = [[shorten(i["query"], 70), str(i["veces"])] for i in items]
        return f"Top búsquedas · últimos {days} días", ["búsqueda", "veces"], rows, items, False

    if index == 4:
        items = api(url, token, "GET", "/admin/plays?limit=400", timeout=25)["items"]
        rows = [
            [str(i["created_at"])[:19], str(i["install_id"])[:8], shorten(i["title"], 48), shorten(i["artist"], 28)]
            for i in items
        ]
        return "Reproducciones (más recientes primero)", ["cuándo", "instalación", "canción", "artista"], rows, items, False

    if index == 5:
        items = api(url, token, "GET", f"/admin/top-songs?days={days}", timeout=25)["items"]
        rows = [[shorten(i["title"], 48), shorten(i["artist"], 28), str(i["veces"])] for i in items]
        return f"Top canciones · últimos {days} días", ["canción", "artista", "veces"], rows, items, False

    if index == 6:
        items = api(url, token, "GET", "/admin/installs?limit=400", timeout=25)["items"]
        rows = [
            [
                str(i["install_id"])[:8],
                str(i["first_seen"])[:19],
                str(i["last_seen"])[:19],
                str(i["app_version"] or "-"),
            ]
            for i in items
        ]
        return "Instalaciones (una por dispositivo)", ["instalación", "primera vez", "última vez", "versión"], rows, items, False

    if index == 7:
        items = api(url, token, "GET", "/admin/ips?limit=400", timeout=25)["items"]
        rows = [
            [
                str(i["ip_hash"])[:16],
                str(i["events"]),
                str(i["events_hour"]),
                str(i["last_seen"])[:19],
                "SÍ" if i["banned"] else "no",
                shorten(i["reason"], 24),
            ]
            for i in items
        ]
        return (
            "IPs / red (hash con salt, no la IP en claro)",
            ["ip_hash", "eventos", "última hora", "vista por última vez", "baneada", "razón"],
            rows,
            items,
            True,
        )

    if index == 8:
        items = api(url, token, "GET", "/admin/bans", timeout=25)["items"]
        rows = [[str(i["ip_hash"])[:16], shorten(i["reason"], 40), str(i["banned_at"])[:19]] for i in items]
        return "Baneos activos", ["ip_hash", "razón", "cuándo"], rows, items, True

    data = api(url, token, "GET", "/admin/rooms", timeout=25)
    items = data["items"]
    rows = [
        [
            str(i["code"]),
            str(i["personas"]),
            shorten(i["nombres"], 22),
            shorten(i["cancion"], 30),
            str(i["segundo"]) + "s",
            "SÍ" if i["sonando"] == "SI" else "no",
            str(i["creada"])[11:19],
        ]
        for i in items
    ]
    return (
        f"Salas de escucha compartida (máx {data['max_members']} personas, {data['ttl_hours']} h)",
        ["código", "gente", "quiénes", "canción", "segundo", "sonando", "creada"],
        rows,
        items,
        True,
    )


def run_ui(url: str, token: str) -> None:
    """Registro navegable en la terminal (curses, sin dependencias extras)."""
    try:
        import curses
    except ImportError:
        sys.exit(
            "Esta terminal no tiene curses; usa los subcomandos "
            "(oymctl.py stats, searches, ips, ...)."
        )

    def loop(stdscr) -> None:
        curses.curs_set(0)
        stdscr.keypad(True)
        curses.use_default_colors()

        view, days = 0, 7
        cursor, offset = 0, 0
        cache: dict | None = None
        status = ""
        dirty = True

        while True:
            height, width = stdscr.getmaxyx()
            if dirty:
                stdscr.erase()
                stdscr.addnstr(0, 0, f"OpenYTMusic · {url}", width - 1, curses.A_BOLD)

                # Pestanas de vistas
                cursor_x = 0
                for i, name in enumerate(VIEWS):
                    label = f"{i + 1}·{name}"
                    if cursor_x + len(label) >= width - 1:
                        break
                    attribute = curses.A_REVERSE if i == view else curses.A_DIM
                    stdscr.addnstr(1, cursor_x, label, width - 1 - cursor_x, attribute)
                    cursor_x += len(label) + 2
                stdscr.addnstr(2, 0, "─" * max(width - 1, 0), width - 1, curses.A_DIM)

                if cache is None:
                    status = status or "cargando…"
                else:
                    title, columns, rows, _raw, _can_ban = cache["data"]
                    stdscr.addnstr(3, 0, title, width - 1, curses.A_BOLD)
                    header = "  ".join(str(c) for c in columns)
                    stdscr.addnstr(4, 0, header, width - 1, curses.A_UNDERLINE)

                    body_rows = max(height - 7, 1)
                    if cursor < offset:
                        offset = cursor
                    if cursor >= offset + body_rows:
                        offset = cursor - body_rows + 1
                    max_offset = max(len(rows) - body_rows, 0)
                    offset = min(offset, max_offset)

                    visible = rows[offset : offset + body_rows]
                    if not visible:
                        stdscr.addnstr(5, 0, "(sin datos)", width - 1, curses.A_DIM)
                    for i, row in enumerate(visible):
                        line = "  ".join(str(cell) for cell in row)
                        attribute = curses.A_REVERSE if (offset + i) == cursor else curses.A_NORMAL
                        stdscr.addnstr(5 + i, 0, line, width - 1, attribute)

                    status = (
                        f"{len(rows)} registros   fila {cursor + 1}/{len(rows) if rows else 1}"
                        f"   ·   {cache['stamp']}"
                    )

                stdscr.addnstr(max(height - 2, 0), 0, status[: width - 1], width - 1, curses.A_DIM)
                stdscr.addnstr(max(height - 1, 0), 0, KEY_HELP, width - 1, curses.A_DIM)
                stdscr.refresh()
                dirty = False

            # Se pide la vista aqui, no en cada tecla. El dibujado de arriba ya mostro
            # "cargando…": esta llamada puede tardar (el servicio de Render despierta)
            # y el usuario tiene que ver que algo esta pasando.
            if cache is None:
                try:
                    data = build_view(url, token, view, days)
                    cache = {
                        "view": view,
                        "days": days,
                        "data": data,
                        "stamp": datetime.now().strftime("%H:%M:%S"),
                    }
                    status = ""
                except ApiError as error:
                    status = f"error: {error}"
                    cache = {
                        "view": view,
                        "days": days,
                        "data": ("Sin conexión", ["detalle"], [[str(error)]], [], False),
                        "stamp": datetime.now().strftime("%H:%M:%S"),
                    }
                cursor, offset = 0, 0
                # continue (no getch): se vuelve a dibujar YA con los datos nuevos.
                # Si se esperara a la proxima tecla, la vista se quedaria en blanco.
                dirty = True
                continue

            key = stdscr.getch()

            # Cualquier tecla puede mover el cursor o cambiar la vista: se repinta.
            dirty = True

            if key in (ord("q"), 27):
                return
            if key in (curses.KEY_DOWN, ord("j")):
                cursor += 1
            elif key in (curses.KEY_UP, ord("k")):
                cursor = max(cursor - 1, 0)
            elif key == curses.KEY_NPAGE:
                cursor += max(height - 7, 1)
            elif key == curses.KEY_PPAGE:
                cursor = max(cursor - max(height - 7, 1), 0)
            elif key == curses.KEY_HOME or key == ord("g"):
                cursor = 0
            elif key == curses.KEY_END or key == ord("G"):
                cursor = 10**9
            elif key == curses.KEY_LEFT:
                view, cache, dirty = (view - 1) % len(VIEWS), None, True
            elif key == curses.KEY_RIGHT or key == 9:
                view, cache, dirty = (view + 1) % len(VIEWS), None, True
            elif ord("1") <= key <= ord("9"):
                view, cache, dirty = key - ord("1"), None, True
            elif key == ord("0") and len(VIEWS) > 9:
                view, cache, dirty = 9, None, True
            elif key == ord("r"):
                cache, dirty = None, True
            elif key == ord("d"):
                if view in DAYS_VIEWS:
                    answer = prompt(stdscr, "¿Cuántos días? (7): ")
                    if answer.isdigit() and int(answer) > 0:
                        days = int(answer)
                    cache, dirty = None, True
            elif key == ord("b"):
                if cache:
                    rows_raw = cache["data"][3]
                    target = None
                    if rows_raw and view in (IP_VIEW, BAN_VIEW) and cursor < len(rows_raw):
                        target = rows_raw[cursor]["ip_hash"]
                    else:
                        answer = prompt(stdscr, "IP a banear: ")
                        if not answer:
                            continue
                        body = {"ip": answer}
                    if target:
                        body = {"ip_hash": target}
                    reason = prompt(stdscr, "Razón (opcional): ") or "baneada desde oymctl"
                    body["reason"] = reason
                    try:
                        result = api(url, token, "POST", "/admin/ban", body)
                        status = f"baneada {str(result.get('ip_hash'))[:16]}"
                    except ApiError as error:
                        status = f"error: {error}"
                    cache, dirty = None, True
            elif key == ord("x"):
                # Cerrar (expulsar) la sala seleccionada: corta las conexiones de
                # todos los que estan dentro.
                if cache and view == ROOM_VIEW:
                    rooms_raw = cache["data"][3]
                    if rooms_raw and cursor < len(rooms_raw):
                        code = rooms_raw[cursor]["code"]
                        try:
                            api(url, token, "DELETE", f"/admin/room/{code}")
                            status = f"sala {code} cerrada"
                        except ApiError as error:
                            status = f"error: {error}"
                        cache, dirty = None, True
            elif key == ord("u"):
                if cache:
                    rows_raw = cache["data"][3]
                    target = None
                    if rows_raw and view in (IP_VIEW, BAN_VIEW) and cursor < len(rows_raw):
                        target = rows_raw[cursor]["ip_hash"]
                    else:
                        answer = prompt(stdscr, "ip_hash a desbanear: ")
                        target = answer or None
                    if target:
                        try:
                            api(url, token, "DELETE", f"/admin/ban/{target}")
                            status = f"desbaneada {str(target)[:16]}"
                        except ApiError as error:
                            status = f"error: {error}"
                        cache, dirty = None, True

            if cache and cache["data"][2]:
                cursor = max(0, min(cursor, len(cache["data"][2]) - 1))

    def prompt(stdscr, label: str) -> str:
        height, width = stdscr.getmaxyx()
        stdscr.move(height - 2, 0)
        stdscr.clrtoeol()
        stdscr.addnstr(height - 2, 0, label, width - 1, curses.A_BOLD)
        stdscr.refresh()
        curses.echo()
        curses.curs_set(1)
        try:
            raw = stdscr.getstr(height - 2, min(len(label), max(width - 2, 0)), 80)
        except Exception:
            return ""
        finally:
            curses.noecho()
            curses.curs_set(0)
        return raw.decode(errors="replace").strip()

    curses.wrapper(loop)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Estadisticas y control de OpenYTMusic",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "command",
        nargs="?",
        default=None,
        choices=[
            "ui",
            "stats",
            "searches",
            "top-searches",
            "plays",
            "top-songs",
            "installs",
            "ips",
            "days",
            "bans",
            "rooms",
            "close-room",
            "ban",
            "unban",
            "export",
            "health",
        ],
        help="sin esto abre el modo interactivo (o 'ui' para forzarlo)",
    )
    parser.add_argument("arg", nargs="?", help="limite, dias, IP o ip_hash")
    parser.add_argument("arg2", nargs="?", help="razon del baneo (solo para ban)")
    parser.add_argument("--hash", dest="is_hash", action="store_true", help="ban: el valor ya es un ip_hash")
    options = parser.parse_args()

    url, token = load_config()
    command = options.command

    # Sin subcomando: registro navegable. En una terminal de verdad (tty) se abre
    # la TUI; si la salida va a un pipe o a la red, se muestra la ayuda.
    if command in (None, "ui"):
        if command is None and not sys.stdout.isatty():
            parser.print_help()
            return
        run_ui(url, token)
        return

    if command == "health":
        with urllib.request.urlopen(f"{url}/healthz", timeout=45) as response:
            print(json.dumps(json.loads(response.read().decode()), indent=2))
        return

    if command == "stats":
        data = call(url, token, "GET", "/admin/stats")
        print("\n== OpenYTMusic · resumen ==")
        for key, value in data.items():
            print(f"  {key.replace('_', ' ').capitalize():<24} {value}")
        print()
        return

    if command == "searches":
        limit = int(options.arg or 50)
        rows = call(url, token, "GET", f"/admin/searches?limit={limit}")["items"]
        rows = [
            {
                "cuando": str(row["created_at"])[:19],
                "instalacion": str(row["install_id"])[:8],
                "busqueda": shorten(row["query"], 60),
            }
            for row in rows
        ]
        table(rows)
        return

    if command == "top-searches":
        days = int(options.arg or 7)
        rows = call(url, token, "GET", f"/admin/top-searches?days={days}")["items"]
        table([{"busqueda": shorten(row["query"], 60), "veces": row["veces"]} for row in rows])
        return

    if command == "plays":
        limit = int(options.arg or 50)
        rows = call(url, token, "GET", f"/admin/plays?limit={limit}")["items"]
        rows = [
            {
                "cuando": str(row["created_at"])[:19],
                "instalacion": str(row["install_id"])[:8],
                "cancion": shorten(row["title"], 45),
                "artista": shorten(row["artist"], 30),
            }
            for row in rows
        ]
        table(rows)
        return

    if command == "top-songs":
        days = int(options.arg or 7)
        rows = call(url, token, "GET", f"/admin/top-songs?days={days}")["items"]
        table(
            [
                {
                    "cancion": shorten(row["title"], 45),
                    "artista": shorten(row["artist"], 30),
                    "veces": row["veces"],
                }
                for row in rows
            ]
        )
        return

    if command == "installs":
        limit = int(options.arg or 100)
        rows = call(url, token, "GET", f"/admin/installs?limit={limit}")["items"]
        table(
            [
                {
                    "instalacion": str(row["install_id"])[:8],
                    "primera_vez": str(row["first_seen"])[:19],
                    "ultima_vez": str(row["last_seen"])[:19],
                    "version": row["app_version"] or "-",
                }
                for row in rows
            ]
        )
        return

    if command == "ips":
        limit = int(options.arg or 50)
        rows = call(url, token, "GET", f"/admin/ips?limit={limit}")["items"]
        table(
            [
                {
                    "ip_hash": str(row["ip_hash"])[:16],
                    "eventos": row["events"],
                    "ultima_hora": row["events_hour"],
                    "ultima_vez": str(row["last_seen"])[:19],
                    "baneada": "SI" if row["banned"] else "no",
                    "razon": shorten(row["reason"], 28),
                }
                for row in rows
            ]
        )
        print("\nEl ip_hash se muestra recortado. Para banear una red usa la IP directo:")
        print("  python3 oymctl.py ban 190.12.34.56 \"abuso\"")
        return

    if command == "days":
        days = int(options.arg or 14)
        rows = call(url, token, "GET", f"/admin/counts-by-day?days={days}")["items"]
        table(
            [
                {
                    "dia": str(row["dia"])[:10],
                    "activos": row["activos"],
                    "busquedas": row["busquedas"],
                    "reproducciones": row["reproducciones"],
                }
                for row in rows
            ]
        )
        return

    if command == "bans":
        rows = call(url, token, "GET", "/admin/bans")["items"]
        table(
            [
                {
                    "ip_hash": row["ip_hash"],
                    "razon": shorten(row["reason"], 40),
                    "cuando": str(row["banned_at"])[:19],
                }
                for row in rows
            ]
        )
        return

    if command == "rooms":
        data = call(url, token, "GET", "/admin/rooms")
        items = data["items"]
        if not items:
            print("  (no hay salas abiertas ahora mismo)")
            return
        table(
            [
                {
                    "codigo": row["code"],
                    "gente": row["personas"],
                    "quienes": shorten(row["nombres"], 24),
                    "cancion": shorten(row["cancion"], 34),
                    "segundo": row["segundo"],
                    "sonando": row["sonando"],
                    "creada": str(row["creada"])[11:19],
                }
                for row in items
            ]
        )
        print(f"\nmáximo {data['max_members']} personas por sala, duran {data['ttl_hours']} h.")
        return

    if command == "close-room":
        if not options.arg:
            sys.exit("uso: oymctl.py close-room <codigo>")
        code = options.arg.upper()
        call(url, token, "DELETE", f"/admin/room/{code}")
        print(f"sala {code} cerrada (se cortaron las conexiones de adentro).")
        return

    if command == "ban":
        if not options.arg:
            sys.exit("uso: oymctl.py ban <ip|ip_hash> [razon]  (--hash si ya es un hash)")
        body = {"reason": options.arg2}
        if options.is_hash:
            body["ip_hash"] = options.arg
        else:
            body["ip"] = options.arg
        result = call(url, token, "POST", "/admin/ban", body)
        print(f"baneada: {result['ip_hash']}")
        print("La app de esa red vera 'Acceso restringido' en el siguiente arranque.")
        return

    if command == "unban":
        if not options.arg:
            sys.exit("uso: oymctl.py unban <ip_hash>")
        call(url, token, "DELETE", f"/admin/ban/{options.arg}")
        print(f"desbaneada: {options.arg}")
        return

    if command == "export":
        data = call(url, token, "GET", "/admin/export")
        stamp = datetime.now().strftime("%Y-%m-%d")
        destination = Path(options.arg) if options.arg else Path(f"oym-analytics-{stamp}.json")
        destination.write_text(json.dumps(data, indent=2, default=str))
        counts = {key: len(value) for key, value in data.items() if isinstance(value, list)}
        print(f"guardado en {destination}")
        for key, value in counts.items():
            print(f"  {key}: {value}")
        return


if __name__ == "__main__":
    main()
