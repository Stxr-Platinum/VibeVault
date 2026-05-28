package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LikeDto — Data Transfer Object for the Supabase `liked_songs` table.
 * Aligned with the denormalized schema v6.
 */
@Serializable
data class LikeDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("song_title") val songTitle: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("source") val source: String? = "stream",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
