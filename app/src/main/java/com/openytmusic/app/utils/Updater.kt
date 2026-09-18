package com.openytmusic.app.utils

import com.openytmusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
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

    /** Unico host aceptado como origen del APK (derivado del dominio oficial). */
    private val officialHost: String?
        get() = runCatching { URI(BuildConfig.SITE_URL).host }.getOrNull()

    /**
     * El manifiesto puede traer el APK como ruta relativa (`OpenYTMusic-0.6.2.apk`), que es lo
     * comodo para publicar la web en cualquier dominio. Un `ACTION_VIEW` con una URI relativa no
     * abre nada, asi que aqui se completa contra la base del manifiesto.
     *
     * Devuelve null si el resultado no es HTTPS en el host oficial: el manifiesto se sirve sin
     * firma, asi que quien controle el sitio (o reclame el subdominio) no debe poder dirigir a
     * los usuarios a un APK ajeno servido en claro.
     */
    private fun resolveApkUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val absolute = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "${BuildConfig.SITE_URL}/${trimmed.trimStart('/')}"
        }
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals(officialHost, ignoreCase = true)) return null
        return uri.toString()
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
                    apkUrl = resolveApkUrl(json.getString("apkUrl"))
                        ?: error("apkUrl del manifiesto no es https en el host oficial"),
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}