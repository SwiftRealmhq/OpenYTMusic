package com.rootleo.velqi.innertube.pages

import com.rootleo.velqi.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
