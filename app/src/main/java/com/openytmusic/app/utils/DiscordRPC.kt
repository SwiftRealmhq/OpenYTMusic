package com.openytmusic.app.utils

import android.content.Context
import android.util.Log
import com.openytmusic.app.rpc.rpc.VelqiRPC
import com.openytmusic.app.rpc.rpc.RpcImage
import com.openytmusic.app.R
import com.openytmusic.app.db.entities.Song

class DiscordRPC(
    val context: Context,
    token: String,
) : VelqiRPC(
    token,
    cacheFile = java.io.File(context.cacheDir, "velqi_image_cache.json")
) {
    private var lastSong: Song? = null
    private var lastSince: Long? = null

    suspend fun updateSong(song: Song, positionMs: Long = 0L, playing: Boolean = true) {
        try {
            lastSong = song
            // Logica del media RPC de la app Flutter de Velqi: el timestamp se
            // congela al ultimo evento (cancion nueva / resume). En pausa se
            // manda sin contador (tarjeta quieta, como Spotify) — sin clears
            // que generen fantasmas ni contadores en 00:00.
            lastSince = if (playing) {
                System.currentTimeMillis() - positionMs.coerceAtLeast(0L)
            } else {
                null
            }
            sendCurrent()
        } catch (e: Exception) {
            Log.w("DiscordRPC", "updateSong fallo: ${e.message}")
        }
    }

    /** Heartbeat (cada 3 s): re-envia la ultima actividad verbatim con el
     *  timestamp congelado — el contador corre del reloj del cliente, cero
     *  drift, y la conexion se mantiene viva. */
    suspend fun refresh() {
        try {
            if (lastSong != null) {
                sendCurrent()
            }
        } catch (e: Exception) {
            Log.w("DiscordRPC", "refresh fallo: ${e.message}")
        }
    }

    private suspend fun sendCurrent() {
        val song = lastSong ?: return
        val appId = applicationId()
        // Sin ID propio no se manda nada: la tarjeta quedaria firmada por otra app.
        if (appId.isEmpty()) return
        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = song.song.title,
            state = song.artists.joinToString { it.name },
            largeImage = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = song.artists.firstOrNull()?.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.song.id}"
            ),
            type = Type.LISTENING,
            since = lastSince,
            applicationId = appId
        )
    }

    /** ID de la aplicacion de Discord que firma el Rich Presence, leido del recurso
     *  en tiempo de ejecucion (ver build.gradle.kts). Vacio = sin configurar. */
    fun applicationId(): String = runCatching {
        context.getString(R.string.discord_app_id).trim()
    }.getOrDefault("")

    companion object {
        /** true cuando hay un ID de Discord propio configurado. Si no lo hay, el
         *  servicio ni siquiera abre el socket (no hay nada que enviar). */
        fun isConfigured(context: Context): Boolean = runCatching {
            context.getString(R.string.discord_app_id).trim().isNotEmpty()
        }.getOrDefault(false)
    }
}