/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.rootleo.velqi.rpc.repository

import com.rootleo.velqi.rpc.remote.ApiService
import com.rootleo.velqi.rpc.utils.toImageAsset
import org.json.JSONObject
import java.io.File

/**
 * Modified by Zion Huang + revivido por Velqi: cachea las URLs resueltas en
 * memoria y en disco para no golpear los rate limits del backend de imagenes.
 */
class VelqiRepository(
    private val cacheFile: File? = null,
) {
    private val api = ApiService()
    private val cache = loadCache()

    suspend fun getImage(url: String): String? {
        cache[url]?.let { return it }
        val id = api.getImage(url).toImageAsset()
        if (id != null) {
            cache[url] = id
            saveCache()
        }
        return id
    }

    suspend fun uploadImage(file: File): String? {
        return api.uploadImage(file).toImageAsset()
    }

    private fun loadCache(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val f = cacheFile ?: return map
            if (!f.exists()) return map
            val json = JSONObject(f.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getString(k)
            }
        } catch (e: Exception) {
            // cache corrupta o inexistente: empezar vacio
        }
        return map
    }

    private fun saveCache() {
        try {
            val f = cacheFile ?: return
            val json = JSONObject()
            cache.forEach { (k, v) -> json.put(k, v) }
            f.parentFile?.mkdirs()
            f.writeText(json.toString())
        } catch (e: Exception) {
            // si no se puede guardar, la memoria alcanza
        }
    }
}