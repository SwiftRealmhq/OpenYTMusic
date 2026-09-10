package com.rootleo.velqi.selene.browse

import com.rootleo.velqi.selene.model.ResponsiveListItem
import com.rootleo.velqi.selene.model.asText
import com.rootleo.velqi.selene.model.bestUrl
import com.rootleo.velqi.selene.model.getContinuation
import com.rootleo.velqi.selene.model.oddElements
import com.rootleo.velqi.selene.model.parseFirstNumber
import com.rootleo.velqi.selene.model.parseTime
import com.rootleo.velqi.selene.model.splitBySeparator
import com.rootleo.velqi.selene.search.AlbumItem
import com.rootleo.velqi.selene.search.AlbumRef
import com.rootleo.velqi.selene.search.ArtistItem
import com.rootleo.velqi.selene.search.ArtistRef
import com.rootleo.velqi.selene.search.MediaItem
import com.rootleo.velqi.selene.search.PlaylistItem
import com.rootleo.velqi.selene.search.SearchParser
import com.rootleo.velqi.selene.search.SongItem

/**
 * Parser de páginas browse: álbumes, artistas y playlists.
 */
object BrowseParser {

    // ============ Álbum ============

    fun parseAlbum(response: BrowseResponse, browseId: String): AlbumPage? {
        val twoColumn = response.contents?.twoColumnBrowseResultsRenderer ?: return null
        val tabContents = twoColumn.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
        val header = tabContents?.contents
            ?.firstOrNull { it.musicResponsiveHeaderRenderer != null }
            ?.musicResponsiveHeaderRenderer ?: return null

        val title = header.title?.asText() ?: return null
        val artists = header.straplineTextOne?.runs?.oddElements()?.map {
            ArtistRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId)
        }
        val year = header.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull()
        val thumbnail = header.thumbnail?.bestUrl() ?: return null
        val playlistId = response.microformat?.microformatDataRenderer?.urlCanonical
            ?.substringAfterLast('=', missingDelimiterValue = "")

        val secondary = twoColumn.secondaryContents?.sectionListRenderer
        val songSection = secondary?.contents?.firstOrNull {
            it.musicPlaylistShelfRenderer != null || it.musicShelfRenderer != null
        }
        val rawSongs = when {
            songSection?.musicPlaylistShelfRenderer != null -> songSection.musicPlaylistShelfRenderer!!.contents.orEmpty()
            songSection?.musicShelfRenderer != null -> songSection.musicShelfRenderer!!.contents.orEmpty()
            else -> emptyList()
        }.mapNotNull { it.musicResponsiveListItemRenderer?.let(::songFromListItem) }

        // Las canciones del álbum no traen portada ni artistas propios:
        // los heredamos del contexto del álbum (así el item queda completo).
        val albumRef = AlbumRef(title, browseId)
        val songs = rawSongs.map { song ->
            song.copy(
                artists = song.artists.ifEmpty { artists.orEmpty() },
                album = song.album ?: albumRef,
                thumbnail = song.thumbnail.ifEmpty { thumbnail },
            )
        }

        val otherVersions = secondary?.contents
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.flatMap { carousel -> carousel.contents.orEmpty() }
            ?.mapNotNull { it.musicTwoRowItemRenderer?.let(SearchParser::toMediaItem) }
            ?.filterIsInstance<AlbumItem>()
            .orEmpty()

