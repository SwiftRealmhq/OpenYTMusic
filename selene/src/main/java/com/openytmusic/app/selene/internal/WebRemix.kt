package com.openytmusic.app.selene.internal

import com.openytmusic.app.selene.model.ClientContext
import com.openytmusic.app.selene.model.ClientInfo
import com.openytmusic.app.selene.player.ClientProfile
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent

/** Cabeceras estándar para los endpoints de YouTube Music (WEB_REMIX). */
internal fun HttpRequestBuilder.webRemixHeaders() {
    val client = ClientProfile.WEB_REMIX
    contentType(ContentType.Application.Json)
    userAgent(client.userAgent)
    header("X-YouTube-Client-Name", client.clientName)
    header("X-YouTube-Client-Version", client.clientVersion)
    header("x-origin", "https://music.youtube.com")
    client.referer?.let { header("Referer", it) }
    parameter("key", client.apiKey!!)
    parameter("prettyPrint", false)
}

internal fun webRemixContext() = ClientContext(
    client = ClientInfo(
        clientName = "WEB_REMIX",
        clientVersion = "1.20220606.03.00",
        gl = "US",
        hl = "en",
    )
)