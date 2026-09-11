package com.openytmusic.app.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicPlaylistShelfRenderer(
    val playlistId: String? = null,
    // Velqi: en la biblioteca autenticada YouTube manda shelves vacios (sin el
    // campo "contents"); con campos obligatorios se perdia la respuesta entera
    // por MissingFieldException y la importacion veia "error".
    val contents: List<MusicShelfRenderer.Content> = emptyList(),
    val collapsedItemCount: Int = 0,
    val continuations: List<Continuation>? = null,
)
