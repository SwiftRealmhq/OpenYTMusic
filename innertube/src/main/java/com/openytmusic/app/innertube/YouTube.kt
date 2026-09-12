package com.openytmusic.app.innertube

import com.openytmusic.app.innertube.models.AccountInfo
import com.openytmusic.app.innertube.models.AlbumItem
import com.openytmusic.app.innertube.models.Artist
import com.openytmusic.app.innertube.models.ArtistItem
import com.openytmusic.app.innertube.models.BrowseEndpoint
import com.openytmusic.app.innertube.models.GridRenderer
import com.openytmusic.app.innertube.models.MusicCarouselShelfRenderer
import com.openytmusic.app.innertube.models.PlaylistItem
import com.openytmusic.app.innertube.models.SearchSuggestions
import com.openytmusic.app.innertube.models.SectionListRenderer
import com.openytmusic.app.innertube.models.SongItem
import com.openytmusic.app.innertube.models.WatchEndpoint
import com.openytmusic.app.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.openytmusic.app.innertube.models.YouTubeClient
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.ANDROID_VR
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.IOS
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.TVHTML5
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.WEB
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.openytmusic.app.innertube.models.YouTubeLocale
import com.openytmusic.app.innertube.models.getContinuation
import com.openytmusic.app.innertube.models.oddElements
import com.openytmusic.app.innertube.models.response.AccountMenuResponse
import com.openytmusic.app.innertube.models.response.BrowseResponse
import com.openytmusic.app.innertube.models.response.GetQueueResponse
import com.openytmusic.app.innertube.models.response.GetSearchSuggestionsResponse
import com.openytmusic.app.innertube.models.response.GetTranscriptResponse
import com.openytmusic.app.innertube.models.response.NextResponse
import com.openytmusic.app.innertube.models.response.PipedResponse
import com.openytmusic.app.innertube.models.response.PlayerResponse
import com.openytmusic.app.innertube.models.response.SearchResponse
import com.openytmusic.app.innertube.pages.AlbumPage
import com.openytmusic.app.innertube.pages.ArtistItemsContinuationPage
import com.openytmusic.app.innertube.pages.ArtistItemsPage
import com.openytmusic.app.innertube.pages.ArtistPage
import com.openytmusic.app.innertube.pages.BrowseResult
import com.openytmusic.app.innertube.pages.ExplorePage
import com.openytmusic.app.innertube.pages.HomePage
import com.openytmusic.app.innertube.pages.MoodAndGenres
import com.openytmusic.app.innertube.pages.NewReleaseAlbumPage
import com.openytmusic.app.innertube.pages.NextPage
import com.openytmusic.app.innertube.pages.NextResult
import com.openytmusic.app.innertube.pages.PlaylistContinuationPage
import com.openytmusic.app.innertube.pages.PlaylistPage
import com.openytmusic.app.innertube.pages.RelatedPage
import com.openytmusic.app.innertube.pages.SearchPage
import com.openytmusic.app.innertube.pages.SearchResult
import com.openytmusic.app.innertube.pages.SearchSuggestionPage
import com.openytmusic.app.innertube.pages.SearchSummary
import com.openytmusic.app.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.Proxy

/**
 * Resultado de [YouTube.player].
 *
 * [streamingDataPoToken] y [clientUsed] viajan DENTRO del resultado a proposito: antes vivian en
 * variables globales de `YouTube` y cualquier otra resolucion concurrente (p. ej. `recoverSong`,
 * que tambien llama a `player()`) las borraba entre el minteo y el uso. El consumidor entonces
 * armaba la URL sin `pot=` y googlevideo cortaba el stream (~1 MB, 403 al hacer seek).
 */
data class PlayerResult(
    val response: PlayerResponse,
    /** Cliente que entrego los streams: ANDROID, IOS, WEB_REMIX+pot, ANDROID_VR o TVHTML5+piped. */
    val clientUsed: String? = null,
    /** `pot=` ligado al video; solo se inyecta cuando esta respuesta es la que se reproduce. */
    val streamingDataPoToken: String? = null,
    /** Diagnostico del intento WEB_REMIX+PoToken; `null` si no fue necesario. */
    val poTokenAttempt: String? = null,
)

