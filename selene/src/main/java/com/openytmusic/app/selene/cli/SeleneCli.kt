package com.openytmusic.app.selene.cli

import com.openytmusic.app.selene.Selene
import com.openytmusic.app.selene.search.AlbumItem
import com.openytmusic.app.selene.search.ArtistItem
import com.openytmusic.app.selene.search.PlaylistItem
import com.openytmusic.app.selene.search.SongItem
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Selene CLI — interfaz de terminal para probar la API.
 *
 * Uso:
 *   selene download <link|videoId> [directorio]
 *   selene player   <link|videoId>
 *   selene search   <query>
 *   selene album    <browseId>
 *   selene artist   <browseId>
 *   selene playlist <playlistId>
 *   selene next     <link|videoId>
 */
fun main(args: Array<String>) = runBlocking {
    val command = args.getOrNull(0)?.lowercase() ?: ""
    when (command) {
        "download" -> download(args.getOrNull(1) ?: "", args.getOrNull(2))
        "player" -> player(args.getOrNull(1) ?: "")
        "search" -> search(args.getOrNull(1) ?: "")
        "album" -> album(args.getOrNull(1) ?: "")
        "artist" -> artist(args.getOrNull(1) ?: "")
        "playlist" -> playlist(args.getOrNull(1) ?: "")
        "next" -> next(args.getOrNull(1) ?: "")
        "help", "-h", "--help", "" -> help()
        else -> {
            println("${RED}❓ Comando desconocido:${RESET} $command\n")
            help()
        }
    }
}

// ============ Comandos ============

private suspend fun download(input: String, dirArg: String?) {
    val videoId = extractVideoId(input) ?: return println("${RED}❌ No pude extraer el video de:${RESET} $input")
    val destinationDir = File(dirArg ?: "selene-downloads").apply { mkdirs() }

    println("${CYAN}⬇️  Descargando $videoId...${RESET}")
    val result = Selene.downloadAudio(videoId, destinationDir) { downloaded, total ->
        val percent = if (total != null && total > 0) downloaded * 100 / total else -1
        val progress = if (percent >= 0) "${percent}%".padStart(4) else " ???"
        val mb = downloaded / 1_048_576.0
        val totalMb = total?.div(1_048_576.0) ?: 0.0
        print("\r${GREEN}⬇️  $progress ${RESET}· ${"%.1f".format(mb)} MB / ${if (total != null) "%.1f".format(totalMb) + " MB" else "?"}   ")
        System.out.flush()
    }
    result.onSuccess { file ->
        println()
        println("${GREEN}✅ Descargado:${RESET} ${file.absolutePath} (${"%.1f".format(file.length() / 1_048_576.0)} MB)")
    }.onFailure {
        println()
        println("${RED}❌ Error:${RESET} ${it.message}")
    }
}

