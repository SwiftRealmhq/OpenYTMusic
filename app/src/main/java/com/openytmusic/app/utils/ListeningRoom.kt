package com.openytmusic.app.utils

import android.content.Context
import android.util.Log
import com.openytmusic.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Escuchar música con alguien más: cliente de la sala.
 *
 * No es una llamada de audio: cada teléfono reproduce su propio stream desde
 * YouTube Music y por aquí solo viaja el estado de reproducción (canción,
 * segundo y si suena). Por eso sincronizar no cuesta ancho de banda.
 *
 * El servidor sella cada mensaje con su hora (`server_ms`). Con eso se calcula
 * el desfase de relojes ([clockOffsetMs]) y así el invitado sabe en qué segundo
 * debería ir la reproducción ahora mismo, sin quedarse atrás por la latencia.
 *
 * Todo el estado vive en [state] para que la interfaz lo observe.
 */

data class RoomMember(val id: String, val name: String, val isMe: Boolean = false)

data class RoomChatMessage(
    val id: String,
    val name: String,
    val text: String,
    val fromMe: Boolean,
)

/** Canción que se está escuchando en la sala. */
data class RoomTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val durationMs: Long,
)

/** Orden de reproducción que llegó de la sala. */
data class RemotePlayback(
    val action: String,
    val track: RoomTrack?,
    val positionMs: Long,
    val playing: Boolean,
    /** Hora del servidor en la que se emitió la orden. */
    val serverMs: Long,
    val byName: String,
)

data class RemoteSync(val positionMs: Long, val playing: Boolean, val serverMs: Long)

/**
 * "Carguen esta canción, avisen y arrancamos".
 *
 * Nada suena hasta el arranque: los dos cargan la canción en pausa, avisan y
 * recién ahí empieza, en el mismo instante. Es lo que evita oír a uno arrancar
 * solo y al otro reiniciarlo encima (repeticiones y cortes).
 */
data class RoomPrepareOrder(
    val seq: Long,
    val track: RoomTrack,
    val positionMs: Long,
    val playing: Boolean,
)

/** Señal de arranque común, sellada con la hora del servidor. */
data class RoomGo(
    val seq: Long,
    val track: RoomTrack?,
    val positionMs: Long,
    val playing: Boolean,
    val serverMs: Long,
)

enum class RoomStatus { NONE, CREATING, CONNECTING, CONNECTED, ERROR }

data class RoomSnapshot(
    val status: RoomStatus = RoomStatus.NONE,
    val code: String = "",
    val myName: String = "",
    /** Id que me asignó el servidor dentro de la sala (con él sé cuál soy yo). */
    val myId: String = "",
    val members: List<RoomMember> = emptyList(),
    val track: RoomTrack? = null,
    val playing: Boolean = false,
    val chat: List<RoomChatMessage> = emptyList(),
    val error: String? = null,
    /** La sala volvió tras un reinicio del servidor: entra en pausa. */
    val restored: Boolean = false,
    /** Los dos están cargando la canción: todavía no suena nada. */
    val preparing: Boolean = false,
    /** El otro está escribiendo en el chat (se borra solo a los pocos segundos). */
    val typingName: String? = null,
) {
    val isConnected: Boolean get() = status == RoomStatus.CONNECTED
    val otherMembers: List<RoomMember> get() = members
}

