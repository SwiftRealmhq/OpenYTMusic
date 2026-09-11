package com.openytmusic.app.innertube.pages

import com.openytmusic.app.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
