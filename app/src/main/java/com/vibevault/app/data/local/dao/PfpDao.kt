package com.vibevault.app.data.local.dao

import androidx.room.*
import com.vibevault.app.data.local.entity.PfpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PfpDao {

    @Query("SELECT * FROM pfps WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllPfpsForUser(userId: String): Flow<List<PfpEntity>>

    @Query("SELECT * FROM pfps WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun getActivePfp(userId: String): Flow<PfpEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPfp(pfp: PfpEntity)

    @Transaction
    suspend fun setActivePfp(userId: String, pfpId: String) {
        clearActivePfps(userId)
        markPfpActive(pfpId)
    }

    @Query("UPDATE pfps SET isActive = 0 WHERE userId = :userId")
    suspend fun clearActivePfps(userId: String)

    @Query("UPDATE pfps SET isActive = 1 WHERE id = :pfpId")
    suspend fun markPfpActive(pfpId: String)

    @Delete
    suspend fun deletePfp(pfp: PfpEntity)
}
