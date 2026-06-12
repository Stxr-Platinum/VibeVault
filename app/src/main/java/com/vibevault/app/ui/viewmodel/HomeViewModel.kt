package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) : ViewModel() {

    // User Profile (reactive)
    val userAvatarUrl: StateFlow<String?> = sessionManager.userAvatarUrlFlow
    val userDisplayName: StateFlow<String?> = sessionManager.userDisplayNameFlow

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

    // Saved Albums
    private val _savedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val savedAlbums: StateFlow<List<Album>> = _savedAlbums.asStateFlow()

    // Global Top 50
    private val _top50Tracks = MutableStateFlow<List<Track>>(emptyList())
    val top50Tracks: StateFlow<List<Track>> = _top50Tracks.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>?>(null)
    val searchResults: StateFlow<List<Track>?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // 2. Initialization Block (Runs after properties are initialized)

    init {
        Log.d("SpotifyDebug", "HomeVM: Initialized")
        // Just refresh data immediately.
        viewModelScope.launch {
            refresh()
        }
    }

    // 3. Methods

    fun refresh() {
        Log.d("SpotifyDebug", "HomeVM: refresh() triggered")
        
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
        viewModelScope.launch {
            musicRepository.getUserSavedAlbums().collect { _savedAlbums.value = it }
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
                val tracks = musicRepository.searchQobuzMusic(query)
                Log.d("SpotifyDebug", "HomeVM: Search result SUCCESS - tracks count = ${tracks.size}")
                _searchResults.value = tracks
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
