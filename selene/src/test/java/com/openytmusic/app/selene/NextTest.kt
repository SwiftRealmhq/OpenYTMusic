package com.openytmusic.app.selene

import com.openytmusic.app.selene.queue.UpNextEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextTest {

    @Test
    fun `Cola de un video suelto`() = runBlocking {
        val queue = Selene.nextForVideo("dQw4w9WgXcQ").getOrThrow()

        println("🎧 ${queue.title}")
        println("   canciones: ${queue.items.size} | actual: ${queue.currentIndex} | cont: ${queue.continuation != null}")
        queue.items.take(5).forEachIndexed { i, song ->
            println("   ${if (i == queue.currentIndex) "▶" else " "} ${song.title} — ${song.artists.joinToString(", ") { it.name }} · ${song.duration}s")
        }

        assertTrue("Debe haber cola", queue.items.isNotEmpty())
        assertEquals("La actual debe ser la primera", 0, queue.currentIndex)
        assertNotNull("Tab de letras", queue.lyricsBrowseId)
        assertNotNull("Tab de relacionado", queue.relatedBrowseId)
        assertNotNull("Automix disponible", queue.automixEndpoint)
    }

    @Test
    fun `Cola de una playlist con continuation`() = runBlocking {
        val endpoint = UpNextEndpoint(
            videoId = "CdqoNKCCt7A",
            playlistId = "RDCLAK5uy_lvHI2Z7dSfpD5g8wvmePjWPfYwq5IgkLo",
        )
        val queue = Selene.next(endpoint).getOrThrow()

        println("🎧 ${queue.title} — ${queue.items.size} canciones")
        queue.items.take(3).forEach {
            println("   ${it.title} — ${it.artists.joinToString(", ") { a -> a.name }} · ${it.duration}s")
        }

        assertTrue("Cola larga de la playlist", queue.items.size >= 20)
        assertNotNull("Con continuación", queue.continuation)
        assertNotNull("Canción actual detectada", queue.currentIndex)
    }

    @Test
    fun `Continuacion de la cola`() = runBlocking {
        val endpoint = UpNextEndpoint(
            videoId = "CdqoNKCCt7A",
            playlistId = "RDCLAK5uy_lvHI2Z7dSfpD5g8wvmePjWPfYwq5IgkLo",
        )
        var queue = Selene.next(endpoint).getOrThrow()
        var pages = 1
        while (queue.continuation != null && pages < 3) {
            queue = Selene.next(endpoint, queue.continuation!!).getOrThrow()
            println("📄 Página ${pages + 1}: ${queue.items.size} canciones")
            assertTrue("Cada página trae canciones", queue.items.isNotEmpty())
            pages++
        }
    }

    @Test
    fun `Automix extiende la cola`() = runBlocking {
        val queue = Selene.nextForVideo("dQw4w9WgXcQ").getOrThrow()
        val automix = queue.automixEndpoint
        if (automix != null) {
            val extended = Selene.next(automix).getOrThrow()
            println("🔁 Automix: ${extended.title} — ${extended.items.size} canciones")
            assertTrue("El automix trae canciones", extended.items.isNotEmpty())
        } else {
            println("ℹ️ Sin automix disponible")
        }
    }
}