package com.openytmusic.app.lyrics

import android.content.Context
import com.openytmusic.app.lrclib.LrcLib
import com.openytmusic.app.constants.EnableLrcLibKey
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.get

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
