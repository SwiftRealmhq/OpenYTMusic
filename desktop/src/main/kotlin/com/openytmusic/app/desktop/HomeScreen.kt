package com.openytmusic.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openytmusic.app.innertube.models.AlbumItem
import com.openytmusic.app.innertube.models.ArtistItem
import com.openytmusic.app.innertube.models.PlaylistItem
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.innertube.models.YTItem
import com.openytmusic.app.innertube.pages.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    playerState: LunaAppState,
    onPlayQueue: (List<SongItem>, Int) -> Unit,
    onOpenDetail: (YTItem) -> Unit,
) {
    var sections by remember { mutableStateOf<List<HomePage.Section>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { Kernel.homeSections() } }
        result.onSuccess { sections = it; println("[HomeScreen] cargadas ${it.size} secciones") }
            .onFailure { error = it.message ?: "Error cargando el inicio"; println("[HomeScreen] error: $error") }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Inicio")

        when {
            error != null -> EmptyState("No se pudo cargar el inicio.\n$error")
            sections == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(sections.orEmpty()) { section ->
                    HomeSection(
                        section = section,
                        playerState = playerState,
                        onPlayQueue = onPlayQueue,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    section: HomePage.Section,
    playerState: LunaAppState,
    onPlayQueue: (List<SongItem>, Int) -> Unit,
    onOpenDetail: (YTItem) -> Unit,
) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val label = section.label
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.items) { item ->
                ItemCard(
                    item = item,
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                // Reproducir la cancion dentro de la cola de su seccion
                                val songQueue = section.items.filterIsInstance<SongItem>()
                                onPlayQueue(songQueue.ifEmpty { listOf(item) }, songQueue.indexOf(item))
                            }

                            is AlbumItem, is PlaylistItem -> onOpenDetail(item)
                            is ArtistItem -> {} // Artistas: pantalla dedicada en una proxima ronda
                            else -> {}
                        }
                    },
                )
            }
        }
    }
}
