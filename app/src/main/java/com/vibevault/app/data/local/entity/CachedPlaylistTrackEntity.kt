package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Room cache entry for ordered tracks inside a cached remote playlist or album.
 */
@Entity(
    tableName = "cached_playlist_tracks",
    primaryKeys = ["playlistId", "sortOrder"],
    indices = [
        Index("playlistId"),
        Index("trackId")
    ]
)
data class CachedPlaylistTrackEntity(
    val playlistId: String,
    val sortOrder: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumImageUrl: String = "",
    val audioUrl: String? = null,
    val durationMs: Long = 0L,
    val source: String = "remote"
)

