package com.rootleo.velqi.utils

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

    // Manifiesto de versiones alojado en la web oficial (Netlify).
    private const val VERSION_URL = "https://velqi.netlify.app/version.json"

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
                    apkUrl = json.getString("apkUrl"),
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}