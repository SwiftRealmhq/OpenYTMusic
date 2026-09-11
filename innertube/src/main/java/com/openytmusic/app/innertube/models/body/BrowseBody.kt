package com.openytmusic.app.innertube.models.body

import com.openytmusic.app.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
)
