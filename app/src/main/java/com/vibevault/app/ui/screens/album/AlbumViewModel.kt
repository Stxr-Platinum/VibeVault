package com.vibevault.app.ui.screens.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.AlbumDetails
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val queueManager: QueueManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val albumId: String = savedStateHandle.get<String>("albumId") ?: ""

    private val _albumDetails = MutableStateFlow<AlbumDetails?>(null)
    val albumDetails: StateFlow<AlbumDetails?> = _albumDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (albumId.isNotBlank()) {
            loadAlbumDetails(albumId)
        }
    }

    fun loadAlbumDetails(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            musicRepository.getAlbumDetails(id)
                .onSuccess { details ->
                    _albumDetails.value = details
                    _isLoading.value = false
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Failed to load album details"
                    _isLoading.value = false
                }
        }
    }

    fun playAlbum(startIndex: Int = 0) {
        val details = _albumDetails.value ?: return
        if (details.tracks.isNotEmpty()) {
            queueManager.setQueue(details.tracks, startIndex)
        }
    }
}
