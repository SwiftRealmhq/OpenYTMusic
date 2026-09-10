package com.rootleo.velqi.selene.streams

/**
 * Selección de stream de audio. Nuestra propia lógica:
 * preferimos Opus (mejor calidad por bit), luego AAC (mp4a),
 * y dentro de cada codec el de mayor bitrate.
 */
object StreamPicker {

    fun pickBest(streams: List<AudioStream>): AudioStream? =
        ranked(streams).firstOrNull()

    /** Todos los streams ordenados de mejor a peor (para probar en orden). */
    fun ranked(streams: List<AudioStream>): List<AudioStream> =
        streams.sortedWith(streamComparator.reversed())

    fun pickByQuality(streams: List<AudioStream>, quality: Quality): AudioStream? {
        val ranked = ranked(streams)
        return when (quality) {
            Quality.HIGH -> ranked.firstOrNull()
            Quality.MEDIUM -> ranked.getOrNull(ranked.size / 2) ?: ranked.firstOrNull()
            Quality.LOW -> ranked.lastOrNull()
        }
    }

    enum class Quality { HIGH, MEDIUM, LOW }

    private val streamComparator = Comparator<AudioStream> { a, b ->
        val byCodec = codecPreference(a.mimeType).compareTo(codecPreference(b.mimeType))
        if (byCodec != 0) byCodec else a.bitrate.compareTo(b.bitrate)
    }

    private fun codecPreference(mimeType: String): Int = when {
        mimeType.contains("opus") -> 2
        mimeType.contains("mp4a") -> 1
        else -> 0
    }
}