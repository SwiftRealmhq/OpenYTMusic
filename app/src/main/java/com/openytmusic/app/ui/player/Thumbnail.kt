package com.openytmusic.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.openytmusic.app.LocalPlayerConnection
import com.openytmusic.app.constants.ShowLyricsKey
import com.openytmusic.app.constants.ThumbnailCornerRadius
import com.openytmusic.app.models.MediaMetadata
import com.openytmusic.app.ui.component.Lyrics
import com.openytmusic.app.utils.rememberPreference

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    // Overlay superpuesto en la parte inferior del arte (titulo + acciones)
    overlay: @Composable BoxScope.(MediaMetadata) -> Unit = {},
    // Tap simple sobre el arte (ej: abrir el visor a pantalla completa).
    // Se dispara dentro del mismo detector para no competir con el doble tap.
    onTap: (() -> Unit)? = null,
    // En vertical el inset superior ya lo aplica el contenedor del reproductor:
    // si se aplica aqui tambien se sumaba dos veces y se perdia altura util
    // (justo la que le hacia falta a la portada).
    applyStatusBarPadding: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentView = LocalView.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()

    val showLyrics by rememberPreference(ShowLyricsKey, false)

    DisposableEffect(showLyrics) {
        currentView.keepScreenOn = showLyrics
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    BoxWithConstraints(
        // clipToBounds: red de seguridad para que nada de este bloque pueda
        // pintarse fuera de su hueco (el encabezado y el titulo viven justo
        // encima y debajo). contentAlignment: la tarjeta va centrada en su
        // hueco, no pegada a la izquierda.
        modifier = modifier
            .clipToBounds()
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Lado de la tarjeta: el menor de los dos huecos disponibles (menos 8dp
        // de margen). Se calcula aqui para que el arte NUNCA sea mas grande que
        // su hueco: cuando el lado salia del ancho, en pantallas bajas el
        // cuadrado se desbordaba por encima del encabezado y por debajo del
        // titulo, tapando ambos textos.
        val artworkSize = (minOf(maxWidth, maxHeight) - 8.dp).coerceAtLeast(0.dp)

        AnimatedVisibility(
            visible = !showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(artworkSize)
            ) {
                Box(
                    // Tamano explicito: ni aspectRatio ni fillMaxWidth, que se
                    // contradecian entre si cuando la altura disponible era
                    // menor que el 85% del ancho.
                    modifier = Modifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius * 2))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    onTap?.invoke()
                                },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2) {
                                        playerConnection.player.seekBack()
                                    } else {
                                        playerConnection.player.seekForward()
                                    }
                                }
                            )
                        }
                ) {
                    // Fondo: la misma portada recortada ocupa todo el cuadrado,
                    // asi un video 16:9 llena la tarjeta igual que una caratula
                    // cuadrada. Antes las canciones con miniatura panoramica
                    // quedaban con franjas negras y no se veian como las demas.
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(28.dp)
                            .alpha(0.55f)
                    )
                    // Portada: encaja completa (sin recortar) sobre el fondo
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Vineta sutil arriba y abajo (Velqi Luna)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.18f)
                                    )
                                )
                            )
                    )
                    // Overlay inferior (acciones) con gradiente de legibilidad sutil
                    mediaMetadata?.let { mm ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                        overlay(mm)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Mismo hueco que la portada para que el titulo y los controles de
            // abajo no pierdan su espacio cuando se abren las letras.
            Box(modifier = Modifier.size(artworkSize)) {
                Lyrics(sliderPositionProvider = sliderPositionProvider)
            }
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center)
        ) {
            error?.let { error ->
                PlaybackError(
                    error = error,
                    retry = playerConnection.player::prepare
                )
            }
        }
    }
}
