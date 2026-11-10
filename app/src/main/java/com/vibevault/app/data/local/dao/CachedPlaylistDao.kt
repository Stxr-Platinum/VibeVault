package com.vibevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vibevault.app.data.local.entity.CachedPlaylistEntity
import com.vibevault.app.data.local.entity.CachedPlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedPlaylistDao {

    @Query("SELECT * FROM cached_playlists WHERE playlistId = :playlistId LIMIT 1")
    fun observePlaylistMeta(playlistId: String): Flow<CachedPlaylistEntity?>

    @Query("SELECT * FROM cached_playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistMeta(playlistId: String): CachedPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistMeta(meta: CachedPlaylistEntity)

    @Query("SELECT * FROM cached_playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun observePlaylistTracks(playlistId: String): Flow<List<CachedPlaylistTrackEntity>>

    @Query("SELECT * FROM cached_playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getPlaylistTracks(playlistId: String): List<CachedPlaylistTrackEntity>

    @Query("DELETE FROM cached_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<CachedPlaylistTrackEntity>)

    @Transaction
    suspend fun replacePlaylistTracksIfChanged(
        playlistId: String,
        tracks: List<CachedPlaylistTrackEntity>
    ): Boolean {
        val existing = getPlaylistTracks(playlistId)
        if (existing == tracks) return false
        deletePlaylistTracks(playlistId)
        if (tracks.isNotEmpty()) {
            insertPlaylistTracks(tracks)
        }
        return true
    }

    @Transaction
    suspend fun upsertPlaylistMetaIfChanged(meta: CachedPlaylistEntity): Boolean {
        val existing = getPlaylistMeta(meta.playlistId)
        if (existing != null &&
            existing.title == meta.title &&
            existing.ownerName == meta.ownerName &&
            existing.coverUrl == meta.coverUrl &&
            existing.trackCount == meta.trackCount &&
            existing.source == meta.source
        ) {
            return false
        }
        upsertPlaylistMeta(meta)
        return true
    }

    @Query("DELETE FROM cached_playlist_tracks WHERE playlistId IN (SELECT playlistId FROM cached_playlists WHERE source = 'spotify')")
    suspend fun clearSpotifyPlaylistTracks()

    @Query("DELETE FROM cached_playlists WHERE source = 'spotify'")
    suspend fun clearSpotifyPlaylistMeta()

    @Transaction
    suspend fun clearAllSpotifyCache() {
        clearSpotifyPlaylistTracks()
        clearSpotifyPlaylistMeta()
    }
}

