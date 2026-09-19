package com.openytmusic.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.os.Binder
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.openytmusic.app.innertube.PlayerResult
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.innertube.models.WatchEndpoint
import com.openytmusic.app.innertube.models.response.PlayerResponse
import com.openytmusic.app.MainActivity
import com.openytmusic.app.R
import com.openytmusic.app.constants.AudioNormalizationKey
import com.openytmusic.app.constants.AudioQuality
import com.openytmusic.app.constants.AudioQualityKey
import com.openytmusic.app.constants.AutoLoadMoreKey
import com.openytmusic.app.constants.AutoSkipNextOnErrorKey
import com.openytmusic.app.constants.BotWallDetectedKey
import com.openytmusic.app.constants.DiscordTokenKey
import com.openytmusic.app.constants.ClearRpcOnExitKey
import com.openytmusic.app.constants.EnableDiscordRPCKey
import com.openytmusic.app.utils.ListeningRoom
import com.openytmusic.app.utils.RemotePlayback
import com.openytmusic.app.utils.RemoteSync
import com.openytmusic.app.utils.RoomGo
import com.openytmusic.app.utils.RoomPrepareOrder
import com.openytmusic.app.utils.RoomSnapshot
import com.openytmusic.app.utils.RoomStatus
import com.openytmusic.app.utils.RoomTrack
import com.openytmusic.app.models.MediaMetadata
import com.openytmusic.app.extensions.metadata
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import com.openytmusic.app.constants.HideExplicitKey
import com.openytmusic.app.constants.MediaSessionConstants.CommandToggleLibrary
import com.openytmusic.app.constants.MediaSessionConstants.CommandToggleLike
import com.openytmusic.app.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.openytmusic.app.constants.MediaSessionConstants.CommandToggleShuffle
import com.openytmusic.app.constants.PauseListenHistoryKey
import com.openytmusic.app.constants.PersistentQueueKey
import com.openytmusic.app.constants.PlayerVolumeKey
import com.openytmusic.app.constants.RepeatModeKey
import com.openytmusic.app.constants.ShowLyricsKey
import com.openytmusic.app.constants.SkipSilenceKey
import com.openytmusic.app.db.MusicDatabase
import com.openytmusic.app.db.entities.Event
import com.openytmusic.app.db.entities.FormatEntity
import com.openytmusic.app.db.entities.LyricsEntity
import com.openytmusic.app.db.entities.RelatedSongMap
import com.openytmusic.app.di.DownloadCache
import com.openytmusic.app.di.PlayerCache
import com.openytmusic.app.extensions.SilentHandler
import com.openytmusic.app.extensions.collect
import com.openytmusic.app.extensions.collectLatest
import com.openytmusic.app.extensions.currentMetadata
import com.openytmusic.app.extensions.findNextMediaItemById
import com.openytmusic.app.extensions.mediaItems
import com.openytmusic.app.extensions.metadata
import com.openytmusic.app.extensions.toMediaItem
import com.openytmusic.app.lyrics.LyricsHelper
import com.openytmusic.app.models.PersistQueue
import com.openytmusic.app.models.toMediaMetadata
import com.openytmusic.app.playback.queues.EmptyQueue
import com.openytmusic.app.playback.queues.ListQueue
import com.openytmusic.app.playback.queues.Queue
import com.openytmusic.app.playback.queues.YouTubeQueue
import com.openytmusic.app.playback.queues.filterExplicit
import com.openytmusic.app.utils.CoilBitmapLoader
import com.openytmusic.app.utils.DiscordRPC
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.enumPreference
import com.openytmusic.app.utils.get
import com.openytmusic.app.utils.isInternetAvailable
import com.openytmusic.app.utils.reportException
import java.util.concurrent.atomic.AtomicBoolean
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    /**
     * Guard del auto-load-more: sin el, dos transiciones rapidas cerca del final de la
     * cola lanzaban dos peticiones con la MISMA continuacion y anadian items duplicados.
     */
    private val loadMoreInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager

    private val audioQuality by enumPreference(this, AudioQualityKey, AudioQuality.AUTO)

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.openytmusic.app.models.MediaMetadata?>(null)
    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.song(mediaMetadata?.id)
    }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private val normalizeFactor = MutableStateFlow(1f)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false

    private var discordRpc: DiscordRPC? = null
    private var lastRpcPlaying: Boolean? = null
    private var lastRpcSongId: String? = null

    /** Se levanta al cerrar el servicio: a partir de ahi no se manda ninguna
     *  presencia mas. Sin esto, el heartbeat de 3 s podia reenviar la ultima
     *  cancion DESPUES del clearPresence de onDestroy y dejaba la tarjeta
     *  "zombie" en Discord (el bug de la cancion que no se va nunca). */
    @Volatile
    private var rpcShuttingDown = false

    private fun isActuallyPlaying(): Boolean =
        player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)

    /** Mantiene la presencia de Discord coherente con el estado real de reproduccion.
     *  Dedupe unificado por (playing, id de cancion): solo envia cuando el
     *  estado REALMENTE cambio. Mata los rebotes de metadata del player al
     *  saltar canciones rapido (el bug de la cancion vieja en el RPC). */
    private fun syncDiscordPresence(force: Boolean = false) {
        if (rpcShuttingDown) return
        val rpc = discordRpc ?: return
        val song = currentSong.value ?: return
        val playing = isActuallyPlaying()
        val songId = song.song.id
        Log.d("VelqiRPC", "sync: playing=$playing song=$songId lastP=$lastRpcPlaying lastS=$lastRpcSongId force=$force")
        if (!force && playing == lastRpcPlaying && songId == lastRpcSongId) return
        lastRpcPlaying = playing
        scope.launch {
            lastRpcSongId = songId
            val pos = player.currentPosition.coerceAtLeast(0L)
            // Logica Dart: en pausa se manda la misma tarjeta SIN contador
            // (como Spotify) — nada de clears que generen fantasmas.
            Log.d("VelqiRPC", "enviando presencia playing=$playing pos=${pos}ms")
            rpc.updateSong(song, pos, playing)
        }
    }

    // ------------------------------------------------------ salas compartidas --
    // Escuchar musica con alguien mas. El socket vive AQUI, no en la pantalla:
    // asi la sincronizacion sigue viva con la app en segundo plano o el telefono
    // bloqueado, que es cuando de verdad se escucha musica.
    //
    // Regla de oro: todo lo que cambia por una orden de la sala se aplica EN
    // SILENCIO y de una en una. Antes cada movimiento del player rebotaba a la
    // sala como si fuera una accion del usuario, y de ahi salia el bucle de
    // "empieza sola, se reinicia, se asienta" al cambiar de cancion.
    val roomState = MutableStateFlow(RoomSnapshot())

    private var listeningRoom: ListeningRoom? = null

    /** Cuantas aplicaciones remotas hay en vuelo ahora mismo. */
    private val applyingRemoteCount = AtomicInteger(0)

    /** Silencio hasta esta hora (reloj local): el player avisa de sus cambios un
     *  poco DESPUES de que termina de aplicarse la orden del otro, y sin esta
     *  ventana esos avisos volvian a la sala como si fueran del usuario. */
    @Volatile
    private var outboundSilencedUntil = 0L

    /** Aplicacion remota en curso (cargar cancion + colocar reproduccion). */
    @Volatile
    private var remoteApplyJob: Job? = null

    /** Cambio de cancion en dos tiempos: nada suena hasta que los dos avisen. */
    @Volatile
    private var roomPreparing = false

    /** Si el cambio en curso lo pedi yo (y no el otro). Con esto, dos toques
     *  seguidos en "siguiente" no se pisan: gana el ultimo. */
    @Volatile
    private var roomLocalPrepare = false

    /** La pantalla de la sala esta abierta: si lo esta, no hace falta avisar de
     *  lo que ya estoy viendo. */
    @Volatile
    var roomScreenVisible = false

    /** Ultimo play/pausa que mande: un toque repetido manda UN aviso, no una
     *  tormenta de mensajes que el otro tenga que aplicar uno por uno. */
    @Volatile
    private var lastControlSentAt = 0L

    /**
     * Una pausa/play que llego MIENTRAS se cargaba la cancion: se guarda y se
     * aplica justo despues del arranque comun, para que no se pierda.
     */
    @Volatile
    private var pendingControl: PendingControl? = null

    private data class PendingControl(val positionMs: Long, val playing: Boolean, val serverMs: Long)

    /** Quienes estaban en la sala la ultima vez (para avisar de las entradas). */
    private var lastRoomNames: List<String> = emptyList()

    /** Ultima cancion ya preparada o aplicada: evita cargarla dos veces (eso era
     *  lo que reiniciaba la cancion varias veces seguidas al cambiarla). */
    @Volatile
    private var roomLastTrackId: String? = null

    @Volatile
    private var roomLastTrackAt = 0L

    /** Si estamos corrigiendo la deriva con la velocidad (para restaurarla). */
    @Volatile
    private var roomSpeedAdjusted = false

    fun isInRoom(): Boolean = listeningRoom?.state?.value?.isConnected == true

    /** Crea una sala y devuelve el codigo para compartir. */
    fun createRoom(name: String, onResult: (Result<String>) -> Unit) {
        freshRoom(name).createRoom(onResult)
    }

    /** Entra a una sala con el codigo que te pasaron. */
    fun joinRoom(code: String, name: String) {
        freshRoom(name).joinRoom(code)
    }

    fun leaveRoom() {
        val room = listeningRoom ?: return
        room.leave()
        listeningRoom = null
        roomPreparing = false
        roomLocalPrepare = false
        remoteApplyJob?.cancel()
        restoreRoomSpeed()
        roomState.value = RoomSnapshot()
    }

    fun sendRoomChat(text: String) {
        listeningRoom?.sendChat(text)
    }

    /** Cambia mi apodo en la sala (sin cuenta: solo para esta sesion). */
    fun renameInRoom(newName: String) {
        listeningRoom?.rename(newName)
    }

    /** "Estoy escribiendo en el chat de la sala". */
    fun sendRoomTyping() {
        listeningRoom?.sendTyping()
    }

    /** (Re)crea el cliente de sala. Si habia una sala abierta, la cierra antes. */
    private fun freshRoom(name: String): ListeningRoom {
        listeningRoom?.close()
        val room = ListeningRoom(
            context = applicationContext,
            name = name.trim().ifEmpty { "Alguien" },
            onSnapshot = { snapshot ->
                roomState.value = snapshot
                // Aviso de entrada: alguien nuevo en la sala (yo no cuento).
                val others = snapshot.members.filterNot { it.isMe }.map { it.name }
                if (snapshot.isConnected && others.size > lastRoomNames.size) {
                    (others - lastRoomNames.toSet()).firstOrNull()?.let { who ->
                        notifyRoomEvent(
                            title = getString(R.string.listening_room),
                            text = getString(R.string.listening_room_notice_joined, who),
                        )
                    }
                }
                lastRoomNames = others
            },
        )
        // OJO, esto era el bug de verdad: los mensajes de la sala llegan en el
        // hilo de RED (OkHttp) y ExoPlayer solo se puede tocar desde el hilo
        // principal. Tocar el player ahi lanzaba "Player is accessed on the
        // wrong thread", la excepcion se tragaba y el mensaje se PERDIA: la sala
        // parecia no recibir nada (nunca sonaba y el otro iba adelantado).
        // Ahora todo pasa por `scope`, que es el hilo principal.
        room.onRejoined = {
            scope.launch {
                roomPreparing = false
                roomLocalPrepare = false
            }
        }
        room.onEnteredRoom = { track -> scope.launch { enteredRoom(track) } }
        room.onChat = { message ->
            if (!message.fromMe) {
                // Fuera de la sala (o en otra pantalla) avisa como una mensajeria.
                notifyRoomEvent(
                    title = getString(R.string.listening_room_notice_chat_title, name.trim().ifEmpty { "Alguien" }, message.name),
                    text = message.text,
                )
            }
        }
        room.onRemotePlayback = { playback -> scope.launch { applyRemotePlayback(playback) } }
        room.onRemoteSync = { sync -> scope.launch { applyRemoteSync(sync) } }
        room.onPrepare = { order -> scope.launch { prepareFromRoom(order) } }
        room.onGo = { go -> scope.launch { startTogetherFromRoom(go) } }
        room.onAdopt = { track, position, playing, serverMs ->
            scope.launch { adoptRoomTrack(track, position, playing, serverMs) }
        }
        room.onClosed = { reason ->
            scope.launch {
                listeningRoom = null
                roomPreparing = false
                roomLocalPrepare = false
                restoreRoomSpeed()
                roomState.value = roomState.value.copy(status = RoomStatus.ERROR, error = reason)
            }
        }
        roomLastTrackId = null
        listeningRoom = room
        return room
    }

    /** Solo se le habla a la sala cuando no estamos aplicando nada de ella. */
    private fun canTalkToRoom(): Boolean {
        val room = listeningRoom ?: return false
        if (!room.state.value.isConnected) return false
        if (roomPreparing) return false
        if (applyingRemoteCount.get() > 0) return false
        return System.currentTimeMillis() >= outboundSilencedUntil
    }

    private fun silenceOutbound(ms: Long = ROOM_ECHO_GUARD_MS) {
        outboundSilencedUntil = System.currentTimeMillis() + ms
    }

    /**
     * Aviso de la sala (chat o alguien que entra). Se muestra solo cuando NO
     * estas mirando la sala: así llega igual si estas en otra pantalla de la app
     * o con ella de fondo, como cualquier mensajeria.
     */
    private fun notifyRoomEvent(title: String, text: String) {
        if (roomScreenVisible) return
        val manager = runCatching { getSystemService<NotificationManager>() }.getOrNull() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(ROOM_CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ROOM_CHANNEL_ID,
                        getString(R.string.listening_room),
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                )
            }
            val notification = NotificationCompat.Builder(this, ROOM_CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(ROOM_NOTIFICATION_ID, notification)
        }
    }

    private fun roomTrackOf(metadata: MediaMetadata): RoomTrack = RoomTrack(
        id = metadata.id,
        title = metadata.title,
        artist = metadata.artists.joinToString { it.name },
        artwork = metadata.thumbnailUrl,
        durationMs = metadata.duration.toLong() * 1000,
    )

    /**
     * Corre una aplicacion remota de una en una: si llega otra mientras la
     * anterior carga, la reemplaza. Antes se apilaban y cada una volvia a cargar
     * la cancion desde cero: ese era el bucle de "empieza sola varias veces".
     */
    private fun launchRemote(block: suspend () -> Unit) {
        remoteApplyJob?.cancel()
        remoteApplyJob = scope.launch {
            applyingRemoteCount.incrementAndGet()
            silenceOutbound()
            try {
                block()
            } finally {
                applyingRemoteCount.decrementAndGet()
                silenceOutbound()
            }
        }
    }

    /** En que segundo deberia ir AHORA, contando lo que tardo el mensaje. */
    private fun roomGoalPosition(positionMs: Long, playing: Boolean, serverMs: Long): Long {
        val base = positionMs.coerceAtLeast(0L)
        if (!playing) return base
        val room = listeningRoom ?: return base
        return (base + (room.serverNow() - serverMs) + ROOM_APPLY_LATENCY_MS).coerceAtLeast(0L)
    }

    /** Entre a la sala: si no hay nada puesto, lo que sonaba antes se detiene. */
    private fun enteredRoom(track: RoomTrack?) {
        roomPreparing = false
        roomLocalPrepare = false
        if (track == null) {
            // La sala manda: lo que estuviera sonando en el telefono se para, para
            // que los dos oigan exactamente lo mismo y no se mezcle con la sala.
            // Se calla el aviso a proposito: mi pausa no es una orden para el otro.
            silenceOutbound()
            runCatching { player.playWhenReady = false }
        }
    }

    // --------------------------------------------- cambio de cancion
    /**
     * Puse una cancion yo (siguiente, la toque en el reproductor, o sono sola).
     *
     * NO suena todavia: se pausa, se avisa a la sala y los dos la cargan. Cuando
     * el ultimo esta listo, el arranque sale para los dos a la vez. Asi no se oye
     * a uno empezar y al otro reiniciarlo encima (el bucle de repeticiones).
     */
    private fun notifyRoomTrackChange(metadata: MediaMetadata) {
        val room = listeningRoom ?: return
        if (!room.state.value.isConnected) return
        if (applyingRemoteCount.get() > 0) return
        if (System.currentTimeMillis() < outboundSilencedUntil) return
        val track = roomTrackOf(metadata)
        if (roomLastTrackId == track.id &&
            System.currentTimeMillis() - roomLastTrackAt < ROOM_TRACK_DEDUPE_MS
        ) {
            return
        }
        // Si ya habia un cambio cargando, gana este: el que suena manda.
        if (roomPreparing) room.cancelPrepare()
        roomLastTrackId = track.id
        roomLastTrackAt = System.currentTimeMillis()
        val intent = player.playWhenReady
        roomPreparing = true
        roomLocalPrepare = true
        player.playWhenReady = false
        silenceOutbound()
        val seq = room.startPrepare(track, 0L, intent)
        if (seq <= 0L) {
            roomPreparing = false
            roomLocalPrepare = false
            if (intent) player.playWhenReady = true
            return
        }
        scope.launch {
            awaitRoomReady(track.id)
            listeningRoom?.sendReady(seq)
        }
    }

    /**
     * Poner una cancion desde el BUSCADOR de la sala. Igual que el cambio normal,
     * con la diferencia de que aqui la cancion todavia no estaba cargada.
     */
    fun playInRoom(track: RoomTrack) {
        val room = listeningRoom ?: return
        if (!room.state.value.isConnected) return
        if (applyingRemoteCount.get() > 0) return
        if (roomPreparing) room.cancelPrepare()
        roomLastTrackId = track.id
        roomLastTrackAt = System.currentTimeMillis()
        roomPreparing = true
        roomLocalPrepare = true
        silenceOutbound()
        player.playWhenReady = false
        val seq = room.startPrepare(track, 0L, true)
        scope.launch {
            loadRoomTrack(track, 0L)
            listeningRoom?.sendReady(seq)
        }
    }

    /** El otro cambio la cancion: la cargo EN PAUSA y aviso cuando esta lista. */
    private fun prepareFromRoom(order: RoomPrepareOrder) {
        roomPreparing = true
        roomLocalPrepare = false
        silenceOutbound()
        launchRemote {
            loadRoomTrack(order.track, order.positionMs)
            listeningRoom?.sendReady(order.seq)
        }
    }

    /** Arranque comun: los dos empiezan en el mismo instante (hora del servidor). */
    private fun startTogetherFromRoom(go: RoomGo) {
        roomPreparing = false
        roomLocalPrepare = false
        go.track?.let {
            roomLastTrackId = it.id
            roomLastTrackAt = System.currentTimeMillis()
        }
        val pending = pendingControl
        pendingControl = null
        launchRemote {
            val incoming = go.track
            if (incoming != null && player.currentMediaItem?.mediaId != incoming.id) {
                loadRoomTrack(incoming, go.positionMs)
            }
            seatPlayback(roomGoalPosition(go.positionMs, go.playing, go.serverMs), go.playing)
            // Lo que llego mientras cargaba, ahora si.
            if (pending != null) {
                seatPlayback(
                    roomGoalPosition(pending.positionMs, pending.playing, pending.serverMs),
                    pending.playing,
                )
            }
        }
    }

    /** Entre a una sala que ya iba sonando: me pongo al dia con ella. */
    private fun adoptRoomTrack(track: RoomTrack, positionMs: Long, playing: Boolean, serverMs: Long) {
        if (roomPreparing) return
        roomLastTrackId = track.id
        roomLastTrackAt = System.currentTimeMillis()
        launchRemote {
            if (player.currentMediaItem?.mediaId != track.id) {
                loadRoomTrack(track, positionMs)
            }
            seatPlayback(roomGoalPosition(positionMs, playing, serverMs), playing)
        }
    }

    /**
     * Carga la cancion de la sala EN PAUSA y la deja lista para arrancar juntos:
     * devuelve cuando esta "asentada" (STATE_READY) o se agota la espera.
     */
    private suspend fun loadRoomTrack(track: RoomTrack, positionMs: Long) {
        if (player.currentMediaItem?.mediaId != track.id) {
            val metadata = MediaMetadata(
                id = track.id,
                title = track.title.ifBlank { "Cancion de la sala" },
                artists = listOf(
                    MediaMetadata.Artist(
                        id = null,
                        name = track.artist.ifBlank { "YouTube Music" },
                    )
                ),
                duration = (track.durationMs / 1000).toInt(),
                thumbnailUrl = track.artwork,
            )
            // En pausa a proposito: si empieza a sonar antes de tiempo, el otro
            // la "coloca" al entrar y se oye el reinicio.
            playQueue(
                YouTubeQueue(WatchEndpoint(videoId = track.id), metadata),
                playWhenReady = false,
            )
        } else {
            player.playWhenReady = false
        }
        awaitRoomReady(track.id)
        if (positionMs > 0L) player.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Espera a que la cancion este CARGADA y lista, no solo elegida. */
    private suspend fun awaitRoomReady(songId: String, timeoutMs: Long = ROOM_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (player.currentMediaItem?.mediaId == songId &&
                player.playbackState == Player.STATE_READY
            ) {
                return true
            }
            delay(60)
        }
        return player.currentMediaItem?.mediaId == songId
    }

    /** Orden de control (pausa, play o saltar en la barra): no cambia cancion. */
    private fun applyRemotePlayback(playback: RemotePlayback) {
        // Mientras se carga una cancion, las ordenes sueltas son del estado
        // viejo: aplicarlas pisaba la carga y armaba el ida y vuelta. Pero no se
        // tiran: se guardan y se aplican en cuanto arranca (si no, una pausa
        // justo en ese momento se perdia y los dos quedaban distintos).
        if (roomPreparing) {
            pendingControl = PendingControl(playback.positionMs, playback.playing, playback.serverMs)
            return
        }
        val incoming = playback.track
        val needsTrack = incoming != null && incoming.id != player.currentMediaItem?.mediaId
        if (needsTrack && incoming != null) {
            roomLastTrackId = incoming.id
            roomLastTrackAt = System.currentTimeMillis()
        }
        val targetMs = roomGoalPosition(playback.positionMs, playback.playing, playback.serverMs)
        launchRemote {
            if (needsTrack && incoming != null) loadRoomTrack(incoming, playback.positionMs)
            seatPlayback(targetMs, playback.playing)
        }
    }

    /** Latido de la sala: solo corrige la deriva, sin cambiar cancion. */
    private fun applyRemoteSync(sync: RemoteSync) {
        // Mientras se carga una cancion, el latido trae una posicion vieja:
        // aplicarlo movia la reproduccion y ese movimiento volvia a la sala como
        // un salto. Ese rebote era el bucle de "empieza sola y se asienta".
        if (roomPreparing) return
        if (applyingRemoteCount.get() > 0) return
        val targetMs = roomGoalPosition(sync.positionMs, sync.playing, sync.serverMs)
        launchRemote { seatPlayback(targetMs, sync.playing) }
    }

    /**
     * Deja la reproduccion en el segundo que toca, corrigiendo la deriva como lo
     * haria un buen oido: si la diferencia es grande se salta (seek), y si es
     * pequeña se ajusta la velocidad un 3 % (inaudible) hasta converger. Saltar
     * a cada rato se oye feo; esto no.
     */
    private fun seatPlayback(targetMs: Long, playing: Boolean) {
        // Sin cancion cargada no hay nada que sentar. Antes esto seguia mandando
        // play/pausa al vacio y el otro respondia: el bucle de pausas.
        if (player.mediaItemCount == 0 || player.currentMediaItem == null) return
        val goal = targetMs.coerceAtLeast(0L)
        val drift = player.currentPosition - goal
        val settled = abs(drift) <= ROOM_SOFT_DRIFT_MS

        if (!playing) {
            // Ya esta en pausa y en el segundo correcto: no se toca nada (un
            // "no hay nada que hacer" es la mejor defensa contra el bucle).
            if (!player.playWhenReady && settled) return
            if (player.playWhenReady) player.playWhenReady = false
            if (!settled) {
                player.seekTo(goal)
                setRoomSpeed(1f)
            }
            return
        }

        if (player.playWhenReady && settled) return
        if (!player.playWhenReady) player.playWhenReady = true
        when {
            abs(drift) > ROOM_HARD_DRIFT_MS -> {
                player.seekTo(goal)
                setRoomSpeed(1f)
            }
            abs(drift) > ROOM_SOFT_DRIFT_MS -> setRoomSpeed(if (drift > 0) 0.97f else 1.03f)
            else -> setRoomSpeed(1f)
        }
    }

    private fun setRoomSpeed(speed: Float) {
        val current = player.playbackParameters
        if (abs(current.speed - speed) < 0.001f) {
            roomSpeedAdjusted = speed != 1f
            return
        }
        player.setPlaybackParameters(PlaybackParameters(speed, current.pitch))
        roomSpeedAdjusted = speed != 1f
    }

    /** Al salir de la sala la velocidad vuelve a la normal (nunca se queda en 1.03x). */
    private fun restoreRoomSpeed() {
        if (!roomSpeedAdjusted) return
        val current = player.playbackParameters
        runCatching { player.setPlaybackParameters(PlaybackParameters(1f, current.pitch)) }
        roomSpeedAdjusted = false
    }

    /** Manda a la sala lo que acaba de hacer el usuario. */
    private fun sendRoomState(action: String) {
        if (!canTalkToRoom()) return
        val room = listeningRoom ?: return
        val metadata = currentMediaMetadata.value ?: return
        room.sendState(
            track = roomTrackOf(metadata),
            positionMs = player.currentPosition,
            playing = player.playWhenReady,
            action = action,
        )
    }

    /**
     * Pausa/play del usuario: eso es lo que viaja a la sala.
     *
     * OJO: esto NO se puede colgar de `onIsPlayingChanged`. `isPlaying` se pone en
     * false cada vez que el stream se queda sin buffer (y vuelve a true al
     * recuperarse), asi que con red regular la app mandaba "pausa" y "play" sola,
     * en bucle, y el otro se pausaba de verdad. Ese era el bucle de pausas al
     * cambiar de cancion. Aqui solo se avisa cuando el cambio lo pidio una persona.
     */
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
        // Un toque repetido de pausa/play manda UN aviso, no una rafaga: el otro
        // recibe una orden clara en vez de diez seguidas.
        val now = System.currentTimeMillis()
        if (now - lastControlSentAt < ROOM_CONTROL_COALESCE_MS) return
        lastControlSentAt = now
        sendRoomState(if (playWhenReady) "play" else "pause")
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // Un salto manual (la barra de progreso) tambien viaja a la sala.
        if (reason == Player.DISCONTINUITY_REASON_SEEK) sendRoomState("seek")
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this, { NOTIFICATION_ID }, CHANNEL_ID, R.string.music_player)
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                }
        )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_ALL)
                addListener(this@MusicService)
                sleepTimer = SleepTimer({ scope }, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
            }
        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleLibrary = ::toggleLibrary
        }
        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(CoilBitmapLoader(this, scope))
            .build()
        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        combine(playerVolume, normalizeFactor) { playerVolume, normalizeFactor ->
            playerVolume * normalizeFactor
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
        }
        currentSong.collect(scope) { song ->
            if (song != null) {
                syncDiscordPresence()
            } else {
                discordRpc?.clearPresence()
            }
        }

        // Latido de la sala: cada 5 s se manda la posicion (asi la deriva nunca
        // crece) y cada 20 s un ping, que mantiene vivo el socket y mide el reloj.
        scope.launch {
            while (isActive) {
                delay(5_000)
                val room = listeningRoom ?: continue
                if (!room.state.value.isConnected) continue
                // El ping sale SIEMPRE (cada 5 s): mantiene el socket vivo, mide el
                // reloj y le dice al servidor que sigo aqui aunque este cargando
                // una cancion. Si no, la sala me tomaria por desaparecido justo al
                // cambiar de cancion y me esperaria como si fuera un fantasma.
                room.ping()
                // Con una cancion cargando (o algo del otro aplicandose) el latido
                // mandaria una posicion que ya no vale: se calla y espera.
                if (!canTalkToRoom()) continue
                room.sendSync(player.currentPosition, player.playWhenReady)
            }
        }

        // Heartbeat estilo app Flutter de Velqi: cada 3 s re-envia la ultima
        // actividad verbatim (timestamp congelado). Mantiene la presencia viva
        // y coherente; si el socket cayo, setActivity reconecta.
        scope.launch {
            while (isActive) {
                delay(3_000)
                if (rpcShuttingDown) continue
                withTimeoutOrNull(5_000) { discordRpc?.refresh() }
            }
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged()
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id).first() == null) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics
                        )
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged()
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            normalizeFactor.value = if (normalizeAudio && format?.loudnessDb != null) {
                min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
            } else {
                1f
            }
        }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (key, enabled) ->
                if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                }
                discordRpc = null
                lastRpcPlaying = null
                lastRpcSongId = null
                // Solo se abre el socket si hay un ID de Discord propio: sin ID, la
                // tarjeta saldria firmada por otra aplicacion (antes, la de InnerTune).
                if (key != null && enabled && DiscordRPC.isConfigured(this)) {
                    discordRpc = DiscordRPC(this, key)
                    syncDiscordPresence(force = true)
                }
            }


        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                playQueue(
                    queue = ListQueue(
                        title = queue.title,
                        items = queue.items.map { it.toMediaItem() },
                        startIndex = queue.mediaItemIndex,
                        position = queue.position
                    ),
                    playWhenReady = false
                )
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.inLibrary != null) R.string.remove_from_library else R.string.add_to_library))
                    .setIconResId(if (currentSong.value?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add)
                    .setSessionCommand(CommandToggleLibrary)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            }
                        )
                    )
                    .setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        }
                    )
                    .setSessionCommand(CommandToggleRepeatMode)
                    .build()
            )
        )
    }

    private suspend fun recoverSong(mediaId: String, playerResponse: PlayerResponse? = null) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playerResponse ?: YouTube.player(mediaId).getOrNull()?.response)?.videoDetails?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main) + Job()
        }
        currentQueue = queue
        queueTitle = null
        // Velqi: toda lista nueva entra en bucle (primera -> ... -> ultima -> primera). El modo
        // "sin repeticion" se GUARDA en preferencias, asi que un toque al boton de repeticion
        // (el de la app o el de la notificacion/lock screen) dejaba la lista muerta en la ultima
        // cancion para siempre, incluso despues de reiniciar. Se reafirma aqui, en cada cola.
        player.repeatMode = REPEAT_MODE_ALL
        // Velqi: al iniciar una cola nueva (cancion/album/playlist) la velocidad y el
        // tono vuelven a 1x/0. Asi un tempo previo nunca deja la musica en x2 sin avisar.
        player.playbackParameters = PlaybackParameters.DEFAULT
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }

        scope.launch(SilentHandler) {
            val initialStatus = withContext(Dispatchers.IO) {
                queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
            }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                // add missing songs back, without affecting current playing song
                player.addMediaItems(0, initialStatus.items.subList(0, initialStatus.mediaItemIndex))
                player.addMediaItems(initialStatus.items.subList(initialStatus.mediaItemIndex + 1, initialStatus.items.size))
            } else {
                player.setMediaItems(initialStatus.items, if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0, initialStatus.position)
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return
        scope.launch(SilentHandler) {
            // La radio se pide ANTES de tocar la cola. Al reves (vaciarla primero)
            // cualquier fallo de red dejaba la reproduccion sin nada despues de la
            // cancion actual, que es justo lo que hacia parecer que no servia.
            val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaMetadata.id))
            val initialStatus = withContext(Dispatchers.IO) {
                radioQueue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
            }
            val items = initialStatus.items.drop(1)
            if (items.isEmpty()) return@launch
            if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
            if (player.currentMediaItemIndex < player.mediaItemCount - 1) player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            player.addMediaItems(items)
            currentQueue = radioQueue
        }
    }

    fun playNext(items: List<MediaItem>) {
        player.addMediaItems(if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1, items)
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        // Con aleatorio activo los temas nuevos se reparten en posiciones
        // aleatorias; si no, quedarian todos al final de la parte barajada
        if (player.shuffleModeEnabled && items.isNotEmpty()) {
            val first = player.mediaItemCount - items.size
            items.indices.forEach { i ->
                val from = first + i
                val to = (first until player.mediaItemCount).random()
                player.moveMediaItem(from, to)
            }
        }
        player.prepare()
    }

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLike())
            }
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Cambio de cancion con la sala abierta: se avisa ya. Se hace AQUI (y no
        // en el flujo de la base) porque este aviso del player ya trae la cancion
        // NUEVA: desde el otro lado no se manda la vieja por error.
        mediaItem?.metadata?.let { metadata ->
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                notifyRoomTrackChange(metadata)
            }
        }

        // Auto load more songs (una sola pagina en vuelo a la vez)
        val shouldAutoLoadMore = dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage()
        if (shouldAutoLoadMore && loadMoreInFlight.compareAndSet(false, true)) {
            scope.launch(SilentHandler) {
                try {
                    val mediaItems = currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                    if (player.playbackState != STATE_IDLE) {
                        player.addMediaItems(mediaItems)
                    }
                } finally {
                    loadMoreInFlight.set(false)
                }
            }
        }

        // Velqi: con aleatorio activo, solo cuando la cancion TERMINA SOLA (AUTO)
        // se elige la siguiente al azar. NO se reacciona a SEEK: el salto aleatorio
        // mismo usa seekTo y reaccionar a SEEK provocaria un bucle infinito (crash).
        if (player.shuffleModeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            shuffleToRandomNext()
        }
    }

    /**
     * Con aleatorio activo: elige al azar la siguiente cancion entre las que no
     * han sonado en esta sesion de cola (o todas menos la actual si ya sonaron
     * todas) y salta a ella.
     */
    fun shuffleToRandomNext() {
        if (player.mediaItemCount < 2) return
        playedMediaIds.add(player.currentMediaItem?.mediaId ?: return)
        val candidates = (0 until player.mediaItemCount)
            .filter { it != player.currentMediaItemIndex && player.getMediaItemAt(it).mediaId !in playedMediaIds }
            .ifEmpty { (0 until player.mediaItemCount).filter { it != player.currentMediaItemIndex } }
        val target = candidates.random()
        if (target != player.currentMediaItemIndex) {
            player.seekTo(target, 0)
        }
    }

    /** IDs ya reproducidos desde que se activo el aleatorio. */
    private val playedMediaIds = mutableSetOf<String>()

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            currentQueue = EmptyQueue
            queueTitle = null
        }
        // Red de seguridad del bucle: si la lista llega al final con la repeticion apagada (un
        // toque al boton del sistema a mitad de lista, o un valor viejo guardado), volvemos a la
        // primera cancion en vez de dejar la musica muerta en la ultima. Detener la reproduccion
        // es otra cosa: apagar el player deja STATE_IDLE, no STATE_ENDED, asi que no pasa por aqui.
        if (playbackState == STATE_ENDED && player.mediaItemCount > 0) {
            player.repeatMode = REPEAT_MODE_ALL
            player.seekTo(0, 0)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
            }
            syncDiscordPresence()
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }


    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        // Velqi: el boton solo activa/desactiva el modo, como Spotify/YT Music.
        // La cola no se reordena; el salto aleatorio ocurre en la siguiente
        // transicion (ver onMediaItemTransition).
        if (shuffleModeEnabled) {
            playedMediaIds.clear()
            player.currentMediaItem?.mediaId?.let { playedMediaIds.add(it) }
        } else {
            playedMediaIds.clear()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (dataStore.get(AutoSkipNextOnErrorKey, false) &&
            isInternetAvailable(this) &&
            player.hasNextMediaItem()
        ) {
            if (player.shuffleModeEnabled) {
                shuffleToRandomNext()
            } else {
                player.seekToNext()
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .cookieJar(
                                        // Kernel de Velqi: sin CookieJar, OkHttp descarta las cookies
                                        // de proteccion de stream (SPCC) y los range requests del seek
                                        // llegan sin cookie -> 403. Con jar, el seek funciona.
                                        object : CookieJar {
                                            private val store = HashMap<String, List<Cookie>>()
                                            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                                                synchronized(store) { store[url.host] = cookies }
                                            }
                                            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                                                synchronized(store) { (store[url.host] ?: emptyList()).filter { it.expiresAt > System.currentTimeMillis() } }
                                        }
                                    )
                                    .build()
                            )
                        )
                    )
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        val songUrlCache = HashMap<String, Pair<String, Long>>()
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            if (downloadCache.isCached(mediaId, dataSpec.position, if (dataSpec.length >= 0) dataSpec.length else 1) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            // Check whether format exists so that users from older version can view format details
            // There may be inconsistent between the downloaded file and the displayed info if user change audio quality frequently
            val playedFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).first() }
            val playerResult = runBlocking(Dispatchers.IO) { resolvePlayerResponse(mediaId) }
            val playerResponse = playerResult.response

            val adaptiveFormat =
                if (playedFormat != null) {
                    playerResponse.streamingData?.adaptiveFormats?.find {
                        // Use itag to identify previously played format
                        it.itag == playedFormat.itag
                    }
                } else {
                    playerResponse.streamingData?.adaptiveFormats
                        ?.filter { it.isAudio }
                        ?.maxByOrNull {
                            it.bitrate * when (audioQuality) {
                                AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                                AudioQuality.HIGH -> 1
                                AudioQuality.LOW -> -1
                            } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
                        }
                }
            // Kernel de Velqi (estrategia de Velqi): los streams adaptativos con spc solo
            // sirven ~1MB sin PO Token (403 en seek). El stream muxed (itag 18) sirve el
            // archivo completo con rangos ilimitados -> muxed-first.
            val format = adaptiveFormat
                ?: playerResponse.streamingData?.formats?.firstOrNull { it.url != null }
                ?: throw PlaybackException(getString(R.string.error_no_stream), null, ERROR_CODE_NO_STREAM)

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength!!,
                        loudnessDb = playerResponse.playerConfig?.audioConfig?.loudnessDb
                    )
                )
            }
            scope.launch(Dispatchers.IO) { recoverSong(mediaId, playerResponse) }

            val muxedFormat = playerResponse.streamingData?.formats?.firstOrNull { it.url != null }
            val useMuxed = format.isAudio && muxedFormat != null
            var streamUrl = if (useMuxed) muxedFormat!!.url!! else format.url!!
            // PoToken de streaming: sin el, un adaptativo con spc solo sirve ~1MB y da 403 al
            // hacer seek. Se toma del propio PlayerResult, asi pertenece a ESTA resolucion y no lo
            // puede pisar otra concurrente (recoverSong, prefetch, descarga).
            //
            // Solo se inyecta cuando se reproduce un ADAPTATIVO: el muxed (itag 18) sirve el
            // archivo completo con rangos ilimitados y no consulta el pot, asi que agregarlo ahi
            // no aporta nada y ensucia el diagnostico del log.
            val playingMuxedStream = !format.isAudio || useMuxed
            if (!playingMuxedStream) {
                playerResult.streamingDataPoToken?.let { pot ->
                    if ("pot=" !in streamUrl) {
                        streamUrl += (if ('?' in streamUrl) '&' else '?') + "pot=" + pot
                    }
                }
            }
            android.util.Log.d("KernelVelqi", "stream itag=" + (if (useMuxed) muxedFormat!!.itag else format.itag) + " muxed=" + useMuxed + " cliente=" + playerResult.clientUsed + " pot=" + (!playingMuxedStream && playerResult.streamingDataPoToken != null) + " intento=" + playerResult.poTokenAttempt + " url=" + streamUrl.take(300))
            songUrlCache[mediaId] = streamUrl to System.currentTimeMillis() + playerResponse.streamingData!!.expiresInSeconds * 1000L
            dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
        }
    }

    /**
     * Resuelve `/player` con reintentos acotados.
     *
     * La razon de ser: cuando YouTube tira el muro anti-bot, la primera resolucion suele perder
     * mientras el BotGuard todavia arranca (ver `PoTokenGenerator`: en frio devuelve `null` a
     * proposito para no bloquear el reproductor). Un segundo intento unos segundos despues suele
     * encontrar el token listo y ganar con WEB_REMIX, asi que la cancion arranca en vez de morir.
     *
     * Un error de red determinista (sin internet, DNS) NO se reintenta: solo haria esperar al
     * usuario para dar el mismo error.
     */
    private suspend fun resolvePlayerResponse(mediaId: String): PlayerResult {
        var lastFailure: Throwable? = null
        var walledDetail: String? = null
        repeat(RESOLVE_MAX_ATTEMPTS) { attempt ->
            val result = YouTube.player(mediaId)
            val playerResult = result.getOrNull()
            if (playerResult != null && playerResult.response.playabilityStatus.status == "OK") {
                // El muro pudo pegar y el rescate haber ganado sin que el usuario se enterara: eso
                // tambien es un dato, y es el que dice si el PoToken sirve de algo.
                playerResult.poTokenAttempt?.let { attemptInfo ->
                    reportBotWall(
                        mediaId = mediaId,
                        detail = "resuelto | $attemptInfo | cliente=${playerResult.clientUsed}",
                    )
                }
                return playerResult
            }

            val throwable = result.exceptionOrNull()
            when (throwable) {
                is ConnectException, is UnknownHostException -> throw PlaybackException(
                    getString(R.string.error_no_internet), throwable, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                )

                is SocketTimeoutException -> throw PlaybackException(
                    getString(R.string.error_timeout), throwable, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                )
            }

            val playabilityStatus = playerResult?.response?.playabilityStatus
            val walled = playabilityStatus?.status == "LOGIN_REQUIRED" ||
                playabilityStatus?.reason?.contains("not a bot", ignoreCase = true) == true
            if (walled) {
                walledDetail = "sin rescate | intento=${playerResult?.poTokenAttempt ?: "sin intento"} | " +
                    "cliente=${playerResult?.clientUsed}"
            }

            lastFailure = throwable ?: PlaybackException(
                playabilityStatus?.reason, null, PlaybackException.ERROR_CODE_REMOTE_ERROR
            )

            if (attempt < RESOLVE_MAX_ATTEMPTS - 1) {
                val backoff = if (walled) RESOLVE_WALL_RETRY_DELAY_MS else RESOLVE_RETRY_DELAY_MS
                Log.w(
                    "KernelVelqi",
                    "player() fallo (intento ${attempt + 1}/$RESOLVE_MAX_ATTEMPTS, walled=$walled): " +
                        "${throwable?.javaClass?.simpleName} ${throwable?.message}. Reintento en ${backoff}ms"
                )
                delay(backoff)
            }
        }
        // Se perdio la reproduccion por el muro: este caso si se reporta siempre.
        walledDetail?.let { reportBotWall(mediaId = mediaId, detail = it, always = true) }

        throw when (val failure = lastFailure) {
            is PlaybackException -> failure
            null -> PlaybackException(getString(R.string.error_unknown), null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
            else -> PlaybackException(getString(R.string.error_unknown), failure, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        }
    }

    /** Evita reportar una vez por cancion cuando el muro esta pegado durante toda la sesion. */
    private val botWallReported = AtomicBoolean(false)

    /**
     * Telemetria del muro anti-bot: no-fatal y flavor-aware via [reportException] (Crashlytics en
     * `full`, solo log en `foss`).
     *
     * Sin esto se va a ciegas: no se sabe si el muro pega de verdad, con que cliente se sale ni si
     * habia sesion. El caso "resuelto" se manda **una sola vez por proceso** (con el muro pegado
     * seria una por cancion); el caso "sin rescate" se manda siempre, porque ahi si se perdio
     * reproduccion.
     */
    private fun reportBotWall(mediaId: String, detail: String, always: Boolean = false) {
        // El aviso de la UI se enciende SIEMPRE que se detecta el muro, sea rescatado o no: es la
        // unica forma de que el usuario se entere de que iniciar sesion lo evita. Escribir la
        // preferencia es idempotente, asi que no hay ruido aunque el muro pegue en cada cancion.
        scope.launch(Dispatchers.IO) {
            dataStore.edit { it[BotWallDetectedKey] = true }
        }
        if (!always && !botWallReported.compareAndSet(false, true)) return
        val hasSession = YouTube.cookie?.contains("SAPISID") == true
        reportException(BotWallException("muro anti-bot | $detail | sesion=$hasSession | mediaId=$mediaId"))
    }

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink.Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(2_000_000, 0.01f, 2_000_000, 0, 256),
                        SonicAudioProcessor()
                    )
                )
                .build()
        }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            ExtractorsFactory {
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor())
            }
        )

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        if (playbackStats.totalPlayTimeMs >= 30000 && !dataStore.get(PauseListenHistoryKey, false)) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs
                        )
                    )
                } catch (_: SQLException) {
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.playbackState == STATE_IDLE) {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            return
        }
        val persistQueue = PersistQueue(
            title = queueTitle,
            items = player.mediaItems.mapNotNull { it.metadata },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )
        runCatching {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistQueue)
                }
            }
        }.onFailure {
            reportException(it)
        }
    }

    override fun onDestroy() {
        // 1) Primerisimo: cortar el heartbeat. Si se cancela despues del clearPresence,
        //    un refresh en vuelo reenvia la cancion y la tarjeta reaparece en Discord
        //    (se queda "zombie" mostrando algo que ya no suena).
        rpcShuttingDown = true
        scope.cancel()

        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }

        // 2) Con el heartbeat ya muerto, limpiar la presencia. Ya no depende de
        //    isRpcRunning(): si el socket venia reconectando, ese chequeo daba false y
        //    nunca se borraba la tarjeta. El ajuste de privacidad decide si se limpia.
        // Salir de la sala con despedida, para que el otro no vea un fantasma.
        runCatching { listeningRoom?.close() }
        listeningRoom = null

        val rpc = discordRpc
        if (rpc != null) {
            if (dataStore.get(ClearRpcOnExitKey, true)) {
                // runCatching: si el socket venia caido, el envio puede fallar y
                // onDestroy NUNCA debe tumbar el cierre del servicio.
                runCatching {
                    runBlocking {
                        withTimeoutOrNull(3_000) { rpc.clearPresence() }
                    }
                }
            }
            runCatching { rpc.closeRPC() }
        }
        discordRpc = null
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Cerrar la app desde Recientes no debe matar una reproduccion en curso;
        // el servicio solo se detiene si no esta sonando nada.
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        /** Deriva a partir de la cual se salta (seek): por debajo no se oye. */
        private const val ROOM_HARD_DRIFT_MS = 700L

        /** Deriva que ya se corrige con un cambio de velocidad inaudible (3 %). */
        private const val ROOM_SOFT_DRIFT_MS = 150L

        /** Margen por el buffering: se coloca un pelin mas adelante para no entrar tarde. */
        private const val ROOM_APPLY_LATENCY_MS = 120L

        /** Silencio tras aplicar algo del otro: sus avisos llegan un poco despues. */
        private const val ROOM_ECHO_GUARD_MS = 1_500L

        /** Dos pausas/play seguidos en este margen cuentan como uno solo. */
        private const val ROOM_CONTROL_COALESCE_MS = 350L

        private const val ROOM_CHANNEL_ID = "listening_room"
        private const val ROOM_NOTIFICATION_ID = 992

        /** La misma cancion no se prepara dos veces seguidas (evita reinicios). */
        private const val ROOM_TRACK_DEDUPE_MS = 20_000L

        /** Espera maxima a que la cancion quede cargada y lista (corta: mejor
         *  entrar con un pelin de desfase que hacer esperar al otro). */
        private const val ROOM_READY_TIMEOUT_MS = 3_000L

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L

        /**
         * Intentos de resolucion de `/player` por pista. Un fallo transitorio no debe detener la
         * cancion: el reintento cubre el muro anti-bot que el BotGuard esta terminando de calentar.
         */
        const val RESOLVE_MAX_ATTEMPTS = 2

        /** Espera tras un muro anti-bot: cubre el arranque en frio del BotGuard (~2-5 s). */
        const val RESOLVE_WALL_RETRY_DELAY_MS = 3_000L

        /** Espera tras un fallo sin muro: suficiente para un hipo de red, sin hacer esperar de mas. */
        const val RESOLVE_RETRY_DELAY_MS = 500L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
    }
}

/**
 * Evento de diagnostico, no un fallo real: YouTube devolvio el muro anti-bot. Se reporta como
 * no-fatal para poder medir su frecuencia real (filtrable por nombre en Crashlytics).
 */
private class BotWallException(message: String) : Exception(message)
