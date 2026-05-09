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

/**
 * PlayerViewModel — Bridges Media3 ExoPlayer to the Compose UI.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: ExoPlayer,
    private val musicRepository: MusicRepository,
    private val audioFocusManager: AudioFocusManager
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
                        _currentTrack.value = _queue.value.find { it.id == trackId }
                        musicRepository.recordPlay(trackId)
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
    }

    // ── Playback Controls ──────────────────────────────────

    /**
     * Play a specific track by ID from a given list (context).
     */
    fun playTrack(trackId: String, context: List<Track> = emptyList()) {
        viewModelScope.launch {
            val targetIndex = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
            
            _queue.value = context
            _currentIndex.value = targetIndex
            _currentTrack.value = context.getOrNull(targetIndex)

            val mediaItems = context.map { track ->
                MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(track.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()
                    )
                    .build()
            }

            audioFocusManager.requestFocus(player)
            player.setMediaItems(mediaItems, targetIndex, 0)
            player.prepare()
            player.play()
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        _currentIndex.value = startIndex
        _currentTrack.value = tracks.getOrNull(startIndex)

        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }

        audioFocusManager.requestFocus(player)
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
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
