package com.vibevault.app.data.local.dao

import androidx.room.*
import com.vibevault.app.data.local.entity.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun clearAll()

    @Query("SELECT * FROM logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<LogEntity>

    @Query("UPDATE logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
