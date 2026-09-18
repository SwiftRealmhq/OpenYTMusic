# Backend de estadísticas y control

Servicio mínimo que registra **usuarios activos, búsquedas y canciones
reproducidas**, y permite **restringir el acceso** de una red que abuse de la
app. Corre en **Render** (plan gratis) con **Postgres** (plan gratis).

```
app Android  ──POST /v1/event──▶  Render (FastAPI)  ──▶  Postgres
                                        ▲
   tu PC  ──oymctl.py (admin)───────────┘
```

## Qué se envía desde la app

| Evento      | Cuándo                          | Datos                                   |
|-------------|---------------------------------|-----------------------------------------|
| `heartbeat` | Una vez al día al abrir la app  | ID de instalación anónimo, versión      |
| `search`    | Cada búsqueda del usuario       | Texto buscado                           |
| `play`      | Cada canción que empieza        | Título y artista                        |

El **ID de instalación** es un UUID aleatorio que se genera en el teléfono. No
hay cuenta, correo ni datos de Google. **La app nunca manda la IP**: el backend
la ve por la conexión.

En la base **no se guarda la IP en claro**, solo su SHA-256 con un salt secreto
(`IP_SALT`). Alcanza para contar abuso por red y para banear, sin dejar un
registro de «quién buscó qué» con IP real.

## Privacidad (lado de la app)

En **Ajustes → Privacidad → Estadísticas anónimas** el usuario puede apagarlo. Si
lo apaga, la app **no hace ni una petición**. Y si el usuario pausa su historial
de búsqueda o de reproducción, ese dato tampoco se envía: es la misma intención
de privacidad dicha en otro switch. Nada de esto se pelea: apagado es apagado.

Extra: si `ANALYTICS_URL` queda vacío al compilar, la telemetría no existe (el
código ni siquiera intenta red).

## Desplegar

### Opción A — Blueprint (más rápido)

Render Dashboard → **New → Blueprint** → elegir el repo. Toma `render.yaml` y
crea el servicio + la base con las variables ya cableadas.

### Opción B — Todo por la API de Render

Con una API key de Render (Account Settings → API Keys):

```bash
# 1) crear la base
curl -X POST https://api.render.com/v1/postgres \
  -H "Authorization: Bearer $RENDER_API_KEY" -H 'Content-Type: application/json' \
  -d '{"name":"openytmusic-analytics-db","plan":"free","region":"oregon","databaseName":"oym","ownerId":"<tu ownerId>"}'

# 2) crear el servicio web (con rootDir analytics) y 3) cargar env vars
#    DATABASE_URL, ADMIN_TOKEN, IP_SALT
```

### Después de desplegar

1. Copia la URL del servicio (`https://<nombre>.onrender.com`).
2. En Render → servicio → Environment, copia `ADMIN_TOKEN`.
3. Compila la app con esa URL:

```bash
./gradlew :app:assembleFossRelease -PoymAnalyticsUrl="https://<nombre>.onrender.com"
```

(El valor también se puede dar con la variable de entorno `OYM_ANALYTICS_URL`.)

4. Guarda el secret `ANALYTICS_URL` en GitHub para el cron que evita que el
   servicio se duerma (`.github/workflows/keepalive.yml`).

## El cronjob que evita que se duerma

El plan gratis de Render duerme el servicio a los **15 minutos** sin tráfico y
tarda **~1 minuto** en despertar. Para eso está
`.github/workflows/keepalive.yml`: visita `/healthz` cada 5 minutos. Es gratis
porque el repo es público.

> Los **Cron Jobs de Render no entran en el plan gratis**, por eso el ping vive
> en GitHub Actions. Alternativa sin depender del repo: `cron-job.org` o
> `UptimeRobot` apuntando a `https://<tu-servicio>/healthz`.

## Los 30 días de la base gratis

El Postgres gratis de Render **caduca a los 30 días** (14 días de gracia y luego
lo borran con todo). Antes de esa fecha:

```bash
python3 oymctl.py export            # guarda oym-analytics-AAAA-MM-DD.json
```

Luego recrea la base (Blueprint o API) y, si quieres conservar totales, importa
ese JSON a mano. Con el export periódico no se pierde el histórico.

## Usar el CLI

```bash
export OYM_BACKEND_URL="https://<nombre>.onrender.com"
export OYM_ADMIN_TOKEN="<ADMIN_TOKEN>"

python3 oymctl.py stats          # resumen: activos hoy/7d/30d, búsquedas, baneos...
python3 oymctl.py days 14        # serie diaria (crece o cae el uso)
python3 oymctl.py searches 50    # últimas búsquedas
python3 oymctl.py top-searches 7 # las búsquedas más repetidas
python3 oymctl.py plays 50       # últimas reproducciones
python3 oymctl.py top-songs 7    # las canciones más reproducidas
python3 oymctl.py installs 100   # instalaciones y su última conexión
python3 oymctl.py ips 50         # redes por tráfico + si están baneadas
python3 oymctl.py ban 190.12.34.56 "abuso de búsquedas"
python3 oymctl.py unban <ip_hash>
python3 oymctl.py export
python3 oymctl.py health
```

También se puede configurar en `~/.oym-analytics.json`:

```json
{ "url": "https://<nombre>.onrender.com", "token": "<ADMIN_TOKEN>" }
```

## Cómo se aplica el baneo

Es un **bloqueo suave**: la app pide `GET /v1/status` al abrir (y recibe el
estado en cada evento). Si el backend dice `banned: true`, la app muestra
**«Acceso restringido»** y no monta la interfaz. No se borra nada del usuario.

Si el backend está dormido, caído o tarda, **no se bloquea a nadie** (fail-open):
un backend sin respuesta no puede dejar sin música a todo el mundo.

## Auto-baneo opcional

`AUTO_BAN_EVENTS_PER_HOUR=0` (por defecto) desactiva el baneo automático. Con un
número, una red que supere esa cantidad de eventos en una hora queda baneada
sola.

## Endpoints

| Método | Ruta | Auth | Para qué |
|---|---|---|---|
| GET | `/healthz` | — | Ping del cronjob |
| POST | `/v1/event` | — | Eventos de la app |
| GET | `/v1/status` | — | Estado de acceso de la red |
| GET | `/admin/stats` | token | Resumen |
| GET | `/admin/searches` | token | Últimas búsquedas |
| GET | `/admin/top-searches` | token | Búsquedas más repetidas |
| GET | `/admin/plays` | token | Últimas reproducciones |
| GET | `/admin/top-songs` | token | Canciones más reproducidas |
| GET | `/admin/installs` | token | Instalaciones |
| GET | `/admin/ips` | token | Redes por tráfico |
| GET | `/admin/counts-by-day` | token | Serie diaria |
| GET | `/admin/bans` | token | Baneos activos |
| POST | `/admin/ban` | token | Banear (por IP o ip_hash) |
| DELETE | `/admin/ban/{ip_hash}` | token | Desbanear |
| GET | `/admin/export` | token | Volcado completo (respaldo) |

El token viaja en la cabecera `X-Admin-Token`.
