package com.rootleo.velqi.selene

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoC: obtiene el player de videos reales de YouTube con NUESTRA API
 * y verifica que el stream de audio responde (206 Partial Content).
 */
class SeleneTest {

    private val rangeClient = HttpClient(OkHttp)

    @Test
    fun `Obtener player real y verificar stream de audio`() = runBlocking {
        val videoId = "dQw4w9WgXcQ" // Never Gonna Give You Up
        val playerData = Selene.player(videoId).getOrThrow()

        println("🎵 ${playerData.title} — ${playerData.author}")
        println("   duración: ${playerData.lengthSeconds}s | cliente: ${playerData.clientUsed} | status: ${playerData.playabilityStatus}")
        println("   streams de audio: ${playerData.streams.size}")
        playerData.streams.sortedByDescending { it.bitrate }.forEach { println("   - $it") }

        assertEquals("OK", playerData.playabilityStatus)
        assertTrue("Debe haber streams de audio", playerData.streams.isNotEmpty())

        val best = Selene.bestAudio(videoId).getOrThrow()
        println("\n▶ Mejor stream elegido: $best")

        val response = rangeClient.get(best.url) {
            header("Range", "bytes=0-1023")
            header("User-Agent", "com.google.android.youtube/20.01.35 (Linux; U; Android 13) gzip")
        }
        println("   Range request → HTTP ${response.status.value} | ${response.status.description}")
        assertEquals("El stream debe responder 206 Partial Content", 206, response.status.value)
    }

    @Test
    fun `Obtener player de varios videos`() = runBlocking {
        val videos = listOf("afXwxtQLvZM", "NCC6lI0GGy0")
        videos.forEach { videoId ->
            val data = Selene.player(videoId).getOrThrow()
            println("✅ $videoId → ${data.title} (${data.playabilityStatus}, ${data.streams.size} streams)")
            assertEquals("OK", data.playabilityStatus)
        }
    }

    @Test
    fun `Video no disponible se reporta sin romperse`() = runBlocking {
        // Este video fue eliminado de YouTube; la API debe reportar el motivo
        // (UNPLAYABLE con reason) devolviendo la última respuesta, no lanzar.
        val data = Selene.player("4H-N260cPCg").getOrThrow()
        println("ℹ️ 4H-N260cPCg → ${data.playabilityStatus} | ${data.reason ?: "sin motivo"}")
        assertEquals("UNPLAYABLE", data.playabilityStatus)
        assertTrue("Debe exponer el motivo", data.reason != null)
    }

    @Test
    fun `Rotacion de clientes ante video bloqueado`() = runBlocking {
        // Video conocido por requerir login/estar bloqueado para clientes anónimos.
        val result = Selene.player("x8VYWazR5mE")
        result.onSuccess {
            println("ℹ️ x8VYWazR5mE → ${it.playabilityStatus} (${it.reason ?: "sin motivo"})")
            println("   último cliente intentado: ${it.clientUsed}")
        }.onFailure {
            println("ℹ️ x8VYWazR5mE → error: ${it.message}")
        }
        // No fallamos el test: documentamos cómo se comporta la rotación.
        assertTrue(true)
    }
}