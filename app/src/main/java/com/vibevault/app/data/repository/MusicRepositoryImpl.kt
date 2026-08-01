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
        // Fetch recommendations from Spotify based on a recently liked track
        val recentLikedIds: List<String> = likedSongDao.getRecentLikedIds()
        val seedId: String? = recentLikedIds.firstOrNull()
        Log.d("SpotifyDebug", "MusicRepo: Seed ID for recommendations = $seedId")
        
        spotifyApi.getRecommendations(seedId).onSuccess { dtos ->
            Log.d("SpotifyDebug", "MusicRepo: Recommendations Success - count = ${dtos.size}")
            val likedIds = likedSongDao.getLikedSongIds().toSet()
            val tracks = dtos.mapNotNull { dto ->
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
            }
            Log.d("SpotifyDebug", "MusicRepo: Emitting ${tracks.size} discovery tracks")
            emit(tracks)
        }.onFailure { err ->
            Log.e("SpotifyDebug", "MusicRepo: Recommendations FAILURE. Fetching trending tracks.", err)
            searchSpotifyAll("trending").onSuccess { result ->
                emit(result.tracks.take(15))
            }.onFailure {
                Log.e("SpotifyDebug", "MusicRepo: Trending FAILURE. Mocking 5 discovery tracks.", it)
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
    override fun getSpotifyRecentlyPlayed(): Flow<List<Track>> = flow { emit(emptyList()) }

    override fun getFeaturedPlaylists(): Flow<List<Playlist>> = flow {
        val featured = listOf(
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
        emit(featured)
    }

    override fun getNewReleases(): Flow<List<Track>> = flow { emit(emptyList()) }

    override fun getTopArtists(): Flow<List<Artist>> = flow { emit(emptyList()) }

    private val _spotifyPlaylistsTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    override fun getUserSpotifyPlaylists(): Flow<List<Playlist>> = _spotifyPlaylistsTrigger.map {
        try {
            val userId = sessionManager.userId
            if (userId != null) {
                val dtos = postgrest.from("spotify_playlists")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<com.vibevault.app.data.remote.dto.SupabaseSpotifyPlaylistDto>()
                
                dtos.map { dto ->
                    Playlist(
                        id = dto.playlistId,
                        title = dto.name,
                        description = dto.description,
                        coverUrl = dto.image,
                        ownerName = dto.ownerName,
                        trackCount = dto.trackCount
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getSpotifyPlaylistTracks(playlistId: String): List<Track> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            Log.e("MusicRepo", "Failed fetching spotify tracks from Supabase, trying online YouTube playlist", e)
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

        tracks
    }

    override suspend fun cacheSpotifyPlaylist(playlistId: String, title: String, coverUrl: String?, tracks: List<Track>) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanTitle = title.ifBlank { "Playlist" }
                val entity = PlaylistEntity(
                    id = playlistId,
                    title = cleanTitle,
                    description = "Spotify Playlist",
                    coverUrl = coverUrl ?: "",
                    trackCount = tracks.size,
                    durationMs = tracks.sumOf { it.durationMs },
                    isPublic = true,
                    isSynced = true,
                    ownerName = "Spotify"
                )
                playlistDao.insertPlaylist(entity)

                tracks.forEach { track ->
                    try {
                        likedSongDao.insertLikedSong(track.toLikedEntity().copy(isDeleted = true))
                    } catch (e: Exception) {
                        // ignore duplicate track insert
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepo", "Failed caching Spotify playlist into Room DB", e)
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
        val userId = sessionManager.userId ?: return
        try {
            val result = spotifyApi.getUserPlaylists()
            if (result.isSuccess) {
                val playlists = result.getOrNull() ?: emptyList()
                val dtos = playlists.map {
                    SupabaseSpotifyPlaylistDto(
                        userId = userId,
                        playlistId = it.id,
                        name = it.name,
                        description = it.description,
                        image = it.images.firstOrNull()?.url,
                        ownerName = it.owner?.displayName,
                        trackCount = 0 // Stub, wait, do we have track count?
                    )
                }
                
                // Diff and sync logic
                val existingPlaylists = postgrest.from("spotify_playlists")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<SupabaseSpotifyPlaylistDto>()
                    
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
                
                // Trigger observers to reload playlists
                _spotifyPlaylistsTrigger.tryEmit(Unit)
            }
        } catch (e: Exception) {
            Log.e("SpotifySync", "Error syncing playlists", e)
        }
    }
    
    override suspend fun backgroundSyncSpotifyPlaylistTracks(playlistId: String) {
        val userId = sessionManager.userId ?: return
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
                    return
                }
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
                                coverUrl = t.album?.images?.firstOrNull()?.url ?: "",
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
        }
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
