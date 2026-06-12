package com.vibevault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistOrchestratorViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun playSpotifyPlaylist(playlistId: String, spotifyToken: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                // Resolve stream URLs
                val streamUrls = musicRepository.resolveSpotifyPlaylistToStreams(playlistId, spotifyToken)
                
                val mediaItemsToPlay = streamUrls.map { streamUrl ->
                    MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaId(streamUrl) 
                        .build()
                }
                
                // Pass the resolved streams to Media3 ExoPlayer
                mediaController?.let { controller ->
                    controller.setMediaItems(mediaItemsToPlay)
                    controller.prepare()
                    controller.play()
                    _uiState.value = UiState.Success("Playing ${mediaItemsToPlay.size} tracks!")
                } ?: run {
                    _uiState.value = UiState.Error("MediaController not connected")
                }

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val error: String) : UiState()
    }
}
