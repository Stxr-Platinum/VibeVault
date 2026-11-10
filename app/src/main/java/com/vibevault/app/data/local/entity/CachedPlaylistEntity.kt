package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache entry for remote/non-local playlist & album metadata (Spotify, YouTube, Album).
 */
@Entity(tableName = "cached_playlists")
data class CachedPlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val title: String,
    val ownerName: String = "",
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val source: String = "remote",
    val updatedAt: Long = System.currentTimeMillis()
)

