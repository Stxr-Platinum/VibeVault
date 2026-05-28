package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlaylistDto — Aligned with Supabase `playlists` table.
 */
@Serializable
data class PlaylistDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
