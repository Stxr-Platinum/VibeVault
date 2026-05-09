package com.vibevault.app.data.repository

import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.entity.LikedSongEntity
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.data.local.entity.PlaylistTrackCrossRef
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.data.sync.SyncScheduler
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicRepositoryImpl — API-driven repository.
 * The master 'tracks' table has been removed. Metadata is now denormalized
 * into 'liked_songs' and 'playlist_tracks' for offline support.
 */
@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val postgrest: Postgrest,
    private val syncScheduler: SyncScheduler,
    private val sessionManager: SessionManager
) : MusicRepository {

    override fun getAllTracks(): Flow<List<Track>> = 
        // In an API-driven app, this might come from a 'Discover' endpoint
        // For now, return liked tracks as a placeholder
        likedSongDao.getAllLikedSongs().map { entities -> entities.map { it.toDomain() } }

    override fun getLikedTracks(): Flow<List<Track>> = 
        likedSongDao.getAllLikedSongs().map { entities -> entities.map { it.toDomain() } }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Track>> = 
        // Placeholder until we add a recently_played table
        getLikedTracks().map { it.take(limit) }

    override fun searchTracks(query: String): Flow<List<Track>> = 
        // This should hit an external API (Spotify, etc.)
        // Placeholder: filter liked tracks
        getLikedTracks().map { tracks -> 
            tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        }

    override fun getTracksByArtist(artistName: String): Flow<List<Track>> = 
        getLikedTracks().map { tracks -> tracks.filter { it.artist.equals(artistName, ignoreCase = true) } }

    override suspend fun toggleLike(trackId: String) {
        // This is tricky now because we need metadata to insert into liked_songs
        // If we don't have it, we might need to fetch it from the API first
        // For now, assume we can only toggle off if we don't have metadata,
        // or toggle on if we are calling this from a screen that HAS the track object.
        
        val isCurrentlyLiked = likedSongDao.getLikedSongIds().contains(trackId)
        if (isCurrentlyLiked) {
            likedSongDao.deleteLikedSong(trackId)
        } else {
            // In a real app, you'd fetch track details from API here before inserting
            // For now, we'll need a better way to handle this in the UI
        }
        syncScheduler.syncNow()
    }

    override suspend fun recordPlay(trackId: String) {
        // Placeholder
    }

    override suspend fun refreshTracks() {
        // No longer refreshing a master tracks table
    }

    override suspend fun syncFromRemote() {
        val userId = sessionManager.userId ?: return
        try {
            // 1. Sync Likes
            val remoteLikes = postgrest.from("liked_songs")
                .select { filter { eq("user_id", userId) } }
                .decodeList<LikeDto>()
            
            remoteLikes.forEach { dto ->
                likedSongDao.insertLikedSong(dto.toLikedSongEntity())
            }

            // 2. Sync Playlists
            val remotePlaylists = postgrest.from("playlists")
                .select { filter { eq("user_id", userId) } }
                .decodeList<PlaylistDto>()
            
            remotePlaylists.forEach { dto ->
                playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity().copy(isSynced = true))
                
                // 3. Sync Playlist Tracks
                val remotePlaylistTracks = postgrest.from("playlist_tracks")
                    .select { filter { eq("playlist_id", dto.id) } }
                    .decodeList<PlaylistTrackDto>()
                
                remotePlaylistTracks.forEach { ptDto ->
                    playlistDao.addTrackToPlaylistFromRemote(ptDto.toCrossRef().copy(isSynced = true))
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("MusicRepo", "Sync failed", e)
        }
    }

    override suspend fun seedMockData() {
        val testAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        val mockSongs = listOf(
            LikedSongEntity("t1", "Billie Jean", "Michael Jackson", "Thriller", "https://upload.wikimedia.org/wikipedia/en/5/55/Michael_Jackson_-_Thriller.png", testAudioUrl, 294_000),
            LikedSongEntity("t2", "Beat It", "Michael Jackson", "Thriller", "https://upload.wikimedia.org/wikipedia/en/5/55/Michael_Jackson_-_Thriller.png", testAudioUrl, 258_000),
            LikedSongEntity("t11", "Anti-Hero", "Taylor Swift", "Midnights", "https://upload.wikimedia.org/wikipedia/en/9/9f/Midnights_-_Taylor_Swift.png", testAudioUrl, 200_000)
        )
        mockSongs.forEach { likedSongDao.insertLikedSong(it) }
    }

    // ── Playlist Operations ────────────────────────────────
    
    override fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    override fun getPlaylistTracks(playlistId: String): Flow<List<Track>> = 
        playlistDao.getTracksForPlaylist(playlistId).map { refs -> 
            refs.map { it.toDomain() } 
        }

    override suspend fun getPlaylist(playlistId: String): PlaylistEntity? = playlistDao.getPlaylistById(playlistId)

    override suspend fun createPlaylist(title: String): String {
        val id = java.util.UUID.randomUUID().toString()
        playlistDao.insertPlaylist(PlaylistEntity(id = id, title = title, isSynced = false))
        syncScheduler.syncNow()
        return id
    }

    override suspend fun renamePlaylist(playlistId: String, newTitle: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(title = newTitle, isSynced = false))
        syncScheduler.syncNow()
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
        syncScheduler.syncNow()
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        // In a real app, you'd fetch metadata from API first.
        // For now, we'll assume we are adding a track we already know about (e.g. from search results)
        // This method signature in the interface might need to change to accept a Track object
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        syncScheduler.syncNow()
    }
}
