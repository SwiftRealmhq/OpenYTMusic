package com.openytmusic.app.selene.browse

import com.openytmusic.app.selene.internal.webRemixContext
import com.openytmusic.app.selene.internal.webRemixHeaders
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Capa HTTP de browse: páginas de álbum, artista, playlist y continuaciones.
 */
class BrowseFetcher(private val client: HttpClient) {

    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): BrowseResponse = client.post(BROWSE_URL) {
        webRemixHeaders()
        if (continuation != null) {
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
            parameter("type", "next")
        }
        setBody(
            BrowseRequest(
                context = webRemixContext(),
                browseId = browseId,
                params = params,
            )
        )
    }.body()

    private companion object {
        const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse"
    }
}