class ListeningRoom(
    private val context: Context,
    /** Apodo de esta persona en la sala. */
    private val name: String,
    private val onSnapshot: (RoomSnapshot) -> Unit,
) {
    val state = MutableStateFlow(RoomSnapshot())

    /** Órdenes de reproducción del otro (las aplica MusicService). */
    var onRemotePlayback: ((RemotePlayback) -> Unit)? = null

    /** Latidos para corregir la deriva. */
    var onRemoteSync: ((RemoteSync) -> Unit)? = null

    /** Hay que cargar la canción (el otro cambió): se carga EN PAUSA y se avisa. */
    var onPrepare: ((RoomPrepareOrder) -> Unit)? = null

    /** Arranque común: los dos empiezan a la vez, ahora. */
    var onGo: ((RoomGo) -> Unit)? = null

    /** Ya había una canción sonando cuando entré: me pongo al día con ella. */
    var onAdopt: ((RoomTrack, Long, Boolean, Long) -> Unit)? = null

    /**
     * Entré a la sala. Si no hay nada puesto se avisa con null: lo que estuviera
     * sonando en el teléfono de antes se detiene, para que la música de la sala
     * sea la única fuente y los dos oigan lo mismo.
     */
    var onEnteredRoom: ((RoomTrack?) -> Unit)? = null

    /**
     * Entré (o volví a entrar tras una caída de red). Sirve para reevaluar el
     * estado local desde cero: si una preparación se quedó a medias porque se
     * cayó el socket, sin esto la app se quedaba esperando para siempre.
     */
    var onRejoined: (() -> Unit)? = null

    /** Mensajes de chat (además de quedar en [state]). */
    var onChat: ((RoomChatMessage) -> Unit)? = null

    /** Se cerró la sala (por administración o por error definitivo). */
    var onClosed: ((String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)   // mantiene vivo el socket en segundo plano
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var retries = 0
    private var retryJob: Job? = null
    private var closedByUser = false

    /** Numero de conexion: los avisos de un socket viejo se ignoran. */
    @Volatile
    private var connectionAttempt = 0

    /** UUID de esta instalacion: la sala la usa para no duplicarme. */
    @Volatile
    private var installId: String = ""
    private var typingJob: Job? = null
    private var lastTypingSentAt = 0L

    /** server_ms - reloj local: sirve para saber "qué hora es" en la sala. */
    @Volatile
    private var clockOffsetMs: Long = 0

    /** Momento (reloj local) del último ping, para medir el ida y vuelta. */
    @Volatile
    private var pingSentAt: Long = 0

    @Volatile
    var rttMs: Long = 0
        private set

    /** Cambio de canción en curso (0 = ninguno). */
    @Volatile
    var activeSeq: Long = 0
        private set

    @Volatile
    var isPreparing: Boolean = false
        private set

    /** Último arranque aplicado: un `go` repetido no vuelve a sonar. */
    @Volatile
    private var lastGoSeq: Long = 0



    private fun publish(snapshot: RoomSnapshot) {
        state.value = snapshot
        onSnapshot(snapshot)
    }

    /** Hora actual del servidor de salas, según el desfase medido. */
    fun serverNow(): Long = System.currentTimeMillis() + clockOffsetMs

    /** ¿La app tiene configurado el backend de salas? */
    fun isAvailable(): Boolean = baseUrl(context).isNotEmpty()

    /**
     * ID de instalacion (se lee una vez y se recuerda). Es lo que hace que, si el
     * socket se cae y vuelvo a entrar, el servidor reemplace mi conexion anterior
     * en lugar de mostrarme dos veces en la sala.
     */
    private suspend fun ensureInstallId(): String {
        if (installId.isNotEmpty()) return installId
        installId = runCatching { Telemetry.installId(context) }.getOrDefault("")
        return installId
    }

    // ------------------------------------------------------------------ sala
    /**
     * Crea una sala en el backend y devuelve el código para compartir.
     * Es lo único que se pide por HTTP; el resto va por el WebSocket.
     */
    fun createRoom(onResult: (Result<String>) -> Unit) {
        publish(state.value.copy(status = RoomStatus.CREATING, error = null))
        val url = baseUrl(context)
        if (url.isEmpty()) {
            onResult(Result.failure(IllegalStateException("salas no configuradas")))
            publish(state.value.copy(status = RoomStatus.ERROR, error = "Salas no configuradas en esta build"))
            return
        }
        scope.launch {
            ensureInstallId()
            val result = runCatching {
                val request = Request.Builder()
                    .url("$url/v1/room")
                    .post(ByteArray(0).toRequestBody(null))
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(errorMessage(response.code, body))
                    }
                    JSONObject(body).getString("code")
                }
            }
            result.onSuccess { code ->
                publish(state.value.copy(code = code, status = RoomStatus.CONNECTING))
                connect(code)
            }.onFailure { error ->
                publish(state.value.copy(status = RoomStatus.ERROR, error = error.message))
            }
            onResult(result)
        }
    }

    /** Entra a una sala existente con su código. */
    fun joinRoom(code: String) {
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (clean.length < 4) {
            publish(state.value.copy(status = RoomStatus.ERROR, error = "Escribe el código completo"))
            return
        }
        closedByUser = false
        retries = 0
        publish(
            RoomSnapshot(
                status = RoomStatus.CONNECTING,
                code = clean,
                myName = name,
            )
        )
        scope.launch {
            ensureInstallId()
            connect(clean)
        }
    }

    private fun connect(code: String) {
        val url = baseUrl(context).replace("https://", "wss://").replace("http://", "ws://")
        if (url.isEmpty()) {
            publish(state.value.copy(status = RoomStatus.ERROR, error = "Salas no configuradas en esta build"))
            return
        }
        // Cada intento tiene su numero. Sin esto, un socket viejo que moria tarde
        // podia abrir OTRA conexion: el mismo telefono entraba dos veces a la sala
        // y cada orden se aplicaba doble (la cancion se reiniciaba sola).
        val attempt = ++connectionAttempt
        retryJob?.cancel()
        retryJob = null
        runCatching { socket?.cancel() }
        val request = Request.Builder().url("$url/v1/room/$code/ws").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            private fun isCurrent(): Boolean = attempt == connectionAttempt

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent()) {
                    runCatching { webSocket.cancel() }
                    return
                }
                retries = 0
                webSocket.send(
                    JSONObject()
                        .put("t", "hello")
                        .put("name", name)
                        // La sala reconoce mi instalacion y reemplaza mi conexion
                        // anterior: asi nunca aparezco dos veces en la lista.
                        .put("install", installId)
                        .toString()
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent()) return
                runCatching { handle(text) }
                    .onFailure { Log.w(TAG, "mensaje de sala ilegible: ${it.message}") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent()) return
                Log.w(TAG, "sala caida: ${t.message} (http ${response?.code})")
                if (closedByUser) return
                if (response?.code == 403) {
                    publish(state.value.copy(status = RoomStatus.ERROR, error = "Esta red tiene el acceso restringido"))
                    onClosed?.invoke("acceso restringido")
                    return
                }
                if (response?.code == 404 || response?.code == 410) {
                    publish(state.value.copy(status = RoomStatus.ERROR, error = "Esa sala ya no existe"))
                    onClosed?.invoke("sala inexistente")
                    return
                }
                // Red inestable: se reintenta solo, sin molestar (es habitual al
                // cambiar de wifi a datos).
                if (retries < 5) {
                    retries += 1
                    publish(state.value.copy(status = RoomStatus.CONNECTING))
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        delay(2_000L * retries)
                        if (!closedByUser && attempt == connectionAttempt) {
                            ensureInstallId()
                            connect(state.value.code)
                        }
                    }
                } else {
                    publish(state.value.copy(status = RoomStatus.ERROR, error = "No pude conectarme a la sala"))
                    onClosed?.invoke("sin conexión")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) return
                if (closedByUser) return
                publish(state.value.copy(status = RoomStatus.NONE, members = emptyList()))
                onClosed?.invoke(reason.ifBlank { "sala cerrada" })
            }
        })
    }

    /** Salida limpia: avisa a la sala al instante (si no, el otro ve un fantasma). */
    fun leave() {
        closedByUser = true
        retryJob?.cancel()
        retryJob = null
        finishPrepare()
        runCatching { socket?.send(JSONObject().put("t", "bye").toString()) }
        runCatching { socket?.close(1000, "adios") }
        socket = null
        publish(RoomSnapshot(status = RoomStatus.NONE, myName = name))
    }

    fun close() {
        closedByUser = true
        runCatching { socket?.send(JSONObject().put("t", "bye").toString()) }
        runCatching { socket?.close(1000, "adios") }
        socket = null
        scope.coroutineContext[Job]?.cancel()
    }

    // ------------------------------------------------------------- envio
    fun sendState(track: RoomTrack?, positionMs: Long, playing: Boolean, action: String) {
        if (!state.value.isConnected) return
        val payload = JSONObject()
            .put("t", "state")
            .put("position_ms", max(positionMs, 0))
            .put("playing", playing)
            .put("action", action)
        track?.let {
            payload.put(
                "track",
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("artist", it.artist)
                    .put("artwork", it.artwork)
                    .put("duration_ms", it.durationMs)
            )
        }
        socket?.send(payload.toString())
        // Mi propio estado también se ve en MI interfaz: el servidor no me
        // devuelve mis propios mensajes, así que sin esto la tarjeta de la sala
        // se quedaba con la canción anterior (decía una y sonaba otra).
        publish(
            state.value.copy(
                track = track ?: state.value.track,
                playing = playing,
            )
        )
    }

    fun sendSync(positionMs: Long, playing: Boolean) {
        if (!state.value.isConnected) return
        socket?.send(
            JSONObject()
                .put("t", "sync")
                .put("position_ms", max(positionMs, 0))
                .put("playing", playing)
                .toString()
        )
    }

    /**
     * Pide un cambio de canción: se avisa a la sala y el arranque sale cuando
     * los dos están cargados (o, a más tardar, [GO_TIMEOUT_MS] después).
     * Devuelve la secuencia (0 si no hay sala).
     */
    fun startPrepare(track: RoomTrack, positionMs: Long, playing: Boolean): Long {
        if (!state.value.isConnected) return 0L
        val seq = System.currentTimeMillis()
        activeSeq = seq
        isPreparing = true
        publish(state.value.copy(preparing = true, track = track, playing = false))
        socket?.send(
            JSONObject()
                .put("t", "prepare")
                .put("seq", seq)
                .put("position_ms", max(positionMs, 0))
                .put("playing", playing)
                .put("track", JSONObject()
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("artwork", track.artwork)
                    .put("duration_ms", track.durationMs))
                .toString()
        )
        return seq
    }

    /**
     * "Ya tengo la canción cargada". Cuando el último avisa, el servidor da el
     * arranque para los dos a la vez. Si alguien desaparece a media carga, es el
     * servidor el que suelta el arranque (esa red de seguridad vive allá, no
     * aquí: menos piezas aquí significa menos formas de quedarse mudo).
     */
    fun sendReady(seq: Long) {
        if (!state.value.isConnected || seq <= 0L) return
        socket?.send(JSONObject().put("t", "ready").put("seq", seq).toString())
    }

    /** Se cancela el cambio en curso (lo reemplaza uno mas nuevo). */
    fun cancelPrepare() = finishPrepare()

    /**
     * "Estoy escribiendo": el otro ve los puntitos. No es un mensaje, así que se
     * manda como mucho uno cada segundo y medio (escribir no debe inundar la sala).
     */
    fun sendTyping() {
        if (!state.value.isConnected) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        runCatching { socket?.send(JSONObject().put("t", "typing").toString()) }
    }

    private fun finishPrepare() {
        isPreparing = false
        activeSeq = 0L
        if (state.value.preparing) publish(state.value.copy(preparing = false))
    }

    /** Cambia tu apodo dentro de la sala (sin cuenta ni nada guardado). */
    fun rename(newName: String) {
        val clean = newName.trim().take(MAX_NAME_CHARS)
        if (clean.isEmpty() || !state.value.isConnected) return
        publish(state.value.copy(myName = clean))
        socket?.send(JSONObject().put("t", "rename").put("name", clean).toString())
    }

    fun sendChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !state.value.isConnected) return
        socket?.send(JSONObject().put("t", "chat").put("text", clean).toString())
    }

    /** Latido: mantiene viva la sala y mide el ida y vuelta. */
    fun ping() {
        if (!state.value.isConnected) return
        pingSentAt = System.currentTimeMillis()
        socket?.send(JSONObject().put("t", "ping").put("at", pingSentAt).toString())
    }

    // ------------------------------------------------------------- recepcion
    private fun handle(text: String) {
        val message = JSONObject(text)
        message.optLong("server_ms", 0L).takeIf { it > 0 }?.let { serverMs ->
            // Desfase de relojes: el mensaje tardo media ida y vuelta en llegar.
            // Se suaviza con lo medido antes para que un mensaje lento no mueva
            // la posicion de golpe: eso se oiria como un salto en la musica.
            val measured = serverMs - System.currentTimeMillis() + rttMs / 2
            clockOffsetMs = if (rttMs > 0L) (clockOffsetMs * 3 + measured) / 4 else measured
        }

        when (message.optString("t")) {
            "joined" -> {
                val me = message.optJSONObject("me")
                val members = parseMembers(message.optJSONArray("members"), me?.optString("id"))
                val track = parseTrack(message.optJSONObject("state")?.optJSONObject("track"))
                val playing = message.optJSONObject("state")?.optBoolean("playing") == true
                val position = message.optJSONObject("state")?.optLong("position_ms", 0L) ?: 0L
                publish(
                    RoomSnapshot(
                        status = RoomStatus.CONNECTED,
                        code = message.optString("code", state.value.code),
                        myName = me?.optString("name") ?: name,
                        myId = me?.optString("id").orEmpty(),
                        members = members,
                        track = track,
                        playing = playing,
                        chat = state.value.chat,
                        restored = message.optBoolean("restored"),
                    )
                )
                onRejoined?.invoke()
                onEnteredRoom?.invoke(track)

                // Si la sala ya estaba sonando, se entra sincronizado al segundo exacto.
                // Si estaba cargando una canción, se carga en pausa y se avisa.
                if (track != null) {
                    val roomState = message.optJSONObject("state")
                    if (roomState?.optString("action") == "prepare") {
                        val seq = roomState.optLong("seq")
                        activeSeq = seq
                        isPreparing = true
                        onPrepare?.invoke(
                            RoomPrepareOrder(
                                seq = seq,
                                track = track,
                                positionMs = position,
                                playing = roomState.optBoolean("intent", true),
                            )
                        )
                    } else {
                        onAdopt?.invoke(track, position, playing, message.optLong("server_ms", serverNow()))
                    }
                }
            }

            "prepare" -> {
                // El otro cambió la canción: se carga EN PAUSA y se avisa.
                val seq = message.optLong("seq")
                val track = parseTrack(message.optJSONObject("track"))
                if (track == null || seq <= 0L) return
                // Un cambio anterior al ultimo arranque ya no pinta nada, y el
                // MISMO cambio repetido no se carga dos veces (eso reiniciaba la
                // cancion: el bucle de repeticiones).
                if (seq <= lastGoSeq) return
                if (activeSeq > 0L && seq <= activeSeq) return
                activeSeq = seq
                isPreparing = true
                publish(state.value.copy(preparing = true, track = track, playing = false))
                onPrepare?.invoke(
                    RoomPrepareOrder(
                        seq = seq,
                        track = track,
                        positionMs = message.optLong("position_ms", 0L),
                        playing = message.optBoolean("playing", true),
                    )
                )
            }

            "go" -> {
                // Arranque: los dos, a la vez, a partir de la hora del servidor.
                val seq = message.optLong("seq")
                if (seq <= lastGoSeq) return
                if (activeSeq > 0L && seq < activeSeq) return
                lastGoSeq = seq
                finishPrepare()
                val track = parseTrack(message.optJSONObject("track"))
                val playing = message.optBoolean("playing", true)
                publish(state.value.copy(track = track ?: state.value.track, playing = playing))
                onGo?.invoke(
                    RoomGo(
                        seq = seq,
                        track = track,
                        positionMs = message.optLong("position_ms", 0L),
                        playing = playing,
                        serverMs = message.optLong("server_ms", serverNow()),
                    )
                )
            }

            "renamed" -> {
                publish(state.value.copy(myName = message.optString("name", state.value.myName)))
            }

            "presence" -> {
                publish(state.value.copy(members = parseMembers(message.optJSONArray("members"))))
            }


            "state" -> {
                val action = message.optString("action", "play")
                val track = parseTrack(message.optJSONObject("track"))
                val playing = message.optBoolean("playing")
                publish(
                    state.value.copy(
                        track = track ?: state.value.track,
                        playing = playing,
                    )
                )
                onRemotePlayback?.invoke(
                    RemotePlayback(
                        action = action,
                        track = track,
                        positionMs = message.optLong("position_ms", 0L),
                        playing = playing,
                        serverMs = message.optLong("server_ms", serverNow()),
                        byName = message.optString("name"),
                    )
                )
            }

            "sync" -> {
                val playing = message.optBoolean("playing")
                publish(state.value.copy(playing = playing))
                onRemoteSync?.invoke(
                    RemoteSync(
                        positionMs = message.optLong("position_ms", 0L),
                        playing = playing,
                        serverMs = message.optLong("server_ms", serverNow()),
                    )
                )
            }

            "typing" -> {
                val who = message.optString("name")
                publish(state.value.copy(typingName = who))
                typingJob?.cancel()
                typingJob = scope.launch {
                    delay(TYPING_TTL_MS)
                    if (state.value.typingName == who) publish(state.value.copy(typingName = null))
                }
            }

            "chat" -> {
                val from = message.optString("from")
                val chatMessage = RoomChatMessage(
                    id = "$from-${message.optLong("server_ms")}-${message.optString("text").hashCode()}",
                    name = message.optString("name"),
                    text = message.optString("text"),
                    fromMe = from.isNotEmpty() && from == state.value.myId,
                )
                if (state.value.chat.none { it.id == chatMessage.id }) {
                    publish(
                        state.value.copy(
                            chat = (state.value.chat + chatMessage).takeLast(120),
                            // Quien escribe deja de "escribir" al mandar el mensaje.
                            typingName = if (chatMessage.fromMe) state.value.typingName else null,
                        )
                    )
                }
                onChat?.invoke(chatMessage)
            }

            "left" -> {
                val gone = message.optString("name")
                publish(
                    state.value.copy(
                        members = parseMembers(message.optJSONArray("members")),
                        chat = if (gone.isBlank()) state.value.chat
                        else state.value.chat + RoomChatMessage("sys-${message.optLong("server_ms")}", "", "$gone salió de la sala", false),
                    )
                )
            }

            "closed" -> {
                val reason = message.optString("reason", "la sala se cerró")
                publish(state.value.copy(status = RoomStatus.ERROR, error = reason, members = emptyList()))
                onClosed?.invoke(reason)
            }

            "pong" -> {
                pingSentAt.takeIf { it > 0 }?.let { sentAt ->
                    rttMs = System.currentTimeMillis() - sentAt
                    clockOffsetMs = message.optLong("server_ms") - (System.currentTimeMillis() - rttMs / 2)
                }
            }

            "error" -> {
                val reason = message.optString("msg", "error de la sala")
                publish(state.value.copy(status = RoomStatus.ERROR, error = reason))
            }
        }
    }

    private fun parseMembers(array: JSONArray?, overrideMyId: String? = null): List<RoomMember> {
        if (array == null) return emptyList()
        val myId = overrideMyId ?: state.value.myId
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            RoomMember(
                id = id,
                name = item.optString("name"),
                isMe = id.isNotEmpty() && id == myId,
            )
        }
    }

    private fun parseTrack(json: JSONObject?): RoomTrack? {
        if (json == null) return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return RoomTrack(
            id = id,
            title = json.optString("title"),
            artist = json.optString("artist"),
            artwork = json.optString("artwork").takeIf { it.isNotBlank() },
            durationMs = json.optLong("duration_ms", 0L),
        )
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        403 -> "Esta red tiene el acceso restringido"
        429 -> "Demasiadas salas creadas desde esta red, espera un rato"
        else -> "No pude crear la sala (error $code)"
    }

    companion object {
        private const val TAG = "ListeningRoom"

        private const val MAX_NAME_CHARS = 24

        /** Cuánto se queda el "está escribiendo" si no llega otro aviso. */
        private const val TYPING_TTL_MS = 3_500L
        private const val TYPING_THROTTLE_MS = 1_500L

        /** URL del backend de salas: la misma del registro de estadísticas. */
        fun baseUrl(context: Context): String =
            runCatching { context.getString(R.string.analytics_url).trim().trimEnd('/') }.getOrDefault("")
    }
}
