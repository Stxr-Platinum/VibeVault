package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vibevault.app.data.mapper.toDomain
import com.vibevault.app.data.remote.api.SpotifyApiService
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.QueueManager
import com.vibevault.app.player.SpotifyPlayerManager
import com.vibevault.app.player.media.AudioFocusManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.vibevault.app.core.session.SessionManager

/**
 * PlayerViewModel — Bridges Media3 ExoPlayer and Spotify App Remote to the Compose UI,
 * using QueueManager as the single source of truth for playlist state.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: ExoPlayer,
    private val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val audioFocusManager: AudioFocusManager,
    private val spotifyPlayerManager: SpotifyPlayerManager,
    private val spotifyApiService: SpotifyApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    // ── Playback State (Delegated to QueueManager) ──────────
    val currentTrack: StateFlow<Track?> = queueManager.currentTrack
    val queue: StateFlow<List<Track>> = queueManager.queueState
    val currentIndex: StateFlow<Int> = queueManager.currentIndex
    val shuffleModeEnabled: StateFlow<Boolean> = queueManager.shuffleModeEnabled
    val repeatMode: StateFlow<Int> = queueManager.repeatMode

    // ── Local State ─────────────────────────────────────────
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _spotifyError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val spotifyError: SharedFlow<String> = _spotifyError.asSharedFlow()

    private var currentlyPlayingTrackId: String? = null

    init {
        // 0. Load last played track
        viewModelScope.launch {
            sessionManager.lastPlayedTrackId?.let { trackId ->
                try {
                    val result = spotifyApiService.getTrack(trackId)
                    val dto = result.getOrNull()
                    if (dto != null) {
                        queueManager.syncExternalTrack(dto.toDomain())
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // 1. Observe QueueManager's currentTrack to trigger playback
        viewModelScope.launch {
            queueManager.currentTrack.collect { track ->
                if (track != null && track.id != currentlyPlayingTrackId) {
                    playInternal(track)
                    musicRepository.recordPlay(track)
                } else if (track == null) {
                    stopAllPlayback()
                }
            }
        }

        // 2. Listen to ExoPlayer state changes
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (isStreamableUrl(currentTrack.value?.audioUrl ?: currentTrack.value?.externalUrl ?: currentTrack.value?.id ?: "")) {
                    _isPlaying.value = playing
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0)
                } else if (playbackState == Player.STATE_ENDED) {
                    // ExoPlayer finished the track. Let QueueManager advance.
                    queueManager.next()
                }
            }
        })

        // 3. Progress ticker
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0)
                } else {
                    val spotifyState = spotifyPlayerManager.playerState.value
                    if (spotifyState != null && !spotifyState.isPaused) {
                        _currentPosition.value = (_currentPosition.value + 500L).coerceAtMost(_duration.value)
                    }
                }
                delay(500)
            }
        }

        // 4. Observe Spotify App Remote PlayerState
        viewModelScope.launch {
            spotifyPlayerManager.playerState.collect { state ->
                if (state != null) {
                    _isPlaying.value = !state.isPaused
                    _duration.value = state.track.duration
                    
                    if (kotlin.math.abs(_currentPosition.value - state.playbackPosition) > 1000) {
                        _currentPosition.value = state.playbackPosition
                    }
                    
                    val currentTrackId = currentTrack.value?.id ?: ""
                    val stateTrackId = state.track.uri.removePrefix("spotify:track:")
                    
                    // If Spotify advanced to the next track on its own, sync the QueueManager
                    if (stateTrackId != currentTrackId && stateTrackId.isNotEmpty() && !state.track.uri.contains("spotify:ad:")) {
                        // Create basic track info
                        val externalTrack = Track(
                            id = stateTrackId,
                            title = state.track.name,
                            artist = state.track.artist.name,
                            album = state.track.album.name,
                            albumImageUrl = "", 
                            audioUrl = state.track.uri,
                            durationMs = state.track.duration,
                            isLiked = false
                        )
                        
                        // Sync QueueManager to this track (it will update currentIndex if it's in the queue)
                        queueManager.syncExternalTrack(externalTrack)
                        currentlyPlayingTrackId = stateTrackId
                        
                        // Fetch full metadata for images
                        try {
                            val result = spotifyApiService.getTrack(stateTrackId)
                            val dto = result.getOrNull()
                            if (dto != null) {
                                queueManager.syncExternalTrack(dto.toDomain())
                            }
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            spotifyPlayerManager.errorEvents.collect { error ->
                _isPlaying.value = false
                _spotifyError.tryEmit(error.message)
            }
        }
    }

    // ── Internal Playback Execution ────────────────────────

    private fun isStreamableUrl(uri: String): Boolean =
        uri.startsWith("http://") || uri.startsWith("https://")

    private fun stopAllPlayback() {
        player.pause()
        player.clearMediaItems()
        if (_isPlaying.value && !isStreamableUrl(currentTrack.value?.audioUrl ?: currentTrack.value?.externalUrl ?: currentTrack.value?.id ?: "")) {
            spotifyPlayerManager.pause()
        }
        _isPlaying.value = false
        currentlyPlayingTrackId = null
    }

    /**
     * Executes playback for EXACTLY ONE track. Queue progression is handled by STATE_ENDED.
     */
    private fun playInternal(track: Track) {
        currentlyPlayingTrackId = track.id
        sessionManager.lastPlayedTrackId = track.id
        val uriString = track.audioUrl ?: track.externalUrl ?: track.id
        
        Log.d("PlaybackDebug", "playInternal: ${track.title} ($uriString)")
        
        if (!isStreamableUrl(uriString)) {
            player.pause()
            player.clearMediaItems()
            _isPlaying.value = true
            
            // Fix: Spotify App Remote playTrack is a suspend function.
            // Launch a coroutine to call it safely without blocking UI flow.
            viewModelScope.launch {
                val success = spotifyPlayerManager.playTrack("spotify:track:${track.id}")
                if (!success) {
                    _isPlaying.value = false
                }
            }
            return
        }

        // It is streamable. Stop Spotify.
        spotifyPlayerManager.pause()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uriString)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()

        audioFocusManager.requestFocus(player)
        // Set only this single item so STATE_ENDED triggers reliably
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // ── Public API ─────────────────────────────────────────

    fun playTrack(trackId: String, context: List<Track> = emptyList()) {
        viewModelScope.launch {
            val effectiveContext = if (context.isEmpty()) {
                try {
                    val result = spotifyApiService.getTrack(trackId)
                    val dto = result.getOrNull()
                    if (dto != null) listOf(dto.toDomain()) else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                context
            }

            if (effectiveContext.isNotEmpty()) {
                queueManager.playTrack(trackId, effectiveContext)
            }
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
    }

    fun togglePlayPause() {
        val currentUri = currentTrack.value?.let { it.audioUrl ?: it.externalUrl ?: it.id } ?: ""
        if (!isStreamableUrl(currentUri)) {
            if (_isPlaying.value) {
                spotifyPlayerManager.pause()
                _isPlaying.value = false
            } else {
                spotifyPlayerManager.resume()
                _isPlaying.value = true
            }
        } else {
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val currentUri = currentTrack.value?.let { it.audioUrl ?: it.externalUrl ?: it.id } ?: ""
        if (!isStreamableUrl(currentUri)) {
            spotifyPlayerManager.seekTo(positionMs)
        } else {
            player.seekTo(positionMs)
        }
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        Log.d("PlaybackDebug", "skipNext called")
        queueManager.next()
    }

    fun skipPrevious() {
        Log.d("PlaybackDebug", "skipPrevious called")
        // 5-second rule: If we are past 5 seconds, restart current track
        if (_currentPosition.value > 5000L) {
            seekTo(0L)
        } else {
            queueManager.previous()
        }
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        player.volume = clampedVolume
        _volume.value = clampedVolume
    }

    fun toggleLike() {
        viewModelScope.launch {
            val track = currentTrack.value ?: return@launch
            musicRepository.toggleLike(track.id)
            queueManager.syncExternalTrack(track.copy(isLiked = !track.isLiked))
        }
    }

    val playlists = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioFocusManager.abandonFocus()
    }
}
