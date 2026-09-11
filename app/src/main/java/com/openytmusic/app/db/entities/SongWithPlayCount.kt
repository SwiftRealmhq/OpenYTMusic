package com.openytmusic.app.db.entities

import androidx.room.Embedded

/**
 * Resultado de [com.openytmusic.app.db.MusicDatabase.mostPlayedSongs] con el
 * numero de veces que se reprodujo cada cancion en el periodo.
 */
data class SongWithPlayCount(
    @Embedded val song: Song,
    val playCount: Int,
)