package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val isCurrentDevice: Boolean = false
)
