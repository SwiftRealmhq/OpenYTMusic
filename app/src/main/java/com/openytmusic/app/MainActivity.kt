package com.openytmusic.app

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import coil.request.ImageRequest
import com.valentinilk.shimmer.LocalShimmerTheme
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.constants.AppBarHeight
import com.openytmusic.app.constants.DarkModeKey
import com.openytmusic.app.constants.DefaultOpenTabKey
import com.openytmusic.app.constants.DisableScreenshotKey
import com.openytmusic.app.constants.DynamicThemeKey
import com.openytmusic.app.constants.MiniPlayerGap
import com.openytmusic.app.constants.MiniPlayerHeight
import com.openytmusic.app.constants.NavigationBarAnimationSpec
import com.openytmusic.app.constants.NavigationBarHeight
import com.openytmusic.app.constants.PauseSearchHistoryKey
import com.openytmusic.app.constants.PureBlackKey
import com.openytmusic.app.constants.SearchSource
import com.openytmusic.app.constants.SearchSourceKey
import com.openytmusic.app.constants.AppUsageByDayKey
import com.openytmusic.app.constants.AppUsageTimeKey
import com.openytmusic.app.constants.StopMusicOnTaskClearKey
import com.openytmusic.app.db.MusicDatabase
import com.openytmusic.app.db.entities.SearchHistory
import com.openytmusic.app.extensions.toEnum
import com.openytmusic.app.extensions.toMediaItem
import com.openytmusic.app.playback.DownloadUtil
import com.openytmusic.app.playback.MusicService
import com.openytmusic.app.playback.MusicService.MusicBinder
import com.openytmusic.app.playback.PlayerConnection
import com.openytmusic.app.playback.queues.ListQueue
import com.openytmusic.app.ui.component.BotWallNotice
import com.openytmusic.app.ui.component.SignedOutNotice
import com.openytmusic.app.ui.component.BottomSheetMenu
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.component.LocalMenuState
import com.openytmusic.app.ui.component.SearchBar
import com.openytmusic.app.ui.component.rememberBottomSheetState
import com.openytmusic.app.ui.component.shimmer.ShimmerTheme
import com.openytmusic.app.ui.menu.YouTubeSongMenu
import com.openytmusic.app.ui.player.BottomSheetPlayer
import com.openytmusic.app.ui.screens.Screens
import com.openytmusic.app.ui.screens.navigationBuilder
import com.openytmusic.app.ui.screens.search.LocalSearchScreen
import com.openytmusic.app.ui.screens.search.OnlineSearchScreen
import com.openytmusic.app.ui.screens.settings.DarkMode
import com.openytmusic.app.ui.screens.settings.NavigationTab
import com.openytmusic.app.ui.theme.ColorSaver
import com.openytmusic.app.ui.theme.DefaultThemeColor
import com.openytmusic.app.ui.theme.LunaTheme
import com.openytmusic.app.ui.theme.extractThemeColor
import com.openytmusic.app.ui.utils.appBarScrollBehavior
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.ui.utils.resetHeightOffset
import com.openytmusic.app.utils.UpdateInfo
import com.openytmusic.app.utils.Updater
import com.openytmusic.app.utils.applyAppLocale
import androidx.datastore.preferences.core.edit
import com.openytmusic.app.utils.AppUsageTracker
import com.openytmusic.app.utils.addDayUsage
import com.openytmusic.app.utils.dataStore
import java.time.LocalDate
import com.openytmusic.app.utils.get
import com.openytmusic.app.utils.rememberEnumPreference
import com.openytmusic.app.utils.rememberPreference
import com.openytmusic.app.utils.reportException
import com.openytmusic.app.utils.urlEncode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    private var updateDialog by mutableStateOf<UpdateInfo?>(null)

    // Medicion del tiempo con la app en primer plano (estadisticas)
    private fun accumulateAppUsageTime() {
        val elapsed = AppUsageTracker.consumeDelta()
        if (elapsed <= 0) return
        val today = LocalDate.now().toString()
        lifecycleScope.launch {
            dataStore.edit {
                it[AppUsageTimeKey] = (it[AppUsageTimeKey] ?: 0L) + elapsed
                it[AppUsageByDayKey] = addDayUsage(it[AppUsageByDayKey], today, elapsed)
            }
        }
    }

    // Aplica el idioma elegido en los ajustes de la app a toda la interfaz
    // (recreando la actividad se vuelve a evaluar este contexto).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.applyAppLocale())
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, MusicService::class.java))
        bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        AppUsageTracker.onAppForeground()
    }

    override fun onStop() {
        accumulateAppUsageTime()
        AppUsageTracker.onAppBackground()
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dataStore.get(StopMusicOnTaskClearKey, false) && playerConnection?.isPlaying?.value == true && isFinishing) {
            stopService(Intent(this, MusicService::class.java))
            unbindService(serviceConnection)
            playerConnection = null
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Acumula el tiempo de uso cada 30s (asi no se pierde si matan el proceso)
        lifecycleScope.launch {
            while (isActive) {
                delay(30_000)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    accumulateAppUsageTime()
                }
            }
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            LaunchedEffect(Unit) {
                if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                    Updater.getLatestVersion().onSuccess { info ->
                        latestVersionName = info.versionName
                        // Solo ofrecer descarga si la version remota es MAYOR (nunca un rollback).
                        if (Updater.isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
                            updateDialog = info
                        }
                    }
                }
            }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            // OpenYTMusic es solo dark: el logo rojo siempre calca sobre fondo oscuro.
            val useDarkTheme = true
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(playerConnection, enableDynamicTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    themeColor = if (song != null) {
                        withContext(Dispatchers.IO) {
                            val result = imageLoader.execute(
                                ImageRequest.Builder(this@MainActivity)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false) // pixel access is not supported on Config#HARDWARE bitmaps
                                    .build()
                            )
                            (result.drawable as? BitmapDrawable)?.bitmap?.extractThemeColor() ?: DefaultThemeColor
                        }
                    } else DefaultThemeColor
                }
            }

            LunaTheme(
                darkTheme = useDarkTheme,
                pureBlack = false,
                themeColor = themeColor
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                    val navController = rememberNavController()
                    // Marca de tiempo de la ultima busqueda iniciada: se usa para
                    // absorber el BACK fantasma que dispara el IME al cerrarse tras
                    // pulsar Enter con teclado fisico (tipico en emulador).
                    var lastSearchNavigate by remember { mutableStateOf(0L) }
                    val navControllerId = System.identityHashCode(navController)
                    DisposableEffect(navController) {
                        Log.d("VELQIDBG", "attach observer ctrl=$navControllerId start=${navController.currentDestination?.route}")
                        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
                            val stack = controller.currentBackStack.value.joinToString(" -> ") { it.destination.route ?: "?" }
                            Log.d("VELQIDBG", "DEST=${destination.route} ctrl=${System.identityHashCode(controller)} stack=[$stack]")
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose {
                            Log.d("VELQIDBG", "DETACH observer ctrl=$navControllerId")
                            navController.removeOnDestinationChangedListener(listener)
                        }
                    }
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val inSelectMode = navBackStackEntry?.savedStateHandle?.getStateFlow("inSelectMode", false)?.collectAsState()

                    val navigationItems = remember { Screens.MainScreens }
                    val defaultOpenTab = remember {
                        dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                    }
                    val tabOpenedFromShortcut = remember {
                        when (intent?.action) {
                            ACTION_SONGS -> NavigationTab.SONG
                            ACTION_ALBUMS -> NavigationTab.ALBUM
                            ACTION_PLAYLISTS -> NavigationTab.PLAYLIST
                            else -> null
                        }
                    }
                    val topLevelScreens = listOf(
                        Screens.Home.route,
                        Screens.Songs.route,
                        Screens.Artists.route,
                        Screens.Albums.route,
                        Screens.Playlists.route,
                        "library",
                        "mood_and_genres",
                        "settings"
                    )

                    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }
                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }
                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        Log.d("VELQIDBG", "active->$newActive route=${navBackStackEntry?.destination?.route}")
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }
                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            Log.d("VELQIDBG", "onSearch text=[$it] route=${navBackStackEntry?.destination?.route}")
                            // Navegar PRIMERO: colapsar la barra antes de navegar (cierre del
                            // teclado/foco) revertia la navegacion al Home al instante.
                            navController.navigate("search/${it.urlEncode()}")
                            Log.d("VELQIDBG", "onSearch navigate OK")
                            lastSearchNavigate = SystemClock.uptimeMillis()
                            onActiveChange(false)
                            onActiveChange(false)
                            if (dataStore[PauseSearchHistoryKey] != true) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    // La barra de busqueda solo aparece al tocar el icono de
                    // buscar (o en la pantalla de resultados): las pestanas de
                    // biblioteca ya no la muestran automaticamente.
                    val shouldShowSearchBar = remember(active, navBackStackEntry, inSelectMode?.value) {
                        val currentRoute = navBackStackEntry?.destination?.route
                        (active || currentRoute?.startsWith("search/") == true) &&
                                inSelectMode?.value != true
                    }
                    val shouldShowNavigationBar = remember(navBackStackEntry, active) {
                        val route = navBackStackEntry?.destination?.route
                        route == null ||
                                ((navigationItems.fastAny { it.route == route } ||
                                        route == "mood_and_genres") && !active)
                    }
                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = ""
                    )

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        // + MiniPlayerGap: hueco para que el miniplayer flote despegado de la barra de navegacion
                        collapsedBound = bottomInset + (if (shouldShowNavigationBar) NavigationBarHeight else 0.dp) + MiniPlayerHeight + MiniPlayerGap,
                        expandedBound = maxHeight,
                    )

                    val isHome = navBackStackEntry?.destination?.route == Screens.Home.route
                    val playerAwareWindowInsets = remember(bottomInset, shouldShowNavigationBar, playerBottomSheetState.isDismissed, isHome) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar) bottom += NavigationBarHeight
                        if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = if (isHome) 0.dp else AppBarHeight, bottom = bottom))
                    }

                    val searchBarScrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )
                    val topAppBarScrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    LaunchedEffect(navBackStackEntry) {
                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")
                            Log.d("VELQIDBG", "restore query raw=[$rawQuery]")
                            val searchQuery = if (rawQuery != null) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        URLDecoder.decode(rawQuery, "UTF-8")
                                    }
                                } catch (e: Exception) {
                                    Log.d("VELQIDBG", "restore decode FAIL: ${e.message}")
                                    rawQuery
                                }
                            } else ""
                            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }
                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()
                    }
                    LaunchedEffect(active, inSelectMode?.value) {
                        if (active && inSelectMode?.value != true) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(playerConnection) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState) {
                        val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null && playerBottomSheetState.isDismissed) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }
                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return@Consumer
                            when (val path = uri.pathSegments.firstOrNull()) {
                                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                                    if (playlistId.startsWith("OLAK5uy_")) {
                                        coroutineScope.launch {
                                            YouTube.albumSongs(playlistId).onSuccess { songs ->
                                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                                    navController.navigate("album/$browseId")
                                                }
                                            }.onFailure {
                                                reportException(it)
                                            }
                                        }
                                    } else {
                                        navController.navigate("online_playlist/$playlistId")
                                    }
                                }

                                "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                                    navController.navigate("artist/$artistId")
                                }

                                else -> when {
                                    path == "watch" -> uri.getQueryParameter("v")
                                    uri.host == "youtu.be" -> path
                                    else -> null
                                }?.let { videoId ->
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            YouTube.queue(listOf(videoId))
                                        }.onSuccess {
                                            sharedSong = it.firstOrNull()
                                        }.onFailure {
                                            reportException(it)
                                        }
                                    }
                                }
                            }
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                NavigationTab.HOME -> Screens.Home
                                NavigationTab.SONG -> Screens.Songs
                                NavigationTab.ARTIST -> Screens.Artists
                                NavigationTab.ALBUM -> Screens.Albums
                                NavigationTab.PLAYLIST -> Screens.Playlists
                            }.route,
                            enterTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeIn(tween(250))
                                } else {
                                    fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                }
                            },
                            exitTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeOut(tween(200))
                                } else {
                                    fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                }
                            },
                            popEnterTransition = {
                                if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                    fadeIn(tween(250))
                                } else {
                                    fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                }
                            },
                            popExitTransition = {
                                if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                    fadeOut(tween(200))
                                } else {
                                    fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                                }
                            },
                            modifier = Modifier
                                .nestedScroll(
                                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                        navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                                        searchBarScrollBehavior.nestedScrollConnection
                                    } else {
                                        topAppBarScrollBehavior.nestedScrollConnection
                                    }
                                )
                        ) {
                            navigationBuilder(navController, topAppBarScrollBehavior, latestVersionName)
                        }

                        AnimatedVisibility(
                            visible = shouldShowSearchBar,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SearchBar(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = onSearch,
                                active = active,
                                onActiveChange = onActiveChange,
                                scrollBehavior = searchBarScrollBehavior,
                                placeholder = {
                                    Text(
                                        text = stringResource(
                                            if (!active) R.string.search
                                            else when (searchSource) {
                                                SearchSource.LOCAL -> R.string.search_library
                                                SearchSource.ONLINE -> R.string.search_yt_music
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            when {
                                                active -> onActiveChange(false)
                                                !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> {
                                                    navController.navigateUp()
                                                }

                                                else -> onActiveChange(true)
                                            }
                                        },
                                        onLongClick = {
                                            when {
                                                active -> {}
                                                !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> {
                                                    navController.backToMain()
                                                }

                                                else -> {}
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painterResource(
                                                if (active || !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                                    R.drawable.arrow_back
                                                } else {
                                                    R.drawable.search
                                                }
                                            ),
                                            contentDescription = null
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (active) {
                                        if (query.text.isNotEmpty()) {
                                            IconButton(
                                                onClick = { onQueryChange(TextFieldValue("")) },
                                                onLongClick = {}
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.close),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                searchSource = searchSource.toggle()
                                            },
                                            onLongClick = {}
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    when (searchSource) {
                                                        SearchSource.LOCAL -> R.drawable.library_music
                                                        SearchSource.ONLINE -> R.drawable.language
                                                    }
                                                ),
                                                contentDescription = null
                                            )
                                        }
                                    } else if (navBackStackEntry?.destination?.route in topLevelScreens) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    navController.navigate("settings")
                                                }
                                        ) {
                                            BadgedBox(
                                                badge = {
                                                    if (Updater.isNewerVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
                                                        Badge()
                                                    }
                                                }
                                            ) {

                                                Icon(
                                                    painter = painterResource(R.drawable.settings),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    }
                                },
                                focusRequester = searchBarFocusRequester,
                                modifier = Modifier.align(Alignment.TopCenter),
                            ) {
                                Crossfade(
                                    targetState = searchSource,
                                    label = "",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp)
                                        .navigationBarsPadding()
                                ) { searchSource ->
                                    when (searchSource) {
                                        SearchSource.LOCAL -> LocalSearchScreen(
                                            query = query.text,
                                            navController = navController,
                                            onDismiss = { onActiveChange(false) }
                                        )

                                        SearchSource.ONLINE -> OnlineSearchScreen(
                                            query = query.text,
                                            onQueryChange = onQueryChange,
                                            navController = navController,
                                            onSearch = {
                                                navController.navigate("search/${it.urlEncode()}")
                                                if (dataStore[PauseSearchHistoryKey] != true) {
                                                    database.query {
                                                        insert(SearchHistory(query = it))
                                                    }
                                                }
                                            },
                                            onDismiss = { onActiveChange(false) }
                                        )
                                    }
                                }
                            }
                        }

                        BottomSheetPlayer(
                            state = playerBottomSheetState,
                            navController = navController
                        )

                        val navItemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val navigateToTab: (Screens) -> Unit = { screen ->
                            if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                navBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                coroutineScope.launch {
                                    searchBarScrollBehavior.state.resetHeightOffset()
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        // Navegacion a rutas tipo 'tab' (home, library, generos):
                        // popUpTo + singleTop evita apilar duplicados en el stack.
                        val navigateToRoute: (String) -> Unit = { route ->
                            if (navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true) {
                                navBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                            } else {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }

                        // Barra inferior flotante tipo pill: SOLO iconos, sin
                        // nombres ni espacio extra. Se desliza fuera de pantalla
                        // al buscar (active) o al expandir el reproductor.
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset {
                                    if (navigationBarHeight == 0.dp) {
                                        IntOffset(x = 0, y = (bottomInset + NavigationBarHeight).roundToPx())
                                    } else {
                                        val slideOffset = (bottomInset + NavigationBarHeight) * playerBottomSheetState.progress.coerceIn(0f, 1f)
                                        val hideOffset = (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                        IntOffset(
                                            x = 0,
                                            y = (slideOffset + hideOffset).roundToPx()
                                        )
                                    }
                                }
                                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = bottomInset + 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                // Home
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (navBackStackEntry?.destination?.route == Screens.Home.route) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable { navigateToRoute(Screens.Home.route) }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.compass),
                                        contentDescription = null,
                                        tint = if (navBackStackEntry?.destination?.route == Screens.Home.route) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Search
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                        .clickable { onActiveChange(true) }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = null,
                                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Library: biblioteca con todo junto
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (navBackStackEntry?.destination?.route == "library") MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable { navigateToRoute("library") }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.library_music),
                                        contentDescription = null,
                                        tint = if (navBackStackEntry?.destination?.route == "library") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Generos y canciones (tarjetas de colores)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (navBackStackEntry?.destination?.route == "mood_and_genres") MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable { navigateToRoute("mood_and_genres") }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.star),
                                        contentDescription = null,
                                        tint = if (navBackStackEntry?.destination?.route == "mood_and_genres") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        sharedSong?.let { song ->
                            playerConnection?.let { playerConnection ->
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        updateDialog?.let { info ->
                            AlertDialog(
                                onDismissRequest = { updateDialog = null },
                                title = { Text(stringResource(R.string.update_dialog_title)) },
                                text = { Text(stringResource(R.string.update_dialog_message, info.versionName)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        updateDialog = null
                                        startActivity(Intent(Intent.ACTION_VIEW, info.apkUrl.toUri()))
                                    }) {
                                        Text(stringResource(R.string.update_download))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { updateDialog = null }) {
                                        Text(stringResource(R.string.update_later))
                                    }
                                }
                            )
                        }
                    }

                    // Aviso cuando YouTube pidio verificacion (muro anti-bot). Va de ultimo en el
                    // BoxWithConstraints a proposito: asi flota por encima del reproductor, la
                    // barra de navegacion y el miniplayer, sin depender de la pantalla abierta.
                    BotWallNotice(
                        onLogin = { navController.navigate("login") },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                    )

                    // Recordatorio periodico para quien no ha capturado sesion. Se apaga solo
                    // cuando hay sesion (lo decide el propio componente) y mientras el usuario
                    // esta en la pantalla de login, donde ya se lo estan explicando.
                    SignedOutNotice(
                        enabled = navBackStackEntry?.destination?.route != "login",
                        onLogin = { navController.navigate("login") }
                    )

                    // Absorbe el BACK fantasma que el IME dispara al cerrarse tras
                    // pulsar Enter con teclado fisico: sin esto, popea la pantalla de
                    // resultados recien abierta y la app vuelve al Home. Tras la
                    // ventana de gracia, BACK funciona normal.
                    BackHandler(
                        enabled = navBackStackEntry?.destination?.route?.startsWith("search/") == true &&
                                SystemClock.uptimeMillis() - lastSearchNavigate < 800
                    ) {
                        // consumir (no hacer nada)
                    }

                    LaunchedEffect(openSearchImmediately) {
                        if (openSearchImmediately) {
                            onActiveChange(true)
                            openSearchImmediately = false
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.openytmusic.app.action.SEARCH"
        const val ACTION_SONGS = "com.openytmusic.app.action.SONGS"
        const val ACTION_ALBUMS = "com.openytmusic.app.action.ALBUMS"
        const val ACTION_PLAYLISTS = "com.openytmusic.app.action.PLAYLISTS"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
