package com.rootleo.velqi.selene

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTest {

    @Test
    fun `Pagina de album completa`() = runBlocking {
        val page = Selene.album("MPREb_oNAdr9eUOfS").getOrThrow()

        println("💿 ${page.album.title} — ${page.album.artists?.joinToString(", ") { it.name }} (${page.album.year})")
        println("   canciones: ${page.songs.size} | otras versiones: ${page.otherVersions.size}")
        page.songs.take(3).forEach {
            println("   🎵 ${it.title} — ${it.artists.joinToString(", ") { a -> a.name }} · ${it.duration}s")
        }

        assertEquals("THE BOOK 2", page.album.title)
        assertTrue("Debe tener canciones", page.songs.isNotEmpty())
        val first = page.songs.first()
        assertTrue("Canciones con artistas heredados del álbum", first.artists.isNotEmpty())
        assertTrue("Canciones con portada heredada del álbum", first.thumbnail.isNotEmpty())
        assertTrue("Canciones con duración", first.duration != null)
    }

    @Test
    fun `Pagina de artista con secciones`() = runBlocking {
        val page = Selene.artist("UCI6B8NkZKqlFWoiC_xE-hzA").getOrThrow()

        println("👤 ${page.artist.title}")
        println("   secciones: ${page.sections.size}")
        page.sections.take(5).forEach { section ->
            println("   📂 ${section.title} (${section.items.size} items)")
        }

        assertEquals("YOASOBI", page.artist.title)
        assertTrue("Debe tener secciones", page.sections.isNotEmpty())
        assertTrue("Alguna sección con items", page.sections.any { it.items.isNotEmpty() })
    }

    @Test
    fun `Pagina de playlist con canciones`() = runBlocking {
        val page = Selene.playlist("RDCLAK5uy_lvHI2Z7dSfpD5g8wvmePjWPfYwq5IgkLo").getOrThrow()

        println("📋 ${page.playlist.title} — ${page.playlist.author ?: "?"} · ${page.playlist.songCount ?: "?"} canciones")
        println("   canciones: ${page.songs.size} | continuation: ${page.continuation != null}")
        page.songs.take(3).forEach {
            println("   🎵 ${it.title} — ${it.artists.joinToString(", ") { a -> a.name }} · ${it.duration}s")
        }

        assertEquals("'80s Rock", page.playlist.title)
        assertTrue("Debe tener canciones", page.songs.isNotEmpty())
        assertTrue("Debe tener songCount", page.playlist.songCount != null)
        val first = page.songs.first()
        assertTrue("Canciones con artistas", first.artists.isNotEmpty())
        assertTrue("Canciones con portada", first.thumbnail.isNotEmpty())
        assertTrue("Canciones con duración", first.duration != null)
    }

    @Test
    fun `Continuacion de playlist no rompe`() = runBlocking {
        val page = Selene.playlist("RDCLAK5uy_lvHI2Z7dSfpD5g8wvmePjWPfYwq5IgkLo").getOrThrow()
        if (page.continuation != null) {
            val next = Selene.playlistContinuation(page.continuation!!).getOrThrow()
            println("📄 Continuación: ${next.songs.size} canciones | hay más: ${next.continuation != null}")
            assertNotNull(next)
        } else {
            println("ℹ️ Sin continuation disponible")
        }
    }
}