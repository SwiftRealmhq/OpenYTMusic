package com.openytmusic.app.selene.queue

import com.openytmusic.app.selene.internal.webRemixHeaders
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Capa HTTP del endpoint next: la cola de reproducción (up next).
 */
class QueueFetcher(private val client: HttpClient) {

    suspend fun next(request: NextRequest): NextResponse =
        client.post(NEXT_URL) {
            webRemixHeaders()
            setBody(request)
        }.body()

    private companion object {
        const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next"
    }
}