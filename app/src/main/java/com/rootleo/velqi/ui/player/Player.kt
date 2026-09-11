package com.rootleo.velqi.ui.player

import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.rootleo.velqi.LocalPlayerConnection
import com.rootleo.velqi.R
import com.rootleo.velqi.constants.DarkModeKey
import com.rootleo.velqi.constants.PlayerHorizontalPadding
import com.rootleo.velqi.constants.PureBlackKey
import com.rootleo.velqi.constants.QueuePeekHeight
import com.rootleo.velqi.constants.ShowLyricsKey
import com.rootleo.velqi.constants.SliderStyle
import com.rootleo.velqi.constants.SliderStyleKey
import com.rootleo.velqi.extensions.togglePlayPause
import com.rootleo.velqi.extensions.toggleRepeatMode
import com.rootleo.velqi.models.MediaMetadata
import com.rootleo.velqi.ui.component.BottomSheet
import com.rootleo.velqi.ui.component.BottomSheetState
import com.rootleo.velqi.ui.component.LocalMenuState
import com.rootleo.velqi.ui.component.ResizableIconButton
import com.rootleo.velqi.ui.component.rememberBottomSheetState
import com.rootleo.velqi.ui.menu.PlayerMenu
import com.rootleo.velqi.ui.screens.settings.DarkMode
import com.rootleo.velqi.utils.makeTimeString
import com.rootleo.velqi.utils.rememberEnumPreference
import com.rootleo.velqi.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.saket.squiggles.SquigglySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val useDarkTheme = remember(isSystemInDarkTheme, darkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    val useBlackBackground = remember(useDarkTheme, pureBlack) {
        useDarkTheme && pureBlack
    }
    val backgroundColor = if (useBlackBackground && state.value > state.collapsedBound) {
        lerp(MaterialTheme.colorScheme.surfaceContainer, Color.Black, state.progress)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    // La cola ya no tiene 'peek' visible (dismissed == collapsed == 0): asi el
    // reproductor llena toda la pantalla sin franja vacia abajo.
    val queueSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = 0.dp,
        expandedBound = state.expandedBound,
    )

    // Menus del player: letras, temporizador y detalles
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false }
        )
    }
    var showDetailsDialog by remember { mutableStateOf(false) }
    if (showDetailsDialog) {
        DetailsDialog(
            onDismiss = { showDetailsDialog = false }
        )
    }
    // Visor de portada a pantalla completa (foto de referencia): tocar el arte
    // lo abre con flecha atras, corazon y compartir.
    var showArtworkViewer by remember { mutableStateOf(false) }
    LaunchedEffect(state.value) {
        if (showArtworkViewer && state.value <= state.collapsedBound) {
            showArtworkViewer = false
        }
    }
    var showLyrics by rememberPreference(ShowLyricsKey, false)

    BottomSheet(
        state = state,
        modifier = modifier,
        backgroundColor = backgroundColor,
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration,
                onOpenPlayer = { state.expandSoft() },
                onOpenArtist = {
                    mediaMetadata?.artists?.firstOrNull()?.id?.let { artistId ->
                        navController.navigate("artist/$artistId")
                        state.collapseSoft()
                    }
                },
            )
        }
    ) {
        // Posicion en la cola ("2 de 12") en la esquina inferior izquierda
        // DENTRO de la portada, como en la referencia.
        val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val positionOverlay: @Composable BoxScope.(MediaMetadata) -> Unit = {
            val total = queueWindows.size
            val index = currentWindowIndex
            if (total > 0 && index in 0 until total) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.queue_position, index + 1, total),
                        style = MaterialTheme.typography.labelLarge.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(0f, 1f),
                                blurRadius = 8f
                            )
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Acciones rapidas del reproductor: like y menu. La radio se quito de
        // aqui porque ya vive en el menu "..." y su icono no decia nada.
        val actionsRow: @Composable () -> Unit = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerActionButton(
                    icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                    tint = if (currentSong?.song?.liked == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = playerConnection::toggleLike
                )

                mediaMetadata?.let { metadata ->
                    PlayerActionButton(
                        icon = R.drawable.more_horiz,
                        onClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = metadata,
                                    navController = navController,
                                    bottomSheetState = state,
                                    onShowDetailsDialog = { showDetailsDialog = true },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
                }
            }
        }

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { _ ->
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 100),
                label = "playPauseRoundness"
            )

            Spacer(Modifier.height(12.dp))

            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    Slider(
                value = (sliderPosition ?: position).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = {
                    sliderPosition = it.toLong()
                },
                onValueChangeFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                        position = it
                    }
                    sliderPosition = null
                },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }

                SliderStyle.SQUIGGLY -> {
                    SquigglySlider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            sliderPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                playerConnection.player.seekTo(it)
                                position = it
                            }
                            sliderPosition = null
                        },
                        squigglesSpec = SquigglySlider.SquigglesSpec(
                            amplitude = if (isPlaying) 7.dp else 2.dp,
                            strokeWidth = 6.dp,
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding + 4.dp)
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Controles: shuffle, prev, play grande, next, repeat
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center),
                        onClick = {
                            playerConnection.service.toggleShuffle()
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center),
                        onClick = playerConnection::seekToPrevious
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Play/Pause grande: blanco redondeado (Velqi Luna)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(playPauseRoundness))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        }
                ) {
                    Image(
                        painter = painterResource(if (playbackState == STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_next,
                        enabled = canSkipNext,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center),
                        onClick = playerConnection::seekToNext
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = when (repeatMode) {
                            REPEAT_MODE_OFF, REPEAT_MODE_ALL -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> throw IllegalStateException()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center)
                            .alpha(if (repeatMode == REPEAT_MODE_OFF) 0.5f else 1f),
                        onClick = playerConnection.player::toggleRepeatMode
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Estado del temporizador de apagado: se refleja en el boton de la
            // luna (color y minutos restantes) para que se vea que quedo activo.
            val sleepTimer = playerConnection.service.sleepTimer
            val sleepTimerActive = sleepTimer.triggerTime != -1L || sleepTimer.pauseWhenSongEnd
            var sleepTimerLeft by remember { mutableLongStateOf(0L) }
            LaunchedEffect(sleepTimerActive, sleepTimer.triggerTime, sleepTimer.pauseWhenSongEnd) {
                while (isActive) {
                    sleepTimerLeft = if (sleepTimer.pauseWhenSongEnd) {
                        (playerConnection.player.duration - playerConnection.player.currentPosition).coerceAtLeast(0L)
                    } else {
                        (sleepTimer.triggerTime - System.currentTimeMillis()).coerceAtLeast(0L)
                    }
                    delay(1000L)
                }
            }

            // Barra inferior: Cola / Luna / Letras (pildoras con texto, como la referencia)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Cola
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { queueSheetState.expandSoft() }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                // Temporizador de apagado (luna): con temporizador activo el
                // boton cambia de color y muestra los minutos que quedan.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (sleepTimerActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .clickable { showSleepTimerDialog = true }
                ) {
                    if (sleepTimerActive && !sleepTimer.pauseWhenSongEnd) {
                        // Minuto restante redondeado hacia arriba: "29m" en
                        // cuanto queda 28:01, asi el numero nunca miente.
                        Text(
                            text = "${((sleepTimerLeft + 59_999L) / 60_000L).coerceAtLeast(1L)}m",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.bedtime),
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (sleepTimerActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Letras
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showLyrics = !showLyrics }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lyrics),
                        contentDescription = null,
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Thumbnail(
                            sliderPositionProvider = { sliderPosition },
                            overlay = positionOverlay,
                            onTap = { showArtworkViewer = true },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    ) {
                        Spacer(Modifier.weight(1f))

                        actionsRow()

                        Spacer(Modifier.height(16.dp))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                ) {
                    // Header: solo "Now Playing". El nombre de la cola/mix iba
                    // aqui abajo, pero se quedaba desactualizado respecto a la
                    // cancion sonando (y el titulo ya aparece sobre los
                    // controles), asi que sobraba.
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    // Bloque flexible: portada + titulo. Se mide DESPUES de que
                    // los controles, la barra Cola/Luna/Letras y el resto de
                    // hijos sin weight ya ocuparon su altura, asi que la portada
                    // encoge en pantallas cortas (o con letra grande) en vez de
                    // empujar esa barra fuera de la pantalla y dejarla cortada.
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Portada: 85% del ancho y cuadrada, pero el lado lo
                            // decide el propio Thumbnail con la altura que le
                            // queda libre (ver artworkSize alli). El weight la
                            // mide despues del titulo, asi que en pantallas
                            // bajas encoge en vez de desbordarse.
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                overlay = positionOverlay,
                                onTap = { showArtworkViewer = true },
                                // El Column ya aplica el inset superior.
                                applyStatusBarPadding = false,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .weight(1f, fill = false)
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                            )

                            Spacer(Modifier.height(14.dp))

                            // Titulo + artista (izquierda) y acciones (derecha)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = mediaMetadata?.title.orEmpty(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                actionsRow()
                            }

                            // Respiro minimo: el titulo nunca queda pegado a los
                            // controles cuando la portada encoge al minimo.
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            backgroundColor = backgroundColor,
            navController = navController
        )

        if (showArtworkViewer) {
            mediaMetadata?.let { viewerMetadata ->
                ArtworkViewer(
                    metadata = viewerMetadata,
                    liked = currentSong?.song?.liked == true,
                    onDismiss = { showArtworkViewer = false },
                    onToggleLike = playerConnection::toggleLike,
                    onShare = {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${viewerMetadata.id}")
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                )
            }
        }
    }
}

/**
 * Visor de portada a pantalla completa (estilo visor de fotos): fondo negro,
 * flecha atras arriba-izquierda, corazon y compartir arriba-derecha.
 */
@Composable
private fun ArtworkViewer(
    metadata: MediaMetadata,
    liked: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        AsyncImage(
            model = metadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Flecha atras (arriba-izquierda)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onDismiss)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Corazon + compartir (arriba-derecha)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onToggleLike)
            ) {
                Icon(
                    painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onShare)
            ) {                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Boton circular de accion rapida del reproductor (radio, like, menu).
 */
@Composable
private fun PlayerActionButton(
    @DrawableRes icon: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
