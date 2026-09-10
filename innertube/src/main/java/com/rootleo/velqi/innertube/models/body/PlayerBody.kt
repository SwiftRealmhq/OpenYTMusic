package com.rootleo.velqi.innertube.models.body

import com.rootleo.velqi.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
)
