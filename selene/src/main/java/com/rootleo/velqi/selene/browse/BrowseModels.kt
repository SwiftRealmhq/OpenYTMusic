package com.rootleo.velqi.selene.browse

import com.rootleo.velqi.selene.model.ButtonRenderer
import com.rootleo.velqi.selene.model.ClientContext
import com.rootleo.velqi.selene.model.Continuation
import com.rootleo.velqi.selene.model.MenuRenderer
import com.rootleo.velqi.selene.model.Runs
import com.rootleo.velqi.selene.model.Shelf
import com.rootleo.velqi.selene.model.ShelfContent
import com.rootleo.velqi.selene.model.ThumbnailRenderer
import kotlinx.serialization.Serializable

// ============ Petición ============

@Serializable
data class BrowseRequest(
    val context: ClientContext,
    val browseId: String? = null,
    val params: String? = null,
)

// ============ Respuesta ============

@Serializable
data class BrowseResponse(
    val contents: BrowseContents? = null,
    val continuationContents: BrowseContinuationContents? = null,
    val header: BrowseHeader? = null,
    val microformat: BrowseMicroformat? = null,
)

@Serializable
data class BrowseContents(
    val twoColumnBrowseResultsRenderer: TwoColumn? = null,
    val singleColumnBrowseResultsRenderer: SingleColumn? = null,
)

/** Páginas de dos columnas: álbumes y playlists. */
@Serializable
data class TwoColumn(
    val tabs: List<BrowseTab>? = null,
    val secondaryContents: SecondaryContents? = null,
)

/** Páginas de una columna: artistas, home, etc. */
@Serializable
data class SingleColumn(val tabs: List<BrowseTab>? = null)

@Serializable
data class SecondaryContents(val sectionListRenderer: SectionList? = null)

@Serializable
data class BrowseTab(val tabRenderer: BrowseTabRenderer? = null)

@Serializable
data class BrowseTabRenderer(val content: BrowseTabContent? = null)

@Serializable
data class BrowseTabContent(val sectionListRenderer: SectionList? = null)

@Serializable
data class SectionList(
    val contents: List<Section>? = null,
    val continuations: List<Continuation>? = null,
)

/** Sección dentro de una página browse (header, canciones, carrusel...). */
@Serializable
data class Section(
    val musicResponsiveHeaderRenderer: ResponsiveHeader? = null,
    val musicEditablePlaylistDetailHeaderRenderer: EditablePlaylistHeader? = null,
    val musicPlaylistShelfRenderer: PlaylistShelf? = null,
    val musicShelfRenderer: Shelf? = null,
    val musicCarouselShelfRenderer: Carousel? = null,
)

/** Header de álbumes y playlists. */
@Serializable
data class ResponsiveHeader(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<HeaderButton>? = null,
)

@Serializable
data class HeaderButton(val menuRenderer: MenuRenderer? = null)

@Serializable
data class EditablePlaylistHeader(val header: EditableInner? = null)

@Serializable
data class EditableInner(val musicResponsiveHeaderRenderer: ResponsiveHeader? = null)

/** Lista de canciones de álbum/playlist. */
@Serializable
data class PlaylistShelf(
    val contents: List<ShelfContent>? = null,
    val continuations: List<Continuation>? = null,
)

/** Carrusel: "Other versions", secciones de artista, etc. */
@Serializable
data class Carousel(
    val header: CarouselHeader? = null,
    val contents: List<ShelfContent>? = null,
)

@Serializable
data class CarouselHeader(val musicCarouselShelfBasicHeaderRenderer: CarouselBasic? = null)

@Serializable
data class CarouselBasic(
    val title: Runs? = null,
    val moreContentButton: MoreButton? = null,
)

@Serializable
data class MoreButton(val buttonRenderer: ButtonRenderer? = null)

/** Header a nivel de página (artistas). */
@Serializable
data class BrowseHeader(
    val musicImmersiveHeaderRenderer: ImmersiveHeader? = null,
    val musicVisualHeaderRenderer: VisualHeader? = null,
)

@Serializable
data class ImmersiveHeader(
    val title: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val playButton: HeaderPlayButton? = null,
    val startRadioButton: HeaderPlayButton? = null,
)

@Serializable
data class HeaderPlayButton(val buttonRenderer: ButtonRenderer? = null)

@Serializable
data class VisualHeader(
    val title: Runs? = null,
    val foregroundThumbnail: ThumbnailRenderer? = null,
)

@Serializable
data class BrowseMicroformat(val microformatDataRenderer: MicroformatData? = null)

@Serializable
data class MicroformatData(val urlCanonical: String? = null)

// ============ Continuaciones ============

@Serializable
data class BrowseContinuationContents(
    val musicPlaylistShelfContinuation: PlaylistShelfContinuation? = null,
    val sectionListContinuation: SectionListContinuation? = null,
)

@Serializable
data class PlaylistShelfContinuation(
    val contents: List<ShelfContent>? = null,
    val continuations: List<Continuation>? = null,
)

@Serializable
data class SectionListContinuation(
    val contents: List<Section>? = null,
    val continuations: List<Continuation>? = null,
)