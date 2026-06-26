package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseSpotifyPlaylistDto(
    @SerialName("user_id") val userId: String,
    @SerialName("playlist_id") val playlistId: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("synced_at") val syncedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
