package com.vibevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vibevault.app.data.local.entity.SearchCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchCacheDao {

    @Query("SELECT * FROM search_cache WHERE cacheKey = :cacheKey LIMIT 1")
    fun observeEntry(cacheKey: String): Flow<SearchCacheEntity?>

    @Query("SELECT * FROM search_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getEntry(cacheKey: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteEntry(cacheKey: String)

    @Query("DELETE FROM search_cache")
    suspend fun clearAll()
}

