package com.rootleo.velqi.db.entities

import androidx.room.Embedded

/**
 * Resultado de [com.rootleo.velqi.db.MusicDatabase.mostPlayedSongs] con el
 * numero de veces que se reprodujo cada cancion en el periodo.
 */
data class SongWithPlayCount(
    @Embedded val song: Song,
    val playCount: Int,
)