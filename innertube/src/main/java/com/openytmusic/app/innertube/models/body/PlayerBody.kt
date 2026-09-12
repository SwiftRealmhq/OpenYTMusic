package com.openytmusic.app.innertube.models.body

import com.openytmusic.app.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    /** PoToken de atestacion: prueba de origen que evita el bot-check de YouTube. */
    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}
