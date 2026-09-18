package com.openytmusic.app.extensions

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.db.entities.Song
import com.openytmusic.app.models.MediaMetadata
import com.openytmusic.app.models.toMediaMetadata
import com.openytmusic.app.ui.utils.highResThumbnail

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem() = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(song.id)
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            // Misma portada en calidad alta que el tag: la notificacion y el
            // reproductor comparten entonces la misma URL (y la misma entrada
            // en la cache de Coil) en lugar de mezclar w544 con 1200.
            .setArtworkUri(song.thumbnailUrl.highResThumbnail()?.toUri())
            .setAlbumTitle(song.albumName)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

fun SongItem.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnail.highResThumbnail()?.toUri())
            .setAlbumTitle(album?.name)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

fun MediaMetadata.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(this)
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnailUrl?.toUri())
            .setAlbumTitle(album?.title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()