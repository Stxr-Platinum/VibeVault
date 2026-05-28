package com.vibevault.app.data.local.dao

import androidx.room.*
import com.vibevault.app.data.local.entity.LikedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY createdAt DESC")
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT id FROM liked_songs")
    suspend fun getLikedSongIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :trackId AND isDeleted = 0)")
    suspend fun isLiked(trackId: String): Boolean

    @Query("SELECT * FROM liked_songs WHERE id = :trackId")
    suspend fun getLikedSong(trackId: String): LikedSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :trackId")
    suspend fun deleteLikedSong(trackId: String)

    @Query("SELECT * FROM liked_songs WHERE isSynced = 0")
    suspend fun getUnsyncedLikes(): List<LikedSongEntity>

    @Query("UPDATE liked_songs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM liked_songs WHERE title LIKE :query OR artist LIKE :query")
    suspend fun searchLikedSongs(query: String): List<LikedSongEntity>
    @Query("SELECT id FROM liked_songs WHERE isDeleted = 0 ORDER BY clientTimestamp DESC LIMIT 5")
    suspend fun getRecentLikedIds(): List<String>
}

