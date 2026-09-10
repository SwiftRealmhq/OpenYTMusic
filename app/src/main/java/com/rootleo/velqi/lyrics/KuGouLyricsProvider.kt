package com.rootleo.velqi.lyrics

import android.content.Context
import com.rootleo.velqi.kugou.KuGou
import com.rootleo.velqi.constants.EnableKugouKey
import com.rootleo.velqi.utils.dataStore
import com.rootleo.velqi.utils.get

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
