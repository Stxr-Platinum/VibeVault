package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseSpotifyPlaylistTrackDto(
    @SerialName("user_id") val userId: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("spotify_track_id") val spotifyTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    @SerialName("cover_url") val coverUrl: String,
    @SerialName("duration_ms") val durationMs: Long,
    val position: Int,
    @SerialName("created_at") val createdAt: String? = null
)
