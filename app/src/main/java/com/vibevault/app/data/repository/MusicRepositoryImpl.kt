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
import com.vibevault.app.player.queue.filterDiverseCharacteristics
import com.vibevault.app.extensions.toTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicRepositoryImpl — Implementation of MusicRepository.
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
            entities.distinctBy { entity ->
                val cleanTitle = entity.title.trim().lowercase()
                val cleanArtist = entity.artist.trim().lowercase()
                if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                    "$cleanTitle::$cleanArtist"
                } else {
                    entity.trackId
                }
            }.take(limit).map { entity ->
                Track(
                    id = entity.trackId,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album.ifEmpty { "Unknown" },
                    albumImageUrl = entity.albumImageUrl,
                    durationMs = 0,
                    playedAt = entity.playedAt
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
                val remoteTracks = response.tracks?.items?.mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    Track(
                        id = id,
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

    override suspend fun searchOnline(query: String): Result<List<Track>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val searchQuery = query.trim()
                if (searchQuery.isEmpty()) return@withContext Result.success(emptyList())

                Log.d("SearchDebug", "Searching iTunes for: $searchQuery")
                val url = java.net.URL("https://itunes.apple.com/search?term=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}&entity=song&limit=25")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    val results = json.optJSONArray("results") ?: org.json.JSONArray()
                    
                    val tracks = mutableListOf<Track>()
                    val likedIds = likedSongDao.getLikedSongIds().toSet()
                    
                    for (i in 0 until results.length()) {
                        val song = results.getJSONObject(i)
                        
                        val songId = song.optInt("trackId", 0).toString()
                        if (songId == "0") continue
                        
                        val title = song.optString("trackName", "Unknown Title")
                        val artist = song.optString("artistName", "Unknown Artist")
                        val album = song.optString("collectionName", "")
                        val coverUrl = song.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
                        val durationMs = song.optLong("trackTimeMillis", 0L)
                        
                        tracks.add(Track(
                            id = songId,
                            title = title,
                            artist = artist,
                            album = album,
                            albumImageUrl = coverUrl,
                            audioUrl = "", // StreamResolver handles this
                            durationMs = durationMs,
                            isLiked = likedIds.contains(songId),
                            source = "itunes"
                        ))
                    }
                    Result.success(tracks)
                } else {
                    Result.failure(Exception("iTunes API error: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("SearchDebug", "iTunes search failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getArtistTopSongs(artistName: String): Result<List<Track>> {
        return searchOnline(artistName).map { tracks -> 
            tracks.filter { it.artist.contains(artistName, ignoreCase = true) }.take(10) 
        }
    }

    override suspend fun getArtistLatestAlbums(artistName: String): Result<List<com.vibevault.app.domain.model.Album>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(artistName, "UTF-8")
                val url = java.net.URL("https://itunes.apple.com/search?term=$encodedQuery&entity=album&limit=20")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = org.json.JSONObject(response)
                    val results = jsonResponse.optJSONArray("results")
                    
                    val albums = mutableListOf<com.vibevault.app.domain.model.Album>()
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val albumObj = results.optJSONObject(i) ?: continue
                            
                            val albumId = albumObj.optString("collectionId", "")
                            val title = albumObj.optString("collectionName", "Unknown")
                            val artist = albumObj.optString("artistName", "Unknown")
                            
                            if (!artist.contains(artistName, ignoreCase = true)) continue

                            var coverUrl = albumObj.optString("artworkUrl100", "")
                            if (coverUrl.isNotEmpty()) {
                                coverUrl = coverUrl.replace("100x100bb", "600x600bb")
                            }
                            
                            var releaseDate = albumObj.optString("releaseDate", "")
                            if (releaseDate.isNotEmpty()) {
                                releaseDate = releaseDate.substring(0, 4) // Just get the year for simplicity, or format it
                            }
                            
                            albums.add(com.vibevault.app.domain.model.Album(
                                id = "album:${title}::${artist}", // Keep consistent with existing album routing
                                title = title,
                                artist = artist,
                                coverUrl = coverUrl,
                                releaseDate = releaseDate
                            ))
                        }
                    }
                    Result.success(albums.sortedByDescending { it.releaseDate })
                } else {
                    Result.failure(Exception("iTunes API error: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("SearchDebug", "iTunes album search failed", e)
                Result.failure(e)
            }
        }
    }

    override fun getDiscoveryTracks(): Flow<List<Track>> = flow {
        Log.d("SpotifyDebug", "MusicRepo: getDiscoveryTracks called")
        val recentLikedIds: List<String> = likedSongDao.getRecentLikedIds()
        val seedId: String? = recentLikedIds.firstOrNull()
        Log.d("SpotifyDebug", "MusicRepo: Seed ID for recommendations = $seedId")

        var discoveryTracks: List<Track> = emptyList()

        if (!seedId.isNullOrBlank()) {
            try {
                val seedTrack = Track(id = seedId, title = "", artist = "", album = "", albumImageUrl = "", durationMs = 0L)
                val similarResult = getSimilarTracks(seedTrack).getOrNull()
                if (!similarResult.isNullOrEmpty()) {
                    discoveryTracks = similarResult
                    Log.d("SpotifyDebug", "MusicRepo: Found ${discoveryTracks.size} discovery tracks from seed $seedId")
                }
            } catch (e: Exception) {
                Log.w("SpotifyDebug", "MusicRepo: Failed to fetch similar tracks for seed $seedId", e)
            }
        }

        if (discoveryTracks.isEmpty()) {
            val trendingResult = searchOnline("trending hits").getOrNull()
            if (!trendingResult.isNullOrEmpty()) {
                discoveryTracks = trendingResult.take(15)
                Log.d("SpotifyDebug", "MusicRepo: Found ${discoveryTracks.size} discovery tracks from trending search")
            }
        }

        if (discoveryTracks.isNotEmpty()) {
            emit(discoveryTracks)
        } else {
            val mockTracks = listOf(
                Track(id = "3B54sVLJ402zHx6TmEte1Z", title = "Starlight", artist = "Muse", album = "Black Holes", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2738c82eb57fcd88147ea4dd302", durationMs = 239000, source = "youtube"),
                Track(id = "7MXVkk9YMqqclZ63nXGIRC", title = "Starboy", artist = "The Weeknd", album = "Starboy", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452", durationMs = 230000, source = "youtube"),
                Track(id = "7BKLCZ1jbUBVqRi2FVlTVw", title = "Closer", artist = "The Chainsmokers", album = "Collage EP", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b273d40cc1cb1703e83b8a13539a", durationMs = 244000, source = "youtube"),
                Track(id = "5HCyWlXZPP0y6Gqq8TgA20", title = "Stay", artist = "The Kid LAROI", album = "F*CK LOVE 3", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b27386c8f94d30e386a604246820", durationMs = 141000, source = "youtube"),
                Track(id = "37BZB0z9T8Xu7U3e65qxFy", title = "Save Your Tears", artist = "The Weeknd", album = "After Hours", albumImageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", durationMs = 215000, source = "youtube")
            )
            emit(mockTracks)
        }
    }

    override suspend fun getSimilarTracks(track: Track): Result<List<Track>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val trackId = track.id
            val likedIds = likedSongDao.getLikedSongIds().toSet()

            // 0. Primary: YouTube Related Endpoint (Matching vivi-music's acoustic & style similarity algorithm)
            try {
                val nextResult = com.music.innertube.YouTube.next(com.music.innertube.models.WatchEndpoint(videoId = trackId)).getOrNull()
                val relatedEndpoint = nextResult?.relatedEndpoint
                if (relatedEndpoint != null) {
                    val relatedResult = com.music.innertube.YouTube.related(relatedEndpoint).getOrNull()
                    if (relatedResult != null && relatedResult.songs.isNotEmpty()) {
                        val tracks = relatedResult.songs.map { song ->
                            Track(
                                id = song.id,
                                title = song.title,
                                artist = song.artists.joinToString(", ") { it.name },
                                album = song.album?.name ?: "",
                                albumImageUrl = song.thumbnail,
                                durationMs = (song.duration ?: 0) * 1000L,
                                isLiked = likedIds.contains(song.id),
                                source = "youtube"
                            )
                        }.filterDiverseCharacteristics(maxPerArtist = 2, currentlyPlayingTrackId = trackId)

                        if (tracks.isNotEmpty()) {
                            return@withContext Result.success(tracks)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "YouTube related songs failed, falling back", e)
            }

            try {
                val watchEndpoint = com.music.innertube.models.WatchEndpoint(
                    videoId = trackId,
                    playlistId = "RDAMVM$trackId"
                )
                val nextResult = com.music.innertube.YouTube.next(watchEndpoint).getOrNull()
                if (nextResult != null && nextResult.items.isNotEmpty()) {
                    val tracks = nextResult.items.map { it.toTrack() }
                        .filterDiverseCharacteristics(maxPerArtist = 1, currentlyPlayingTrackId = trackId)
                    if (tracks.isNotEmpty()) {
                        return@withContext Result.success(tracks)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "YouTube fallback radio failed", e)
            }

            Result.success(emptyList())
        }
    }

    override fun getTracksByArtist(artistName: String): Flow<List<Track>> = 
        getLikedTracks().map { tracks -> 
            tracks.filter { it.artist.equals(artistName, ignoreCase = true) } 
        }

    override suspend fun toggleLike(trackId: String) {
        val current = likedSongDao.getLikedSong(trackId)
        if (current != null) {
            val newState = !current.isDeleted
            likedSongDao.insertLikedSong(current.copy(isDeleted = newState, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        } else {
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
            }
        }
        try {
            syncScheduler.syncNow()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun toggleLikeTrack(track: Track) {
        val allLiked = likedSongDao.getAllLikedSongs().firstOrNull() ?: emptyList()
        val current = allLiked.find { 
            it.id == track.id || 
            (it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true))
        }

        if (current != null) {
            val newState = !current.isDeleted
            likedSongDao.insertLikedSong(current.copy(isDeleted = newState, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        } else {
            val entity = LikedSongEntity(
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
            likedSongDao.insertLikedSong(entity)
        }
        try {
            syncScheduler.syncNow()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun recordPlay(track: Track) {
        historyDao.insertHistory(
            HistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
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
        searchOnline("trending").onSuccess { tracks ->
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
                    limit(50)
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
                    album = dto.album ?: "",
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
            searchOnline(query).onSuccess { tracks ->
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

    override suspend fun updatePlaylistCover(playlistId: String, coverUrl: String) {
        // 1. Update in local Room database if present
        val localPlaylist = playlistDao.getPlaylistById(playlistId)
        if (localPlaylist != null) {
            playlistDao.updatePlaylist(localPlaylist.copy(coverUrl = coverUrl, isSynced = false, clientTimestamp = System.currentTimeMillis()))
            syncScheduler.syncNow()
        }

        // 2. Update in-memory & persistent disk cache for Spotify playlists
        val updatedCache = _cachedSpotifyPlaylists.value.map {
            if (it.id == playlistId) it.copy(coverUrl = coverUrl) else it
        }
        saveSpotifyPlaylistsToCache(updatedCache)
        _spotifyPlaylistsTrigger.tryEmit(Unit)

        // 3. Update in Supabase `spotify_playlists` table if user logged in
        // Only send web-compatible URLs (http://, https://, data:image/...) to Supabase
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://") || coverUrl.startsWith("data:image/")) {
            val userId = sessionManager.userId
            if (userId != null) {
                try {
                    postgrest.from("spotify_playlists")
                        .update(mapOf("image" to coverUrl)) {
                            filter {
                                eq("user_id", userId)
                                eq("playlist_id", playlistId)
                            }
                        }
                } catch (e: Exception) {
                    Log.e("MusicRepo", "Failed updating playlist cover in Supabase", e)
                }
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        addDeletedSpotifyPlaylistId(playlistId)
        
        // 1. Remove from in-memory and disk cached Spotify playlists
        val updatedCache = _cachedSpotifyPlaylists.value.filter { it.id != playlistId }
        saveSpotifyPlaylistsToCache(updatedCache)
        _spotifyPlaylistsTrigger.tryEmit(Unit)

        // 2. Delete from Supabase if user logged in
        val userId = sessionManager.userId
        if (userId != null) {
            try {
                postgrest.from("spotify_playlists")
                    .delete {
                        filter {
                            eq("user_id", userId)
                            eq("playlist_id", playlistId)
                        }
                    }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Failed to delete Spotify playlist from Supabase", e)
            }
        }

        // 3. Mark as deleted in local Room database if present
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
            syncScheduler.syncNow()
        }
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

    override suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean {
        val crossRef = playlistDao.getCrossRef(playlistId, trackId)
        return crossRef != null && !crossRef.isDeleted
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val ref = playlistDao.getCrossRef(playlistId, trackId) ?: return
        playlistDao.addTrackToPlaylist(ref.copy(isDeleted = true, isSynced = false, clientTimestamp = System.currentTimeMillis()))
        syncScheduler.syncNow()
    }
    override fun getSpotifyRecentlyPlayed(): Flow<List<Track>> = flow { emit(emptyList()) }

    @Volatile
    private var cachedFeaturedPlaylists: List<Playlist>? = null
    @Volatile
    private var cachedFeaturedTimestamp: Long = 0L

    override fun getFeaturedPlaylists(): Flow<List<Playlist>> = flow {
        val now = System.currentTimeMillis()
        val cacheTtlMs = 60 * 60 * 1000L // 1 hour TTL
        
        cachedFeaturedPlaylists?.let { cached ->
            if (now - cachedFeaturedTimestamp < cacheTtlMs && cached.isNotEmpty()) {
                emit(cached)
                return@flow
            }
        }

        val dynamicPlaylists = mutableListOf<Playlist>()

        try {
            val homePage = com.music.innertube.YouTube.home().getOrNull()
            
            homePage?.sections?.forEach { section ->
                section.items.filterIsInstance<com.music.innertube.models.PlaylistItem>().forEach { item ->
                    if (item.id.isNotBlank() && item.title.isNotBlank() && dynamicPlaylists.none { it.id == item.id }) {
                        dynamicPlaylists.add(
                            Playlist(
                                id = item.id,
                                title = item.title,
                                description = section.title,
                                coverUrl = item.thumbnail,
                                ownerName = item.author?.name ?: "YouTube Music",
                                trackCount = 50
                            )
                        )
                    }
                }
            }

            if (dynamicPlaylists.isEmpty()) {
                val explorePage = com.music.innertube.YouTube.explore().getOrNull()
                explorePage?.newReleaseAlbums?.forEach { album ->
                    if (album.playlistId != null && album.title.isNotBlank() && dynamicPlaylists.none { it.id == album.playlistId }) {
                        dynamicPlaylists.add(
                            Playlist(
                                id = album.playlistId!!,
                                title = album.title,
                                description = album.artists?.firstOrNull()?.name ?: "New Release",
                                coverUrl = album.thumbnail,
                                ownerName = album.artists?.firstOrNull()?.name ?: "YouTube Music",
                                trackCount = 50
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch dynamic InnerTube browse playlists", e)
        }

        if (dynamicPlaylists.isNotEmpty()) {
            cachedFeaturedPlaylists = dynamicPlaylists
            cachedFeaturedTimestamp = now
            emit(dynamicPlaylists)
        } else {
            val fallback = listOf(
                Playlist(
                    id = "RDCLAK5uy_kL21mX1f0b001n-071a9l9019",
                    title = "Global Top 50",
                    description = "The most played tracks right now across the globe.",
                    coverUrl = "https://lh3.googleusercontent.com/w4pS8M-a26",
                    ownerName = "YouTube Music",
                    trackCount = 50
                ),
                Playlist(
                    id = "RDCLAK5uy_n9FAC29uL9d4",
                    title = "Today's Hits",
                    description = "Biggest hit songs right now.",
                    coverUrl = "https://lh3.googleusercontent.com/v_18pL0m02",
                    ownerName = "VibeVault",
                    trackCount = 50
                ),
                Playlist(
                    id = "RDCLAK5uy_m-78_g3xX0",
                    title = "Pop Rising",
                    description = "The next generation of pop superstars.",
                    coverUrl = "https://lh3.googleusercontent.com/a-10xP0",
                    ownerName = "VibeVault",
                    trackCount = 50
                ),
                Playlist(
                    id = "RDCLAK5uy_l4309uX_0",
                    title = "Chill Vibes",
                    description = "Relaxing, acoustic and chill hits.",
                    coverUrl = "https://lh3.googleusercontent.com/b-20yQ1",
                    ownerName = "VibeVault",
                    trackCount = 50
                )
            )
            cachedFeaturedPlaylists = fallback
            cachedFeaturedTimestamp = now
            emit(fallback)
        }
    }

    override fun getNewReleases(): Flow<List<Track>> = flow { emit(emptyList()) }

    override fun getTopArtists(): Flow<List<Artist>> = flow { emit(emptyList()) }

    private val gson = com.google.gson.Gson()
    private val prefs get() = sessionManager.prefs

    override fun clearSpotifyCache() {
        prefs.edit().remove("persistent_spotify_playlists").remove("deleted_spotify_playlist_ids").apply()
        _cachedSpotifyPlaylists.value = emptyList()
        _spotifyPlaylistsTrigger.tryEmit(Unit)
    }

    private val _cachedSpotifyPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _spotifyPlaylistsTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    private fun getDeletedSpotifyPlaylistIds(): Set<String> {
        return prefs.getStringSet("deleted_spotify_playlist_ids", emptySet()) ?: emptySet()
    }

    private fun addDeletedSpotifyPlaylistId(id: String) {
        val current = getDeletedSpotifyPlaylistIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("deleted_spotify_playlist_ids", current).apply()
    }

    private fun loadSpotifyPlaylistsFromCache(): List<Playlist> {
        val json = prefs.getString("persistent_spotify_playlists", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Playlist>>() {}.type
            gson.fromJson<List<Playlist>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSpotifyPlaylistsToCache(playlists: List<Playlist>) {
        _cachedSpotifyPlaylists.value = playlists
        try {
            prefs.edit().putString("persistent_spotify_playlists", gson.toJson(playlists)).apply()
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to save spotify playlists to prefs cache", e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getUserSpotifyPlaylists(): Flow<List<Playlist>> = _spotifyPlaylistsTrigger.flatMapLatest {
        flow {
            val deletedIds = getDeletedSpotifyPlaylistIds()
            if (_cachedSpotifyPlaylists.value.isEmpty()) {
                val diskCached = loadSpotifyPlaylistsFromCache()
                if (diskCached.isNotEmpty()) {
                    _cachedSpotifyPlaylists.value = diskCached
                }
            }

            var playlists = _cachedSpotifyPlaylists.value.filter { it.id !in deletedIds }
            
            // 1. Emit cached immediately for instant UI render
            emit(playlists)

            // 2. Always fetch & update cache from Supabase `spotify_playlists` if user is signed in
            val currentUserId = sessionManager.userId
            if (currentUserId != null) {
                try {
                    val dtos = postgrest.from("spotify_playlists")
                        .select {
                            filter { eq("user_id", currentUserId) }
                        }
                        .decodeList<com.vibevault.app.data.remote.dto.SupabaseSpotifyPlaylistDto>()
                    
                    val supabasePlaylists = dtos.map { dto ->
                        Playlist(
                            id = dto.playlistId,
                            title = dto.name,
                            description = dto.description,
                            coverUrl = (dto.displayCoverUrl ?: dto.image)?.takeIf { it.isNotBlank() },
                            ownerName = dto.ownerName,
                            trackCount = dto.trackCount
                        )
                    }.filter { it.id !in deletedIds }

                    if (supabasePlaylists.isNotEmpty()) {
                        playlists = supabasePlaylists
                        saveSpotifyPlaylistsToCache(playlists)
                        emit(playlists)
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepo", "Failed fetching spotify playlists from Supabase", e)
                }
            }

            // 3. Always update from Spotify API if connected to keep playlists in sync
            if (sessionManager.isSpotifyConnected.value) {
                try {
                    val result = spotifyApi.getUserPlaylists()
                    if (result.isSuccess) {
                        val dtos = result.getOrNull() ?: emptyList()
                        val directPlaylists = dtos.map { dto ->
                            val existingCover = playlists.find { p -> p.id == dto.id }?.coverUrl
                            Playlist(
                                id = dto.id,
                                title = dto.name,
                                description = dto.description,
                                coverUrl = existingCover ?: dto.images.firstOrNull()?.url?.takeIf { it.isNotBlank() },
                                ownerName = dto.owner?.displayName,
                                trackCount = 0
                            )
                        }.filter { it.id !in deletedIds }

                        if (directPlaylists.isNotEmpty()) {
                            playlists = directPlaylists
                            saveSpotifyPlaylistsToCache(playlists)
                            emit(playlists)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepo", "Failed fetching spotify playlists directly from API", e)
                }
            }
        }
    }

    private fun getSpotifyPlaylistTracksFromCache(playlistId: String): List<Track> {
        val json = prefs.getString("persistent_spotify_tracks_$playlistId", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Track>>() {}.type
            gson.fromJson<List<Track>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSpotifyPlaylistTracksToCache(playlistId: String, tracks: List<Track>) {
        try {
            prefs.edit().putString("persistent_spotify_tracks_$playlistId", gson.toJson(tracks)).apply()
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to save playlist tracks to prefs cache", e)
        }
    }

    override suspend fun getSpotifyPlaylistTracks(playlistId: String): List<Track> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cachedTracks = getSpotifyPlaylistTracksFromCache(playlistId)
        if (cachedTracks.isNotEmpty()) {
            return@withContext cachedTracks
        }

        val tracks = mutableListOf<Track>()
        try {
            val userId = sessionManager.userId
            if (userId != null) {
                val dtos = postgrest.from("spotify_playlist_tracks")
                    .select {
                        filter { 
                            eq("user_id", userId)
                            eq("playlist_id", playlistId)
                        }
                    }
                    .decodeList<com.vibevault.app.data.remote.dto.SupabaseSpotifyPlaylistTrackDto>()
                
                tracks.addAll(dtos.sortedBy { it.position }.map { dto ->
                    Track(
                        id = dto.spotifyTrackId,
                        title = dto.title,
                        artist = dto.artist,
                        album = dto.album,
                        albumImageUrl = dto.coverUrl,
                        durationMs = dto.durationMs
                    )
                })
            }
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed fetching spotify tracks from Supabase", e)
        }

        if (tracks.isEmpty() && sessionManager.isSpotifyConnected.value) {
            try {
                val result = spotifyApi.getPlaylistTracks(playlistId)
                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()
                    val directTracks = items.mapNotNull { item ->
                        val t = item.track ?: item.item ?: return@mapNotNull null
                        val id = t.id ?: return@mapNotNull null
                        Track(
                            id = id,
                            title = t.name,
                            artist = t.artists.firstOrNull()?.name ?: "Unknown",
                            album = t.album?.name ?: "Unknown",
                            albumImageUrl = t.album?.images?.firstOrNull()?.url ?: "",
                            durationMs = t.durationMs
                        )
                    }
                    if (directTracks.isNotEmpty()) {
                        tracks.addAll(directTracks)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Direct Spotify track fetch failed for $playlistId", e)
            }
        }

        if (tracks.isEmpty()) {
            try {
                val ytPlaylist = com.music.innertube.YouTube.playlist(playlistId).getOrNull()
                if (ytPlaylist != null && ytPlaylist.songs.isNotEmpty()) {
                    val onlineTracks = ytPlaylist.songs.map { it.toTrack() }
                    tracks.addAll(onlineTracks)
                    cacheSpotifyPlaylist(
                        playlistId = playlistId,
                        title = ytPlaylist.playlist.title ?: "Playlist",
                        coverUrl = ytPlaylist.playlist.thumbnail,
                        tracks = onlineTracks
                    )
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "YouTube playlist fetch failed for $playlistId", e)
            }
        }

        if (tracks.isNotEmpty()) {
            saveSpotifyPlaylistTracksToCache(playlistId, tracks)
        }

        tracks
    }

    override suspend fun cacheSpotifyPlaylist(playlistId: String, title: String, coverUrl: String?, tracks: List<Track>) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Cache track metadata locally for offline lookup without inserting a user PlaylistEntity
                tracks.forEach { track ->
                    try {
                        likedSongDao.insertLikedSong(track.toLikedEntity().copy(isDeleted = true))
                    } catch (e: Exception) {
                        // ignore duplicate track insert
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Failed caching playlist tracks into Room DB", e)
            }
        }
    }

    override fun getBrowseCategories(): Flow<List<Category>> = flow { emit(emptyList()) }

    companion object {
        @Volatile
        var activeSearchInstance = "https://us-west.monochrome.tf"
    }

    override suspend fun searchSpotifyAll(query: String): Result<SpotifySearchResult> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var searchQuery = query
                val baseInstances = listOf(
                    "https://us-west.monochrome.tf",
                    "https://eu-central.monochrome.tf",
                    "https://api.monochrome.tf",
                    "https://monochrome-api.samidy.com",
                    "https://hifi-two.spotisaver.net"
                )
                
                val active = activeSearchInstance
                val API_INSTANCES = listOf(active) + baseInstances.filter { it != active }

                for (instance in API_INSTANCES) {
                    try {
                        val url = java.net.URL("$instance/search/?limit=25&s=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        if (connection.responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().readText()
                            val json = org.json.JSONObject(response)
                            val data = json.optJSONObject("data")
                            val items = data?.optJSONArray("items") ?: org.json.JSONArray()
                            
                            val tracks = mutableListOf<Track>()
                            val likedIds = likedSongDao.getLikedSongIds().toSet()
                            
                            for (i in 0 until items.length()) {
                                val content = items.getJSONObject(i)
                                
                                val trackId = content.optInt("id", 0).toString()
                                if (trackId == "0") continue
                                
                                val title = content.optString("title", "Unknown")
                                val durationSec = content.optInt("duration", 0)
                                
                                val artistObj = content.optJSONObject("artist")
                                val artistName = artistObj?.optString("name", "Unknown") ?: "Unknown"
                                
                                val albumObj = content.optJSONObject("album")
                                val albumName = albumObj?.optString("title", "Unknown") ?: "Unknown"
                                
                                val coverUuid = albumObj?.optString("cover", "") ?: ""
                                val coverUrl = if (coverUuid.isNotEmpty()) {
                                    "https://resources.tidal.com/images/${coverUuid.replace("-", "/")}/640x640.jpg"
                                } else ""
                                
                                tracks.add(Track(
                                    id = trackId,
                                    title = title,
                                    artist = artistName,
                                    album = albumName,
                                    albumImageUrl = coverUrl,
                                    durationMs = durationSec * 1000L,
                                    isLiked = likedIds.contains(trackId),
                                    source = "tidal"
                                ))
                            }
                            activeSearchInstance = instance
                            return@withContext Result.success(SpotifySearchResult(tracks, emptyList(), emptyList(), emptyList()))
                        }
                    } catch (e: Exception) {
                        Log.w("MusicRepo", "Search failed via $instance", e)
                    }
                }
                Result.failure(Exception("All API instances failed for search"))
            } catch (e: Exception) {
                Log.e("MusicRepo", "Search failed with exception", e)
                Result.failure(e)
            }
        }
    }

    override fun getGlobalTop50(): Flow<List<Track>> = flow {
        searchSpotifyAll("billboard hot 100").onSuccess { result ->
            emit(result.tracks)
        }.onFailure { 
            emit(emptyList())
        }
    }

    override suspend fun backgroundSyncSpotifyPlaylists() {
        try {
            val result = spotifyApi.getUserPlaylists()
            if (result.isSuccess) {
                val playlists = result.getOrNull() ?: emptyList()
                val mappedPlaylists = playlists.map { dto ->
                    Playlist(
                        id = dto.id,
                        title = dto.name,
                        description = dto.description,
                        coverUrl = dto.images.firstOrNull()?.url,
                        ownerName = dto.owner?.displayName,
                        trackCount = 0
                    )
                }
                _cachedSpotifyPlaylists.value = mappedPlaylists

                val userId = sessionManager.userId
                if (userId != null) {
                    val existingPlaylists = postgrest.from("spotify_playlists")
                        .select { filter { eq("user_id", userId) } }
                        .decodeList<SupabaseSpotifyPlaylistDto>()
                    val existingMap = existingPlaylists.associateBy { it.playlistId }

                    val dtos = playlists.map {
                        val existingImage = existingMap[it.id]?.displayCoverUrl
                        SupabaseSpotifyPlaylistDto(
                            userId = userId,
                            playlistId = it.id,
                            name = it.name,
                            description = it.description,
                            image = existingImage ?: it.images.firstOrNull()?.url,
                            ownerName = it.owner?.displayName,
                            trackCount = 0
                        )
                    }
                        
                    val newIds = dtos.map { it.playlistId }.toSet()
                    val idsToDelete = existingPlaylists.map { it.playlistId }.filter { !newIds.contains(it) }
                    
                    if (idsToDelete.isNotEmpty()) {
                        for (chunk in idsToDelete.chunked(50)) {
                            postgrest.from("spotify_playlists").delete {
                                filter { 
                                    eq("user_id", userId)
                                    isIn("playlist_id", chunk)
                                }
                            }
                        }
                    }
                    
                    if (dtos.isNotEmpty()) {
                        for (chunk in dtos.chunked(50)) {
                            postgrest.from("spotify_playlists").upsert(chunk) {
                                onConflict = "user_id, playlist_id"
                            }
                        }
                    }
                }
                
                // Trigger observers to reload playlists
                _spotifyPlaylistsTrigger.tryEmit(Unit)
            }
        } catch (e: Exception) {
            Log.e("SpotifySync", "Error syncing playlists", e)
        }
    }
    
    override suspend fun backgroundSyncSpotifyPlaylistTracks(playlistId: String): Result<Unit> = runCatching {
        val userId = sessionManager.userId ?: return@runCatching
        try {
            val result = spotifyApi.getPlaylistTracks(playlistId)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                Log.d("SpotifySync", "API returned ${items.size} items for playlist $playlistId")
                
                val parsed = items.mapNotNull { item ->
                    if (item.track == null && item.item == null) {
                        Log.d("SpotifySync", "Item track and item are both null!")
                        return@mapNotNull null
                    }
                    val t = item.track ?: item.item ?: return@mapNotNull null
                    if (t.id == null) {
                        Log.d("SpotifySync", "Track ${t.name} has no ID (likely local), skipping.")
                        return@mapNotNull null
                    }
                    t
                }.distinctBy { it.id!! }
                
                if (parsed.isEmpty()) {
                    Log.d("SpotifySync", "Parsed tracks is empty. Skipping sync to avoid erasing existing tracks.")
                    return@runCatching
                }

                // Update per-playlist local cache immediately
                val freshTracksList = parsed.map { t ->
                    Track(
                        id = t.id!!,
                        title = t.name,
                        artist = t.artists.firstOrNull()?.name ?: "Unknown",
                        album = t.album?.name ?: "Unknown",
                        albumImageUrl = t.album?.images?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: t.album?.images?.firstOrNull()?.url ?: "",
                        durationMs = t.durationMs
                    )
                }
                saveSpotifyPlaylistTracksToCache(playlistId, freshTracksList)

                val existingTracks = postgrest.from("spotify_playlist_tracks")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("playlist_id", playlistId)
                        }
                    }
                    .decodeList<SupabaseSpotifyPlaylistTrackDto>()
                    
                val existingMap = existingTracks.associateBy({ it.spotifyTrackId }, { it.position })
                
                val newIdsSet = mutableSetOf<String>()
                val rowsToUpsert = mutableListOf<SupabaseSpotifyPlaylistTrackDto>()
                
                for ((i, t) in parsed.withIndex()) {
                    val trackId = t.id!!
                    newIdsSet.add(trackId)
                    
                    val existingPos = existingMap[trackId]
                    if (existingPos == null || existingPos != i) {
                        rowsToUpsert.add(
                            SupabaseSpotifyPlaylistTrackDto(
                                userId = userId,
                                playlistId = playlistId,
                                spotifyTrackId = trackId,
                                title = t.name,
                                artist = t.artists.firstOrNull()?.name ?: "Unknown",
                                album = t.album?.name ?: "Unknown",
                                coverUrl = t.album?.images?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: t.album?.images?.firstOrNull()?.url ?: "",
                                durationMs = t.durationMs,
                                position = i
                            )
                        )
                    }
                }
                
                val idsToDelete = existingMap.keys.filter { !newIdsSet.contains(it) }
                
                if (idsToDelete.isNotEmpty()) {
                    for (chunk in idsToDelete.chunked(50)) {
                        postgrest.from("spotify_playlist_tracks").delete {
                            filter {
                                eq("user_id", userId)
                                eq("playlist_id", playlistId)
                                isIn("spotify_track_id", chunk)
                            }
                        }
                    }
                }
                
                if (rowsToUpsert.isNotEmpty()) {
                    for (chunk in rowsToUpsert.chunked(50)) {
                        postgrest.from("spotify_playlist_tracks").upsert(chunk) {
                            onConflict = "user_id, playlist_id, spotify_track_id"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SpotifySync", "Error syncing tracks for $playlistId", e)
            throw e
        }
    }

    override suspend fun forceRefreshSpotifyPlaylist(playlistId: String): Result<Pair<Playlist?, List<Track>>> = runCatching {
        var freshPlaylist: Playlist? = null
        val freshTracks = mutableListOf<Track>()
        val userId = sessionManager.userId

        // 1. Fetch from Supabase for this specific playlist ID
        if (userId != null) {
            try {
                val playlistDtos = postgrest.from("spotify_playlists")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("playlist_id", playlistId)
                        }
                    }
                    .decodeList<SupabaseSpotifyPlaylistDto>()
                
                val spDto = playlistDtos.firstOrNull()
                if (spDto != null) {
                    freshPlaylist = Playlist(
                        id = spDto.playlistId,
                        title = spDto.name,
                        description = spDto.description,
                        coverUrl = (spDto.displayCoverUrl ?: spDto.image)?.takeIf { it.isNotBlank() },
                        ownerName = spDto.ownerName,
                        trackCount = spDto.trackCount
                    )
                }

                val trackDtos = postgrest.from("spotify_playlist_tracks")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("playlist_id", playlistId)
                        }
                    }
                    .decodeList<SupabaseSpotifyPlaylistTrackDto>()
                
                if (trackDtos.isNotEmpty()) {
                    val supabaseTracks = trackDtos.sortedBy { it.position }.map { dto ->
                        Track(
                            id = dto.spotifyTrackId,
                            title = dto.title,
                            artist = dto.artist,
                            album = dto.album,
                            albumImageUrl = dto.coverUrl,
                            durationMs = dto.durationMs
                        )
                    }
                    freshTracks.clear()
                    freshTracks.addAll(supabaseTracks)
                    saveSpotifyPlaylistTracksToCache(playlistId, supabaseTracks)
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Failed fetching per-playlist data from Supabase for $playlistId", e)
            }
        }

        // 2. Sync directly with Spotify API for this specific playlist ID
        if (sessionManager.isSpotifyConnected.value) {
            try {
                val apiResult = spotifyApi.getPlaylistTracks(playlistId)
                if (apiResult.isSuccess) {
                    val items = apiResult.getOrNull() ?: emptyList()
                    val spotifyTracks = items.mapNotNull { item ->
                        val t = item.track ?: item.item ?: return@mapNotNull null
                        val id = t.id ?: return@mapNotNull null
                        Track(
                            id = id,
                            title = t.name,
                            artist = t.artists.firstOrNull()?.name ?: "Unknown",
                            album = t.album?.name ?: "Unknown",
                            albumImageUrl = t.album?.images?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: t.album?.images?.firstOrNull()?.url ?: "",
                            durationMs = t.durationMs
                        )
                    }.distinctBy { it.id }

                    if (spotifyTracks.isNotEmpty()) {
                        freshTracks.clear()
                        freshTracks.addAll(spotifyTracks)
                        saveSpotifyPlaylistTracksToCache(playlistId, spotifyTracks)
                        backgroundSyncSpotifyPlaylistTracks(playlistId)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Failed syncing per-playlist with Spotify API for $playlistId", e)
            }
        }

        // 3. Update single playlist entry in _cachedSpotifyPlaylists memory & disk cache
        if (freshPlaylist != null || freshTracks.isNotEmpty()) {
            val currentCache = _cachedSpotifyPlaylists.value.toMutableList()
            val index = currentCache.indexOfFirst { it.id == playlistId }
            val updatedPlaylist = Playlist(
                id = playlistId,
                title = freshPlaylist?.title ?: currentCache.getOrNull(index)?.title ?: "Playlist",
                description = freshPlaylist?.description ?: currentCache.getOrNull(index)?.description,
                coverUrl = freshPlaylist?.coverUrl ?: currentCache.getOrNull(index)?.coverUrl,
                ownerName = freshPlaylist?.ownerName ?: currentCache.getOrNull(index)?.ownerName ?: "Spotify",
                trackCount = freshTracks.size.takeIf { it > 0 } ?: freshPlaylist?.trackCount ?: currentCache.getOrNull(index)?.trackCount ?: 0
            )

            if (index != -1) {
                currentCache[index] = updatedPlaylist
            } else {
                currentCache.add(updatedPlaylist)
            }
            saveSpotifyPlaylistsToCache(currentCache)
        }

        Pair(freshPlaylist, freshTracks)
    }

    override suspend fun getAlbumDetails(albumId: String): Result<com.vibevault.app.domain.model.AlbumDetails> = runCatching {
        val cleanId = albumId.removePrefix("album:")
        val targetBrowseId = if (cleanId.startsWith("MPREb_") || cleanId.startsWith("OLAK5uy_")) {
            cleanId
        } else {
            val parts = cleanId.split("::")
            val searchQuery = if (parts.size >= 2) "${parts[0]} ${parts[1]}" else cleanId
            val searchResult = com.music.innertube.YouTube.search(searchQuery, com.music.innertube.YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
            val foundAlbum = searchResult?.items?.filterIsInstance<com.music.innertube.models.AlbumItem>()?.firstOrNull()
            foundAlbum?.id ?: throw IllegalArgumentException("Could not resolve YouTube Music album ID for: $cleanId")
        }

        val albumPage = com.music.innertube.YouTube.album(targetBrowseId).getOrThrow()
        val albumItem = albumPage.album
        val tracks = albumPage.songs.map { song ->
            Track(
                id = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                album = albumItem.title,
                albumImageUrl = albumItem.thumbnail,
                durationMs = (song.duration ?: 0) * 1000L,
                source = "youtube"
            )
        }
        val otherVersions = albumPage.otherVersions.map { other ->
            com.vibevault.app.domain.model.Album(
                id = other.id,
                title = other.title,
                artist = other.artists?.joinToString(", ") { it.name } ?: "",
                coverUrl = other.thumbnail,
                releaseDate = other.year?.toString() ?: ""
            )
        }
        com.vibevault.app.domain.model.AlbumDetails(
            id = targetBrowseId,
            title = albumItem.title,
            artist = albumItem.artists?.joinToString(", ") { it.name } ?: "Unknown",
            year = albumItem.year?.toString(),
            albumImageUrl = albumItem.thumbnail,
            playlistId = albumItem.playlistId,
            tracks = tracks,
            description = albumPage.description,
            otherVersions = otherVersions
        )
    }
}
