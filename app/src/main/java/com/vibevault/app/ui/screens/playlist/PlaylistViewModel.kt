package com.vibevault.app.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
    val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    val userPlaylists: StateFlow<List<PlaylistEntity>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            if (playlistId.startsWith("album:")) {
                val data = playlistId.removePrefix("album:").split("::")
                val albumName = data[0]
                val artistName = data.getOrNull(1) ?: ""

                _playlist.value = PlaylistEntity(
                    id = playlistId,
                    title = albumName,
                    ownerName = artistName.ifBlank { "Album" },
                    createdAt = 0L,
                    trackCount = 0,
                    coverUrl = null
                )
                
                val query = if (artistName.isNotEmpty()) "$albumName $artistName" else albumName
                
                musicRepository.searchOnline(query).onSuccess { results ->
                    var filtered = results.filter { 
                        (it.album.contains(albumName, ignoreCase = true) || albumName.contains(it.album, ignoreCase = true)) && 
                        (artistName.isEmpty() || it.artist.contains(artistName, ignoreCase = true) || artistName.contains(it.artist, ignoreCase = true))
                    }.distinctBy { it.title.trim().lowercase() }
                    
                    if (filtered.isEmpty() && artistName.isNotEmpty()) {
                        // Fallback: search just album name if the combined query returns nothing
                        musicRepository.searchOnline(albumName).onSuccess { fallbackResults ->
                            filtered = fallbackResults.filter { 
                                it.album.contains(albumName, ignoreCase = true) || albumName.contains(it.album, ignoreCase = true) 
                            }.distinctBy { it.title.trim().lowercase() }
                            _tracks.value = filtered
                            if (filtered.isNotEmpty()) {
                                _playlist.value = _playlist.value?.copy(
                                    coverUrl = filtered.first().albumImageUrl,
                                    trackCount = filtered.size
                                )
                            }
                        }
                    } else {
                        _tracks.value = filtered
                        if (filtered.isNotEmpty()) {
                            _playlist.value = _playlist.value?.copy(
                                coverUrl = filtered.first().albumImageUrl,
                                trackCount = filtered.size
                            )
                        }
                    }
                }
                return@launch
            }

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
                        ownerName = sp.ownerName ?: "Spotify",
                        createdAt = 0L,
                        trackCount = sp.trackCount,
                        coverUrl = sp.coverUrl?.takeIf { it.isNotBlank() }
                    )
                }
                
                // 1. Immediately display cached tracks for fast UI loading
                val cachedTracks = musicRepository.getSpotifyPlaylistTracks(playlistId)
                if (cachedTracks.isNotEmpty()) {
                    _tracks.value = cachedTracks
                    cachedTracks.firstOrNull()?.let {
                        Log.d("ImageSourceDebug", "[PlaylistViewModel] Cached Track '${it.title}' | Cover: '${it.albumImageUrl}'")
                    }
                }
                
                // 2. Simultaneously query Supabase and sync directly with Spotify API for this open playlist in background
                viewModelScope.launch {
                    val syncResult = musicRepository.forceRefreshSpotifyPlaylist(playlistId)
                    syncResult.onSuccess { (freshPlaylist, freshTracks) ->
                        if (freshTracks.isNotEmpty()) {
                            _tracks.value = freshTracks
                            freshTracks.firstOrNull()?.let {
                                Log.d("ImageSourceDebug", "[PlaylistViewModel] Fresh Track '${it.title}' | Cover: '${it.albumImageUrl}'")
                            }
                        }
                        if (freshPlaylist != null) {
                            _playlist.value = PlaylistEntity(
                                id = freshPlaylist.id,
                                title = freshPlaylist.title,
                                ownerName = freshPlaylist.ownerName ?: "Spotify",
                                createdAt = 0L,
                                trackCount = freshPlaylist.trackCount.takeIf { it > 0 } ?: freshTracks.size,
                                coverUrl = freshPlaylist.coverUrl?.takeIf { it.isNotBlank() } ?: sp?.coverUrl
                            )
                        }
                    }.onFailure { e ->
                        Log.e("PlaylistVM", "Background playlist sync failed for $playlistId", e)
                    }
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

    fun updatePlaylistCover(coverUrl: String) {
        viewModelScope.launch {
            musicRepository.updatePlaylistCover(playlistId, coverUrl)
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshPlaylist() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Check if this is a Spotify playlist (not local, not album)
                if (!playlistId.startsWith("album:")) {
                    val localPlaylist = musicRepository.getPlaylist(playlistId)
                    if (localPlaylist != null) {
                        // Local playlist - re-collect tracks
                        musicRepository.getPlaylistTracks(playlistId).collect {
                            _tracks.value = it
                        }
                    } else {
                        // Spotify playlist - instantly fetch fresh data from Supabase & sync directly with Spotify API for this playlist
                        val syncResult = musicRepository.forceRefreshSpotifyPlaylist(playlistId)
                        syncResult.onSuccess { (freshPlaylist, freshTracks) ->
                            if (freshTracks.isNotEmpty()) {
                                _tracks.value = freshTracks
                            }
                            if (freshPlaylist != null) {
                                _playlist.value = PlaylistEntity(
                                    id = freshPlaylist.id,
                                    title = freshPlaylist.title,
                                    ownerName = freshPlaylist.ownerName ?: "Spotify",
                                    createdAt = 0L,
                                    trackCount = freshPlaylist.trackCount.takeIf { it > 0 } ?: freshTracks.size,
                                    coverUrl = freshPlaylist.coverUrl?.takeIf { it.isNotBlank() }
                                )
                            }
                        }.onFailure { e ->
                            Log.e("PlaylistVM", "Failed to refresh Spotify playlist $playlistId", e)
                        }
                    }
                } else {
                    // Album - reload
                    loadPlaylist()
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Failed to refresh playlist", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun recordPlayed() {
        val p = _playlist.value ?: return
        val type = if (p.id.startsWith("album:")) "album" else "playlist"
        sessionManager.addRecentContext(p.id, type, p.title, p.coverUrl ?: "")
    }

    fun addAlbumToPlaylist(targetPlaylistId: String) {
        viewModelScope.launch {
            _tracks.value.forEach { track ->
                musicRepository.addTrackToPlaylist(targetPlaylistId, track.id)
            }
        }
    }
}
