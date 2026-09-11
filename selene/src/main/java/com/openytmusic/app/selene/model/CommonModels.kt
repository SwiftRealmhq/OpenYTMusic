@file:OptIn(ExperimentalSerializationApi::class)

package com.openytmusic.app.selene.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// ============ Contexto de cliente (compartido por todos los endpoints) ============

@Serializable
data class ClientContext(val client: ClientInfo)

@Serializable
data class ClientInfo(
    val clientName: String,
    val clientVersion: String,
    val gl: String,
    val hl: String,
)

// ============ Texto (runs) ============

@Serializable
data class Runs(val runs: List<Run>? = null)

@Serializable
data class Run(
    val text: String? = null,
    val navigationEndpoint: NavEndpoint? = null,
)

// ============ Navegación ============

@Serializable
data class NavEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val watchPlaylistEndpoint: WatchPlaylistEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
) {
    val videoId: String?
        get() = watchEndpoint?.videoId ?: watchPlaylistEndpoint?.videoId

    val playlistId: String?
        get() = watchPlaylistEndpoint?.playlistId ?: watchEndpoint?.playlistId
}

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val index: Int? = null,
    val playlistSetVideoId: String? = null,
)

@Serializable
data class WatchPlaylistEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
)

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseConfigs? = null,
) {
    val pageType: String?
        get() = browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
}

@Serializable
data class BrowseConfigs(val browseEndpointContextMusicConfig: BrowseMusicConfig? = null)

@Serializable
data class BrowseMusicConfig(val pageType: String? = null)

// ============ Thumbnails ============

@Serializable
data class ThumbnailRenderer(val musicThumbnailRenderer: MusicThumbnailRenderer? = null)

@Serializable
data class MusicThumbnailRenderer(val thumbnail: Thumbnails? = null)

@Serializable
data class Thumbnails(val thumbnails: List<Thumbnail>? = null)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

// ============ Continuación ============

@Serializable
data class Continuation(
    @JsonNames("nextContinuationData", "nextRadioContinuationData")
    val nextContinuationData: NextContinuationData? = null,
)

@Serializable
data class NextContinuationData(val continuation: String? = null)

// ============ Item de lista ============

@Serializable
data class FlexColumn(
    @JsonNames("musicResponsiveListItemFixedColumnRenderer")
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null,
)

@Serializable
data class FlexColumnRenderer(val text: Runs? = null)

@Serializable
data class PlaylistItemData(val videoId: String? = null)

@Serializable
data class PlayOverlay(val musicItemThumbnailOverlayRenderer: OverlayRenderer? = null)

@Serializable
data class OverlayRenderer(val content: OverlayContent? = null)

@Serializable
data class OverlayContent(val musicPlayButtonRenderer: PlayButtonRenderer? = null)

@Serializable
data class PlayButtonRenderer(val playNavigationEndpoint: NavEndpoint? = null)

@Serializable
data class Menu(val menuRenderer: MenuRenderer? = null)

@Serializable
data class MenuRenderer(val items: List<MenuItem>? = null)

@Serializable
data class MenuItem(val menuNavigationItemRenderer: MenuNavItem? = null)

@Serializable
data class MenuNavItem(
    val icon: Icon? = null,
    val navigationEndpoint: NavEndpoint? = null,
)

@Serializable
data class Icon(val iconType: String? = null)

@Serializable
data class Badge(val musicInlineBadgeRenderer: InlineBadge? = null)

@Serializable
data class InlineBadge(val icon: Icon? = null)

/** Botón genérico (botón de play de header, "ver más", etc.). */
@Serializable
data class ButtonRenderer(val navigationEndpoint: NavEndpoint? = null)

@Serializable
data class ResponsiveListItem(
    val flexColumns: List<FlexColumn>? = null,
    val fixedColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val menu: Menu? = null,
    val playlistItemData: PlaylistItemData? = null,
    val overlay: PlayOverlay? = null,
    val navigationEndpoint: NavEndpoint? = null,
    val badges: List<Badge>? = null,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null ||
            navigationEndpoint.watchEndpoint != null ||
            navigationEndpoint.watchPlaylistEndpoint != null

    val isAlbum: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType in ALBUM_PAGE_TYPES

    val isArtist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType == "MUSIC_PAGE_TYPE_ARTIST"

    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType == "MUSIC_PAGE_TYPE_PLAYLIST"

    fun isExplicit(): Boolean =
        badges?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } == true

    private companion object {
        val ALBUM_PAGE_TYPES = setOf("MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK")
    }
}

/** Ítem de carrusel/grid: álbumes, artistas y playlists con portada grande. */
@Serializable
data class TwoRowItem(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val subtitleBadges: List<Badge>? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavEndpoint? = null,
    val thumbnailOverlay: PlayOverlay? = null,
) {
    val isAlbum: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType in setOf("MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK")

    val isArtist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType == "MUSIC_PAGE_TYPE_ARTIST"

    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType == "MUSIC_PAGE_TYPE_PLAYLIST"

    fun isExplicit(): Boolean =
        subtitleBadges?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } == true
}

/** Shelf: lista de items con título (resultados de búsqueda, secciones de artista...). */
@Serializable
data class Shelf(
    val title: Runs? = null,
    val contents: List<ShelfContent>? = null,
    val continuations: List<Continuation>? = null,
)

@Serializable
data class ShelfContent(
    val musicResponsiveListItemRenderer: ResponsiveListItem? = null,
    val musicTwoRowItemRenderer: TwoRowItem? = null,
)

// ============ Helpers de extracción ============

/** URL de la portada de mayor resolución. */
fun ThumbnailRenderer.bestUrl(): String? =
    musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url

/** Une todos los runs en un texto plano. */
fun Runs.asText(): String? =
    runs?.joinToString(separator = "") { it.text.orEmpty() }?.takeIf { it.isNotEmpty() }

/** Divide una lista de runs por el separador " • " (Artista • Álbum • Duración). */
fun List<Run>.splitBySeparator(): List<List<Run>> {
    val result = mutableListOf<List<Run>>()
    var current = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            result.add(current)
            current = mutableListOf()
        } else {
            current.add(run)
        }
    }
    result.add(current)
    return result
}

/** Pares (nombre, navegación) — los runs de artistas vienen intercalados. */
fun List<Run>.oddElements(): List<Run> =
    filterIndexed { index, _ -> index % 2 == 0 }

/**
 * Descarta el primer segmento si es una etiqueta de tipo sin navegación
 * ("Song • ...", "Album • ...", "EP • ...") para unificar el parseo
 * entre el tab "Todo" y los shelves filtrados.
 */
fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null) this
    else drop(1)

fun List<Continuation>.getContinuation(): String? =
    firstOrNull()?.nextContinuationData?.continuation

/** "4:22" → 262 segundos. */
fun String.parseTime(): Int? {
    val parts = split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}

/** "1.1B plays" / "132 songs" → 132 (extrae el primer número). */
fun String.parseFirstNumber(): Int? =
    """\d+""".toRegex().find(this)?.value?.toIntOrNull()