private suspend fun player(input: String) {
    val videoId = extractVideoId(input) ?: return println("${RED}❌ No pude extraer el video de:${RESET} $input")
    Selene.player(videoId).onSuccess { data ->
        println()
        println("${CYAN}🎵 ${data.title}${RESET} — ${data.author ?: "?"}")
        println("   ${DIM}videoId:${RESET} ${data.videoId} · ${DIM}duración:${RESET} ${data.lengthSeconds}s · ${DIM}cliente:${RESET} ${data.clientUsed} · ${DIM}status:${RESET} ${data.playabilityStatus}")
        println("   ${DIM}streams de audio:${RESET} ${data.streams.size}")
        data.streams.sortedByDescending { it.bitrate }.forEach { stream ->
            println("   ${YELLOW}▸${RESET} $stream")
        }
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private suspend fun search(query: String) {
    if (query.isEmpty()) return println("${RED}❌ Uso:${RESET} selene search <query>")
    println("${CYAN}🔍 Buscando \"$query\"...${RESET}")
    Selene.search(query).onSuccess { result ->
        println()
        result.items.take(20).forEachIndexed { i, item ->
            when (item) {
                is SongItem -> println("${i + 1}. ${YELLOW}🎵${RESET} ${item.title} — ${item.artists.joinToString(", ") { it.name }} · ${item.duration}s")
                is AlbumItem -> println("${i + 1}. ${CYAN}💿${RESET} ${item.title} (${item.year ?: "?"})")
                is ArtistItem -> println("${i + 1}. ${CYAN}👤${RESET} ${item.title}")
                is PlaylistItem -> println("${i + 1}. ${CYAN}📋${RESET} ${item.title} · ${item.songCount ?: "?"} canciones")
            }
        }
        println()
        println("${DIM}${result.items.size} resultados${RESET}")
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private suspend fun album(browseId: String) {
    if (browseId.isEmpty()) return println("${RED}❌ Uso:${RESET} selene album <browseId>")
    Selene.album(browseId).onSuccess { page ->
        println()
        println("${CYAN}💿 ${page.album.title}${RESET} — ${page.album.artists?.joinToString(", ") { it.name } ?: "?"} (${page.album.year ?: "?"})")
        page.songs.forEachIndexed { i, song ->
            println("${i + 1}. ${YELLOW}🎵${RESET} ${song.title} · ${song.duration}s")
        }
        println("${DIM}${page.songs.size} canciones · ${page.otherVersions.size} otras versiones${RESET}")
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private suspend fun artist(browseId: String) {
    if (browseId.isEmpty()) return println("${RED}❌ Uso:${RESET} selene artist <browseId>")
    Selene.artist(browseId).onSuccess { page ->
        println()
        println("${CYAN}👤 ${page.artist.title}${RESET}")
        page.sections.forEach { section ->
            println()
            println("${YELLOW}📂 ${section.title}${RESET}")
            section.items.take(6).forEach { item ->
                when (item) {
                    is SongItem -> println("   ${item.title} — ${item.artists.joinToString(", ") { it.name }}")
                    is AlbumItem -> println("   💿 ${item.title} (${item.year ?: "?"})")
                    is ArtistItem -> println("   👤 ${item.title}")
                    is PlaylistItem -> println("   📋 ${item.title}")
                }
            }
            if (section.items.size > 6) println("${DIM}   ... y ${section.items.size - 6} más${RESET}")
        }
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private suspend fun playlist(input: String) {
    val playlistId = extractPlaylistId(input) ?: return println("${RED}❌ No pude extraer la playlist de:${RESET} $input")
    Selene.playlist(playlistId).onSuccess { page ->
        println()
        println("${CYAN}📋 ${page.playlist.title}${RESET} · ${page.playlist.songCount ?: "?"} canciones")
        page.songs.take(15).forEachIndexed { i, song ->
            println("${i + 1}. ${YELLOW}🎵${RESET} ${song.title} — ${song.artists.joinToString(", ") { it.name }} · ${song.duration}s")
        }
        println("${DIM}${page.songs.size} canciones cargadas · continuación: ${page.continuation != null}${RESET}")
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private suspend fun next(input: String) {
    val videoId = extractVideoId(input) ?: return println("${RED}❌ No pude extraer el video de:${RESET} $input")
    Selene.nextForVideo(videoId).onSuccess { queue ->
        println()
        println("${CYAN}🎧 ${queue.title ?: "Cola"}${RESET} — ${queue.items.size} canciones")
        queue.items.forEachIndexed { i, song ->
            val marker = if (i == queue.currentIndex) "▶" else " "
            println("$marker ${song.title} — ${song.artists.joinToString(", ") { it.name }} · ${song.duration}s")
        }
        println()
        println("${DIM}letras: ${queue.lyricsBrowseId != null} · relacionado: ${queue.relatedBrowseId != null} · automix: ${queue.automixEndpoint != null}${RESET}")
    }.onFailure { println("${RED}❌ Error:${RESET} ${it.message}") }
}

private fun help() {
    println(
        """
        ${CYAN}🌙 Selene CLI${RESET} — API de YouTube Music de Velqi Luna

        ${YELLOW}Uso:${RESET} selene <comando> [argumentos]

          ${GREEN}download${RESET} <link|videoId> [directorio]   Descarga el audio (mejor stream)
          ${GREEN}player${RESET}   <link|videoId>                Muestra metadatos + streams
          ${GREEN}search${RESET}   <query>                       Busca canciones/álbumes/artistas
          ${GREEN}album${RESET}    <browseId>                    Página de álbum
          ${GREEN}artist${RESET}   <browseId>                    Página de artista
          ${GREEN}playlist${RESET} <playlistId|link>             Página de playlist
          ${GREEN}next${RESET}     <link|videoId>                Cola de reproducción

        ${DIM}Links aceptados: youtu.be/ID · youtube.com/watch?v=ID · music.youtube.com/...${RESET}
        """.trimIndent()
    )
}

// ============ Utilidades ============

private val VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

private fun extractVideoId(input: String): String? {
    val trimmed = input.trim()
    if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

    return when {
        trimmed.contains("youtu.be/") ->
            trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('&').take(11).takeIf { VIDEO_ID_REGEX.matches(it) }

        trimmed.contains("v=") ->
            trimmed.substringAfter("v=").substringBefore('&').take(11).takeIf { VIDEO_ID_REGEX.matches(it) }

        else -> null
    }
}

private fun extractPlaylistId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.startsWith("RD") || trimmed.startsWith("PL") || trimmed.startsWith("OLAK5uy_")) return trimmed
    return if (trimmed.contains("list=")) {
        trimmed.substringAfter("list=").substringBefore('&')
    } else null
}

// ============ Colores ANSI ============

private const val RESET = "\u001B[0m"
private const val GREEN = "\u001B[32m"
private const val YELLOW = "\u001B[33m"
private const val CYAN = "\u001B[36m"
private const val RED = "\u001B[31m"
private const val DIM = "\u001B[2m"