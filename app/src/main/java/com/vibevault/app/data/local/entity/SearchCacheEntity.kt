package com.vibevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable Room cache entry for Search screen suggestions and query results.
 */
@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

