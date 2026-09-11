package com.openytmusic.app.desktop

/**
 * Reproductor de escritorio: gestiona el ciclo de vida de mpv + el proxy local
 * que sirve el stream con Range/UA correctos. API en milisegundos para la UI.
 */
class DesktopPlayer {
    private val mpv = MpvProcess()
    private var proxy: StreamProxy? = null

    @Volatile
    var ready: Boolean = false
        private set

    fun init(): String {
        if (ready) return "ok"
        val res = mpv.start()
        if (res == "ok") ready = true
        return res
    }

    /** Reproduce una URL de stream ya resuelta por el kernel (via proxy local). */
    fun playUrl(url: String) {
        if (!ready) init()
        proxy?.close()
        val newProxy = StreamProxy(url)
        proxy = newProxy
        mpv.loadUrl(newProxy.baseUrl)
        mpv.play()
    }

    fun pause() = mpv.pause()

    fun resume() = mpv.play()

    fun seekToMs(ms: Long) = mpv.seek(ms / 1000.0)

    fun currentTimeMs(): Long = ((mpv.currentTime() ?: 0.0) * 1000).toLong()

    fun durationMs(): Long = ((mpv.duration() ?: 0.0) * 1000).toLong()

    fun stop() = mpv.stop()

    fun close() {
        mpv.close()
        proxy?.close()
        proxy = null
        ready = false
    }
}
