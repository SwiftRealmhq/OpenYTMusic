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

Uso:
    python3 oymctl.py stats
    python3 oymctl.py searches 50
    python3 oymctl.py top-searches 7
    python3 oymctl.py plays 50
    python3 oymctl.py top-songs 7
    python3 oymctl.py installs 100
    python3 oymctl.py ips 50
    python3 oymctl.py days 14
    python3 oymctl.py bans
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


def call(url: str, token: str, method: str, path: str, body: dict | None = None):
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
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:300]
        sys.exit(f"el backend respondio {error.code}: {detail}")
    except urllib.error.URLError as error:
        sys.exit(
            f"no pude conectar con {url}: {error.reason}\n"
            "Si el servicio estaba dormido, espera un minuto y reintenta."
        )


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


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Estadisticas y control de OpenYTMusic",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "command",
        choices=[
            "stats",
            "searches",
            "top-searches",
            "plays",
            "top-songs",
            "installs",
            "ips",
            "days",
            "bans",
            "ban",
            "unban",
            "export",
            "health",
        ],
    )
    parser.add_argument("arg", nargs="?", help="limite, dias, IP o ip_hash")
    parser.add_argument("arg2", nargs="?", help="razon del baneo (solo para ban)")
    parser.add_argument("--hash", dest="is_hash", action="store_true", help="ban: el valor ya es un ip_hash")
    options = parser.parse_args()

    url, token = load_config()
    command = options.command

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
