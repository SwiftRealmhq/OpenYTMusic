package com.openytmusic.app.selene.player

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent

/**
 * Capa HTTP del player: construye la petición y envía el JSON nosotros
 * mismos. Sin cabeceras de más: solo Content-Type + User-Agent del cliente.
 */
class PlayerFetcher(private val client: HttpClient) {

    suspend fun fetch(
        profile: ClientProfile,
        videoId: String,
        playlistId: String? = null,
    ): PlayerResponse {
        val request = PlayerRequest(
            context = PlayerRequest.Context(
                client = PlayerRequest.Context.Client(
                    clientName = profile.clientName,
                    clientVersion = profile.clientVersion,
                    androidSdkVersion = profile.androidSdkVersion,
                    osVersion = profile.osVersion,
                    gl = "US",
                    hl = "en",
                )
            ),
            videoId = videoId,
            playlistId = playlistId,
        )
        return client.post(PLAYER_URL) {
            contentType(ContentType.Application.Json)
            userAgent(profile.userAgent)
            setBody(request)
        }.body()
    }

    private companion object {
        const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
    }
}