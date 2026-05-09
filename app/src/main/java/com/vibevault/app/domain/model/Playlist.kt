package com.vibevault.app.domain.model

/**
 * Playlist — Pure domain model for user-created playlists.
 */
data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int = 0
)
