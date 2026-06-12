package com.vibevault.app.data.repository

import android.util.Log
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.dao.*
import com.vibevault.app.data.local.entity.*
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.data.sync.SyncScheduler
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.model.Album
import com.vibevault.app.domain.model.Artist
import com.vibevault.app.domain.repository.MusicRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import com.vibevault.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.vibevault.app.domain.model.QobuzSearchResponse
import com.vibevault.app.domain.model.QobuzStreamResponse
import com.vibevault.app.domain.model.toDomainTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
    private val syncScheduler: SyncScheduler,
    private val sessionManager: SessionManager,
    private val spotifyApi: com.vibevault.app.data.remote.api.SpotifyApiService
) : MusicRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val QOBUZ_INSTANCES = listOf(
        "https://qobuz.kennyy.com.br",
        "https://mono.scavengerfurs.net"
    )

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
        val localResults = likedSongDao.searchLikedSongs("%$query%")
        val localDomain = localResults.map { it.toDomain() }
        emit(localDomain)
    }

    override suspend fun searchSpotify(query: String): Result<List<Track>> {
        return Result.failure(Exception("Not implemented"))
    }

    override fun getDiscoveryTracks(): Flow<List<Track>> = flow {
        Log.d("SpotifyDebug", "MusicRepo: getDiscoveryTracks called (MOCKED)")
        emit(emptyList())
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
                Log.e("SpotifyDebug", "MusicRepo: track $trackId not found in history, cannot toggle like without metadata")
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                    Log.e("SpotifyDebug", "MusicRepo: Playlist tracks sync FAILED for ${dto.id}", e)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "MusicRepo: Playlists sync FAILED", e)
        }
        
        // 5. Sync Recently Played
        try {
            Log.d("SpotifyDebug", "MusicRepo: Syncing recently played...")
            syncRecentlyPlayed()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    override suspend fun updatePlaylistCoverUrl(playlistId: String, coverUrl: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(coverUrl = coverUrl, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }

    override suspend fun deletePlaylist(playlistId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        // Mark as deleted locally
        playlistDao.updatePlaylist(playlist.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        // Mocked track addition since we removed spotify
        val ref = PlaylistTrackCrossRef(
            playlistId = playlistId,
            trackId = trackId,
            title = "Unknown Track",
            artist = "Unknown Artist",
            album = "Unknown Album",
            albumImageUrl = "",
            durationMs = 0,
            isSynced = false,
            isDeleted = false,
            clientTimestamp = System.currentTimeMillis()
        )
        playlistDao.addTrackToPlaylist(ref)
        syncScheduler.syncNow()
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val ref = playlistDao.getCrossRef(playlistId, trackId) ?: return
        playlistDao.addTrackToPlaylist(ref.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }

    override suspend fun reorderTracks(playlistId: String, fromIndex: Int, toIndex: Int) {
        val currentTracks = playlistDao.getTracksForPlaylistSync(playlistId).toMutableList()
        if (fromIndex !in currentTracks.indices || toIndex !in currentTracks.indices || fromIndex == toIndex) return
        
        val item = currentTracks.removeAt(fromIndex)
        currentTracks.add(toIndex, item)
        
        val updatedTracks = currentTracks.mapIndexed { index, track ->
            track.copy(sortOrder = index, isSynced = false, clientTimestamp = System.currentTimeMillis())
        }
        playlistDao.updateTrackOrders(updatedTracks)
        syncScheduler.syncNow()
    }
    override fun getSpotifyRecentlyPlayed(): Flow<List<Track>> = flow {
        emit(emptyList())
    }

    override fun getFeaturedPlaylists(): Flow<List<Playlist>> = flow {
        emit(emptyList())
    }

    override fun getNewReleases(): Flow<List<Track>> = flow {
        emit(emptyList())
    }

    override fun getTopArtists(): Flow<List<Artist>> = flow {
        emit(emptyList())
    }

    override fun getUserSpotifyPlaylists(): Flow<List<Playlist>> = flow {
        emit(emptyList())
    }

    override fun getBrowseCategories(): Flow<List<Category>> = flow {
        emit(emptyList())
    }

    override suspend fun searchSpotifyAll(query: String): Result<SpotifySearchResult> {
        val fallbackResult = SpotifySearchResult(
            tracks = emptyList(),
            artists = emptyList(),
            albums = emptyList(),
            playlists = emptyList()
        )
        return Result.success(fallbackResult)
    }

    override fun getGlobalTop50(): Flow<List<Track>> = flow {
        emit(emptyList())
    }

    // ── Album Operations ─────────────────────────────────────

    override fun getUserSavedAlbums(): Flow<List<Album>> = flow {
        emit(emptyList())
    }

    // ── Artist Detail Operations ─────────────────────────────

    override suspend fun getArtistDetails(artistName: String): Result<Artist> {
        return Result.failure(Exception("Not implemented"))
    }

    override suspend fun getArtistTopTracks(artistId: String): Result<List<Track>> {
        return Result.failure(Exception("Not implemented"))
    }

    override suspend fun getArtistAlbums(artistId: String): Result<List<Album>> {
        return Result.failure(Exception("Not implemented"))
    }



    override suspend fun resolveSpotifyPlaylistToStreams(playlistId: String, token: String): List<String> {
        return withContext(Dispatchers.IO) {
            val response = spotifyApi.getPlaylistTracks("Bearer $token", playlistId)
            val streamUrls = mutableListOf<String>()

            for (item in response.items) {
                val track = item.track ?: continue
                val isrc = track.externalIds?.isrc
                if (isrc != null) {
                    try {
                        val qobuzTracks = searchQobuzMusic("isrc:$isrc")
                        val firstMatch = qobuzTracks.firstOrNull()
                        if (firstMatch != null) {
                            val streamUrl = getQobuzStreamUrl(firstMatch.id)
                            streamUrls.add(streamUrl)
                        }
                    } catch (e: Exception) {
                        Log.e("CrossService", "Failed to resolve track ${track.name} (ISRC: $isrc)", e)
                    }
                } else {
                    // Fallback to artist + title search if ISRC is missing
                    try {
                        val query = "${track.artists.firstOrNull()?.name ?: ""} ${track.name}"
                        val qobuzTracks = searchQobuzMusic(query)
                        val firstMatch = qobuzTracks.firstOrNull()
                        if (firstMatch != null) {
                            val streamUrl = getQobuzStreamUrl(firstMatch.id)
                            streamUrls.add(streamUrl)
                        }
                    } catch (e: Exception) {
                        Log.e("CrossService", "Failed to fallback resolve track ${track.name}", e)
                    }
                }
            }
            streamUrls
        }
    }

    // ── Qobuz / Main Data Operations ─────────────────────────

    override suspend fun searchQobuzMusic(query: String): List<Track> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val path = "/api/get-music?q=$encodedQuery&offset=0"

        var lastException: Exception? = null

        for (base in QOBUZ_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url(base + path)
                    .build()

                val response: Response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: $errorBody")
                }

                val bodyString = response.body?.string() ?: throw Exception("Empty response body")
                val searchResponse = gson.fromJson(bodyString, QobuzSearchResponse::class.java)

                val tracks = searchResponse.data?.tracks?.items?.map { it.toDomainTrack() } ?: emptyList()
                return@withContext tracks
            } catch (e: Exception) {
                Log.w("MusicRepository", "Instance $base failed: ${e.message}")
                lastException = e
            }
        }

        throw lastException ?: Exception("All Qobuz instances failed")
    }

    override suspend fun getQobuzStreamUrl(trackId: String): String = withContext(Dispatchers.IO) {
        val path = "/api/download-music?track_id=$trackId&quality=6"

        var lastException: Exception? = null

        for (base in QOBUZ_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url(base + path)
                    .build()

                val response: Response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: $errorBody")
                }

                val bodyString = response.body?.string() ?: throw Exception("Empty response body")
                val streamResponse = gson.fromJson(bodyString, QobuzStreamResponse::class.java)

                val url = streamResponse.data?.url
                if (url.isNullOrEmpty()) {
                    throw Exception("No playable stream URL returned")
                }
                return@withContext url
            } catch (e: Exception) {
                Log.w("MusicRepository", "Instance $base failed: ${e.message}")
                lastException = e
            }
        }

        throw lastException ?: Exception("All Qobuz instances failed to fetch stream")
    }

    override suspend fun getSpotifyPreviewUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        val token = sessionManager.spotifyAccessToken ?: return@withContext null
        if (token.isEmpty()) return@withContext null
        try {
            val track = spotifyApi.getTrack("Bearer $token", trackId)
            track.previewUrl
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSpotifyPlaylists(): List<com.vibevault.app.data.remote.dto.SpotifyPlaylistDto> = withContext(Dispatchers.IO) {
        val token = sessionManager.spotifyAccessToken ?: return@withContext emptyList()
        if (token.isEmpty()) return@withContext emptyList()
        try {
            val response = spotifyApi.getMyPlaylists("Bearer $token")
            response.items.filterNotNull()
        } catch (e: Exception) {
            Log.e("SpotifyPlaylists", "Failed to fetch Spotify playlists", e)
            emptyList()
        }
    }

    override suspend fun getSpotifyPlaylist(playlistId: String): com.vibevault.app.data.local.entity.PlaylistEntity? = withContext(Dispatchers.IO) {
        val token = sessionManager.spotifyAccessToken ?: return@withContext null
        if (token.isEmpty()) return@withContext null
        try {
            val dto = spotifyApi.getPlaylist("Bearer $token", playlistId)
            dto.toPlaylistEntity()
        } catch (e: Exception) {
            Log.e("SpotifyPlaylist", "Failed to fetch Spotify playlist metadata", e)
            null
        }
    }

    override suspend fun getSpotifyPlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val token = sessionManager.spotifyAccessToken
        Log.d("PlaylistDebug", "getSpotifyPlaylistTracks: playlistId=$playlistId, tokenPresent=${token != null}, tokenEmpty=${token?.isEmpty()}")
        if (token.isNullOrEmpty()) {
            Log.w("PlaylistDebug", "getSpotifyPlaylistTracks: No Spotify token available — returning empty list")
            return@withContext emptyList()
        }
        try {
            val response = spotifyApi.getPlaylistTracks("Bearer $token", playlistId)
            val tracks = response.items.mapNotNull { it.track?.toDomainTrack() }
            Log.d("PlaylistDebug", "getSpotifyPlaylistTracks: API returned ${response.items.size} items, mapped to ${tracks.size} tracks")
            tracks
        } catch (e: Exception) {
            Log.e("PlaylistDebug", "getSpotifyPlaylistTracks: API call failed for $playlistId", e)
            emptyList()
        }
    }
    override suspend fun getSimilarTracks(seedTrack: Track): List<Track> = withContext(Dispatchers.IO) {
        val token = sessionManager.spotifyAccessToken
        if (token.isNullOrEmpty()) {
            Log.w("SpotifyAutoplay", "No Spotify token available for recommendations.")
            return@withContext emptyList()
        }
        try {
            var spotifyId = seedTrack.id
            if (spotifyId.all { it.isDigit() }) { 
                // It's a Qobuz ID. We must find the Spotify ID.
                val searchResponse = spotifyApi.searchTracks("Bearer $token", "track:${seedTrack.title} artist:${seedTrack.artist}")
                val foundId = searchResponse.tracks?.items?.firstOrNull()?.id
                if (foundId != null) {
                    spotifyId = foundId
                } else {
                    Log.e("SpotifyAutoplay", "Could not find Spotify ID for ${seedTrack.title}")
                    return@withContext emptyList()
                }
            }

            val response = spotifyApi.getRecommendations("Bearer $token", seedTracks = spotifyId)
            val tracks = response.tracks.map { it.toDomainTrack() }
            Log.d("SpotifyAutoplay", "Fetched ${tracks.size} similar tracks for seed $spotifyId")
            tracks
        } catch (e: Exception) {
            Log.e("SpotifyAutoplay", "Failed to fetch similar tracks for ${seedTrack.id}", e)
            emptyList()
        }
    }
}
