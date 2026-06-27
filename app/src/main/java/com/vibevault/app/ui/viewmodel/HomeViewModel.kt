package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.remote.api.SpotifyApiService
import com.vibevault.app.data.remote.dto.SpotifySearchResponse
import com.vibevault.app.domain.model.*
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickPickItem(
    val id: String,
    val title: String,
    val coverUrl: String,
    val type: String,
    val track: Track? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val musicRepository: MusicRepository,
    private val spotifyApi: SpotifyApiService,
    private val sessionManager: SessionManager,
    private val queueManager: com.vibevault.app.player.QueueManager
) : ViewModel() {

    private val prefs = context.getSharedPreferences("home_cache", android.content.Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    // 1. State Properties (Initialized first)

    // Recently Played
    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    // Liked Songs
    val likedSongs: StateFlow<List<Track>> = musicRepository.getLikedTracks()
        .onEach { Log.d("SpotifyDebug", "HomeVM: LikedSongs flow emitted ${it.size} tracks") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Loading State
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Discovery
    private val _discoveryTracks = MutableStateFlow<List<Track>>(emptyList())
    val discoveryTracks: StateFlow<List<Track>> = _discoveryTracks.asStateFlow()

    // Playlists
    val playlists: StateFlow<List<PlaylistEntity>> = musicRepository.getPlaylists()
        .onEach { Log.d("SpotifyDebug", "HomeVM: Playlists flow emitted ${it.size} entities") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Quick Picks
    private val _quickPicks = MutableStateFlow<List<QuickPickItem>>(emptyList())
    val quickPicks: StateFlow<List<QuickPickItem>> = _quickPicks.asStateFlow()

    // Trending Tracks
    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks: StateFlow<List<Track>> = _trendingTracks.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<SpotifySearchResponse?>(null)
    val searchResults: StateFlow<SpotifySearchResponse?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // User Avatar and Display Name
    val userAvatarUrl: StateFlow<String?> = sessionManager.userAvatarUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.userAvatarUrl)
        
    val userDisplayName: StateFlow<String?> = sessionManager.userDisplayNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.userDisplayName)

    val isSpotifyConnected: StateFlow<Boolean> = sessionManager.isSpotifyConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.isSpotifyConnected.value)

    // 2. Initialization Block (Runs after properties are initialized)

    init {
        Log.d("SpotifyDebug", "HomeVM: Initialized")
        viewModelScope.launch {
            Log.d("SpotifyDebug", "HomeVM: Triggering auto-refresh.")
            refresh()
        }
    }

    // 3. Methods

    fun disconnectSpotify() {
        sessionManager.clearSpotifySession()
    }

    fun refresh() {
        Log.d("SpotifyDebug", "HomeVM: refresh() triggered")
        
        // 1. Load cached tracks to show UI immediately
        val cachedTrendingJson = prefs.getString("trending_tracks", null)
        if (cachedTrendingJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<Track>>() {}.type
                val cachedTracks: List<Track> = gson.fromJson(cachedTrendingJson, type)
                if (cachedTracks.isNotEmpty()) {
                    _trendingTracks.value = cachedTracks
                }
            } catch (e: Exception) {
                Log.e("SpotifyDebug", "HomeVM: Failed to load cached trending tracks", e)
            }
        }
        
        // Unblock UI immediately
        _isLoading.value = false
        
        // Keep flows updated in background
        viewModelScope.launch {
            musicRepository.getRecentlyPlayed(10).collect { tracks ->
                if (tracks.isNotEmpty()) {
                    _recentlyPlayed.value = tracks
                }
            }
        }
        
        viewModelScope.launch {
            combine(
                musicRepository.getRecentlyPlayed(50),
                musicRepository.getPlaylists(),
                sessionManager.recentContextsFlow
            ) { recent, playlists, recentContexts ->
                val items = mutableListOf<QuickPickItem>()
                
                recentContexts.forEach { ctx ->
                    if (items.none { it.id == ctx.id }) {
                        items.add(QuickPickItem(ctx.id, ctx.title, ctx.coverUrl, ctx.type, null))
                    }
                }
                
                recent.forEach { track ->
                    val albumId = "album:${track.album}"
                    if (track.album.isNotEmpty() && items.none { it.id == albumId }) {
                        items.add(QuickPickItem(albumId, track.album, track.albumImageUrl, "album", track))
                    }
                }
                
                playlists.forEach { playlist ->
                    if (playlist.title.length > 1 && items.none { it.id == playlist.id }) {
                        items.add(QuickPickItem(playlist.id, playlist.title, playlist.coverUrl ?: "", "playlist", null))
                    }
                }
                items.take(8)
            }.collect { items ->
                if (items.isNotEmpty()) {
                    _quickPicks.value = items
                }
            }
        }
        
        viewModelScope.launch {
            Log.d("SpotifyDebug", "HomeVM: Starting sync sequence...")
            
            // 1. Sync recent history so personalized trending has data
            musicRepository.syncRecentlyPlayed()
            
            // 2. Fetch Trending
            fetchTrending()
            
            // 3. Sync from Supabase (cloud backup)
            musicRepository.syncFromRemote()
        }
    }

    private suspend fun fetchTrending() {
        Log.d("SpotifyDebug", "HomeVM: fetchTrending() called")
        val recent = musicRepository.getRecentlyPlayed(3).firstOrNull() ?: emptyList()
        if (recent.isNotEmpty()) {
            val combined = mutableListOf<Track>()
            recent.forEach { track ->
                val similar = musicRepository.getSimilarTracks(track).getOrNull()
                if (!similar.isNullOrEmpty()) {
                    combined.addAll(similar)
                } else if (track.artist.isNotBlank() && track.artist != "Unknown Artist" && track.artist != "Unknown") {
                    val artistSearch = musicRepository.searchSpotifyAll(track.artist).getOrNull()
                    if (artistSearch != null && artistSearch.tracks.isNotEmpty()) {
                        combined.addAll(artistSearch.tracks.take(10))
                    }
                }
            }
            if (combined.isNotEmpty()) {
                val finalTracks = combined.distinctBy { it.title }.shuffled().take(20)
                _trendingTracks.value = finalTracks
                prefs.edit().putString("trending_tracks", gson.toJson(finalTracks)).apply()
                return
            }
        }
        // Fallback to top hits
        val top = musicRepository.getGlobalTop50().firstOrNull()
        if (top != null) {
            val finalTracks = top.take(20)
            _trendingTracks.value = finalTracks
            prefs.edit().putString("trending_tracks", gson.toJson(finalTracks)).apply()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            performSearch(query)
        } else {
            _searchResults.value = null
        }
    }

    private fun performSearch(query: String) {
        Log.d("SpotifyDebug", "HomeVM: performSearch called for '$query'")
        
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val result = spotifyApi.searchTracks(query)
                result.onSuccess { response ->
                    Log.d("SpotifyDebug", "HomeVM: Search result SUCCESS - tracks count = ${response.tracks?.items?.size}")
                    _searchResults.value = response
                }
                result.onFailure {
                    Log.e("SpotifyDebug", "HomeVM: Search result FAILURE", it)
                    _searchResults.value = null
                }
            } catch (e: Exception) {
                Log.e("SpotifyDebug", "HomeVM: Search exception", e)
                _searchResults.value = null
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.recordPlay(track)
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleLike(track.id)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track.id)
        }
    }
}
