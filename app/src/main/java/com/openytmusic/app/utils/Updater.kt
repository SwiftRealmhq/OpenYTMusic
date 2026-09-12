package com.openytmusic.app.utils

import com.openytmusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
)

object Updater {
    var lastCheckTime = -1L
        private set

    // Manifiesto de versiones alojado en la web oficial (Netlify). Sale de BuildConfig para
    // que el dominio viva en un solo sitio: si este apunta a otro host que el de la web, el
    // chequeo devuelve 404 y la app se queda sin avisar de actualizaciones para siempre.
    private val VERSION_URL get() = "${BuildConfig.SITE_URL}/version.json"

    /**
     * El manifiesto puede traer el APK como ruta relativa (`OpenYTMusic-0.5.0.apk`), que es lo
     * comodo para publicar la web en cualquier dominio. Un `ACTION_VIEW` con una URI relativa no
     * abre nada, asi que aqui se completa contra la base del manifiesto.
     */
    private fun resolveApkUrl(value: String): String = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "${BuildConfig.SITE_URL}/${value.trimStart('/')}"
    }

    /**
     * Solo hay actualizacion si la version remota es estrictamente MAYOR
     * que la instalada (nunca con una descarga o rollback).
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun getLatestVersion(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            lastCheckTime = System.currentTimeMillis()
            val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            try {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                UpdateInfo(
                    versionName = json.getString("version"),
                    apkUrl = resolveApkUrl(json.getString("apkUrl")),
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}