package com.vibevault.app.domain.model

/**
 * Track — Pure domain model representing a music track.
 * Enhanced to support multiple sources (Spotify, Local, etc.) and external links.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumImageUrl: String,
    val audioUrl: String? = null,
    val durationMs: Long,
    val isLiked: Boolean = false,
    val localPath: String? = null,
    val source: String = "stream",
    val externalUrl: String? = null
)
