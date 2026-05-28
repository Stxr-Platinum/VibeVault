package com.vibevault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.media.AudioFocusManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.vibevault.app.player.SpotifyPlayerManager

/**
 * PlayerViewModel — Bridges Media3 ExoPlayer to the Compose UI.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: ExoPlayer,
    private val musicRepository: MusicRepository,
    private val audioFocusManager: AudioFocusManager,
    private val spotifyPlayerManager: SpotifyPlayerManager
) : ViewModel() {

    // ── Playback State ─────────────────────────────────────
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /** Spotify-specific error messages for the UI to display */
    private val _spotifyError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val spotifyError: SharedFlow<String> = _spotifyError.asSharedFlow()

    init {
        // Listen to ExoPlayer state changes
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId
                _currentIndex.value = player.currentMediaItemIndex
                if (trackId != null) {
                    viewModelScope.launch {
                        // Find track in current queue
                        val track = _queue.value.find { it.id == trackId }
                        _currentTrack.value = track
                        track?.let { musicRepository.recordPlay(it) }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0)
                }
            }
        })

        // Progress ticker
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0)
                }
                delay(500)
            }
        }

        // Observe Spotify playback errors — reset playing state and surface to UI
        viewModelScope.launch {
            spotifyPlayerManager.errorEvents.collect { error ->
                _isPlaying.value = false
                _spotifyError.tryEmit(error.message)
            }
        }
    }

    // ── Playback Controls ──────────────────────────────────

    /**
     * Play a specific track by ID from a given list (context).
     */
    /**
     * Returns true if the URI is a streamable HTTP(S) URL that ExoPlayer can handle.
     * Anything else (bare Spotify IDs, spotify: URIs, blanks) must go to Spotify App Remote.
     */
    private fun isStreamableUrl(uri: String): Boolean =
        uri.startsWith("http://") || uri.startsWith("https://")

    fun playTrack(trackId: String, context: List<Track> = emptyList()) {
        viewModelScope.launch {
            val targetIndex = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
            
            _queue.value = context
            _currentIndex.value = targetIndex
            _currentTrack.value = context.getOrNull(targetIndex)

            val track = context.getOrNull(targetIndex) ?: return@launch
            val uriString = track.audioUrl ?: track.externalUrl ?: track.id
            
            // If the URI is NOT a streamable HTTP(S) URL, route to Spotify App Remote
            if (!isStreamableUrl(uriString)) {
                var success = false
                try {
                    player.pause() // Pause ExoPlayer
                    _isPlaying.value = true // Set loading state
                    success = spotifyPlayerManager.playTrack("spotify:track:${track.id}")
                } finally {
                    if (!success) {
                        _isPlaying.value = false // Revert loading state on failure
                    }
                }
                return@launch
            }

            // Build ExoPlayer media items — only include tracks with valid HTTP(S) URLs
            val mediaItems = context.mapNotNull { t ->
                val u = t.audioUrl ?: t.externalUrl ?: t.id
                if (!isStreamableUrl(u)) return@mapNotNull null
                
                MediaItem.Builder()
                    .setMediaId(t.id)
                    .setUri(u)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title)
                            .setArtist(t.artist)
                            .setAlbumTitle(t.album)
                            .build()
                    )
                    .build()
            }

            if (mediaItems.isEmpty()) return@launch

            audioFocusManager.requestFocus(player)
            player.setMediaItems(mediaItems, 0, 0)
            player.prepare()
            player.play()
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            _queue.value = tracks
            _currentIndex.value = startIndex
            _currentTrack.value = tracks.getOrNull(startIndex)

            val track = tracks.getOrNull(startIndex) ?: return@launch
            val uriString = track.audioUrl ?: track.externalUrl ?: track.id
            
            // If the URI is NOT a streamable HTTP(S) URL, route to Spotify App Remote
            if (!isStreamableUrl(uriString)) {
                var success = false
                try {
                    player.pause() // Pause ExoPlayer
                    _isPlaying.value = true // Set loading state
                    success = spotifyPlayerManager.playTrack("spotify:track:${track.id}")
                } finally {
                    if (!success) {
                        _isPlaying.value = false // Revert loading state on failure
                    }
                }
                return@launch
            }

            // Build ExoPlayer media items — only include tracks with valid HTTP(S) URLs
            val mediaItems = tracks.mapNotNull { t ->
                val u = t.audioUrl ?: t.externalUrl ?: t.id
                if (!isStreamableUrl(u)) return@mapNotNull null
                
                MediaItem.Builder()
                    .setMediaId(t.id)
                    .setUri(u)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title)
                            .setArtist(t.artist)
                            .setAlbumTitle(t.album)
                            .build()
                    )
                    .build()
            }

            if (mediaItems.isEmpty()) return@launch

            audioFocusManager.requestFocus(player)
            player.setMediaItems(mediaItems, 0, 0)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        val currentUri = _currentTrack.value?.let { it.audioUrl ?: it.externalUrl ?: it.id } ?: ""
        if (!isStreamableUrl(currentUri)) {
            // Currently playing via Spotify App Remote
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
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) player.seekToNext()
    }

    fun skipPrevious() {
        if (player.hasPreviousMediaItem()) player.seekToPrevious()
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        player.volume = clampedVolume
        _volume.value = clampedVolume
    }

    fun toggleLike() {
        viewModelScope.launch {
            val track = _currentTrack.value ?: return@launch
            musicRepository.toggleLike(track.id)
            // Local update of isLiked flag
            _currentTrack.value = track.copy(isLiked = !track.isLiked)
            _queue.value = _queue.value.map { 
                if (it.id == track.id) it.copy(isLiked = !it.isLiked) else it 
            }
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
