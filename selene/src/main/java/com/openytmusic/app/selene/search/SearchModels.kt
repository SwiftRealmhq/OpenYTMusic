package com.openytmusic.app.selene.search

import com.openytmusic.app.selene.model.ClientContext
import com.openytmusic.app.selene.model.Continuation
import com.openytmusic.app.selene.model.NavEndpoint
import com.openytmusic.app.selene.model.ResponsiveListItem
import com.openytmusic.app.selene.model.Runs
import com.openytmusic.app.selene.model.Shelf
import com.openytmusic.app.selene.model.ShelfContent
import com.openytmusic.app.selene.model.ThumbnailRenderer
import com.openytmusic.app.selene.model.TwoRowItem
import kotlinx.serialization.Serializable

// ============ Peticiones ============

@Serializable
data class SearchRequest(
    val context: ClientContext,
    val query: String? = null,
    val params: String? = null,
)

@Serializable
data class SuggestionsRequest(
    val context: ClientContext,
    val input: String,
)

// ============ Respuesta de búsqueda ============

@Serializable
data class SearchResponse(
    val contents: SearchContents? = null,
    val continuationContents: SearchContinuationContents? = null,
)

@Serializable
data class SearchContents(
    val tabbedSearchResultsRenderer: TabbedResults? = null,
)

@Serializable
data class TabbedResults(val tabs: List<Tab>? = null)

@Serializable
data class Tab(val tabRenderer: TabRenderer? = null)

@Serializable
data class TabRenderer(val content: TabContent? = null)

@Serializable
data class TabContent(val sectionListRenderer: SectionList? = null)

@Serializable
data class SectionList(val contents: List<Section>? = null)

/**
 * Una sección del resultado: puede ser un shelf ("Canciones", "Álbumes"...),
 * un itemSection (contenido suelto del tab "Todo") o la card del top result.
 */
@Serializable
data class Section(
    val musicShelfRenderer: Shelf? = null,
    val itemSectionRenderer: ItemSection? = null,
    val musicCardShelfRenderer: CardShelf? = null,
)

@Serializable
data class ItemSection(val contents: List<ItemSectionContent>? = null)

@Serializable
data class ItemSectionContent(
    val musicResponsiveListItemRenderer: ResponsiveListItem? = null,
)

/** Card del "top result": un solo item destacado (canción, artista, álbum...). */
@Serializable
data class CardShelf(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val onTap: NavEndpoint? = null,
    val contents: List<ShelfContent>? = null,
)

@Serializable
data class SearchContinuationContents(
    val musicShelfContinuation: ShelfContinuation? = null,
)

@Serializable
data class ShelfContinuation(
    val contents: List<ShelfContent>? = null,
    val continuations: List<Continuation>? = null,
)

// ============ Respuesta de sugerencias ============

@Serializable
data class SuggestionsResponse(val contents: List<SuggestionSection>? = null)

@Serializable
data class SuggestionSection(val searchSuggestionsSectionRenderer: SuggestionRenderer? = null)

@Serializable
data class SuggestionRenderer(val contents: List<SuggestionContent>? = null)

@Serializable
data class SuggestionContent(
    val searchSuggestionRenderer: Suggestion? = null,
    val musicResponsiveListItemRenderer: ResponsiveListItem? = null,
)

@Serializable
data class Suggestion(
    val suggestion: com.openytmusic.app.selene.model.Runs? = null,
    val navigationEndpoint: com.openytmusic.app.selene.model.NavEndpoint? = null,
)