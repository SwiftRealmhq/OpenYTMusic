package com.rootleo.velqi.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.rootleo.velqi.innertube.models.SongItem
import com.rootleo.velqi.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    // Modo headless para pruebas del motor (mantiene la verificacion)
    if (args.firstOrNull() in setOf("search", "play", "home", "detail")) {
        headlessCli(args)
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Velqi Luna",
        ) {
            LunaApp()
        }
    }
}

private fun headlessCli(args: Array<String>) {
    val query = args.drop(1).joinToString(" ")
    when (args.first()) {
        "home" -> {
            val sections = kotlinx.coroutines.runBlocking { Kernel.homeSections() }
            println("Home: ${sections.size} secciones")
            sections.take(6).forEach { s ->
                println("  - ${s.title} (${s.items.size} items)")
                s.items.take(3).forEach { it ->
                    println("      * ${it::class.simpleName}: ${it.title} [${it.id}]")
                }
            }
            return
        }
        "detail" -> {
            val id = args.getOrNull(1) ?: run {
                println("uso: detail <playlistId>")
                return
            }
            val page = kotlinx.coroutines.runBlocking { Kernel.playlistPage(id) }
            if (page == null) {
                println("FALLO: no se pudo abrir la playlist")
                return
            }
            println("Playlist: ${page.playlist.title} — ${page.songs.size} canciones")
            page.songs.take(5).forEachIndexed { i, s ->
                println("  $i. ${s.title} — ${s.artists.joinToString { it.name }}")
            }
            return
        }
    }
    // search / play
    val songs = kotlinx.coroutines.runBlocking { Kernel.searchSongs(query) }
    if (args.first() == "search") {
        songs.take(10).forEachIndexed { i, s ->
            println("  $i. ${s.title} — ${s.artists.joinToString { it.name }}")
        }
        return
    }
    // play: verificacion headless del motor
    val song = songs.firstOrNull() ?: run {
        println("Sin resultados")
        return
    }
    val url = kotlinx.coroutines.runBlocking { Kernel.resolveStreamUrl(song.id) }
        ?: run {
            println("FALLO: sin stream")
            return
        }
    val mpv = MpvProcess()
    val startRes = mpv.start()
    if (startRes != "ok") {
        println("FALLO mpv: $startRes")
        return
    }
    val proxy = StreamProxy(url)
    mpv.loadUrl(proxy.baseUrl)
    mpv.play()
    Thread.sleep(6000)
    val t1 = mpv.currentTime()
    Thread.sleep(3000)
    val t2 = mpv.currentTime()
    if (t1 == null || t2 == null || t2 <= t1) {
        println("FALLO: el tiempo no avanza")
        mpv.close(); proxy.close(); return
    }
    println("PLAYBACK OK (avanza %.1f -> %.1f)".format(t1, t2))
    mpv.close(); proxy.close()
}

enum class LunaTab(val label: String) {
    Home("Inicio"),
    Search("Buscar"),
    Settings("Ajustes"),
}

/** Item abierto en la vista de detalle (playlist o album online). */
data class DetailTarget(
    val item: YTItem,
)

/** Estado global de la app desktop. */
class LunaAppState {
    var tab by mutableStateOf(LunaTab.Home)
    var detail by mutableStateOf<DetailTarget?>(null)

    // Cola de reproduccion
    var queue by mutableStateOf<List<SongItem>>(emptyList())
    var queueIndex by mutableStateOf(-1)
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(1L)

    val current: SongItem?
        get() = queue.getOrNull(queueIndex)
}

