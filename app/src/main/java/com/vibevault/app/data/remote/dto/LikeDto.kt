package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LikeDto — Data Transfer Object for the Supabase `likes` table.
 * Represents a user's "liked" status for a track.
 */
@Serializable
data class LikeDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("song_title") val songTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)
