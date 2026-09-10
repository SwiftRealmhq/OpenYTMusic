package com.rootleo.velqi.selene

import com.rootleo.velqi.selene.Selene.SearchFilter
import com.rootleo.velqi.selene.search.AlbumItem
import com.rootleo.velqi.selene.search.ArtistItem
import com.rootleo.velqi.selene.search.PlaylistItem
import com.rootleo.velqi.selene.search.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    @Test
    fun `Buscar canciones filtradas`() = runBlocking {
        val result = Selene.search("YOASOBI", SearchFilter.SONG).getOrThrow()

        println("🎵 ${result.items.size} canciones:")
        result.items.take(5).forEach { item ->
            val song = item as SongItem
            println("   ${song.title} — ${song.artists.joinToString(", ") { it.name }} · ${song.duration}s")
        }

        assertTrue("Debe haber canciones", result.items.isNotEmpty())
        assertTrue("Todas deben ser canciones", result.items.all { it is SongItem })
        val first = result.items.first() as SongItem
        assertTrue("La canción debe tener artistas", first.artists.isNotEmpty())
        assertTrue("La canción debe tener duración", first.duration != null)
    }

    @Test
    fun `Buscar todo mezclado`() = runBlocking {
        val result = Selene.search("Daft Punk").getOrThrow()

        val byType = result.items.groupBy { it::class.simpleName }.mapValues { it.value.size }
        println("🔀 ${result.items.size} items: $byType")

        assertTrue("Debe haber resultados mezclados", result.items.isNotEmpty())
    }

    @Test
    fun `Continuacion de busqueda`() = runBlocking {
        var result = Selene.search("YOASOBI", SearchFilter.SONG).getOrThrow()
        assertTrue("La primera página debe tener continuation", result.continuation != null)

        var pages = 1
        while (result.continuation != null && pages < 3) {
            val previous = result.items.size
            result = Selene.searchContinuation(result.continuation!!).getOrThrow()
            pages++
            println("📄 Página $pages: ${result.items.size} items (página anterior: $previous)")
            assertTrue("Cada página debe traer items", result.items.isNotEmpty())
        }
    }

    @Test
    fun `Sugerencias de busqueda`() = runBlocking {
        val suggestions = Selene.searchSuggestions("yoa").getOrThrow()
        println("💡 Sugerencias: ${suggestions.queries.joinToString(" | ")}")
        assertTrue("Debe haber sugerencias", suggestions.queries.isNotEmpty())
    }

    @Test
    fun `Buscar albumes y artistas filtrados`() = runBlocking {
        val albums = Selene.search("Daft Punk", SearchFilter.ALBUM).getOrThrow()
        println("💿 Álbumes (${albums.items.size}):")
        albums.items.take(3).forEach { println("   ${(it as AlbumItem).title} (${it.year ?: "?"})") }
        assertTrue(albums.items.all { it is AlbumItem })

        val artists = Selene.search("Daft Punk", SearchFilter.ARTIST).getOrThrow()
        println("👤 Artistas (${artists.items.size}):")
        artists.items.take(3).forEach { println("   ${(it as ArtistItem).title}") }
        assertTrue(artists.items.all { it is ArtistItem })
    }

    @Test
    fun `Buscar playlists filtradas`() = runBlocking {
        val playlists = Selene.search("rock", SearchFilter.PLAYLIST_FEATURED).getOrThrow()
        println("📋 Playlists (${playlists.items.size}):")
        playlists.items.take(5).forEach {
            val p = it as PlaylistItem
            println("   ${p.title} · ${p.author ?: "?"} · ${p.songCount ?: "?"} canciones")
        }
        assertTrue("Debe haber playlists", playlists.items.isNotEmpty())
        assertTrue(playlists.items.all { it is PlaylistItem })
    }
}