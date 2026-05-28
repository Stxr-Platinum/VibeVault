package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: String,
    val message: String,
    val tag: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
