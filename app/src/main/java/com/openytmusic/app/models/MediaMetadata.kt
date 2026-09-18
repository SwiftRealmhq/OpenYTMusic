package com.openytmusic.app.models

import androidx.compose.runtime.Immutable
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.db.entities.*
import com.openytmusic.app.ui.utils.highResThumbnail
import java.io.Serializable

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val explicit: Boolean = false,
) : Serializable {
    data class Artist(
        val id: String?,
        val name: String,
    ) : Serializable

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable

    fun toSongEntity() = SongEntity(
        id = id,
        title = title,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        albumId = album?.id,
        albumName = album?.title
    )
}

fun Song.toMediaMetadata() = MediaMetadata(
    id = song.id,
    title = song.title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name
        )
    },
    duration = song.duration,
    // Calidad alta: este metadata es el que alimenta el reproductor, el visor a
    // pantalla completa y la notificacion. Antes se usaba la URL guardada tal
    // cual (w544 del API), que al estirarla a pantalla completa se veia borrosa.
    thumbnailUrl = song.thumbnailUrl.highResThumbnail(),
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.title
        )
    } ?: song.albumId?.let { albumId ->
        MediaMetadata.Album(
            id = albumId,
            title = song.albumName.orEmpty()
        )
    }
)

fun SongItem.toMediaMetadata() = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name
        )
    },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.highResThumbnail(),
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.name
        )
    },
    explicit = explicit
)
