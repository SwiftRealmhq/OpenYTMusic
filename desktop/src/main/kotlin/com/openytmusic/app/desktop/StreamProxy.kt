package com.openytmusic.app.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL

/**
 * Mini proxy HTTP local: mpv pide `http://127.0.0.1:<port>/stream` y este
 * proxy reenvia la peticion a googlevideo con el User-Agent del cliente
 * ANDROID y el header Range de mpv (como hace OkHttp en la app Android).
 *
 * Por que: googlevideo responde 403 a un GET abierto de mpv (sin Range),
 * pero 206/200 a rangos con el UA correcto. El proxy resuelve ambos.
 */
class StreamProxy(private val targetUrl: String) : AutoCloseable {
    private val server: HttpServer

    init {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stream") { exchange -> handle(exchange) }
        server.executor = null // single-threaded es suficiente para un PoC
        server.start()
    }

    val port: Int
        get() = server.address.port

    val baseUrl: String
        get() = "http://127.0.0.1:$port/stream"

    private fun handle(exchange: HttpExchange) {
        try {
            // Si mpv abre sin Range (GET abierto), googlevideo responde 403.
            // Forzamos un rango abierto desde 0 para que responda 206.
            val range = exchange.requestHeaders.getFirst("Range") ?: "bytes=0-"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty(
                "User-Agent",
                "com.google.android.youtube/20.01.35 (Linux; U; Android 13) gzip",
            )
            conn.setRequestProperty("Range", range)
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            val isError = code >= 400
            val stream = if (isError) conn.errorStream else conn.inputStream

            // Reenviar status + headers utiles
            exchange.responseHeaders.set("Content-Type", conn.contentType ?: "video/mp4")
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            val contentLength = conn.contentLengthLong
            if (contentLength > 0) {
                exchange.responseHeaders.set("Content-Length", contentLength.toString())
            }
            exchange.sendResponseHeaders(code, if (contentLength > 0) contentLength else 0)

            if (stream != null) {
                stream.use { input ->
                    exchange.responseBody.use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            try {
                if (exchange.responseCode == -1) {
                    val msg = e.message ?: "proxy error"
                    exchange.sendResponseHeaders(500, msg.toByteArray().size.toLong())
                    exchange.responseBody.write(msg.toByteArray())
                }
            } catch (_: Exception) {
            }
        } finally {
            exchange.close()
        }
    }

    override fun close() {
        server.stop(0)
    }
}
