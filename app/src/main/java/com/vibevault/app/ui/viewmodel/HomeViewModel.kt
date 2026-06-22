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
    private val musicRepository: MusicRepository,
    private val spotifyApi: SpotifyApiService,
    private val sessionManager: SessionManager
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

    // Quick Picks
    val quickPicks: StateFlow<List<QuickPickItem>> = combine(
        musicRepository.getRecentlyPlayed(50),
        musicRepository.getPlaylists()
    ) { recent, playlists ->
        val items = mutableListOf<QuickPickItem>()
        recent.forEach { track ->
            val albumId = "album:${track.album}"
            if (track.album.isNotEmpty() && items.none { it.id == albumId }) {
                items.add(QuickPickItem(albumId, track.album, track.albumImageUrl, "album", track))
            }
        }
        playlists.forEach { playlist ->
            if (items.none { it.id == playlist.id }) {
                items.add(QuickPickItem(playlist.id, playlist.title, playlist.coverUrl ?: "", "playlist", null))
            }
        }
        items.take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // User Avatar
    val userAvatarUrl: StateFlow<String?> = sessionManager.userAvatarUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.userAvatarUrl)

    // 2. Initialization Block (Runs after properties are initialized)

    init {
        Log.d("SpotifyDebug", "HomeVM: Initialized")
        viewModelScope.launch {
            Log.d("SpotifyDebug", "HomeVM: Triggering auto-refresh.")
            refresh()
        }
    }

    // 3. Methods

    fun refresh() {
        Log.d("SpotifyDebug", "HomeVM: refresh() triggered")
        
        viewModelScope.launch {
            Log.d("SpotifyDebug", "HomeVM: Starting sync sequence...")
            
            // 1. Sync recent history so personalized trending has data
            musicRepository.syncRecentlyPlayed()
            
            // 2. Fetch Trending
            fetchTrending()
            
            // 3. Sync from Supabase (cloud backup)
            musicRepository.syncFromRemote()
            
            _isLoading.value = false
        }
    }

    private suspend fun fetchTrending() {
        Log.d("SpotifyDebug", "HomeVM: fetchTrending() called")
        val recent = musicRepository.getRecentlyPlayed(3).firstOrNull() ?: emptyList()
        if (recent.isNotEmpty()) {
            val combined = mutableListOf<Track>()
            recent.forEach { track ->
                val similar = musicRepository.getSimilarTracks(track.id).getOrNull()
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
                _trendingTracks.value = combined.distinctBy { it.title }.take(20)
                return
            }
        }
        // Fallback to top hits
        val top = musicRepository.getGlobalTop50().firstOrNull()
        if (top != null) {
            _trendingTracks.value = top.take(20)
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
