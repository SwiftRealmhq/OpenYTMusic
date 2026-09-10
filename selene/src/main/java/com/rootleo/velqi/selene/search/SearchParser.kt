package com.rootleo.velqi.selene.search

import com.rootleo.velqi.selene.model.ResponsiveListItem
import com.rootleo.velqi.selene.model.ShelfContent
import com.rootleo.velqi.selene.model.TwoRowItem
import com.rootleo.velqi.selene.model.asText
import com.rootleo.velqi.selene.model.bestUrl
import com.rootleo.velqi.selene.model.clean
import com.rootleo.velqi.selene.model.getContinuation
import com.rootleo.velqi.selene.model.oddElements
import com.rootleo.velqi.selene.model.parseFirstNumber
import com.rootleo.velqi.selene.model.parseTime
import com.rootleo.velqi.selene.model.splitBySeparator

/**
 * Parser de resultados de búsqueda: convierte el JSON crudo de YouTube
 * en el modelo de dominio de Selene.
 */
object SearchParser {

    /**
     * Búsqueda filtrada: toma el shelf de resultados (ej: "Canciones")
     * y devuelve sus items + la continuation para paginar.
     */
    fun parseFiltered(response: SearchResponse): SearchResult {
        val sections = response.contents?.tabbedSearchResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
            .orEmpty()

        val shelf = sections.lastOrNull { it.musicShelfRenderer != null }?.musicShelfRenderer
        val items = shelf?.contents.orEmpty().mapNotNull { content ->
            content.musicResponsiveListItemRenderer?.let { toMediaItem(it) }
        }
        val continuation = shelf?.continuations?.getContinuation()
        return SearchResult(items = items, continuation = continuation)
    }

    /**
     * Búsqueda "Todo": aplana todas las secciones (itemSections + shelves)
     * en una sola lista mezclada de canciones, álbumes, artistas y playlists.
     */
    fun parseAll(response: SearchResponse): SearchResult {
        val sections = response.contents?.tabbedSearchResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
            .orEmpty()

        val items = sections.flatMap { section ->
            buildList {
                section.musicShelfRenderer?.contents.orEmpty().forEach { addAll(toMediaItems(it)) }
                section.itemSectionRenderer?.contents.orEmpty().forEach { content ->
                    content.musicResponsiveListItemRenderer?.let { toMediaItem(it) }?.let { add(it) }
                }
                // El card del top result se parsea como UN item (canción/artista/álbum),
                // no como sus canciones internas (que no traen artista en sus columnas).
                section.musicCardShelfRenderer?.let { card -> toMediaItem(card)?.let { add(it) } }
            }
        }
        return SearchResult(items = items)
    }

    /** Paginación de una búsqueda filtrada. */
    fun parseContinuation(response: SearchResponse): SearchResult {
        val continuation = response.continuationContents?.musicShelfContinuation
        val items = continuation?.contents.orEmpty().mapNotNull { content ->
            content.musicResponsiveListItemRenderer?.let { toMediaItem(it) }
        }
        return SearchResult(
            items = items,
            continuation = continuation?.continuations?.getContinuation(),
        )
    }

    private fun toMediaItems(content: ShelfContent): List<MediaItem> {
        val responsive = content.musicResponsiveListItemRenderer?.let { toMediaItem(it) }
        val twoRow = content.musicTwoRowItemRenderer?.let { toMediaItem(it) }
        return listOfNotNull(responsive, twoRow)
    }

    // ============ ResponsiveListItem (resultados de lista) ============

    fun toMediaItem(renderer: ResponsiveListItem): MediaItem? = when {
        renderer.isSong -> toSong(renderer)
        renderer.isArtist -> toArtist(renderer)
        renderer.isAlbum -> toAlbum(renderer)
        renderer.isPlaylist -> toPlaylist(renderer)
        else -> null
    }

