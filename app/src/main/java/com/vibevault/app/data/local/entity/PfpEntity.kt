package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PfpEntity — Local storage for profile pictures (PFP).
 * This reorganizes PFPs into a separate table, supporting history and active selection.
 */
@Entity(tableName = "pfps")
data class PfpEntity(
    @PrimaryKey
    val id: String, // Supabase UUID
    val userId: String,
    val url: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
