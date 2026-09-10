package com.rootleo.velqi.selene.queue

import com.rootleo.velqi.selene.model.NavEndpoint
import com.rootleo.velqi.selene.search.SongItem

/**
 * Qué reproducir a continuación: video, playlist, posición y params.
 * Es el "endpoint" de reproducción de Selene.
 */
data class UpNextEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val index: Int? = null,
    val params: String? = null,
) {
    companion object {
        /** Construye un endpoint desde un watchPlaylistEndpoint del JSON. */
        fun fromNavEndpoint(endpoint: NavEndpoint?): UpNextEndpoint? {
            val watchPlaylist = endpoint?.watchPlaylistEndpoint
            if (watchPlaylist != null) {
                return UpNextEndpoint(
                    videoId = watchPlaylist.videoId,
                    playlistId = watchPlaylist.playlistId,
                )
            }
            val watch = endpoint?.watchEndpoint ?: return null
            return UpNextEndpoint(
                videoId = watch.videoId,
                playlistId = watch.playlistId,
            )
        }
    }
}

/**
 * Cola de reproducción (up next) para una canción o playlist.
 */
data class NextResult(
    /** Nombre de la cola ("Never Gonna Give You Up Mix", "'80s Rock"...). */
    val title: String? = null,
    /** Canciones en cola. */
    val items: List<SongItem>,
    /** Índice de la canción actual dentro de [items]. */
    val currentIndex: Int? = null,
    /** browseId del tab de letras (para Selene.lyrics). */
    val lyricsBrowseId: String? = null,
    /** browseId del tab de contenido relacionado (para Selene.related). */
    val relatedBrowseId: String? = null,
    /** Continuación de esta misma cola. */
    val continuation: String? = null,
    /**
     * Cuando la cola termina, YouTube sugiere una radio automix.
     * Llama a [com.rootleo.velqi.selene.Selene.next] con este endpoint
     * para extender la reproducción.
     */
    val automixEndpoint: UpNextEndpoint? = null,
)