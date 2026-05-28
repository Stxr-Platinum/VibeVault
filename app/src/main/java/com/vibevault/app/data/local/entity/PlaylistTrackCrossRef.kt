package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * PlaylistTrackCrossRef — Junction table linking playlists to tracks.
 * Stores denormalized track metadata for offline support.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("trackId")
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    
    // Denormalized Metadata
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumImageUrl: String = "",
    val audioUrl: String = "",
    val durationMs: Long = 0,
    
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false,
    val clientTimestamp: Long = System.currentTimeMillis()
)
