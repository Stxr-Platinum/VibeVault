package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlaylistTrackDto — Junction table entry for playlist contents.
 */
@Serializable
data class PlaylistTrackDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("song_title") val songTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null
)
