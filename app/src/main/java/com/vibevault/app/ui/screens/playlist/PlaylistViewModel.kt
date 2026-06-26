package com.vibevault.app.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
    val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            val localPlaylist = musicRepository.getPlaylist(playlistId)
            if (localPlaylist != null) {
                _playlist.value = localPlaylist
                musicRepository.getPlaylistTracks(playlistId).collect {
                    _tracks.value = it
                }
            } else {
                // Try to find it in Spotify Playlists
                val spotifyPlaylists = musicRepository.getUserSpotifyPlaylists().first()
                val sp = spotifyPlaylists.find { it.id == playlistId }
                if (sp != null) {
                    _playlist.value = PlaylistEntity(
                        id = sp.id,
                        title = sp.title,
                        createdAt = 0L,
                        trackCount = sp.trackCount,
                        coverUrl = sp.coverUrl
                    )
                    _tracks.value = musicRepository.getSpotifyPlaylistTracks(playlistId)
                }
            }
        }
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
            loadPlaylist()
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }
}
