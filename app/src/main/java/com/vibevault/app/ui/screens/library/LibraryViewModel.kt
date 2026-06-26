package com.vibevault.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) : ViewModel() {
    val userDisplayName = sessionManager.userDisplayNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.userDisplayName)

    val userAvatarUrl = sessionManager.userAvatarUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.userAvatarUrl)

    val likedTracks: StateFlow<List<Track>> = musicRepository.getLikedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<com.vibevault.app.data.local.entity.PlaylistEntity>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spotifyPlaylists: StateFlow<List<com.vibevault.app.domain.model.Playlist>> = musicRepository.getUserSpotifyPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(title)
        }
    }
    
    init {
        viewModelScope.launch {
            musicRepository.backgroundSyncSpotifyPlaylists()
        }
    }
}
