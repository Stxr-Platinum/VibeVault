package com.vibevault.app.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.mapper.toDomain
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.QueueManager
import com.vibevault.app.player.media.AudioFocusManager
import com.vibevault.app.player.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PlayerViewModel — Bridges Media3 MediaController to the Compose UI,
 * using QueueManager as the single source of truth for playlist state.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val audioFocusManager: AudioFocusManager,
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

    // ── Media3 ──────────────────────────────────────────────
    private var player: MediaController? = null
    private var pendingTrack: Track? = null

    init {
        // Build MediaController asynchronously
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture.addListener(
            {
                player = controllerFuture.get()
                setupPlayerListener()
                
                // If a track was requested while controller was loading, play it now
                pendingTrack?.let {
                    playInternal(it)
                    pendingTrack = null
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        // 0. Load last played track
        viewModelScope.launch {
            sessionManager.lastPlayedTrackId?.let { trackId ->
                try {
                    // Try to get track from local repository instead
                    val localTrackResult = musicRepository.searchTracks(trackId).firstOrNull()?.firstOrNull()
                    if (localTrackResult != null) {
                        queueManager.syncExternalTrack(localTrackResult)
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

        // 1b. Observe QueueManager's queueEnded for Infinite Radio
        viewModelScope.launch {
            queueManager.queueEnded.collect {
                val track = queueManager.currentTrack.value ?: return@collect
                Log.d("PlaybackDebug", "Queue ended, triggering infinite radio for ${track.title}")
                try {
                    val result = musicRepository.getSimilarTracks(track.id)
                    result.onSuccess { similarTracks ->
                        if (similarTracks.isNotEmpty()) {
                            Log.d("PlaybackDebug", "Got ${similarTracks.size} similar tracks. Appending to queue.")
                            queueManager.appendTracks(similarTracks)
                        } else {
                            Log.d("PlaybackDebug", "No similar tracks found.")
                        }
                    }.onFailure { err ->
                        Log.e("PlaybackDebug", "Failed to fetch similar tracks", err)
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackDebug", "Exception in infinite radio fetch", e)
                }
            }
        }

        // 2. Progress ticker
        viewModelScope.launch {
            while (isActive) {
                if (player?.isPlaying == true) {
                    _currentPosition.value = player?.currentPosition?.coerceAtLeast(0) ?: 0L
                }
                delay(500)
            }
        }
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0) ?: 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    // ExoPlayer finished the track. Let QueueManager advance.
                    queueManager.next()
                }
            }
        })
    }

    // ── Internal Playback Execution ────────────────────────

    private fun stopAllPlayback() {
        player?.pause()
        player?.clearMediaItems()
        _isPlaying.value = false
        currentlyPlayingTrackId = null
    }

    /**
     * Executes playback for EXACTLY ONE track. Queue progression is handled by STATE_ENDED.
     * Delegates Qobuz stream resolution entirely to PlaybackService.
     */
    private fun playInternal(track: Track) {
        if (player == null) {
            pendingTrack = track
            return
        }

        currentlyPlayingTrackId = track.id
        sessionManager.lastPlayedTrackId = track.id
        
        Log.d("PlaybackDebug", "playInternal: ${track.title} (${track.id}) via MediaController")
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()

        player?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    // ── Public API ─────────────────────────────────────────

    fun playTrack(trackId: String, context: List<Track> = emptyList()) {
        viewModelScope.launch {
            val effectiveContext = if (context.isEmpty()) {
                try {
                    val localTrackResult = musicRepository.searchTracks(trackId).firstOrNull()?.firstOrNull()
                    if (localTrackResult != null) listOf(localTrackResult) else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                context
            }

            if (effectiveContext.isNotEmpty()) {
                val index = effectiveContext.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                queueManager.setQueue(effectiveContext, index)
            }
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (currentlyPlayingTrackId == null && queue.value.isNotEmpty()) {
                playInternal(queue.value[currentIndex.value])
            } else {
                p.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
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
        player?.volume = clampedVolume
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
        player?.release()
    }
}
