package com.rootleo.velqi.selene.search

import com.rootleo.velqi.selene.internal.webRemixContext
import com.rootleo.velqi.selene.internal.webRemixHeaders
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Capa HTTP de búsqueda: construye la petición con el perfil WEB_REMIX
 * (YouTube Music web) y envía el JSON nosotros mismos.
 */
class SearchFetcher(private val client: HttpClient) {

    suspend fun search(
        query: String?,
        params: String?,
        continuation: String?,
    ): SearchResponse = client.post(SEARCH_URL) {
        webRemixHeaders()
        if (continuation != null) {
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
        }
        setBody(
            SearchRequest(
                context = webRemixContext(),
                query = query,
                params = params,
            )
        )
    }.body()

    suspend fun suggestions(input: String): SuggestionsResponse =
        client.post(SUGGESTIONS_URL) {
            webRemixHeaders()
            setBody(SuggestionsRequest(context = webRemixContext(), input = input))
        }.body()

    private companion object {
        const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search"
        const val SUGGESTIONS_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions"
    }
}