package com.vibevault.app.data.lyrics

import android.content.Context
import com.music.vivi.betterlyrics.BetterLyrics

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
