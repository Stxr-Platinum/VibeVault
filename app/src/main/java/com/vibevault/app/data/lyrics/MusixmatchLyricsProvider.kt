package com.vibevault.app.data.lyrics

import android.content.Context
import com.music.musixmatch.Musixmatch

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        Musixmatch.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Musixmatch.getAllLyrics(title, artist, duration, album, callback)
    }
}
