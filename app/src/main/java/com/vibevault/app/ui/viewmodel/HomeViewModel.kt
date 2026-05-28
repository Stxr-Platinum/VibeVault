package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.remote.dto.SpotifySearchResponse
import com.vibevault.app.data.remote.api.SpotifyApiService
import com.vibevault.app.domain.model.*
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.data.local.entity.PlaylistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val spotifyApi: SpotifyApiService,
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) : ViewModel() {

    // 1. State Properties (Initialized first)

    // Recently Played
    val recentlyPlayed: StateFlow<List<Track>> = musicRepository.getRecentlyPlayed(10)
        .onEach { Log.d("SpotifyDebug", "HomeVM: RecentlyPlayed flow emitted ${it.size} tracks") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // Featured Playlists
    private val _featuredPlaylists = MutableStateFlow<List<com.vibevault.app.domain.model.Playlist>>(emptyList())
    val featuredPlaylists: StateFlow<List<com.vibevault.app.domain.model.Playlist>> = _featuredPlaylists.asStateFlow()

    // New Releases
    private val _newReleases = MutableStateFlow<List<Track>>(emptyList())
    val newReleases: StateFlow<List<Track>> = _newReleases.asStateFlow()

    // Top Artists
    private val _topArtists = MutableStateFlow<List<Artist>>(emptyList())
    val topArtists: StateFlow<List<Artist>> = _topArtists.asStateFlow()

    // Browse Categories
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // Global Top 50
    private val _top50Tracks = MutableStateFlow<List<Track>>(emptyList())
    val top50Tracks: StateFlow<List<Track>> = _top50Tracks.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<SpotifySearchResponse?>(null)
    val searchResults: StateFlow<SpotifySearchResponse?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // 2. Initialization Block (Runs after properties are initialized)

    init {
        Log.d("SpotifyDebug", "HomeVM: Initialized")
        // RE-NAVIGATE and FETCH: If token is null, wait for it.
        viewModelScope.launch {
            while (sessionManager.spotifyAccessToken == null) {
                Log.d("SpotifyDebug", "HomeVM: Waiting for Spotify token...")
                delay(1000)
            }
            Log.d("SpotifyDebug", "HomeVM: Token detected! Triggering auto-refresh.")
            refresh()
        }
    }

    // 3. Methods

    fun refresh() {
        Log.d("SpotifyDebug", "HomeVM: refresh() triggered")
        val token = sessionManager.spotifyAccessToken
        if (token == null) {
            Log.w("SpotifyDebug", "HomeVM: Spotify token null, skipping refresh")
            return
        }
        
        viewModelScope.launch {
            Log.d("SpotifyDebug", "HomeVM: Starting sync sequence...")
            
            // 1. Fetch Discovery from Spotify immediately
            refreshDiscovery()
            
            // 2. Fetch Home Sections
            refreshHomeSections()
            
            // 3. Sync from Supabase (cloud backup)
            musicRepository.syncFromRemote()
            
            // 4. Sync recent history
            musicRepository.syncRecentlyPlayed()
            
            // 4. Fallback: If still empty after 2s, seed trending tracks
            delay(2000)
            if (discoveryTracks.value.isEmpty() && recentlyPlayed.value.isEmpty()) {
                Log.w("SpotifyDebug", "HomeVM: Content empty, seeding trending tracks...")
                musicRepository.seedMockData()
            }
            
            _isLoading.value = false
        }
    }

    fun refreshDiscovery() {
        Log.d("SpotifyDebug", "HomeVM: refreshDiscovery() called")
        viewModelScope.launch {
            musicRepository.getDiscoveryTracks().collect { tracks ->
                Log.d("SpotifyDebug", "HomeVM: Discovery flow collected ${tracks.size} tracks")
                _discoveryTracks.value = tracks
            }
        }
    }

    fun refreshHomeSections() {
        Log.d("SpotifyDebug", "HomeVM: refreshHomeSections() called")
        viewModelScope.launch {
            musicRepository.getFeaturedPlaylists().collect { _featuredPlaylists.value = it }
        }
        viewModelScope.launch {
            musicRepository.getNewReleases().collect { _newReleases.value = it }
        }
        viewModelScope.launch {
            musicRepository.getTopArtists().collect { _topArtists.value = it }
        }
        viewModelScope.launch {
            musicRepository.getBrowseCategories().collect { _categories.value = it }
        }
        viewModelScope.launch {
            musicRepository.getGlobalTop50().collect { _top50Tracks.value = it }
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
        val token = sessionManager.spotifyAccessToken
        if (token == null) {
            Log.e("SpotifyDebug", "HomeVM: Search aborted - No token")
            return
        }
        
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