    private fun toSong(renderer: ResponsiveListItem): SongItem? {
        val secondaryLine = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.splitBySeparator()
            ?: return null
        // Unifica el tab "Todo" ("Song • Artista • ...") con el shelf filtrado
        // ("Artista • Álbum • Duración"): descarta la etiqueta de tipo.
        val thirdLine = renderer.flexColumns?.getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.splitBySeparator()
            .orEmpty()
        val listRun = (secondaryLine + thirdLine).clean()
        return SongItem(
            id = renderer.playlistItemData?.videoId ?: return null,
            title = renderer.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text
                ?.firstRun() ?: return null,
            artists = listRun.getOrNull(0)?.oddElements()?.map {
                ArtistRef(
                    name = it.text.orEmpty(),
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            } ?: return null,
            album = listRun.getOrNull(1)?.firstOrNull()
                ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                ?.let {
                    AlbumRef(
                        name = it.text.orEmpty(),
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                },
            duration = secondaryLine.lastOrNull()?.firstOrNull()?.text?.parseTime(),
            thumbnail = renderer.thumbnail?.bestUrl() ?: return null,
            explicit = renderer.isExplicit(),
            playlistId = renderer.navigationEndpoint?.playlistId,
        )
    }

    private fun toAlbum(renderer: ResponsiveListItem): AlbumItem? {
        val secondaryLine = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.splitBySeparator()
            ?: return null
        // "Album • Artista • Año" / "EP • Artista • Año" → descarta la etiqueta.
        val listRun = secondaryLine.clean()
        return AlbumItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            playlistId = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.playlistId,
            title = renderer.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text
                ?.firstRun() ?: return null,
            artists = listRun.getOrNull(0)?.oddElements()?.map {
                ArtistRef(
                    name = it.text.orEmpty(),
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            },
            year = listRun.getOrNull(1)?.firstOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnail?.bestUrl() ?: return null,
            explicit = renderer.isExplicit(),
        )
    }

    private fun toArtist(renderer: ResponsiveListItem): ArtistItem? {
        return ArtistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            title = renderer.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text
                ?.firstRun() ?: return null,
            thumbnail = renderer.thumbnail?.bestUrl() ?: return null,
        )
    }

    private fun toPlaylist(renderer: ResponsiveListItem): PlaylistItem? {
        val secondaryLine = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.splitBySeparator()
            ?: return null
        // Filtrado: "YouTube Music • 132 songs" (sin etiqueta).
        // Todo: "Playlist • YouTube Music • 132 songs" (con etiqueta sin navegación).
        val author = if (secondaryLine.firstOrNull()?.firstOrNull()?.text in TYPE_LABELS) {
            secondaryLine.getOrNull(1)?.firstOrNull()?.text
        } else {
            secondaryLine.firstOrNull()?.firstOrNull()?.text
        }
        return PlaylistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title = renderer.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text
                ?.firstRun() ?: return null,
            author = author,
            songCount = renderer.flexColumns?.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.lastOrNull()?.text?.parseFirstNumber(),
            thumbnail = renderer.thumbnail?.bestUrl() ?: return null,
        )
    }

    // ============ TwoRowItem (álbumes/artistas/playlists en carruseles) ============

    fun toMediaItem(renderer: TwoRowItem): MediaItem? = when {
        renderer.isAlbum -> toAlbum(renderer)
        renderer.isArtist -> toArtist(renderer)
        renderer.isPlaylist -> toPlaylist(renderer)
        else -> null
    }

    private fun toAlbum(renderer: TwoRowItem): AlbumItem? {
        return AlbumItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            playlistId = renderer.navigationEndpoint?.playlistId
                ?: renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.playlistId,
            title = renderer.title?.asText() ?: return null,
            artists = null,
            year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnailRenderer?.bestUrl() ?: return null,
            explicit = renderer.isExplicit(),
        )
    }

    private fun toArtist(renderer: TwoRowItem): ArtistItem? {
        return ArtistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            title = renderer.title?.asText() ?: return null,
            thumbnail = renderer.thumbnailRenderer?.bestUrl() ?: return null,
        )
    }

    private fun toPlaylist(renderer: TwoRowItem): PlaylistItem? {
        return PlaylistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title = renderer.title?.asText() ?: return null,
            author = renderer.subtitle?.asText(),
            songCount = null,
            thumbnail = renderer.thumbnailRenderer?.bestUrl() ?: return null,
        )
    }

    // ============ Card del top result ============

    fun toMediaItem(card: CardShelf): MediaItem? {
        val onTap = card.onTap ?: return null
        val title = card.title?.asText() ?: return null
        val thumbnail = card.thumbnail?.bestUrl() ?: return null

        onTap.watchEndpoint?.let { watch ->
            val subtitleParts = card.subtitle?.runs?.splitBySeparator().orEmpty()
            return SongItem(
                id = watch.videoId ?: return null,
                title = title,
                artists = subtitleParts.clean().getOrNull(0)?.oddElements()?.map {
                    ArtistRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId)
                }.orEmpty(),
                album = subtitleParts.clean().getOrNull(1)?.firstOrNull()
                    ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                    ?.let { AlbumRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId) },
                duration = null,
                thumbnail = thumbnail,
                explicit = false,
                playlistId = watch.playlistId,
            )
        }

        return when (onTap.browseEndpoint?.pageType) {
            "MUSIC_PAGE_TYPE_ARTIST" -> ArtistItem(
                id = onTap.browseEndpoint!!.browseId ?: return null,
                title = title,
                thumbnail = thumbnail,
            )

            "MUSIC_PAGE_TYPE_PLAYLIST" -> PlaylistItem(
                id = onTap.browseEndpoint!!.browseId?.removePrefix("VL") ?: return null,
                title = title,
                author = null,
                songCount = null,
                thumbnail = thumbnail,
            )

            in ALBUM_PAGE_TYPES -> AlbumItem(
                id = onTap.browseEndpoint!!.browseId ?: return null,
                playlistId = onTap.playlistId,
                title = title,
                artists = null,
                year = null,
                thumbnail = thumbnail,
            )

            else -> null
        }
    }

    /** Primer run de un Runs (el título). */
    private fun com.rootleo.velqi.selene.model.Runs.firstRun(): String? =
        runs?.firstOrNull()?.text

    private val TYPE_LABELS = setOf(
        "Song", "Video", "Album", "EP", "Single", "Artist", "Playlist", "Movie", "Podcast",
    )

    private val ALBUM_PAGE_TYPES = setOf("MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK")
}