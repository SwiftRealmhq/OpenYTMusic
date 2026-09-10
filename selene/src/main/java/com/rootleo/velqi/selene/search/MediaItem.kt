package com.rootleo.velqi.selene.search

/**
 * Modelo de dominio de Selene: lo que la API devuelve, desacoplado
 * del JSON crudo de YouTube.
 */
sealed class MediaItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String
}

data class ArtistRef(
    val name: String,
    val id: String?,
)

data class AlbumRef(
    val name: String,
    val id: String?,
)

data class SongItem(
    /** videoId */
    override val id: String,
    override val title: String,
    val artists: List<ArtistRef>,
    val album: AlbumRef?,
    /** duración en segundos */
    val duration: Int?,
    override val thumbnail: String,
    val explicit: Boolean = false,
    /** playlistId de contexto (radio) si viene en el endpoint */
    val playlistId: String? = null,
) : MediaItem()

data class AlbumItem(
    /** browseId (ej: MPREb_...) */
    override val id: String,
    val playlistId: String?,
    override val title: String,
    val artists: List<ArtistRef>?,
    val year: Int?,
    override val thumbnail: String,
    val explicit: Boolean = false,
) : MediaItem()

data class ArtistItem(
    /** browseId (ej: UC...) */
    override val id: String,
    override val title: String,
    override val thumbnail: String,
) : MediaItem()

data class PlaylistItem(
    /** playlistId sin prefijo VL */
    override val id: String,
    override val title: String,
    val author: String?,
    val songCount: Int?,
    override val thumbnail: String,
) : MediaItem()

data class SearchResult(
    val items: List<MediaItem>,
    val continuation: String? = null,
)

data class SearchSuggestions(
    val queries: List<String>,
)