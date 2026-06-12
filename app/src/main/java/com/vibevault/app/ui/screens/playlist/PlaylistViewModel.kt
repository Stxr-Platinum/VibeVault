package com.vibevault.app.ui.screens.playlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val application: Application,
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
    val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                if (playlistId.startsWith("spotify_")) {
                    val realId = playlistId.removePrefix("spotify_")
                    android.util.Log.d("PlaylistDebug", "Loading Spotify playlist: $realId")
                    _playlist.value = musicRepository.getSpotifyPlaylist(realId)
                    val tracks = musicRepository.getSpotifyPlaylistTracks(realId)
                    android.util.Log.d("PlaylistDebug", "Fetched ${tracks.size} tracks for Spotify playlist $realId")
                    _tracks.value = tracks
                    if (tracks.isEmpty()) {
                        _errorMessage.value = "No tracks found. Spotify token may have expired — try re-logging into Spotify."
                    }
                } else {
                    _playlist.value = musicRepository.getPlaylist(playlistId)
                    musicRepository.getPlaylistTracks(playlistId).collect {
                        _tracks.value = it
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDebug", "Failed to load playlist $playlistId", e)
                _errorMessage.value = "Failed to load playlist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        loadPlaylist()
    }

    fun renamePlaylist(newName: String) {
        if (playlistId.startsWith("spotify_")) return
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
            loadPlaylist()
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        if (playlistId.startsWith("spotify_")) return
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun removeTrackFromPlaylist(trackId: String) {
        if (playlistId.startsWith("spotify_")) return
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (playlistId.startsWith("spotify_")) return
        viewModelScope.launch {
            musicRepository.reorderTracks(playlistId, fromIndex, toIndex)
        }
    }

    fun onImagePicked(uri: Uri) {
        if (playlistId.startsWith("spotify_")) return
        viewModelScope.launch {
            val permanentPath = copyImageToInternalStorage(uri)
            if (permanentPath != null) {
                musicRepository.updatePlaylistCoverUrl(playlistId, permanentPath)
                loadPlaylist()
            }
        }
    }

    private suspend fun copyImageToInternalStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = application.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            
            val playlistImagesDir = File(application.filesDir, "playlist_covers")
            if (!playlistImagesDir.exists()) playlistImagesDir.mkdirs()
            
            val imageFile = File(playlistImagesDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
            imageFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            "file://${imageFile.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }
}
