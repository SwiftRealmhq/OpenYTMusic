package com.rootleo.velqi.selene

import com.rootleo.velqi.selene.browse.AlbumPage
import com.rootleo.velqi.selene.browse.ArtistPage
import com.rootleo.velqi.selene.browse.BrowseFetcher
import com.rootleo.velqi.selene.browse.BrowseParser
import com.rootleo.velqi.selene.browse.PlaylistContinuationPage
import com.rootleo.velqi.selene.browse.PlaylistPage
import com.rootleo.velqi.selene.internal.webRemixContext
import com.rootleo.velqi.selene.player.ClientProfile
import com.rootleo.velqi.selene.player.PlayerFetcher
import com.rootleo.velqi.selene.player.PlayerResponse
import com.rootleo.velqi.selene.queue.NextRequest
import com.rootleo.velqi.selene.queue.NextResult
import com.rootleo.velqi.selene.queue.QueueFetcher
import com.rootleo.velqi.selene.queue.QueueParser
import com.rootleo.velqi.selene.queue.UpNextEndpoint
import com.rootleo.velqi.selene.search.SearchFetcher
import com.rootleo.velqi.selene.search.SearchParser
import com.rootleo.velqi.selene.search.SearchResult
import com.rootleo.velqi.selene.search.SearchSuggestions
import com.rootleo.velqi.selene.streams.AudioStream
import com.rootleo.velqi.selene.streams.StreamPicker
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Resultado limpio de un player: metadatos del video + streams de audio.
 */
data class PlayerData(
    val videoId: String,
    val title: String,
    val author: String?,
    val lengthSeconds: Int,
    val playabilityStatus: String,
    val reason: String?,
    val streams: List<AudioStream>,
    val expiresInSeconds: Int?,
    /** Cliente que respondió OK (ANDROID, ANDROID_VR, IOS...). */
    val clientUsed: String,
)

/**
 * Selene — nuestra propia API de YouTube, escrita desde cero.
 * Punto de entrada único; todas las funciones devuelven Result<T>.
 */
object Selene {

    private val client = createClient()
    private val fetcher = PlayerFetcher(client)
    private val searchFetcher = SearchFetcher(client)
    private val browseFetcher = BrowseFetcher(client)
    private val queueFetcher = QueueFetcher(client)

