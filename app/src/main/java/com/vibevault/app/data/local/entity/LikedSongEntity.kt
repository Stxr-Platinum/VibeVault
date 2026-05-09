package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LikedSongEntity — Stores track metadata for liked songs.
 * Since the master tracks table is removed, we denormalize metadata here
 * to allow offline access to the user's library.
 */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumImageUrl: String,
    val audioUrl: String,
    val durationMs: Long,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
