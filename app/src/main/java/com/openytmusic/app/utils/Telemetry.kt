package com.openytmusic.app.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.openytmusic.app.R
import com.openytmusic.app.constants.AnalyticsInstallIdKey
import com.openytmusic.app.constants.AnalyticsLastHeartbeatDayKey
import com.openytmusic.app.constants.AnonymousStatsKey
import com.openytmusic.app.constants.PauseListenHistoryKey
import com.openytmusic.app.constants.PauseSearchHistoryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID

/**
 * Cliente del backend de "control total": usuarios activos, busquedas y
 * reproducciones, mas el estado de acceso de la red.
 *
 * Reglas de privacidad y de comportamiento (importantes):
 *
 *  - Si [AnonymousStatsKey] esta apagado, o si build.gradle.kts no definio
 *    ANALYTICS_URL, esta clase NO hace ninguna peticion de red. Punto.
 *  - Nunca se envia la IP: el backend la ve por la conexion (asi los baneos se
 *    aplican por red sin que la app tenga que mandar nada personal).
 *  - Se envia un ID de instalacion aleatorio (UUID) para poder contar usuarios
 *    activos sin identificar a nadie. No hay cuenta, correo ni cuenta de Google.
 *  - Si el usuario pausa su historial (de busqueda o de reproduccion) tampoco se
 *    envia ese dato: es la misma intencion de privacidad dicha en otro switch.
 *  - Todo es "fire and forget": si el backend esta dormido o falla, la app sigue
 *    funcionando igual. Nunca se bloquea la interfaz esperando a la red.
 */
object Telemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000

    /**
     * Acceso restringido por el backend. Arranca permitido y SOLO se bloquea si
     * el backend responde explicitamente que esta red esta baneada: si el
     * servicio esta dormido (el plan gratis de Render se duerme) o da error, se
     * deja pasar a todo el mundo. Fail-open a proposito: un backend caido no
     * puede dejar sin musica a los usuarios.
     */
    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    // Se lee del recurso en cada uso: asi el valor del APK instalado es siempre el
    // ultimo compilado y no una constante inlinada que se quedo vieja.
    private fun baseUrl(context: Context) =
        context.getString(R.string.analytics_url).trim().trimEnd('/')

    /** Arranque de la app: latido diario (usuarios activos) + estado de acceso. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!statsEnabled(app)) return@launch

            val today = LocalDate.now().toEpochDay()
            if (app.dataStore.get(AnalyticsLastHeartbeatDayKey, -1L) != today) {
                app.dataStore.edit { it[AnalyticsLastHeartbeatDayKey] = today }
                post(app, "heartbeat", JSONObject())
            }

            checkAccess(app)
        }
    }

    /** Una busqueda del usuario (texto tal cual lo escribio). */
    fun logSearch(context: Context, query: String) {
        val app = context.applicationContext
        val text = query.trim()
        if (text.isEmpty()) return
        scope.launch {
            if (!statsEnabled(app)) return@launch
            if (app.dataStore.get(PauseSearchHistoryKey, false)) return@launch
            post(app, "search", JSONObject().put("query", text))
        }
    }

    /** Una cancion que empezo a sonar. */
    fun logPlay(context: Context, title: String?, artist: String?) {
        val app = context.applicationContext
        if (title.isNullOrBlank()) return
        scope.launch {
            if (!statsEnabled(app)) return@launch
            if (app.dataStore.get(PauseListenHistoryKey, false)) return@launch
            post(
                app,
                "play",
                JSONObject()
                    .put("title", title)
                    .put("artist", artist.orEmpty())
            )
        }
    }

    private suspend fun statsEnabled(context: Context): Boolean =
        baseUrl(context).isNotEmpty() && context.dataStore.get(AnonymousStatsKey, true)

    /**
     * ID aleatorio de esta instalacion (nunca identifica a nadie).
     *
     * Lo usan tambien las salas: con el, el servidor reconoce que una conexion
     * nueva es la misma app que ya estaba y reemplaza la vieja, en vez de dejarla
     * de fantasma en la sala.
     */
    suspend fun installId(context: Context): String {
        context.dataStore.get(AnalyticsInstallIdKey)?.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[AnalyticsInstallIdKey] = id }
        return id
    }

    private suspend fun post(context: Context, type: String, payload: JSONObject) {
        val body = JSONObject()
            .put("type", type)
            .put("install_id", installId(context))
            // Version real instalada (PackageManager), no la constante inlinada de
            // BuildConfig: si no, el backend registraria versiones viejas.
            .put("app_version", AppVersion.name(context))
            .put("payload", payload)

        // Cualquier evento devuelve tambien el estado de acceso: asi un baneo se
        // aplica sin gastar una peticion extra.
        request(context, "POST", "/v1/event", body.toString())?.let(::applyAccess)
    }

    private suspend fun checkAccess(context: Context) {
        val id = URLEncoder.encode(installId(context), "UTF-8")
        // Si esto falla (servicio dormido, sin red, timeout) NO se bloquea a nadie.
        request(context, "GET", "/v1/status?install_id=$id", null)?.let(::applyAccess)
    }

    private fun applyAccess(json: JSONObject) {
        _blocked.value = runCatching { json.optBoolean("banned", false) }.getOrDefault(false)
    }

    private suspend fun request(
        context: Context,
        method: String,
        path: String,
        body: String?,
    ): JSONObject? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(baseUrl(context) + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "OpenYTMusic/${AppVersion.name(context)}")
                    doOutput = body != null
                }
                if (body != null) {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                }
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            } catch (t: Throwable) {
                // Red caida, timeout, JSON raro: la app no se entera de nada.
                null
            } finally {
                connection?.disconnect()
            }
        }
}
