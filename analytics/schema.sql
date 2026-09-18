-- Esquema del backend de OpenYTMusic.
-- Se aplica solo al arrancar (CREATE TABLE IF NOT EXISTS), asi que es seguro
-- volver a desplegar sobre una base existente.

-- Una fila por instalacion anonima (UUID aleatorio que genera la app).
CREATE TABLE IF NOT EXISTS installs (
    install_id  TEXT PRIMARY KEY,
    app_version TEXT,
    first_seen  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Busquedas. ip_hash = SHA-256 con salt secreto (nunca la IP en claro).
CREATE TABLE IF NOT EXISTS searches (
    id         BIGSERIAL PRIMARY KEY,
    install_id TEXT,
    query      TEXT NOT NULL,
    ip_hash    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Canciones que empezaron a sonar.
CREATE TABLE IF NOT EXISTS plays (
    id         BIGSERIAL PRIMARY KEY,
    install_id TEXT,
    title      TEXT NOT NULL,
    artist     TEXT,
    ip_hash    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Contadores por red (para ver abuso por volumen).
CREATE TABLE IF NOT EXISTS ip_counters (
    ip_hash     TEXT PRIMARY KEY,
    events      BIGINT NOT NULL DEFAULT 0,
    events_hour BIGINT NOT NULL DEFAULT 0,
    hour_bucket TIMESTAMPTZ,
    first_seen  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Redes bloqueadas: la app las ve en /v1/status y muestra "acceso restringido".
CREATE TABLE IF NOT EXISTS bans (
    ip_hash   TEXT PRIMARY KEY,
    reason    TEXT,
    banned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Salas de escucha compartida. El estado vivo esta en memoria del proceso; esta
-- tabla es solo el respaldo para que un reinicio del servicio no corte la
-- sesion. No guarda usuarios ni chat: solo la cancion y el segundo.
CREATE TABLE IF NOT EXISTS rooms (
    code       TEXT PRIMARY KEY,
    state      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS rooms_expires_idx ON rooms (expires_at);
CREATE INDEX IF NOT EXISTS searches_created_at_idx ON searches (created_at DESC);
CREATE INDEX IF NOT EXISTS searches_install_idx ON searches (install_id);
CREATE INDEX IF NOT EXISTS plays_created_at_idx ON plays (created_at DESC);
CREATE INDEX IF NOT EXISTS installs_last_seen_idx ON installs (last_seen DESC);
