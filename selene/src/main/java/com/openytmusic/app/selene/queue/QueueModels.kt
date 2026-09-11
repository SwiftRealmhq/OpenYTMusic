package com.openytmusic.app.selene.queue

import com.openytmusic.app.selene.model.Badge
import com.openytmusic.app.selene.model.ClientContext
import com.openytmusic.app.selene.model.Continuation
import com.openytmusic.app.selene.model.NavEndpoint
import com.openytmusic.app.selene.model.Runs
import com.openytmusic.app.selene.model.Thumbnails
import kotlinx.serialization.Serializable

// ============ Petición ============

@Serializable
data class NextRequest(
    val context: ClientContext,
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val index: Int? = null,
    val params: String? = null,
    val continuation: String? = null,
)

// ============ Respuesta ============

@Serializable
data class NextResponse(
    val contents: NextContents? = null,
    val continuationContents: NextContinuationContents? = null,
)

@Serializable
data class NextContents(
    val singleColumnMusicWatchNextResultsRenderer: SingleColumn? = null,
)

@Serializable
data class SingleColumn(val tabbedRenderer: TabbedRenderer? = null)

@Serializable
data class TabbedRenderer(val watchNextTabbedResultsRenderer: WatchNextTabs? = null)

@Serializable
data class WatchNextTabs(val tabs: List<NextTab>? = null)

/** Tabs: Up next (cola), Lyrics, Comments, Related. */
@Serializable
data class NextTab(val tabRenderer: NextTabRenderer? = null)

@Serializable
data class NextTabRenderer(
    val title: String? = null,
    val endpoint: NavEndpoint? = null,
    val content: NextTabContent? = null,
)

@Serializable
data class NextTabContent(val musicQueueRenderer: MusicQueueRenderer? = null)

@Serializable
data class MusicQueueRenderer(
    val header: QueueHeader? = null,
    val content: QueueContent? = null,
)

@Serializable
data class QueueHeader(val musicQueueHeaderRenderer: QueueHeaderRenderer? = null)

@Serializable
data class QueueHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
)

@Serializable
data class QueueContent(val playlistPanelRenderer: PlaylistPanel? = null)

/** El panel de la cola: canciones + continuación + automix. */
@Serializable
data class PlaylistPanel(
    val playlistId: String? = null,
    val contents: List<PanelContent>? = null,
    val continuations: List<Continuation>? = null,
)

@Serializable
data class PanelContent(
    val playlistPanelVideoRenderer: PanelVideo? = null,
    val automixPreviewVideoRenderer: AutomixPreview? = null,
)

@Serializable
data class PanelVideo(
    val title: Runs? = null,
    val lengthText: Runs? = null,
    val longBylineText: Runs? = null,
    val badges: List<Badge>? = null,
    val videoId: String? = null,
    val playlistSetVideoId: String? = null,
    val selected: Boolean = false,
    val thumbnail: Thumbnails? = null,
    val navigationEndpoint: NavEndpoint? = null,
)

/** Marcador de "cuando termina la cola, sigue esta radio automix". */
@Serializable
data class AutomixPreview(val content: AutomixContent? = null)

@Serializable
data class AutomixContent(val automixPlaylistVideoRenderer: AutomixVideo? = null)

@Serializable
data class AutomixVideo(val navigationEndpoint: NavEndpoint? = null)

// ============ Continuación ============

@Serializable
data class NextContinuationContents(
    val playlistPanelContinuation: PlaylistPanel? = null,
)