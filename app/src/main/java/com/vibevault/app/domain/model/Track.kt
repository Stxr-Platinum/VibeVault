package com.vibevault.app.domain.model

/**
 * Track — Pure domain model, free of Room/Serialization annotations.
 * Used across the domain and UI layers.
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
    val localPath: String? = null
)
