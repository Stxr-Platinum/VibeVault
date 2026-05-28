package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LikedSongEntity — Stores track metadata for liked songs.
 * Denormalized for offline access.
 */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey
    val id: String, // track_id
    val title: String,
    val artist: String,
    val album: String,
    val albumImageUrl: String,
    val audioUrl: String,
    val durationMs: Long,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val clientTimestamp: Long = System.currentTimeMillis()
)
