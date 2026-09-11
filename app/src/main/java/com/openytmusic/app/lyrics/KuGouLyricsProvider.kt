package com.openytmusic.app.lyrics

import android.content.Context
import com.openytmusic.app.kugou.KuGou
import com.openytmusic.app.constants.EnableKugouKey
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> =
        KuGou.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        KuGou.getAllLyrics(title, artist, duration, callback)
    }
}
