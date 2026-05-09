package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlaylistDto — Data Transfer Object for the Supabase `playlists` table.
 */
@Serializable
data class PlaylistDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
