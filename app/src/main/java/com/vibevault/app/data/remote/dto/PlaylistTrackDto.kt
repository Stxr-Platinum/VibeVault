package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlaylistTrackDto — Aligned with Supabase `playlist_tracks` table.
 */
@Serializable
data class PlaylistTrackDto(
    @SerialName("id") val id: String? = null,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("song_title") val songTitle: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("source") val source: String? = "stream",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
