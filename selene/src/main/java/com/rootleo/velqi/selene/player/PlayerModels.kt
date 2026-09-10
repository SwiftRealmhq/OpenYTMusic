package com.rootleo.velqi.selene.player

import kotlinx.serialization.Serializable

/** Cuerpo de la petición POST al endpoint de player. */
@Serializable
data class PlayerRequest(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    @Serializable
    data class Context(
        val client: Client,
    ) {
        @Serializable
        data class Client(
            val clientName: String,
            val clientVersion: String,
            val androidSdkVersion: Int? = null,
            val osVersion: String? = null,
            val gl: String,
            val hl: String,
        )
    }
}

/** Respuesta del endpoint de player (solo los campos que nos interesan). */
@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val videoDetails: VideoDetails? = null,
    val streamingData: StreamingData? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val lengthSeconds: String? = null,
        val author: String? = null,
    )

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat> = emptyList(),
        val expiresInSeconds: String? = null,
    )

    @Serializable
    data class AdaptiveFormat(
        val itag: Int? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val url: String? = null,
        val signatureCipher: String? = null,
        val contentLength: String? = null,
    )
}