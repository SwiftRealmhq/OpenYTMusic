package com.openytmusic.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.openytmusic.app.innertube.models.AlbumItem
import com.openytmusic.app.innertube.models.ArtistItem
import com.openytmusic.app.innertube.models.PlaylistItem
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(
    target: DetailTarget,
    playerState: LunaAppState,
    onBack: () -> Unit,
    onPlayQueue: (List<SongItem>, Int) -> Unit,
    onOpenDetail: (YTItem) -> Unit,
) {
    val item = target.item
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf(item.title) }
    var subtitle by remember { mutableStateOf(item.subtitleText()) }
    var cover by remember { mutableStateOf(item.thumbnail) }
    var songs by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    LaunchedEffect(item.id) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (item) {
                    is PlaylistItem -> Kernel.playlistPage(item.id)
                    is AlbumItem -> Kernel.albumPage(item.id)
                    else -> null
                }
            }
        }
        result.onSuccess { page ->
            when (page) {
                is com.openytmusic.app.innertube.pages.PlaylistPage -> {
                    title = page.playlist.title
                    subtitle = listOfNotNull(page.playlist.author?.name, page.playlist.songCountText).joinToString(" · ")
                    cover = page.playlist.thumbnail
                    songs = page.songs
                }

                is com.openytmusic.app.innertube.pages.AlbumPage -> {
                    title = page.album.title
                    subtitle = listOfNotNull(page.album.artists?.joinToString { it.name }, page.album.year?.toString())
                        .joinToString(" · ")
                    cover = page.album.thumbnail
                    songs = page.songs
                }

                else -> {}
            }
        }.onFailure {
            error = it.message ?: "Error cargando"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Volver",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> EmptyState("No se pudo cargar el contenido.\n$error")

            else -> Column(Modifier.fillMaxSize()) {
                // Encabezado
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = cover,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222222)),
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        if (songs.isNotEmpty()) {
                            FilledIconButton(onClick = { onPlayQueue(songs, 0) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                            }
                        }
                    }
                }

                // Lista de canciones
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(songs) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = playerState.current?.id == song.id,
                            onClick = { onPlayQueue(songs, index) },
                        )
                    }
                }
            }
        }
    }
}
