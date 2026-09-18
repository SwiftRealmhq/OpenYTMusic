package com.openytmusic.app.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer?,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String?,
        val thumbnailScale: String?,
    ) {
        // La MAS GRANDE declarada, no la ultima de la lista. El API no siempre
        // devuelve la lista ordenada de menor a mayor (hay videos donde el
        // ultimo elemento es hqdefault, 400x225, mientras sddefault/hq720 estan
        // antes), y quedarse con lastOrNull dejaba esas portadas borrosas.
        fun getThumbnailUrl() = thumbnail.thumbnails
            .maxByOrNull { (it.width ?: 0).toLong() * (it.height ?: 0) }?.url
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}
