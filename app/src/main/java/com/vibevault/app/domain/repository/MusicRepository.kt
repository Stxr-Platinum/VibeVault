package com.vibevault.app.domain.repository

import com.vibevault.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * MusicRepository — Domain-layer interface for music data operations.
 * The data layer implementation coordinates between Room (local) and Supabase (remote).
 */
interface MusicRepository {

    /** Observe all tracks (e.g. from a search or discovery source). */
    fun getAllTracks(): Flow<List<Track>>

    /** Observe liked tracks only. */
    fun getLikedTracks(): Flow<List<Track>>

    /** Observe recently played tracks. */
    fun getRecentlyPlayed(limit: Int = 30): Flow<List<Track>>

    /** Search tracks by title, artist, or album. */
    fun searchTracks(query: String): Flow<List<Track>>

    /** Get tracks by artist name. */
    fun getTracksByArtist(artistName: String): Flow<List<Track>>

    /** Toggle like status for a track. */
    suspend fun toggleLike(trackId: String)

    /** Record that a track was played. */
    suspend fun recordPlay(trackId: String)

    /** Fetch and cache tracks from remote source. */
    suspend fun refreshTracks()

    /** Full synchronization: Fetches user likes and playlists from Supabase. */
    suspend fun syncFromRemote()

    /** Seed the database with mock data for development. */
    suspend fun seedMockData()

    // ── Playlist Operations ────────────────────────────────
    fun getPlaylists(): Flow<List<com.vibevault.app.data.local.entity.PlaylistEntity>>
    fun getPlaylistTracks(playlistId: String): Flow<List<Track>>
    suspend fun getPlaylist(playlistId: String): com.vibevault.app.data.local.entity.PlaylistEntity?
    suspend fun createPlaylist(title: String): String
    suspend fun renamePlaylist(playlistId: String, newTitle: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
}
