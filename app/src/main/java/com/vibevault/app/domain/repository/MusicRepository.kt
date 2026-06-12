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

    /** Observe discovery/recommended tracks. */
    fun getDiscoveryTracks(): Flow<List<Track>>

    /** Get tracks by artist name. */
    fun getTracksByArtist(artistName: String): Flow<List<Track>>

    /** Search tracks specifically on Spotify. */
    suspend fun searchSpotify(query: String): Result<List<Track>>

    /** Toggle like status for a track. */
    suspend fun toggleLike(trackId: String)

    /** Record that a track was played. */
    suspend fun recordPlay(track: Track)

    /** Get system logs. */
    fun getLogs(): Flow<List<com.vibevault.app.data.local.entity.LogEntity>>

    /** Get authenticated devices. */
    fun getDevices(): Flow<List<com.vibevault.app.data.local.entity.DeviceEntity>>

    /** Clear local cache. */
    suspend fun clearLocalData()

    /** Fetch and cache tracks from remote source. */
    suspend fun refreshTracks()

    /** Full synchronization: Fetches user likes and playlists from Supabase. */
    suspend fun syncFromRemote()

    /** Fetches recent history IDs from Supabase and rich metadata from Spotify. */
    suspend fun syncRecentlyPlayed()

    /** Seed the database with mock data for development. */
    suspend fun seedMockData()

    // ── Playlist Operations ────────────────────────────────
    fun getPlaylists(): Flow<List<com.vibevault.app.data.local.entity.PlaylistEntity>>
    fun getPlaylistTracks(playlistId: String): Flow<List<Track>>
    suspend fun getPlaylist(playlistId: String): com.vibevault.app.data.local.entity.PlaylistEntity?
    suspend fun createPlaylist(title: String): String
    suspend fun renamePlaylist(playlistId: String, newTitle: String)
    suspend fun updatePlaylistCoverUrl(playlistId: String, coverUrl: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun reorderTracks(playlistId: String, fromIndex: Int, toIndex: Int)

    // ── Spotify Specific Data ────────────────────────────────
    fun getSpotifyRecentlyPlayed(): Flow<List<Track>>
    fun getFeaturedPlaylists(): Flow<List<com.vibevault.app.domain.model.Playlist>>
    fun getNewReleases(): Flow<List<Track>>
    fun getTopArtists(): Flow<List<com.vibevault.app.domain.model.Artist>>
    fun getUserSpotifyPlaylists(): Flow<List<com.vibevault.app.domain.model.Playlist>>
    fun getBrowseCategories(): Flow<List<com.vibevault.app.domain.model.Category>>
    suspend fun searchSpotifyAll(query: String): Result<com.vibevault.app.domain.model.SpotifySearchResult>
    fun getGlobalTop50(): Flow<List<Track>>

    // ── Album Operations ─────────────────────────────────────
    fun getUserSavedAlbums(): Flow<List<com.vibevault.app.domain.model.Album>>

    // ── Artist Detail Operations ─────────────────────────────
    suspend fun getArtistDetails(artistName: String): Result<com.vibevault.app.domain.model.Artist>
    suspend fun getArtistTopTracks(artistId: String): Result<List<Track>>
    suspend fun getArtistAlbums(artistId: String): Result<List<com.vibevault.app.domain.model.Album>>

    // ── Qobuz / Main Data Operations ─────────────────────────
    suspend fun searchQobuzMusic(query: String): List<Track>
    suspend fun getQobuzStreamUrl(trackId: String): String

    // Spotify fallback
    suspend fun getSpotifyPreviewUrl(trackId: String): String?
    suspend fun getSpotifyPlaylists(): List<com.vibevault.app.data.remote.dto.SpotifyPlaylistDto>
    suspend fun getSpotifyPlaylist(playlistId: String): com.vibevault.app.data.local.entity.PlaylistEntity?
    suspend fun getSpotifyPlaylistTracks(playlistId: String): List<Track>

    // Cross-Service Playback Resolution
    suspend fun resolveSpotifyPlaylistToStreams(playlistId: String, token: String): List<String>
    
    // Autoplay / Queue Generation
    suspend fun getSimilarTracks(seedTrack: Track): List<Track>
}
