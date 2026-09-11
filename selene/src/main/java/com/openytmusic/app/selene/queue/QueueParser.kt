package com.openytmusic.app.selene.queue

import com.openytmusic.app.selene.model.getContinuation
import com.openytmusic.app.selene.model.oddElements
import com.openytmusic.app.selene.model.parseTime
import com.openytmusic.app.selene.model.splitBySeparator
import com.openytmusic.app.selene.search.AlbumRef
import com.openytmusic.app.selene.search.ArtistRef
import com.openytmusic.app.selene.search.SongItem

/**
 * Parser del endpoint next: convierte el JSON en la cola de reproducción.
 */
object QueueParser {

    /** Cola inicial de una canción/playlist. */
    fun parseInitial(response: NextResponse): NextResult {
        val tabs = response.contents?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs
            .orEmpty()

        // Tab 0 = "Up next" (la cola)
        val queueTab = tabs.firstOrNull { it.tabRenderer?.content?.musicQueueRenderer != null }
            ?.tabRenderer?.content?.musicQueueRenderer
        val panel = queueTab?.content?.playlistPanelRenderer

        val title = queueTab?.header?.musicQueueHeaderRenderer?.subtitle
            ?.runs?.firstOrNull()?.text
        val (songs, currentIndex) = parsePanel(panel)
        val continuation = panel?.continuations?.getContinuation()
        val automixEndpoint = panel?.contents?.lastOrNull()?.automixPreviewVideoRenderer
            ?.content?.automixPlaylistVideoRenderer?.navigationEndpoint
            ?.let(UpNextEndpoint.Companion::fromNavEndpoint)

        // Tabs 1 y 3 = letras y relacionado
        val lyricsBrowseId = tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint?.browseId
        val relatedBrowseId = tabs.getOrNull(3)?.tabRenderer?.endpoint?.browseEndpoint?.browseId

        return NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsBrowseId = lyricsBrowseId,
            relatedBrowseId = relatedBrowseId,
            continuation = continuation,
            automixEndpoint = automixEndpoint,
        )
    }

    /** Continuación de una cola (más canciones de la misma radio/playlist). */
    fun parseContinuation(response: NextResponse): NextResult {
        val panel = response.continuationContents?.playlistPanelContinuation
        val (songs, _) = parsePanel(panel)
        return NextResult(
            items = songs,
            continuation = panel?.continuations?.getContinuation(),
        )
    }

    /** Devuelve las canciones del panel + el índice de la seleccionada. */
    private fun parsePanel(panel: PlaylistPanel?): Pair<List<SongItem>, Int?> {
        val songs = mutableListOf<SongItem>()
        var selectedIndex: Int? = null
        panel?.contents.orEmpty().forEachIndexed { index, content ->
            content.playlistPanelVideoRenderer?.let { renderer ->
                songFromPanelVideo(renderer)?.let { song ->
                    songs.add(song)
                    if (renderer.selected && selectedIndex == null) {
                        selectedIndex = index
                    }
                }
            }
        }
        return songs to selectedIndex
    }

    /** Canción de la cola (playlistPanelVideoRenderer). */
    fun songFromPanelVideo(renderer: PanelVideo): SongItem? {
        val videoId = renderer.videoId ?: return null
        val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
        val bylineParts = renderer.longBylineText?.runs?.splitBySeparator().orEmpty()
        return SongItem(
            id = videoId,
            title = title,
            artists = bylineParts.firstOrNull()?.oddElements()?.map {
                ArtistRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId)
            }.orEmpty(),
            album = bylineParts.getOrNull(1)?.firstOrNull()
                ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                ?.let { AlbumRef(it.text.orEmpty(), it.navigationEndpoint?.browseEndpoint?.browseId) },
            duration = renderer.lengthText?.runs?.firstOrNull()?.text?.parseTime(),
            thumbnail = renderer.thumbnail?.thumbnails?.lastOrNull()?.url.orEmpty(),
            explicit = renderer.badges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
            playlistId = renderer.navigationEndpoint?.playlistId,
        )
    }
}