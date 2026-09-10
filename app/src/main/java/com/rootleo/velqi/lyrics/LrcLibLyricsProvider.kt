package com.rootleo.velqi.lyrics

import android.content.Context
import com.rootleo.velqi.lrclib.LrcLib
import com.rootleo.velqi.constants.EnableLrcLibKey
import com.rootleo.velqi.utils.dataStore
import com.rootleo.velqi.utils.get

/**
 */
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
