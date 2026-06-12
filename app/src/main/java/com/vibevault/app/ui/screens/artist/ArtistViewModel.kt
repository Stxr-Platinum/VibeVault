package com.vibevault.app.ui.screens.artist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.Album
import com.vibevault.app.domain.model.Artist
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    private val _topTracks = MutableStateFlow<List<Track>>(emptyList())
    val topTracks: StateFlow<List<Track>> = _topTracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Keep backward compat: expose tracks as a flat list for the old callers
    val artistTracks: StateFlow<List<Track>> = _topTracks

    fun loadArtist(name: String) {
        _isLoading.value = true
        viewModelScope.launch {
            // 1. Search for the artist to get their Spotify ID and image
            val artistResult = musicRepository.getArtistDetails(name)
            val foundArtist = artistResult.getOrNull()

            if (foundArtist != null) {
                _artist.value = foundArtist
                Log.d("SpotifyDebug", "ArtistVM: Found artist ${foundArtist.name} (${foundArtist.id})")

                // 2. Fetch top tracks using artist ID
                launch {
                    val tracksResult = musicRepository.getArtistTopTracks(foundArtist.id)
                    tracksResult.onSuccess { tracks ->
                        Log.d("SpotifyDebug", "ArtistVM: Loaded ${tracks.size} top tracks")
                        _topTracks.value = tracks
                    }.onFailure {
                        Log.e("SpotifyDebug", "ArtistVM: Top tracks FAILED, falling back to local", it)
                        // Fallback to local liked songs by artist
                        musicRepository.getTracksByArtist(name).collect { localTracks ->
                            _topTracks.value = localTracks
                        }
                    }
                }

                // 3. Fetch artist albums
                launch {
                    val albumsResult = musicRepository.getArtistAlbums(foundArtist.id)
                    albumsResult.onSuccess { albums ->
                        Log.d("SpotifyDebug", "ArtistVM: Loaded ${albums.size} albums")
                        _albums.value = albums
                    }.onFailure {
                        Log.e("SpotifyDebug", "ArtistVM: Albums FAILED", it)
                    }
                }
            } else {
                Log.w("SpotifyDebug", "ArtistVM: Artist not found on Spotify, falling back to local")
                _artist.value = Artist(id = "", name = name, imageUrl = null)
                // Fallback: use local tracks filtered by artist name
                musicRepository.getTracksByArtist(name).collect { localTracks ->
                    _topTracks.value = localTracks
                }
            }

            _isLoading.value = false
        }
    }
}
