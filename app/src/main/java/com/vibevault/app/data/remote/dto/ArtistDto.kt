package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ArtistDto — Aligned with Supabase `artists` table.
 */
@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("monthly_listeners") val monthlyListeners: Int? = 0,
    @SerialName("created_at") val createdAt: String? = null
)
