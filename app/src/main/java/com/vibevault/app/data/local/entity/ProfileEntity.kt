package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ProfileEntity — Local cache for user profile data (PFP).
 * Reorganizes profile information into a dedicated table.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val username: String? = null,
    val accountHolderName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
