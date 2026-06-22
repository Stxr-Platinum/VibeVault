package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * HistoryEntity — Represents a track in the user's listening history.
 * Table name: listening_history
 */
@Entity(tableName = "listening_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumImageUrl: String,
    val playedAt: Long = System.currentTimeMillis()
)