/**
 * Parse useful data with [InnerTube] sending requests.
 */
object YouTube {
    private val innerTube = InnerTube()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            }.orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let { renderer ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                }
            }.orEmpty()
        )
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
        SearchSummaryPage(
            summaries = listOf(
                SearchSummary(
                    title = "Songs",
                    items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                        ?.tabRenderer?.content?.sectionListRenderer?.contents
                        ?.flatMap { section ->
                            // Kernel de Velqi: el tab "Todo" reparte canciones en varias
                            // itemSectionRenderer; las unimos en UNA sola lista.
                            section.itemSectionRenderer?.contents
                                ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                                .orEmpty()
                        }
                        .orEmpty()
                        .plus(
                            response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                                ?.tabRenderer?.content?.sectionListRenderer?.contents
                                ?.mapNotNull { it.musicCardShelfRenderer }
                                ?.mapNotNull(SearchSummaryPage.Companion::fromMusicCardShelfRenderer)
                                .orEmpty()
                        )
                        .distinctBy { it.id }
                )
            )
        )
    }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        SearchResult(
            items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.contents?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }.orEmpty(),
            continuation = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.continuations?.getContinuation()
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        SearchResult(
            items = response.continuationContents?.musicShelfContinuation?.contents
                ?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }!!,
            continuation = response.continuationContents.musicShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        val playlistId = response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
        AlbumPage(
            album = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                artists = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.straplineTextOne?.runs?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                }!!,
                year = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
            ),
            songs = if (withSongs) albumSongs(playlistId).getOrThrow() else emptyList(),
            otherVersions = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer?.contents?.getOrNull(1)?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                .orEmpty()
        )
    }

    suspend fun albumSongs(playlistId: String): Result<List<SongItem>> = runCatching {
        var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()
        val songs = response.contents?.twoColumnBrowseResultsRenderer
            ?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer?.contents
            ?.mapNotNull {
                AlbumPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }!!
            .toMutableList()
        var continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
        while (continuation != null) {
            response = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
            ).body<BrowseResponse>()
            songs += response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                AlbumPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }.orEmpty()
            continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        }
        songs
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()!!,
                shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.gridRenderer
        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridRenderer.continuations?.getContinuation()
            )
        } else {
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                    }!!,
                continuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
        val gridContinuation = response.continuationContents?.gridContinuation
        if (gridContinuation != null) {
            ArtistItemsContinuationPage(
                items = gridContinuation.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridContinuation.continuations?.getContinuation()
            )
        } else {
            ArtistItemsContinuationPage(
                items = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }!!,
                continuation = response.continuationContents.musicPlaylistShelfContinuation.continuations?.getContinuation()
            )
        }
    }

    /**
     * Velqi: playlists PROPIAS de la cuenta (Biblioteca -> Playlists).
     * El landing (FEmusic_library_landing) muestra "chips" de filtro
     * (Albumes/Artistas/Playlists/Videos); cada chip trae su propio
     * browseId+params. Recorremos todos los chips y colectamos las
     * playlists de cada pagina filtrada - sin hardcodear nada.
     */
    suspend fun libraryPlaylists(): Result<List<PlaylistItem>> = runCatching {
        fun parseContents(contents: List<SectionListRenderer.Content>?): List<PlaylistItem> =
            contents?.flatMap { content ->
                val twoRowItems = content.gridRenderer?.items?.mapNotNull { it.musicTwoRowItemRenderer }
                    ?: content.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer }
                    ?: emptyList()
                twoRowItems.mapNotNull { renderer ->
                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer) as? PlaylistItem
                        // Rescate: las playlists PRIVADAS de la cuenta llegan sin
                        // browseEndpointContextSupportedConfigs y el parser las bota.
                        // El prefijo "VL" del browseId es marca inequivoca de playlist.
                        ?: renderer.navigationEndpoint?.browseEndpoint?.browseId
                            ?.takeIf { it.startsWith("VL") }
                            ?.let { browseId ->
                                PlaylistItem(
                                    id = browseId.removePrefix("VL"),
                                    title = renderer.title.runs?.firstOrNull()?.text ?: return@let null,
                                    author = renderer.subtitle?.runs?.getOrNull(2)?.let {
                                        Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                                    },
                                    songCountText = renderer.subtitle?.runs?.getOrNull(4)?.text,
                                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl().orEmpty(),
                                    playEndpoint = null,
                                    shuffleEndpoint = WatchEndpoint(playlistId = browseId.removePrefix("VL")),
                                    radioEndpoint = null
                                )
                            }
                }
            }.orEmpty()

        val landingBody = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_library_landing",
            setLogin = true
        ).bodyAsText()
        // logcat corta lineas largas (~4KB): troceamos para ver la respuesta completa
        landingBody.chunked(2000).forEachIndexed { i, chunk ->
            println("VELQIDBG: raw[$i]=$chunk")
        }
        // Json tolerante (mismo config que el cliente): sin esto un key desconocido
        // (ej. maxAgeSeconds) mata el decode de toda la respuesta.
        val landing = Json { ignoreUnknownKeys = true; explicitNulls = false }.decodeFromString<BrowseResponse>(landingBody)
        val landingSectionList = landing.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
        println("VELQIDBG: import: landing secciones=${landingSectionList?.contents?.map { c ->
            listOfNotNull(
                if (c.gridRenderer != null) "grid(items=${c.gridRenderer?.items?.size})" else null,
                if (c.musicShelfRenderer != null) "shelf(items=${c.musicShelfRenderer?.contents?.size})" else null,
                if (c.musicCarouselShelfRenderer != null) "carousel" else null,
                if (c.musicPlaylistShelfRenderer != null) "playlistShelf" else null,
            ).ifEmpty { listOf("otro") }
        }} continuation=${landingSectionList?.continuations != null}")

        val chips = (landingSectionList?.header?.chipCloudRenderer
            ?: landingSectionList?.header?.musicSideAlignedItemRenderer?.startItems
                ?.firstOrNull()?.chipCloudRenderer)?.chips.orEmpty()
        println("VELQIDBG: import: landing chips=${chips.map { it.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text to (it.chipCloudChipRenderer.navigationEndpoint?.browseEndpoint?.params != null) }}")

        val playlists = mutableListOf<PlaylistItem>()
        playlists += parseContents(landingSectionList?.contents)

        // Pagina completa de playlists: el chip cuyo endpoint apunta a FEmusic_playlists
        val playlistChip = chips.map { it.chipCloudChipRenderer }.firstOrNull {
            it.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_liked_playlists"
        }
        if (playlistChip != null) {
            val full = innerTube.browse(
                client = WEB_REMIX,
                browseId = playlistChip.navigationEndpoint?.browseEndpoint?.browseId,
                params = playlistChip.navigationEndpoint?.browseEndpoint?.params,
                setLogin = true
            ).body<BrowseResponse>()
            var continuation: String? = null
            do {
                val contents = full.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents
                playlists += parseContents(contents)
                continuation = contents?.lastOrNull()?.gridRenderer?.continuations?.getContinuation()
            } while (continuation != null)
        } else {
            println("VELQIDBG: import: entrando por chip FEmusic_liked_playlists (pagina completa de la cuenta)")
        }
        playlists.distinctBy { it.id }.also {
            println("VELQIDBG: import: libraryPlaylists parse final count=${it.size}")
        }
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = true
        ).body<BrowseResponse>()
        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
        // Velqi: sin !! - las playlists privadas de la cuenta no traen todos los
        // botones (ej. radio) y los !! lanzaban NPE revendiendo la carga completa.
        val shelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer
        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header?.title?.runs?.firstOrNull()?.text ?: playlistId,
                author = header?.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = header?.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url.orEmpty(),
                playEndpoint = null,
                shuffleEndpoint = header?.buttons?.lastOrNull()?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                    ?: WatchEndpoint(playlistId = playlistId),
                radioEndpoint = header?.buttons?.lastOrNull()?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
            ),
            songs = shelf?.contents?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }.orEmpty(),
            songsContinuation = shelf?.continuations?.getContinuation(),
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()
        PlaylistContinuationPage(
            songs = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }!!,
            continuation = response.continuationContents.musicPlaylistShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun home(): Result<HomePage> = runCatching {
        var response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home").body<BrowseResponse>()
        var continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.toMutableList()
        while (continuation != null) {
            response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull {
                    HomePage.Section.fromMusicCarouselShelfRenderer(it)
                }.orEmpty()
        }
        HomePage(sections)
    }

    suspend fun explore(): Result<ExplorePage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
        ExplorePage(
            newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
            moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicNavigationButtonRenderer }
                ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                .orEmpty()
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
    }

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> {
                        BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.gridRenderer.items
                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    content.musicCarouselShelfRenderer != null -> {
                        BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.musicCarouselShelfRenderer.contents
                                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    else -> null
                }
            }.orEmpty()
        )
    }

    suspend fun likedPlaylists(): Result<List<PlaylistItem>> = runCatching {
        val pageBody = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_liked_playlists",
            setLogin = true
        ).bodyAsText()
        // Velqi: dump troceado para diagnostico (logcat corta lineas de ~4KB)
        pageBody.chunked(2000).forEachIndexed { i, chunk ->
            println("VELQIDBG: likedPage[$i]=$chunk")
        }
        val response = Json { ignoreUnknownKeys = true; explicitNulls = false }.decodeFromString<BrowseResponse>(pageBody)
        // Velqi: sin !! - un response inesperado no debe morir en silencio;
        // devuelve lista vacia y la pantalla muestra "reintentar".
        // Ademas: parseamos TODAS las secciones, no solo la primera - las
        // playlists propias pueden venir en una seccion distinta al grid inicial.
        val allContents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
        println("VELQIDBG: import: likedPage secciones=${allContents.map { c ->
            listOfNotNull(
                if (c.gridRenderer != null) "grid(items=${c.gridRenderer?.items?.size})" else null,
                if (c.musicShelfRenderer != null) "shelf(items=${c.musicShelfRenderer?.contents?.size})" else null,
                if (c.musicCarouselShelfRenderer != null) "carousel" else null,
                if (c.musicPlaylistShelfRenderer != null) "playlistShelf" else null,
            ).ifEmpty { listOf("otro") }
        }}")
        val grid = allContents.firstOrNull()?.gridRenderer
        val items = grid?.items ?: emptyList()
        val parsed = items
            .drop(1) // the first item is "create new playlist"
            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
            .mapNotNull { renderer ->
                ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer) as? PlaylistItem
                    // Rescate VL: playlists privadas pueden llegar sin el campo
                    // de clasificacion; el prefijo VL del browseId es marca de playlist.
                    ?: renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?.takeIf { it.startsWith("VL") }
                        ?.let { browseId ->
                            PlaylistItem(
                                id = browseId.removePrefix("VL"),
                                title = renderer.title.runs?.firstOrNull()?.text ?: return@let null,
                                author = renderer.subtitle?.runs?.getOrNull(2)?.let {
                                    Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                                },
                                songCountText = renderer.subtitle?.runs?.getOrNull(4)?.text,
                                thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl().orEmpty(),
                                playEndpoint = null,
                                shuffleEndpoint = WatchEndpoint(playlistId = browseId.removePrefix("VL")),
                                radioEndpoint = null
                            )
                        }
            }.toMutableList()
        println("VELQIDBG: import: likedPlaylists grid items=${items.size} parseados=${parsed.size} continuation=${grid?.continuations != null}")
        // Paginacion: traer las paginas siguientes del grid si existen
        var continuation = grid?.continuations?.getContinuation()
        while (continuation != null) {
            val next = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
                setLogin = true
            ).body<BrowseResponse>()
            val nextItems: List<GridRenderer.Item>
            val nextContinuation: String?
            val nextSectionGrid = next.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer
            val nextGridCont = next.continuationContents?.gridContinuation
            if (nextSectionGrid != null) {
                nextItems = nextSectionGrid.items
                nextContinuation = nextSectionGrid.continuations?.getContinuation()
            } else if (nextGridCont != null) {
                nextItems = nextGridCont.items
                nextContinuation = nextGridCont.continuations?.getContinuation()
            } else break
            parsed += nextItems
                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                .mapNotNull { renderer -> ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer) as? PlaylistItem }
            continuation = nextContinuation
        }
        parsed
    }

    var poTokenProvider: (suspend (videoId: String, visitorData: String?) -> Pair<String, String>?)? = null

    suspend fun player(videoId: String, playlistId: String? = null): Result<PlayerResult> = runCatching {
        // Estrategia de resolucion (validada contra la API real):
        // 1) Clientes moviles (ANDROID con cookie si hay sesion, luego IOS): son los que
        //    responden OK en la practica. La reproduccion normal no paga ningun costo extra.
        // 2) Solo si TODOS responden muro anti-bot (LOGIN_REQUIRED / "not a bot"), se pide
        //    un PoToken (BotGuard via WebView) y se reintenta con WEB_REMIX + poToken, que es
        //    el camino que pasa el bot-check sin sesion. El WebView se levanta solo aqui.
        // 3) ANDROID_VR, TVHTML5 y piped como ultimos recursos.
        // El token de streaming viaja en el PlayerResult que se devuelve: no hay estado global
        // que otra resolucion concurrente pueda borrar entre el minteo y el uso.
        // Snapshot del visitorData: el PoToken de sesion debe estar ligado EXACTAMENTE al mismo
        // valor que viaja en el /player request. Leido dos veces, un cambio de visitorData en el
        // medio (login o refresh) dejaria un token que no valida y el muro anti-bot volveria.
        val visitorData = this.visitorData
        var poTokenAttempt: String? = null
        var lastResponse: PlayerResponse? = null
        var lastError: Throwable? = null
        var walled = false
        // Si no hay proveedor de PoToken, el camino web no aporta nada y se salta.
        val hasPoTokenProvider = poTokenProvider != null

        // 1) Clientes moviles: son los que responden OK en la practica y sin costo extra. La cookie
        //    de sesion viaja en la peticion, asi que con sesion el muro no aparece.
        val mobileClients = listOf(YouTubeClient.ANDROID, YouTubeClient.IOS)
        for (client in mobileClients) {
            try {
                val response = innerTube.player(client, videoId, playlistId).body<PlayerResponse>()
                lastResponse = response
                if (response.playabilityStatus.status == "OK") {
                    return@runCatching PlayerResult(
                        response = response,
                        clientUsed = client.clientName,
                    )
                }
                if (
                    response.playabilityStatus.status == "LOGIN_REQUIRED" ||
                    response.playabilityStatus.reason?.contains("not a bot", ignoreCase = true) == true
                ) {
                    walled = true
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }

        // 2) Muro anti-bot -> PoToken (BotGuard via WebView).
        //    Solo se paga el costo de levantar el WebView cuando de verdad nos bloquearon.
        if (walled && hasPoTokenProvider) {
            try {
                val pot = poTokenProvider?.invoke(videoId, visitorData)
                if (pot != null) {
                    // Con el token en la mano se prueban dos clientes, en este orden:
                    //  a) ANDROID + pot — es el que MEJOR entrega streams (muxed itag 18 y
                    //     adaptativos con rangos completos). Si lo que faltaba era la atestacion,
                    //     gana aqui y el rescate no depende del cliente web.
                    //  b) WEB_REMIX + pot — camino historico, medido contra la API real.
                    // `poTokenAttempt` deja en el log (`intento=`) cual de los dos gano: asi se mide
                    // cual vale la pena en vez de asumirlo.
                    for (client in listOf(YouTubeClient.ANDROID, YouTubeClient.WEB_REMIX)) {
                        val clientLabel = "${client.clientName}+pot"
                        val response = runCatching {
                            innerTube.player(
                                client = client,
                                videoId = videoId,
                                playlistId = playlistId,
                                poToken = pot.first,
                                visitorData = visitorData,
                            ).body<PlayerResponse>()
                        }.getOrElse { t ->
                            poTokenAttempt = "$clientLabel excepcion: ${t.javaClass.simpleName}: ${t.message}"
                            null
                        }
                        if (response != null && response.playabilityStatus.status == "OK") {
                            poTokenAttempt = "$clientLabel OK"
                            // El pot= de la URL va ligado al video: se devuelve junto con ESTA
                            // respuesta, para que solo se inyecte si es la que se va a reproducir.
                            return@runCatching PlayerResult(
                                response = response,
                                clientUsed = clientLabel,
                                streamingDataPoToken = pot.second,
                                poTokenAttempt = poTokenAttempt,
                            )
                        }
                        if (response != null) {
                            poTokenAttempt = "$clientLabel status=${response.playabilityStatus.status} reason=${response.playabilityStatus.reason}"
                        }
                    }
                } else {
                    poTokenAttempt = "sin PoToken (BotGuard frio, calentando o no disponible)"
                }
            } catch (t: Throwable) {
                poTokenAttempt = "pot excepcion: ${t.javaClass.simpleName}: ${t.message}"
            }
        }

        // 3) Ultimos recursos: ANDROID_VR y luego TVHTML5 + piped.
        try {
            val response = innerTube.player(YouTubeClient.ANDROID_VR, videoId, playlistId).body<PlayerResponse>()
            lastResponse = response
            if (response.playabilityStatus.status == "OK") {
                return@runCatching PlayerResult(
                    response = response,
                    clientUsed = "ANDROID_VR",
                    poTokenAttempt = poTokenAttempt,
                )
            }
        } catch (e: Throwable) {
            lastError = e
        }
        val safePlayerResponse = try {
            innerTube.player(TVHTML5, videoId, playlistId).body<PlayerResponse>()
        } catch (e: Throwable) {
            lastError = e
            null
        }
        if (safePlayerResponse == null || safePlayerResponse.playabilityStatus.status != "OK") {
            if (lastResponse != null) {
                return@runCatching PlayerResult(
                    response = lastResponse,
                    poTokenAttempt = poTokenAttempt,
                )
            }
            throw lastError ?: RuntimeException("No streams available")
        }
        val audioStreams = innerTube.pipedStreams(videoId).body<PipedResponse>().audioStreams
        return@runCatching PlayerResult(
            response = safePlayerResponse.copy(
                streamingData = safePlayerResponse.streamingData?.copy(
                    adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats.mapNotNull { adaptiveFormat ->
                        audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.let {
                            adaptiveFormat.copy(
                                url = it.url
                            )
                        }
                    }
                )
            ),
            clientUsed = "TVHTML5+piped",
            poTokenAttempt = poTokenAttempt,
        )
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        val response = innerTube.next(WEB_REMIX, endpoint.videoId, endpoint.playlistId, endpoint.playlistSetVideoId, endpoint.index, endpoint.params, continuation).body<NextResponse>()
        val title = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
            ?: response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.content?.playlistPanelRenderer!!

        val items = playlistPanelRenderer.contents.mapNotNull { content ->
            content.playlistPanelVideoRenderer
                ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                ?.let { it to content.playlistPanelVideoRenderer.selected }
        }
        val songs = items.map { it.first }
        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

        // load automix items
        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = songs + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = currentIndex,
                    endpoint = watchPlaylistEndpoint
                )
            }
        }
        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint
        )
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    ) songs.add(item)

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
        }
        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText
                .trim('♪')
                .trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        }!!
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first { (it as? JsonPrimitive)?.content?.startsWith(VISITOR_DATA_PREFIX) == true }
            .jsonPrimitive.content
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        val response = innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
        // Velqi: el menu a veces llega sin header (respuesta lenta del WebView al
        // navegar); antes el !! lanzaba NullPointerException y se reportaba como
        // fallo de login aunque las cookies estuvieran guardadas.
        val header = response.actions.firstOrNull()?.openPopupAction?.popup?.multiPageMenuRenderer
            ?.header?.activeAccountHeaderRenderer
            ?: error("account menu sin header de cuenta")
        header.toAccountInfo()
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000

    private const val VISITOR_DATA_PREFIX = "Cgt"

    const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
}
