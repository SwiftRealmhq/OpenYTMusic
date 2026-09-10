package com.rootleo.velqi.innertube.pages

import com.rootleo.velqi.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
