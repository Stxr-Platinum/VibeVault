package com.vibevault.app.data.local.dao

import androidx.room.*
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.data.local.entity.PlaylistTrackCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * PlaylistDao — Data Access Object for playlists and the playlist-track junction.
 */
@Dao
interface PlaylistDao {

    @Query("""
        SELECT p.id, p.title, p.description, p.coverUrl, 
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id AND pt.isDeleted = 0) as trackCount,
               p.durationMs, p.isPublic, p.isSynced, p.isDeleted, p.createdAt, p.updatedAt, p.clientTimestamp
        FROM playlists p 
        WHERE p.isDeleted = 0
        ORDER BY p.updatedAt DESC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistFromRemote(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    // ── Junction table (playlist ↔ track) ──────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylistFromRemote(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun getCrossRef(playlistId: String, trackId: String): PlaylistTrackCrossRef?

    @Query("""
        SELECT * FROM playlist_tracks 
        WHERE playlistId = :playlistId 
        ORDER BY sortOrder ASC
    """)
    fun getTracksForPlaylist(playlistId: String): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountForPlaylist(playlistId: String): Int

    @Query("SELECT * FROM playlists WHERE isSynced = 0")
    suspend fun getUnsyncedPlaylists(): List<PlaylistEntity>

    @Query("UPDATE playlists SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM playlist_tracks WHERE isSynced = 0")
    suspend fun getUnsyncedPlaylistTracks(): List<PlaylistTrackCrossRef>

    @Query("UPDATE playlist_tracks SET isSynced = 1 WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun markTrackSynced(playlistId: String, trackId: String)
}
