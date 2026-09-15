package com.openytmusic.app.playback

import android.app.PendingIntent
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
import com.openytmusic.app.constants.EnableDiscordRPCKey
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

    private fun isActuallyPlaying(): Boolean =
        player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)

    /** Mantiene la presencia de Discord coherente con el estado real de reproduccion.
     *  Dedupe unificado por (playing, id de cancion): solo envia cuando el
     *  estado REALMENTE cambio. Mata los rebotes de metadata del player al
     *  saltar canciones rapido (el bug de la cancion vieja en el RPC). */
    private fun syncDiscordPresence(force: Boolean = false) {
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

        // Heartbeat estilo app Flutter de Velqi: cada 3 s re-envia la ultima
        // actividad verbatim (timestamp congelado). Mantiene la presencia viva
        // y coherente; si el socket cayo, setActivity reconecta.
        scope.launch {
            while (isActive) {
                delay(3_000)
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
                if (key != null && enabled) {
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
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        if (discordRpc?.isRpcRunning() == true) {
            // Limpiar la presencia antes de cerrar el socket: evita la
            // actividad fantasma que Discord deja colgada al morir la app.
            runBlocking {
                withTimeoutOrNull(3_000) { discordRpc?.clearPresence() }
            }
            discordRpc?.closeRPC()
        }
        discordRpc = null
        // Cancelar ANTES de liberar el player: los collectors y los bucles de este
        // scope seguian vivos sobre un player ya liberado (y escribian la cola
        // persistida con un estado invalido).
        scope.cancel()
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
