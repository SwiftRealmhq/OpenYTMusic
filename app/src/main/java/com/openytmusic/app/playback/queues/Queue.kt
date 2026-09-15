package com.openytmusic.app.playback.queues

import androidx.media3.common.MediaItem
import com.openytmusic.app.extensions.metadata
import com.openytmusic.app.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                val filtered = items.filterExplicit()
                // El indice tiene que bajar con los items descartados POR DELANTE del actual:
                // conservarlo dejaba la reproduccion arrancando en la cancion equivocada o
                // reventaba el subList(0, mediaItemIndex) de MusicService.
                val newIndex = items.take(mediaItemIndex.coerceIn(0, items.size))
                    .count { it.metadata?.explicit != true }
                copy(
                    items = filtered,
                    mediaItemIndex = newIndex.coerceIn(0, filtered.lastIndex.coerceAtLeast(0))
                )
            } else this
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else this

