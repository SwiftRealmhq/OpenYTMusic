package com.rootleo.velqi.innertube.models.body

import com.rootleo.velqi.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
