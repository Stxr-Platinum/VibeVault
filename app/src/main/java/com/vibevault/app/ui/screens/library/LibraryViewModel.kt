package com.vibevault.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.vibevault.app.data.remote.dto.SpotifyPlaylistDto
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class PlaylistWithTracks(
    val playlist: com.vibevault.app.data.local.entity.PlaylistEntity,
    val coverTracks: List<String>
)

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

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylistDto>>(emptyList())
    val spotifyPlaylists = _spotifyPlaylists.asStateFlow()

    init {
        fetchSpotifyPlaylists()
    }

    private fun fetchSpotifyPlaylists() {
        viewModelScope.launch {
            _spotifyPlaylists.value = musicRepository.getSpotifyPlaylists()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistsWithTracks: StateFlow<List<PlaylistWithTracks>> = musicRepository.getPlaylists()
        .flatMapLatest { playlists ->
            if (playlists.isEmpty()) return@flatMapLatest flowOf(emptyList())
            
            val playlistFlows = playlists.map { playlist ->
                musicRepository.getPlaylistTracks(playlist.id).map { tracks ->
                    PlaylistWithTracks(
                        playlist = playlist,
                        coverTracks = tracks.take(4).map { it.albumImageUrl }
                    )
                }
            }
            combine(playlistFlows) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(title)
        }
    }
}
