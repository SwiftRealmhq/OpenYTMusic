package com.rootleo.velqi.innertube.models.body

import com.rootleo.velqi.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
)
