package com.openytmusic.app.innertube.models.body

import com.openytmusic.app.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