        return AlbumPage(
            album = AlbumItem(
                id = browseId,
                playlistId = playlistId?.takeIf { it.isNotEmpty() },
                title = title,
                artists = artists,
                year = year,
                thumbnail = thumbnail,
            ),
            songs = songs,
            otherVersions = otherVersions,
        )
    }

    // ============ Artista ============

    fun parseArtist(response: BrowseResponse, browseId: String): ArtistPage? {
        val immersive = response.header?.musicImmersiveHeaderRenderer
        val visual = response.header?.musicVisualHeaderRenderer

        val title = immersive?.title?.asText() ?: visual?.title?.asText() ?: return null
        val thumbnail = immersive?.thumbnail?.bestUrl() ?: visual?.foregroundThumbnail?.bestUrl()
            ?: return null
        val description = immersive?.description?.asText()

        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
            .orEmpty()
            .mapNotNull { section ->
                when {
                    section.musicShelfRenderer != null -> sectionFromShelf(section.musicShelfRenderer!!)
                    section.musicCarouselShelfRenderer != null -> sectionFromCarousel(section.musicCarouselShelfRenderer!!)
                    else -> null
                }
            }

        return ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = title,
                thumbnail = thumbnail,
            ),
            sections = sections,
            description = description,
        )
    }

    private fun sectionFromShelf(shelf: com.rootleo.velqi.selene.model.Shelf): ArtistSection? {
        val title = shelf.title?.asText() ?: return null
        val items = shelf.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer?.let(SearchParser::toMediaItem) }
            .ifEmpty { return null }
        val moreBrowseId = shelf.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
        return ArtistSection(title, items, moreBrowseId)
    }

    private fun sectionFromCarousel(carousel: Carousel): ArtistSection? {
        val title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.asText()
            ?: return null
        val items = carousel.contents.orEmpty()
            .mapNotNull { it.musicTwoRowItemRenderer?.let(SearchParser::toMediaItem) }
            .ifEmpty { return null }
        val moreBrowseId = carousel.header?.musicCarouselShelfBasicHeaderRenderer
            ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId
        return ArtistSection(title, items, moreBrowseId)
    }

    // ============ Playlist ============

    fun parsePlaylist(response: BrowseResponse, playlistId: String): PlaylistPage? {
        val twoColumn = response.contents?.twoColumnBrowseResultsRenderer ?: return null
        val tabContents = twoColumn.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer

        val header = tabContents?.contents
            ?.firstOrNull { it.musicResponsiveHeaderRenderer != null }
            ?.musicResponsiveHeaderRenderer
            ?: tabContents?.contents
                ?.firstOrNull { it.musicEditablePlaylistDetailHeaderRenderer != null }
                ?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
            ?: return null

        val title = header.title?.asText() ?: return null
        val author = header.straplineTextOne?.asText()
        // secondSubtitle: "132 songs • 7+ hours" → 132 (todo el texto, no el último run)
        val songCount = header.secondSubtitle?.asText()?.parseFirstNumber()
        val thumbnail = header.thumbnail?.bestUrl() ?: return null

        val secondary = twoColumn.secondaryContents?.sectionListRenderer
        val shelf = secondary?.contents
            ?.firstOrNull { it.musicPlaylistShelfRenderer != null }
            ?.musicPlaylistShelfRenderer
        val songs = shelf?.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer?.let(::songFromListItem) }
        val continuation = shelf?.continuations?.getContinuation()
            ?: secondary?.continuations?.getContinuation()

        return PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = title,
                author = author,
                songCount = songCount,
                thumbnail = thumbnail,
            ),
            songs = songs,
            continuation = continuation,
        )
    }

    fun parsePlaylistContinuation(response: BrowseResponse): PlaylistContinuationPage {
        val songs = mutableListOf<SongItem>()
        val cc = response.continuationContents
        cc?.musicPlaylistShelfContinuation?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { songFromListItem(it) }?.let(songs::add)
        }
        cc?.sectionListContinuation?.contents.orEmpty().forEach { section ->
            section.musicPlaylistShelfRenderer?.contents.orEmpty().forEach { content ->
                content.musicResponsiveListItemRenderer?.let { songFromListItem(it) }?.let(songs::add)
            }
        }
        val continuation = cc?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
            ?: cc?.sectionListContinuation?.continuations?.getContinuation()
        return PlaylistContinuationPage(songs = songs, continuation = continuation)
    }

    // ============ Item de canción (álbum/playlist) ============

    /**
     * Canción de una lista de álbum/playlist. Más permisivo que el de
     * búsqueda: artistas opcionales, portada opcional (se hereda del contexto).
     */
    fun songFromListItem(renderer: ResponsiveListItem): SongItem? {
        val videoId = renderer.playlistItemData?.videoId ?: renderer.navigationEndpoint?.videoId
            ?: return null
        val title = renderer.flexColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text
            ?.runs?.firstOrNull()?.text.orEmpty()
        val artists = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.splitBySeparator()?.firstOrNull()?.oddElements()
            ?.map { ArtistRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId) }
            .orEmpty()
        val duration = renderer.fixedColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.firstOrNull()?.text?.parseTime()
            ?: renderer.flexColumns?.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.lastOrNull()?.text?.parseTime()
        val thumbnail = renderer.thumbnail?.bestUrl()

        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            album = null,
            duration = duration,
            thumbnail = thumbnail.orEmpty(),
            explicit = renderer.isExplicit(),
            playlistId = renderer.navigationEndpoint?.playlistId,
        )
    }
}