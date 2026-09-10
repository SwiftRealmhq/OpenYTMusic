package com.rootleo.velqi.models

import com.rootleo.velqi.innertube.models.YTItem
import com.rootleo.velqi.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
