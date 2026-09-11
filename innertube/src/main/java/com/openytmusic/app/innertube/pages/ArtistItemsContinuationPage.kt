package com.openytmusic.app.innertube.pages

import com.openytmusic.app.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
