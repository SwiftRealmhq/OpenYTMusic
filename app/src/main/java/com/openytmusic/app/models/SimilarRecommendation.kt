package com.openytmusic.app.models

import com.openytmusic.app.innertube.models.YTItem
import com.openytmusic.app.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
