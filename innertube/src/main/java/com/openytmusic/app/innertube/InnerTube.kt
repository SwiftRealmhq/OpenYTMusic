package com.openytmusic.app.innertube

import com.openytmusic.app.innertube.encoder.brotli
import com.openytmusic.app.innertube.models.Context
import com.openytmusic.app.innertube.models.YouTubeClient
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.WEB
import com.openytmusic.app.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.openytmusic.app.innertube.models.YouTubeLocale
import com.openytmusic.app.innertube.models.body.*
import com.openytmusic.app.innertube.utils.parseCookieString
import com.openytmusic.app.innertube.utils.sha1
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.Proxy
import java.util.*

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
class InnerTube {
    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    var visitorData: String = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
    var cookie: String? = null
        set(value) {
            field = value
            // Una cookie pegada a mano no debe tumbar el proceso: si el parseo falla
            // se sigue sin sesion y la app lo reporta como "no logueado".
            cookieMap = if (value == null) {
                emptyMap()
            } else {
                runCatching { parseCookieString(value) }.getOrDefault(emptyMap())
            }
        }
    private var cookieMap = emptyMap<String, String>()

    var proxy: Proxy? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var useLoginForBrowse: Boolean = false

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        // Sin timeouts, una conexion que se queda colgada bloquea la resolucion (y el
        // hilo que la espera) indefinidamente: la app se quedaba "cargando" para siempre.
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }

        install(ContentEncoding) {
            brotli(1.0F)
            gzip(0.9F)
            deflate(0.8F)
        }

        if (proxy != null) {
            engine {
                proxy = this@InnerTube.proxy
            }
        }

        defaultRequest {
            url("https://music.youtube.com/youtubei/v1/")
        }
    }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("x-origin", "https://music.youtube.com")
            if (client.referer != null) {
                append("Referer", client.referer)
            }
            if (setLogin) {
                cookie?.let { append("cookie", it) }
            }
        }
        if (setLogin) sapisidHashHeader("https://music.youtube.com")
        userAgent(client.userAgent)
        parameter("key", client.api_key)
        parameter("prettyPrint", false)
    }

    /**
     * Firma `SAPISIDHASH`: es como la familia **web** de InnerTube reconoce una sesion
     * autenticada. Los clientes moviles (ANDROID/IOS) se autentican solo con la cookie; la web
     * exige ademas este `Authorization`, asi que mandar la cookie sin firma equivale a ir sin
     * sesion — y con IP de datacenter eso es justo lo que dispara el muro anti-bot.
     */
    private fun HttpRequestBuilder.sapisidHashHeader(origin: String) {
        val sapisid = cookieMap["SAPISID"] ?: return
        val currentTime = System.currentTimeMillis() / 1000
        val sapisidHash = sha1("$currentTime $sapisid $origin")
        header("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = httpClient.post("search") {
        ytClient(client, setLogin = useLoginForBrowse)
        setBody(
            SearchBody(
                context = client.toContext(locale, visitorData),
                query = query,
                params = params
            )
        )
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        poToken: String? = null,
        visitorData: String? = null,
    ) = httpClient.post("https://www.youtube.com/youtubei/v1/player") {
        // Kernel de Velqi: request medido contra la API real — solo Content-Type + User-Agent del
        // cliente (+ cookie si hay sesion). Sin parametro key ni cabeceras X-YouTube-Client-*:
        // asi es como responden OK los clientes moviles.
        contentType(ContentType.Application.Json)
        userAgent(client.userAgent)
        cookie?.let { header("cookie", it) }
        // La familia web solo acepta la sesion si va firmada con SAPISIDHASH: sin firma, mandar
        // la cookie es lo mismo que no mandarla.
        if (client == WEB || client == WEB_REMIX) {
            sapisidHashHeader("https://music.youtube.com")
        }
        setBody(
            PlayerBody(
                context = Context(
                    client = Context.Client(
                        clientName = client.clientName,
                        clientVersion = client.clientVersion,
                        osVersion = client.osVersion,
                        androidSdkVersion = client.androidSdkVersion,
                        gl = "US",
                        hl = "en",
                        visitorData = visitorData,
                    )
                ).let {
                    if (client == YouTubeClient.TVHTML5) {
                        it.copy(
                            thirdParty = Context.ThirdParty(
                                embedUrl = "https://www.youtube.com/watch?v=${videoId}"
                            )
                        )
                    } else it
                },
                videoId = videoId,
                playlistId = playlistId,
                // PoToken: prueba de origen que evita el "confirm you're not a bot".
                serviceIntegrityDimensions = poToken?.let {
                    PlayerBody.ServiceIntegrityDimensions(it)
                },
                contentCheckOk = true,
                racyCheckOk = true,
            )
        )
    }

    suspend fun pipedStreams(videoId: String) =
        httpClient.get("https://pipedapi.kavin.rocks/streams/${videoId}") {
            contentType(ContentType.Application.Json)
        }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = httpClient.post("browse") {
        ytClient(client, setLogin = setLogin || useLoginForBrowse)
        setBody(
            BrowseBody(
                context = client.toContext(locale, visitorData),
                browseId = browseId,
                params = params
            )
        )
        if (continuation != null) {
            // Velqi: solo con continuation real. Antes parameter(name, null) enviaba
            // la cadena "null" (continuation=null&ctoken=null) y YouTube respondia
            // 400 INVALID_ARGUMENT en cualquier browse paginado desde la primera pagina.
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
            parameter("type", "next")
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = httpClient.post("next") {
        ytClient(client, setLogin = true)
        setBody(
            NextBody(
                context = client.toContext(locale, visitorData),
                videoId = videoId,
                playlistId = playlistId,
                playlistSetVideoId = playlistSetVideoId,
                index = index,
                params = params,
                continuation = continuation
            )
        )
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = httpClient.post("music/get_search_suggestions") {
        ytClient(client)
        setBody(
            GetSearchSuggestionsBody(
                context = client.toContext(locale, visitorData),
                input = input
            )
        )
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = httpClient.post("music/get_queue") {
        ytClient(client)
        setBody(
            GetQueueBody(
                context = client.toContext(locale, visitorData),
                videoIds = videoIds,
                playlistId = playlistId
            )
        )
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
        parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
        headers {
            append("Content-Type", "application/json")
        }
        setBody(
            GetTranscriptBody(
                context = client.toContext(locale, null),
                params = "\n${11.toChar()}$videoId".encodeBase64()
            )
        )
    }

    suspend fun getSwJsData() = httpClient.get("https://music.youtube.com/sw.js_data")

    suspend fun accountMenu(client: YouTubeClient) = httpClient.post("account/account_menu") {
        ytClient(client, setLogin = true)
        setBody(AccountMenuBody(client.toContext(locale, visitorData)))
    }
}