@Composable
fun LunaApp() {
    LunaTheme(themeModeState.value) {
        Surface(Modifier.fillMaxSize()) {
            val state = remember { LunaAppState() }
            val player = remember { DesktopPlayer() }
            val scope = rememberCoroutineScope()

            LaunchedEffect(player) { player.init() }

            fun playQueue(songs: List<SongItem>, index: Int) {
                if (songs.isEmpty()) return
                val i = index.coerceIn(0, songs.size - 1)
                state.queue = songs
                state.queueIndex = i
                state.playing = true
                scope.launch(Dispatchers.IO) {
                    val url = Kernel.resolveStreamUrl(songs[i].id)
                    if (url != null) {
                        player.playUrl(url)
                    } else {
                        state.playing = false
                    }
                }
            }

            fun togglePlay() {
                if (state.current == null) return
                if (state.playing) player.pause() else player.resume()
                state.playing = !state.playing
            }

            fun next() {
                if (state.queueIndex < state.queue.size - 1) playQueue(state.queue, state.queueIndex + 1)
            }

            fun prev() {
                if (state.queueIndex > 0) playQueue(state.queue, state.queueIndex - 1)
            }

            // Polling de posicion/duration
            LaunchedEffect(state.playing, state.current) {
                while (state.playing) {
                    delay(300)
                    state.positionMs = player.currentTimeMs()
                    val dur = player.durationMs()
                    if (dur > 0) state.durationMs = dur
                }
            }

            Row(Modifier.fillMaxSize()) {
                // Barra lateral anclada a la izquierda
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(8.dp))
                    LunaTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = state.tab == tab && state.detail == null || state.tab == tab && tab != LunaTab.Home,
                            onClick = {
                                state.detail = null
                                state.tab = tab
                            },
                            icon = {
                                Icon(
                                    when (tab) {
                                        LunaTab.Home -> Icons.Default.Home
                                        LunaTab.Search -> Icons.Default.Search
                                        LunaTab.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val bmp = rememberLunaLogo()
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Velqi Luna",
                            modifier = Modifier.size(28.dp).padding(bottom = 0.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.detail != null -> DetailScreen(
                                target = state.detail!!,
                                playerState = state,
                                onBack = { state.detail = null },
                                onPlayQueue = ::playQueue,
                                onOpenDetail = { state.detail = DetailTarget(it) },
                            )

                            state.tab == LunaTab.Home -> HomeScreen(
                                playerState = state,
                                onPlayQueue = ::playQueue,
                                onOpenDetail = { state.detail = DetailTarget(it) },
                            )

                            state.tab == LunaTab.Search -> SearchScreen(
                                playerState = state,
                                onPlayQueue = ::playQueue,
                                onOpenDetail = { state.detail = DetailTarget(it) },
                            )

                            else -> SettingsScreen(playerState = state)
                        }
                    }

                    // Barra del reproductor (persistente)
                    val current = state.current
                    if (current != null) {
                        PlayerBar(
                            current = current,
                            playing = state.playing,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            canPrev = state.queueIndex > 0,
                            canNext = state.queueIndex < state.queue.size - 1,
                            onToggle = ::togglePlay,
                            onPrev = ::prev,
                            onNext = ::next,
                            onSeek = { ms ->
                                state.positionMs = ms
                                player.seekToMs(ms)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerBar(
    current: SongItem,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    canPrev: Boolean,
    canNext: Boolean,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                current.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = canPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onNext, enabled = canNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%d:%02d / %d:%02d".format(
                        positionMs / 60000, (positionMs / 1000) % 60,
                        durationMs / 60000, (durationMs / 1000) % 60,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Carga el logo de la app (recurso empaquetado) como ImageBitmap. */
@Composable
fun rememberLunaLogo(): ImageBitmap? {
    val bytes = remember { DesktopPlayer::class.java.getResourceAsStream("/luna_logo.webp")?.readBytes() }
    return remember(bytes) {
        bytes?.let { data ->
            try {
                org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Carga un recurso empaquetado como ImageBitmap (para banners). */
@Composable
fun rememberResourceImage(name: String): ImageBitmap? {
    val bytes = remember(name) { DesktopPlayer::class.java.getResourceAsStream("/$name")?.readBytes() }
    return remember(bytes) {
        bytes?.let { data ->
            try {
                org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}
