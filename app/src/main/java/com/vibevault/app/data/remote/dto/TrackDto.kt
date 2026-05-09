package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TrackDto — Aligned with Supabase `tracks` table schema.
 *
 * Actual columns: id (uuid), title, artist_id (uuid→artists),
 * album_id, url, duration (int), album_art, created_at
 */
@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    val url: String,
    val duration: Int,
    @SerialName("album_art") val albumArt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
