package com.openytmusic.app.models

import com.openytmusic.app.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
