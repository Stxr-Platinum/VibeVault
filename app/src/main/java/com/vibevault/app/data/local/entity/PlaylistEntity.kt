package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PlaylistEntity — Room entity representing a user-created playlist.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val ownerName: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0,
    val isPublic: Boolean = false,
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clientTimestamp: Long = System.currentTimeMillis()
)
