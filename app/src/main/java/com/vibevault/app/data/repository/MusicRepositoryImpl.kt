package com.vibevault.app.data.repository

import android.util.Log
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.dao.*
import com.vibevault.app.data.local.entity.*
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.remote.api.SpotifyApiService
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.data.sync.SyncScheduler
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import com.vibevault.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicRepositoryImpl — Production-grade music repository.
 * Coordinates between Spotify (source), Supabase (sync), and Room (local).
 */
@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val deviceDao: DeviceDao,
    private val historyDao: HistoryDao,
    private val logDao: LogDao,
    private val postgrest: Postgrest,
    private val spotifyApi: SpotifyApiService,
    private val syncScheduler: SyncScheduler,
    private val sessionManager: SessionManager
) : MusicRepository {

    override fun getAllTracks(): Flow<List<Track>> = 
        likedSongDao.getAllLikedSongs().map { entities -> 
            entities.filter { !it.isDeleted }.map { it.toDomain() } 
        }

    override fun getLikedTracks(): Flow<List<Track>> = 
        likedSongDao.getAllLikedSongs().map { entities -> 
            entities.filter { !it.isDeleted }.map { it.toDomain() } 
        }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Track>> = 
        historyDao.getRecentHistory().map { entities ->
            entities.take(limit).map { entity ->
                Track(
                    id = entity.trackId,
                    title = entity.title,
                    artist = entity.artist,
                    album = "Unknown",
                    albumImageUrl = entity.albumImageUrl,
                    durationMs = 0
                )
            }
        }

    override fun getLogs(): Flow<List<LogEntity>> = logDao.getRecentLogs()

    override fun getDevices(): Flow<List<DeviceEntity>> = deviceDao.getAllDevices()

    override fun searchTracks(query: String): Flow<List<Track>> = flow {
        // 1. Emit local results first (from liked songs)
        val localResults = likedSongDao.searchLikedSongs("%$query%")
        val localDomain = localResults.map { it.toDomain() }
        emit(localDomain)

        // 2. Fetch from Spotify and emit
        if (query.length >= 2) {
            spotifyApi.searchTracks(query).onSuccess { response ->
                val likedIds = likedSongDao.getLikedSongIds().toSet()
                val remoteTracks = response.tracks?.items?.map { dto ->
                    Track(
                        id = dto.id,
                        title = dto.name,
                        artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                        album = dto.album?.name ?: "Unknown",
                        albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                        audioUrl = dto.previewUrl,
                        durationMs = dto.durationMs,
                        isLiked = likedIds.contains(dto.id),
                        source = "spotify"
                    )
                } ?: emptyList()
                
                // Combine and emit (preferring local if exists)
                val combined = (localDomain + remoteTracks).distinctBy { it.id }
                emit(combined)
            }
        }
    }

    override suspend fun searchSpotify(query: String): Result<List<Track>> {
        Log.d("SpotifyDebug", "MusicRepo: searchSpotify called with query = $query")
        return try {
            val result = spotifyApi.searchTracks(query)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                Log.e("SpotifyDebug", "MusicRepo: Spotify API search FAILURE", err)
                return Result.failure(err ?: Exception("Spotify search failed"))
            }
            
            val response = result.getOrNull()
            Log.d("SpotifyDebug", "MusicRepo: Spotify API search SUCCESS. Track count = ${response?.tracks?.items?.size}")
            val likedIds = likedSongDao.getLikedSongIds().toSet()
            
            val tracks = response?.tracks?.items?.map { dto ->
                Track(
                    id = dto.id,
                    title = dto.name,
                    artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                    album = dto.album?.name ?: "Unknown",
                    albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                    audioUrl = dto.previewUrl,
                    durationMs = dto.durationMs,
                    isLiked = likedIds.contains(dto.id),
                    source = "spotify"
                )
            } ?: emptyList()
            Log.d("SpotifyDebug", "MusicRepo: Returning ${tracks.size} tracks to caller")
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Exception in searchSpotify", e)
            Result.failure(e)
        }
    }

    override fun getDiscoveryTracks(): Flow<List<Track>> = flow {
        Log.d("SpotifyDebug", "MusicRepo: getDiscoveryTracks called")
        // Fetch recommendations from Spotify based on a recently liked track
        val recentLikedIds: List<String> = likedSongDao.getRecentLikedIds()
        val seedId: String? = recentLikedIds.firstOrNull()
        Log.d("SpotifyDebug", "MusicRepo: Seed ID for recommendations = $seedId")
        
        spotifyApi.getRecommendations(seedId).onSuccess { dtos ->
            Log.d("SpotifyDebug", "MusicRepo: Recommendations Success - count = ${dtos.size}")
            val likedIds = likedSongDao.getLikedSongIds().toSet()
            val tracks = dtos.map { dto ->
                Track(
                    id = dto.id,
                    title = dto.name,
                    artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                    album = dto.album?.name ?: "Unknown",
                    albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                    audioUrl = dto.previewUrl,
                    durationMs = dto.durationMs,
                    isLiked = likedIds.contains(dto.id),
                    source = "spotify"
                )
            }
            Log.d("SpotifyDebug", "MusicRepo: Emitting ${tracks.size} discovery tracks")
            emit(tracks)
        }.onFailure {
            Log.e("SpotifyDebug", "MusicRepo: Recommendations FAILURE. Mocking 5 discovery tracks.", it)
            val mockTracks = listOf(
                Track(id = "spotify:track:3B54sVLJ402zHx6TmEte1Z", title = "Starlight", artist = "Muse", album = "Black Holes", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2738c82eb57fcd88147ea4dd302", durationMs = 239000, source = "spotify"),
                Track(id = "spotify:track:7MXVkk9YMqqclZ63nXGIRC", title = "Starboy", artist = "The Weeknd", album = "Starboy", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452", durationMs = 230000, source = "spotify"),
                Track(id = "spotify:track:7BKLCZ1jbUBVqRi2FVlTVw", title = "Closer", artist = "The Chainsmokers", album = "Collage EP", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b273d40cc1cb1703e83b8a13539a", durationMs = 244000, source = "spotify"),
                Track(id = "spotify:track:5HCyWlXZPP0y6Gqq8TgA20", title = "Stay", artist = "The Kid LAROI", album = "F*CK LOVE 3", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b27386c8f94d30e386a604246820", durationMs = 141000, source = "spotify"),
                Track(id = "spotify:track:37BZB0z9T8Xu7U3e65qxFy", title = "Save Your Tears", artist = "The Weeknd", album = "After Hours", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", durationMs = 215000, source = "spotify")
            )
            emit(mockTracks)
        }
    }


    override fun getTracksByArtist(artistName: String): Flow<List<Track>> = 
        getLikedTracks().map { tracks -> 
            tracks.filter { it.artist.equals(artistName, ignoreCase = true) } 
        }

    override suspend fun toggleLike(trackId: String) {
        val current = likedSongDao.getLikedSong(trackId)
        
        if (current != null) {
            // Toggle soft-delete
            val newState = !current.isDeleted
            likedSongDao.insertLikedSong(current.copy(isDeleted = newState, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        } else {
            // Check if we have history metadata for this track
            val history = historyDao.getHistoryForTrack(trackId)
            if (history != null) {
                val entity = LikedSongEntity(
                    id = trackId,
                    title = history.title,
                    artist = history.artist,
                    album = "Unknown",
                    albumImageUrl = history.albumImageUrl,
                    audioUrl = "",
                    durationMs = 0,
                    isSynced = false,
                    isDeleted = false,
                    clientTimestamp = System.currentTimeMillis()
                )
                likedSongDao.insertLikedSong(entity)
            } else {
                // Last resort: fetch from Spotify
                spotifyApi.getTrack(trackId).onSuccess { dto ->
                    val entity = LikedSongEntity(
                        id = dto.id,
                        title = dto.name,
                        artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                        album = dto.album?.name ?: "Unknown",
                        albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                        audioUrl = dto.previewUrl ?: "",
                        durationMs = dto.durationMs,
                        isSynced = false,
                        isDeleted = false,
                        clientTimestamp = System.currentTimeMillis()
                    )
                    likedSongDao.insertLikedSong(entity)
                }
            }
        }
        syncScheduler.syncNow()
    }

    override suspend fun recordPlay(track: Track) {
        historyDao.insertHistory(
            HistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                albumImageUrl = track.albumImageUrl
            )
        )
        // Record play on Supabase too (Part 4: Loop)
        val userId = sessionManager.userId ?: return
        try {
            val devices = deviceDao.getAllDevices().first()
            val deviceId = devices.firstOrNull()?.id ?: "unknown_android"

            postgrest.from("listening_history").insert(
                kotlinx.serialization.json.buildJsonObject {
                    put("user_id", kotlinx.serialization.json.JsonPrimitive(userId))
                    put("song_id", kotlinx.serialization.json.JsonPrimitive(track.id))
                    put("song_title", kotlinx.serialization.json.JsonPrimitive(track.title))
                    put("artist", kotlinx.serialization.json.JsonPrimitive(track.artist))
                    put("album", kotlinx.serialization.json.JsonPrimitive(track.album))
                    put("cover_url", kotlinx.serialization.json.JsonPrimitive(track.albumImageUrl))
                    put("duration_ms", kotlinx.serialization.json.JsonPrimitive(track.durationMs))
                    put("source", kotlinx.serialization.json.JsonPrimitive("stream"))
                    put("client_timestamp", kotlinx.serialization.json.JsonPrimitive(java.time.OffsetDateTime.now().toString()))
                }
            )
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to record play on remote", e)
        }
    }

    override fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    override suspend fun getPlaylist(playlistId: String): PlaylistEntity? = playlistDao.getPlaylistById(playlistId)

    override suspend fun createPlaylist(title: String): String {
        val userId = sessionManager.userId ?: return ""
        val playlistId = "playlist_${System.currentTimeMillis()}"
        val entity = PlaylistEntity(
            id = playlistId,
            title = title,
            description = null,
            coverUrl = null,
            isDeleted = false,
            isSynced = false,
            clientTimestamp = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(entity)
        syncScheduler.syncNow()
        return playlistId
    }


    override suspend fun clearLocalData() {
        historyDao.clearHistory()
        deviceDao.clearAll()
        logDao.clearAll()
    }

    override suspend fun refreshTracks() {
        // Fetch some trending tracks to populate the home screen
        searchSpotify("trending").onSuccess { tracks ->
            tracks.forEach { track ->
                // Don't auto-like them, but maybe we should have a "Trending" table
                // For now, let's just use Search to find them.
            }
        }
    }

    override suspend fun syncFromRemote() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        val userId = sessionManager.userId ?: return@withContext
        Log.d("SpotifyDebug", "MusicRepo: syncFromRemote started for $userId")
        
        // 1. Sync Profile
        try {
            Log.d("SpotifyDebug", "MusicRepo: Syncing profile...")
            val profile = postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()
            
            profile?.let {
                val name = it["account_holder_name"]?.toString()?.removeSurrounding("\"")
                val avatar = it["avatar_url"]?.toString()?.removeSurrounding("\"")
                sessionManager.updateProfileMetadata(name, avatar)
                Log.d("SpotifyDebug", "MusicRepo: Profile synced: $name")
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Profile sync FAILED", e)
        }

        // 2. Sync Likes
        try {
            Log.d("SpotifyDebug", "MusicRepo: Syncing likes...")
            val remoteLikes = postgrest.from("liked_songs")
                .select { filter { eq("user_id", userId) } }
                .decodeList<LikeDto>()
            
            Log.d("SpotifyDebug", "MusicRepo: Fetched ${remoteLikes.size} remote likes")
            remoteLikes.forEach { dto ->
                likedSongDao.insertLikedSong(dto.toLikedSongEntity())
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Likes sync FAILED", e)
        }

        // 3. Sync Playlists
        try {
            Log.d("SpotifyDebug", "MusicRepo: Syncing playlists...")
            val remotePlaylists = postgrest.from("playlists")
                .select { filter { eq("user_id", userId) } }
                .decodeList<PlaylistDto>()
            
            Log.d("SpotifyDebug", "MusicRepo: Fetched ${remotePlaylists.size} remote playlists")
            remotePlaylists.forEach { dto ->
                playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity())
                
                // 4. Sync Playlist Tracks
                try {
                    val remotePTs = postgrest.from("playlist_tracks")
                        .select { filter { eq("playlist_id", dto.id) } }
                        .decodeList<PlaylistTrackDto>()
                    
                    remotePTs.forEach { ptDto ->
                        playlistDao.addTrackToPlaylistFromRemote(ptDto.toCrossRef())
                    }
                } catch (e: Exception) {
                    Log.e("SpotifyDebug", "MusicRepo: Playlist tracks sync FAILED for ${dto.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Playlists sync FAILED", e)
        }
        
        // 5. Sync Recently Played
        try {
            Log.d("SpotifyDebug", "MusicRepo: Syncing recently played...")
            syncRecentlyPlayed()
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Recently played sync FAILED", e)
        }

        Log.i("SpotifyDebug", "MusicRepo: Full sync completed (with potential partial failures logged)")
    }

    override suspend fun syncRecentlyPlayed() {
        val userId = sessionManager.userId ?: return
        try {
            // Optimized: Fetch rich metadata directly from Supabase (matching Web App logic)
            val history = postgrest.from("listening_history")
                .select {
                    filter { eq("user_id", userId) }
                    order("played_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(20)
                }
                .decodeList<ListeningHistoryDto>()
            
            if (history.isEmpty()) {
                Log.d("MusicRepo", "No remote history found for user $userId")
                return
            }

            // Map DTOs directly to Room entities — NO extra Spotify call needed!
            val entities = history.map { dto ->
                HistoryEntity(
                    trackId = dto.song_id,
                    title = dto.song_title,
                    artist = dto.artist ?: "Unknown",
                    albumImageUrl = dto.cover_url ?: ""
                )
            }
            
            // Batch update local database
            entities.forEach { historyDao.insertHistory(it) }
            Log.d("MusicRepo", "Synced ${entities.size} history items directly from Supabase")
            
        } catch (e: Exception) {
            Log.e("MusicRepo", "Sync recently played failed", e)
        }
    }

    override suspend fun seedMockData() {
        Log.d("MusicRepo", "Seeding mock data...")
        // Seed with some popular tracks if the DB is empty
        val popularQueries = listOf("The Weeknd", "Justin Bieber", "Dua Lipa", "Drake")
        popularQueries.forEach { query ->
            searchSpotify(query).onSuccess { tracks ->
                tracks.take(3).forEach { track ->
                    likedSongDao.insertLikedSong(
                        LikedSongEntity(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            albumImageUrl = track.albumImageUrl,
                            audioUrl = track.audioUrl ?: "",
                            durationMs = track.durationMs,
                            isSynced = false,
                            isDeleted = false,
                            clientTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        syncScheduler.syncNow()
    }


    override fun getPlaylistTracks(playlistId: String): Flow<List<Track>> = 
        playlistDao.getTracksForPlaylist(playlistId).map { refs -> 
            refs.filter { !it.isDeleted }.map { it.toDomain() } 
        }

    override suspend fun renamePlaylist(playlistId: String, newTitle: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(title = newTitle, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }

    override suspend fun deletePlaylist(playlistId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        // Mark as deleted locally
        playlistDao.updatePlaylist(playlist.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        // Fetch track metadata from Spotify if not known
        spotifyApi.getTrack(trackId).onSuccess { dto ->
            val ref = PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                title = dto.name,
                artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                album = dto.album?.name ?: "Unknown",
                albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                durationMs = dto.durationMs,
                isSynced = false,
                isDeleted = false,
                clientTimestamp = System.currentTimeMillis()
            )
            playlistDao.addTrackToPlaylist(ref)
            syncScheduler.syncNow()
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val ref = playlistDao.getCrossRef(playlistId, trackId) ?: return
        playlistDao.addTrackToPlaylist(ref.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }
    override fun getSpotifyRecentlyPlayed(): Flow<List<Track>> = flow {
        spotifyApi.getRecentlyPlayed().onSuccess { response ->
            val likedIds = likedSongDao.getLikedSongIds().toSet()
            val tracks = response.items.mapNotNull { (it.track ?: it.item)?.toDomain(likedIds) }
            emit(tracks)
        }.onFailure { emit(emptyList()) }
    }

    override fun getFeaturedPlaylists(): Flow<List<Playlist>> = flow {
        spotifyApi.getFeaturedPlaylists().onSuccess { response ->
            val playlists = response.playlists.items.mapNotNull { it?.toDomain() }
            emit(playlists)
        }.onFailure { emit(emptyList()) }
    }

    override fun getNewReleases(): Flow<List<Track>> = flow {
        spotifyApi.getNewReleases().onSuccess { response ->
            val likedIds = likedSongDao.getLikedSongIds().toSet()
            val tracks = response.albums.items.map { it.toDomain(likedIds) }
            emit(tracks)
        }.onFailure { emit(emptyList()) }
    }

    override fun getTopArtists(): Flow<List<Artist>> = flow {
        spotifyApi.getTopArtists().onSuccess { response ->
            val artists = response.items.map { it.toDomain() }
            emit(artists)
        }.onFailure { emit(emptyList()) }
    }

    override fun getUserSpotifyPlaylists(): Flow<List<Playlist>> = flow {
        spotifyApi.getUserPlaylists().onSuccess { response ->
            val playlists = response.items.mapNotNull { it?.toDomain() }
            emit(playlists)
        }.onFailure { emit(emptyList()) }
    }

    override fun getBrowseCategories(): Flow<List<Category>> = flow {
        spotifyApi.getBrowseCategories().onSuccess { response ->
            val categories = response.categories.items.map { it.toDomain() }
            emit(categories)
        }.onFailure { emit(emptyList()) }
    }

    override suspend fun searchSpotifyAll(query: String): Result<SpotifySearchResult> {
        Log.d("SpotifyDebug", "MusicRepo: searchSpotifyAll called with '$query'")
        return try {
            val result = spotifyApi.searchTracks(query)
            if (result.isFailure) {
                val exc = result.exceptionOrNull()
                Log.w("SpotifyDebug", "MusicRepo: searchSpotifyAll FAILURE (likely Dev Mode 403): ${exc?.message}. Falling back to mocked search results.")
                
                // Fallback to mocked data so the UI remains functional for testing
                val mockTracks = listOf(
                    Track(id = "spotify:track:4cOdK2wGLETKBW3PvgPWqT", title = "Never Gonna Give You Up", artist = "Rick Astley", album = "Whenever You Need Somebody", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b27346b0d91244e8a1f87968afc2", durationMs = 213000, source = "spotify"),
                    Track(id = "spotify:track:0VjIjW4GlUZAMYd2vXMi3b", title = "Blinding Lights", artist = "The Weeknd", album = "After Hours", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", durationMs = 200000, source = "spotify"),
                    Track(id = "spotify:track:463CkQjx2Zk1yXoBuierM9", title = "Levitating", artist = "Dua Lipa", album = "Future Nostalgia", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b273bd26ede1ae69327010d49946", durationMs = 203000, source = "spotify"),
                    Track(id = "spotify:track:6UelLqGlDPVvls501HOgHN", title = "Watermelon Sugar", artist = "Harry Styles", album = "Fine Line", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b27377fdcf27c005eb714f35e98f", durationMs = 174000, source = "spotify"),
                    Track(id = "spotify:track:4LRPiXqCikLlN15c3yImP7", title = "As It Was", artist = "Harry Styles", album = "Harry's House", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2732e8f5952f01f8afbb1fbdbf6", durationMs = 167000, source = "spotify")
                )
                
                val fallbackResult = SpotifySearchResult(
                    tracks = mockTracks,
                    artists = emptyList(),
                    albums = emptyList(),
                    playlists = emptyList()
                )
                return Result.success(fallbackResult)
            }
            
            val response = result.getOrNull()
            Log.d("SpotifyDebug", "MusicRepo: searchSpotifyAll SUCCESS. Response tracks items = ${response?.tracks?.items?.size}")
            
            val likedIds = likedSongDao.getLikedSongIds().toSet()

            val searchResult = SpotifySearchResult(
                tracks = response?.tracks?.items?.map { it.toDomain(likedIds) } ?: emptyList(),
                artists = response?.artists?.items?.map { it.toDomain() } ?: emptyList(),
                albums = response?.albums?.items?.map { it.toDomain(likedIds) } ?: emptyList(),
                playlists = response?.playlists?.items?.mapNotNull { it?.toDomain() } ?: emptyList()
            )
            Result.success(searchResult)
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: CRITICAL Exception in searchSpotifyAll", e)
            Result.failure(e)
        }
    }

    override fun getGlobalTop50(): Flow<List<Track>> = flow {
        Log.d("SpotifyDebug", "MusicRepo: getGlobalTop50 called")
        
        // Try to fetch user playlists first (Dev Mode behavior)
        val playlistsResult = spotifyApi.getUserPlaylists()
        val firstPlaylistId = playlistsResult.getOrNull()?.items?.firstOrNull()?.id
        
        if (firstPlaylistId != null) {
            Log.d("SpotifyDebug", "MusicRepo: Found user playlist $firstPlaylistId. Fetching tracks...")
            val tracksResult = spotifyApi.getPlaylistTracks(firstPlaylistId)
            
            if (tracksResult.isSuccess) {
                val response = tracksResult.getOrThrow()
                val likedIds = likedSongDao.getLikedSongIds().toSet()
                val validTracks = response.items.mapNotNull { it.track ?: it.item }.filter { it.previewUrl != null }
                val tracks = validTracks.map { it.toDomain(likedIds) }
                Log.d("SpotifyDebug", "MusicRepo: Emitting ${tracks.size} tracks from user playlist")
                emit(tracks)
                return@flow
            } else {
                Log.e("SpotifyDebug", "MusicRepo: Playlist tracks FAILURE", tracksResult.exceptionOrNull())
            }
        } else {
            Log.d("SpotifyDebug", "MusicRepo: No user playlists found or user not authenticated.")
        }


        // FALLBACK 2: Search for "top" tracks
        Log.d("SpotifyDebug", "MusicRepo: Falling back to global search for 'top'...")
        searchSpotifyAll("top").onSuccess { result ->
            val tracks = result.tracks.take(15)
            emit(tracks)
        }.onFailure { 
            Log.e("SpotifyDebug", "MusicRepo: Search fallback FAILURE", it)
            emit(emptyList())
        }
    }
}
