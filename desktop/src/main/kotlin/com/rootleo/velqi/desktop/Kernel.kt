package com.rootleo.velqi.desktop

import com.rootleo.velqi.innertube.YouTube
import com.rootleo.velqi.innertube.models.SongItem
import com.rootleo.velqi.innertube.models.YTItem
import com.rootleo.velqi.innertube.pages.AlbumPage
import com.rootleo.velqi.innertube.pages.HomePage
import com.rootleo.velqi.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade de escritorio sobre el kernel `:innertube` (el mismo que usa la app
 * Android). Expone lo minimo que necesita la UI: buscar canciones y resolver
 * el stream reproducible, replicando la estrategia del kernel (muxed-first).
 */
object Kernel {
    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            .orEmpty()
    }

    /**
     * Elige el mejor stream, replicando el kernel (muxed-first):
     * el muxed (itag 18) sirve el archivo completo con rangos ilimitados;
     * los adaptativos piden PO token / 403 en seek.
     */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val response = YouTube.player(videoId).getOrNull() ?: return@withContext null
        if (response.playabilityStatus.status != "OK") {
            println("[kernel] playability != OK: ${response.playabilityStatus.reason}")
        }
        val sd = response.streamingData ?: return@withContext null
        sd.formats?.firstOrNull { it.url != null }?.url
            ?: sd.adaptiveFormats
                .filter { it.isAudio && it.url != null }
                .maxByOrNull { it.bitrate }
                ?.url
    }

    /** Secciones del Home (carruseles de playlists, albumes, canciones...). */
    suspend fun homeSections(): List<HomePage.Section> = withContext(Dispatchers.IO) {
        YouTube.home().getOrNull()?.sections.orEmpty()
    }

    /** Canciones de una playlist online. */
    suspend fun playlistPage(playlistId: String): PlaylistPage? = withContext(Dispatchers.IO) {
        YouTube.playlist(playlistId).getOrNull()
    }

    /** Canciones de un album online. */
    suspend fun albumPage(browseId: String): AlbumPage? = withContext(Dispatchers.IO) {
        YouTube.album(browseId).getOrNull()
    }

    /** Items de una playlist/album para reproducir en cola. */
    fun pageSongs(page: Any?): List<SongItem> = when (page) {
        is PlaylistPage -> page.songs
        is AlbumPage -> page.songs
        else -> emptyList()
    }
}
