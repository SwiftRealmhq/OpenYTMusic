package com.rootleo.velqi.selene.browse

import com.rootleo.velqi.selene.search.AlbumItem
import com.rootleo.velqi.selene.search.ArtistItem
import com.rootleo.velqi.selene.search.MediaItem
import com.rootleo.velqi.selene.search.PlaylistItem
import com.rootleo.velqi.selene.search.SongItem

/** Página completa de un álbum. */
data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val otherVersions: List<AlbumItem>,
)

/** Sección de la página de un artista (canciones populares, álbumes, singles...). */
data class ArtistSection(
    val title: String,
    val items: List<MediaItem>,
    /** browseId del botón "ver todo" de la sección. */
    val moreBrowseId: String?,
)

/** Página completa de un artista. */
data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
)

/** Página completa de una playlist. */
data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    /** Continuación de canciones (puede llevar a más canciones o recomendaciones). */
    val continuation: String?,
)

/** Paginación de las canciones de una playlist. */
data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)