    /** Orden de rotación: si un cliente es bloqueado, probamos el siguiente. */
    private val profiles = listOf(
        ClientProfile.ANDROID,
        ClientProfile.ANDROID_VR,
        ClientProfile.IOS,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }
            )
        }
    }

    /**
     * Obtiene el player de un video. Rota entre clientes hasta conseguir
     * playabilityStatus OK; si ninguno lo logra, devuelve la última
     * respuesta (con su motivo de error) para que el llamador decida.
     */
    suspend fun player(videoId: String, playlistId: String? = null): Result<PlayerData> = runCatching {
        var lastResponse: PlayerResponse? = null
        var lastError: Throwable? = null

        for (profile in profiles) {
            try {
                val response = fetcher.fetch(profile, videoId, playlistId)
                lastResponse = response
                if (response.playabilityStatus?.status == "OK") {
                    return@runCatching response.toPlayerData(profile.clientName)
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }
        lastResponse?.toPlayerData(profiles.last().clientName)
            ?: throw (lastError ?: IllegalStateException("No player data"))
    }

    /** Atajo: el mejor stream de audio de un video. */
    suspend fun bestAudio(videoId: String, playlistId: String? = null): Result<AudioStream> =
        player(videoId, playlistId).map { playerData ->
            StreamPicker.pickBest(playerData.streams)
                ?: error("El video no expone streams de audio (${playerData.reason ?: playerData.playabilityStatus})")
        }

    /**
     * Descarga el mejor stream de audio de un video a [destinationDir].
     * El archivo se nombra con el título del video (sanitizado).
     *
     * Robusto ante las dos protecciones de YouTube:
     *  1. Exige el header Range cerrado (sin Range devuelve 403), por eso
     *     descargamos por chunks con `bytes=offset-end`.
     *  2. En IPs de reputación dudosa (datacenter/VPN) o videos de ciertos
     *     sellos solo sirve los primeros ~1 MB del stream. Si un chunk
     *     recibe 403 más allá del primer MB, probamos el siguiente formato
     *     de audio; si todos están capados, fallamos con un mensaje claro.
     */
    suspend fun downloadAudio(
        videoId: String,
        destinationDir: File = File("."),
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        val playerData = player(videoId).getOrThrow()

        // Probamos los streams de mejor a peor: si uno está capado, el
        // siguiente podría no estarlo.
        val candidates = StreamPicker.ranked(playerData.streams)
            .filter { it.contentLength == null || it.contentLength > 0 }
        if (candidates.isEmpty()) {
            error("El video no expone streams de audio (${playerData.reason ?: playerData.playabilityStatus})")
        }

        var lastError: Throwable? = null
        var cappedMessage: String? = null
        for (stream in candidates) {
            try {
                return@runCatching downloadStream(playerData, stream, destinationDir, onProgress)
            } catch (e: StreamCappedException) {
                cappedMessage = e.message
                lastError = e
            } catch (e: StreamHttpException) {
                lastError = e
            }
        }
        // Si algún formato llegó a descargar ~1 MB y se cortó, el problema
        // es el límite de IP: lo reportamos con prioridad sobre el error HTTP.
        error(cappedMessage ?: "No se pudo descargar ningún stream de audio: ${lastError?.message}")
    }

    private suspend fun downloadStream(
        playerData: PlayerData,
        stream: AudioStream,
        destinationDir: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File {
        val extension = if (stream.mimeType.contains("webm")) "webm" else "m4a"
        val safeTitle = playerData.title
            .replace(Regex("""[^\wáéíóúñüÁÉÍÓÚÑÜ .-]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)
        val file = File(destinationDir, "$safeTitle.$extension")
        val total = stream.contentLength

        try {
            java.io.FileOutputStream(file).use { output ->
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                var chunkSize = LARGE_CHUNK
                while (true) {
                    val chunkEnd = when {
                        total != null -> minOf(offset + chunkSize - 1, total - 1)
                        else -> offset + chunkSize - 1
                    }
                    val received = try {
                        fetchChunk(stream.url, offset, chunkEnd, buffer, output, onProgress, total)
                    } catch (e: StreamHttpException) {
                        when {
                            e.status == 416 -> break // rango fuera del archivo: ya terminamos
                            e.status == 403 && chunkSize > FALLBACK_CHUNK -> {
                                // El 403 puede ser por un Range demasiado grande:
                                // reintentamos el mismo offset con un chunk chico.
                                chunkSize = FALLBACK_CHUNK
                                continue
                            }
                            e.status == 403 && offset > 0 -> throw StreamCappedException()
                            else -> throw e
                        }
                    }
                    offset += received
                    if (total != null && offset >= total) break
                    if (received < chunkSize) break // no queda más data
                }
            }
        } catch (e: Throwable) {
            file.delete() // nunca dejar archivos truncados
            throw e
        }
        // Validación final: si el servidor nos dio el tamaño total, el archivo
        // debe coincidir (detecta bucles de descarga o respuestas raras).
        if (total != null && file.length() != total) {
            file.delete()
            error("Descarga incompleta: se esperaban $total bytes pero se recibieron ${file.length()}")
        }
        return file
    }

    private suspend fun fetchChunk(
        url: String,
        offset: Long,
        chunkEnd: Long,
        buffer: ByteArray,
        output: java.io.FileOutputStream,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        total: Long?,
    ): Long = try {
        client.prepareGet(url) {
            header("User-Agent", ClientProfile.ANDROID.userAgent)
            header("Range", "bytes=$offset-$chunkEnd")
        }.execute { response ->
            val status = response.status.value
            if (status !in 200..299) throw StreamHttpException(status)
            val channel = response.bodyAsChannel()
            var count = 0L
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                output.write(buffer, 0, read)
                count += read
                onProgress(offset + count, total)
            }
            count
        }
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        // expectSuccess=true puede lanzar antes de nuestro chequeo de status
        if (e.response.status.value == 416) 0L else throw StreamHttpException(e.response.status.value)
    }

    /** Error HTTP del servidor de streams (Range rechazado, IP limitada...). */
    private class StreamHttpException(val status: Int) : Exception("HTTP $status")

    /** El stream solo sirve los primeros ~1 MB (protección anti-descarga de música). */
    private class StreamCappedException : Exception(
        "Este video está protegido por su sello discográfico: YouTube solo sirve " +
            "~1 MB de cada stream (medida anti-descarga de música, aplica a VEVO/" +
            "sellos/canales automáticos desde cualquier IP). Probá con otro video " +
            "o ejecutá el diagnóstico: bash selene/scripts/diag-cap.sh"
    )

    // ============ Búsqueda ============

    /**
     * Busca en YouTube Music. Sin filtro devuelve la mezcla completa
     * ("Todo"); con filtro devuelve el shelf tipado + continuation.
     */
    suspend fun search(query: String, filter: String? = null): Result<SearchResult> = runCatching {
        if (filter == null) {
            SearchParser.parseAll(searchFetcher.search(query, null, null))
        } else {
            SearchParser.parseFiltered(searchFetcher.search(query, filter, null))
        }
    }

    /** Pagina una búsqueda filtrada a partir de su continuation. */
    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        SearchParser.parseContinuation(searchFetcher.search(null, null, continuation))
    }

    /** Sugerencias de autocompletado para el texto de búsqueda. */
    suspend fun searchSuggestions(input: String): Result<SearchSuggestions> = runCatching {
        val response = searchFetcher.suggestions(input)
        SearchSuggestions(
            queries = response.contents.orEmpty().flatMap { section ->
                section.searchSuggestionsSectionRenderer?.contents.orEmpty().mapNotNull { content ->
                    content.searchSuggestionRenderer?.suggestion?.runs
                        ?.joinToString(separator = "") { it.text.orEmpty() }
                }
            }
        )
    }

    // ============ Browse (páginas de contenido) ============

    /** Página completa de un álbum: metadatos + canciones + otras versiones. */
    suspend fun album(browseId: String): Result<AlbumPage> = runCatching {
        BrowseParser.parseAlbum(browseFetcher.browse(browseId = browseId), browseId)
            ?: error("No se pudo parsear el álbum $browseId")
    }

    /** Página completa de un artista: header + secciones (canciones, álbumes...). */
    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        BrowseParser.parseArtist(browseFetcher.browse(browseId = browseId), browseId)
            ?: error("No se pudo parsear el artista $browseId")
    }

    /** Página completa de una playlist: metadatos + canciones + continuación. */
    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        BrowseParser.parsePlaylist(
            browseFetcher.browse(browseId = "VL$playlistId"),
            playlistId,
        ) ?: error("No se pudo parsear la playlist $playlistId")
    }

    /** Pagina las canciones de una playlist a partir de su continuation. */
    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> =
        runCatching {
            BrowseParser.parsePlaylistContinuation(browseFetcher.browse(continuation = continuation))
        }

    // ============ Cola de reproducción (next) ============

    /**
     * Obtiene la cola (up next) de una canción o playlist.
     * - Con [continuation]: pagina la misma cola.
     * - Sin ella: devuelve la cola inicial + [NextResult.automixEndpoint]
     *   para extender la reproducción cuando termine.
     */
    suspend fun next(
        endpoint: UpNextEndpoint,
        continuation: String? = null,
    ): Result<NextResult> = runCatching {
        val response = queueFetcher.next(
            NextRequest(
                context = webRemixContext(),
                videoId = endpoint.videoId,
                playlistId = endpoint.playlistId,
                playlistSetVideoId = endpoint.playlistSetVideoId,
                index = endpoint.index,
                params = endpoint.params,
                continuation = continuation,
            )
        )
        if (continuation != null) {
            QueueParser.parseContinuation(response)
        } else {
            QueueParser.parseInitial(response)
        }
    }

    /** Atajo: cola de un video sin playlist. */
    suspend fun nextForVideo(videoId: String): Result<NextResult> =
        next(UpNextEndpoint(videoId = videoId))

    /** Filtros de búsqueda predefinidos (params de InnerTube). */
    object SearchFilter {
        const val SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val VIDEO = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        const val ALBUM = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTIST = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val PLAYLIST_FEATURED = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"
        const val PLAYLIST_COMMUNITY = "EgeKAQQoAEABagoQAxAEEAoQCRAF"
    }

    /** Chunk grande para IPs normales (rápido, pocos requests). */
    private const val LARGE_CHUNK = 4L * 1024 * 1024

    /** Chunk chico de respaldo: bajo el límite de ~1 MB que YouTube sirve a IPs marcadas. */
    private const val FALLBACK_CHUNK = 900L * 1024

    private fun PlayerResponse.toPlayerData(clientUsed: String) = PlayerData(
        videoId = videoDetails?.videoId.orEmpty(),
        title = videoDetails?.title.orEmpty(),
        author = videoDetails?.author,
        lengthSeconds = videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
        playabilityStatus = playabilityStatus?.status.orEmpty(),
        reason = playabilityStatus?.reason,
        streams = streamingData?.adaptiveFormats.orEmpty().mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            AudioStream(
                itag = format.itag ?: 0,
                mimeType = format.mimeType.orEmpty(),
                bitrate = format.bitrate ?: 0,
                url = url,
                contentLength = format.contentLength?.toLongOrNull(),
            )
        },
        expiresInSeconds = streamingData?.expiresInSeconds?.toIntOrNull(),
        clientUsed = clientUsed,
    )
}