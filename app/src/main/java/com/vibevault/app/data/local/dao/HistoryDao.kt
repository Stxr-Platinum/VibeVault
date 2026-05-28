package com.vibevault.app.data.local.dao

import androidx.room.*
import com.vibevault.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("SELECT * FROM listening_history WHERE trackId = :trackId LIMIT 1")
    suspend fun getHistoryForTrack(trackId: String): HistoryEntity?

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()
}
