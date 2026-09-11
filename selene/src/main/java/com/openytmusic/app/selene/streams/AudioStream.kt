package com.openytmusic.app.selene.streams

/**
 * Un stream de audio listo para reproducir (URL directa de Googlevideo).
 */
data class AudioStream(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    val contentLength: Long? = null,
) {
    val codec: String
        get() = mimeType.substringAfter("codecs=\"").substringBefore('"')

    val container: String
        get() = mimeType.substringBefore(';')

    override fun toString(): String =
        "$container ($codec) · ${bitrate / 1000} kbps · itag $itag"
}