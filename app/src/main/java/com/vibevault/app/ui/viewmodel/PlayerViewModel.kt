package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PlayerViewModel — Bridges Media3 MediaController and the Compose UI,
 * using QueueManager as the single source of truth for playlist state.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // ── Playback State (Delegated to QueueManager) ──────────
    val currentTrack: StateFlow<Track?> = combine(
        queueManager.currentTrack,
        musicRepository.getLikedTracks()
    ) { track, likedTracks ->
        if (track == null) return@combine null
        val isLiked = likedTracks.any { it.id == track.id }
        if (track.isLiked != isLiked) track.copy(isLiked = isLiked) else track
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    
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

    private var currentlyPlayingTrackId: String? = null
    private var isTrackRestoredAndNotPlayed = false
    private var isSeamlessTransitioning = false
    private var isFetchingAutoplay = false

    private var mediaController: MediaController? = null

    init {
        // 1. Observe QueueManager's currentTrack to trigger playback
        viewModelScope.launch {
            queueManager.currentTrack.collect { track ->
                if (track != null && track.id != currentlyPlayingTrackId) {
                    playInternal(track)
                    musicRepository.recordPlay(track)
                } else if (track == null) {
                    mediaController?.pause()
                    mediaController?.clearMediaItems()
                }
            }
        }

        // 2. Progress ticker
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
                    }
                }
                delay(500)
            }
        }
    }

    // ── MediaController Setup ────────────────────────────────
    fun setMediaController(controller: MediaController) {
        this.mediaController = controller

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = controller.duration.coerceAtLeast(0)
                }
                
                if (playbackState == Player.STATE_ENDED) {
                    // Queue exhausted! Trigger infinite autoplay
                    val currentIndex = queueManager.currentIndex.value
                    val queueSize = queueManager.queueState.value.size
                    if (currentIndex >= queueSize - 1 && queueManager.repeatMode.value == Player.REPEAT_MODE_OFF) {
                        sessionManager.lastPlayedTrackId?.let { lastTrackId ->
                            val seedTrack = queueManager.queueState.value.find { it.id == lastTrackId }
                            if (seedTrack != null) triggerInfiniteAutoplay(seedTrack, resumePlayback = true) 
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // ExoPlayer seamlessly transitioned to the preloaded next track!
                    Log.d("PlaybackDebug", "Seamless auto-transition to ${mediaItem?.mediaId}")
                    isSeamlessTransitioning = true
                    queueManager.next(isAutoTransition = true)
                }
            }
        })
    }

    // ── Internal Playback Execution ────────────────────────

    /**
     * Executes playback and preloads the next track for gapless playback.
     */
    private fun playInternal(track: Track) {
        val controller = mediaController
        if (controller == null) {
            Log.w("PlaybackDebug", "playInternal called but MediaController is null!")
            return
        }

        if (isSeamlessTransitioning) {
            isSeamlessTransitioning = false
            currentlyPlayingTrackId = track.id
            sessionManager.lastPlayedTrackId = track.id
            Log.d("PlaybackDebug", "Seamlessly continuing playback: '${track.title}'")

            // ExoPlayer's timeline currently has [OldTrack, CurrentTrack].
            // We remove the OldTrack and append the new NextTrack to keep a 2-item rolling window.
            if (controller.mediaItemCount > 1) {
                controller.removeMediaItem(0)
            }
            
            val queueList = queueManager.queueState.value
            val currentIndex = queueManager.currentIndex.value
            
            // --- INFINITE AUTOPLAY CHECK ---
            if (currentIndex == queueList.size - 1 && queueManager.repeatMode.value == Player.REPEAT_MODE_OFF) {
                triggerInfiniteAutoplay(track, resumePlayback = false)
            }
            
            val nextTrack = if (currentIndex + 1 < queueList.size) queueList[currentIndex + 1] else null
            
            if (nextTrack != null && controller.mediaItemCount < 2) {
                val extrasBundle = android.os.Bundle().apply { if (nextTrack.isrc.isNotBlank()) putString("isrc", nextTrack.isrc) }
                val nextMediaItem = MediaItem.Builder()
                    .setMediaId(nextTrack.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextTrack.title)
                            .setArtist(nextTrack.artist)
                            .setAlbumTitle(nextTrack.album)
                            .setExtras(extrasBundle)
                            .build()
                    ).build()
                controller.addMediaItem(nextMediaItem)
            }
            return
        }

        // Check if the user manually skipped to the track we ALREADY preloaded!
        val isPreloadedNext = controller.mediaItemCount > 1 && controller.getMediaItemAt(1).mediaId == track.id
        
        if (isPreloadedNext) {
            Log.d("PlaybackDebug", "Manual skip to preloaded track. Removing current track for instant play!")
            
            currentlyPlayingTrackId = track.id
            isTrackRestoredAndNotPlayed = false
            sessionManager.lastPlayedTrackId = track.id
            
            // Removing the currently playing item forces ExoPlayer to instantly play the next preloaded item!
            controller.removeMediaItem(0)
            
            // Now preload the NEW next track
            val queueList = queueManager.queueState.value
            val currentIndex = queueManager.currentIndex.value
            val nextTrack = if (currentIndex + 1 < queueList.size) queueList[currentIndex + 1] else null
            
            if (nextTrack != null) {
                val nextExtrasBundle = android.os.Bundle().apply { if (nextTrack.isrc.isNotBlank()) putString("isrc", nextTrack.isrc) }
                val nextMediaItem = MediaItem.Builder()
                    .setMediaId(nextTrack.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextTrack.title)
                            .setArtist(nextTrack.artist)
                            .setAlbumTitle(nextTrack.album)
                            .setExtras(nextExtrasBundle)
                            .build()
                    ).build()
                controller.addMediaItem(nextMediaItem)
            }
            
            controller.play()
            return
        }

        // Standard manual play (random song tap, etc.)
        // INSTANTLY pause to prevent audio overlap during URL fetch
        controller.pause() 

        currentlyPlayingTrackId = track.id
        isTrackRestoredAndNotPlayed = false
        sessionManager.lastPlayedTrackId = track.id
        
        Log.d("PlaybackDebug", "playInternal: '${track.title}' by '${track.artist}' [id=${track.id}, isrc=${track.isrc}]")

        val extrasBundle = android.os.Bundle().apply {
            if (track.isrc.isNotBlank()) putString("isrc", track.isrc)
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            // URI is left null — PlaybackService will resolve via Qobuz search using title+artist
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setExtras(extrasBundle)
                    .build()
            )
            .build()

        controller.clearMediaItems()
        controller.addMediaItem(mediaItem)

        // Preload next track
        val queueList = queueManager.queueState.value
        val currentIndex = queueManager.currentIndex.value
        val nextTrack = if (currentIndex + 1 < queueList.size) queueList[currentIndex + 1] else null
        
        if (nextTrack != null) {
            val nextExtrasBundle = android.os.Bundle().apply { if (nextTrack.isrc.isNotBlank()) putString("isrc", nextTrack.isrc) }
            val nextMediaItem = MediaItem.Builder()
                .setMediaId(nextTrack.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(nextTrack.title)
                        .setArtist(nextTrack.artist)
                        .setAlbumTitle(nextTrack.album)
                        .setExtras(nextExtrasBundle)
                        .build()
                ).build()
            controller.addMediaItem(nextMediaItem)
        }

        controller.prepare()
        controller.play()
    }

    // ── Public API ─────────────────────────────────────────

    fun playTrack(trackId: String, context: List<Track> = emptyList()) {
        if (context.isNotEmpty()) {
            queueManager.playTrack(trackId, context)
            val current = queueManager.currentTrack.value
            if (current != null && current.id == trackId && (trackId == currentlyPlayingTrackId || isTrackRestoredAndNotPlayed)) {
                playInternal(current)
                viewModelScope.launch {
                    musicRepository.recordPlay(current)
                }
            }
            return
        }
        
        // Context is empty (e.g. played from search). We start a Radio/Autoplay queue.
        val shellTrack = Track(
            id = trackId,
            title = "Loading...",
            artist = "Unknown",
            album = "Unknown",
            albumImageUrl = "",
            durationMs = 0
        )
        queueManager.playTrack(trackId, listOf(shellTrack))

        // Fetch similar tracks asynchronously and append to queue
        viewModelScope.launch {
            try {
                val similarTracks = musicRepository.getSimilarTracks(shellTrack)
                if (similarTracks.isNotEmpty()) {
                    queueManager.appendTracks(similarTracks)
                    // If we just appended the next track while the current one is playing,
                    // we need to push it to ExoPlayer for preloading.
                    val controller = mediaController
                    if (controller != null && controller.mediaItemCount == 1) {
                        val nextTrack = similarTracks.first()
                        val nextExtrasBundle = android.os.Bundle().apply { if (nextTrack.isrc.isNotBlank()) putString("isrc", nextTrack.isrc) }
                        val nextMediaItem = MediaItem.Builder()
                            .setMediaId(nextTrack.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(nextTrack.title)
                                    .setArtist(nextTrack.artist)
                                    .setAlbumTitle(nextTrack.album)
                                    .setExtras(nextExtrasBundle)
                                    .build()
                            ).build()
                        controller.addMediaItem(nextMediaItem)
                        Log.d("SpotifyAutoplay", "Pushed newly fetched Autoplay track to ExoPlayer timeline")
                    }
                }
            } catch (e: Exception) {
                Log.e("SpotifyAutoplay", "Failed to generate autoplay queue", e)
            }
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        
        if (isTrackRestoredAndNotPlayed) {
            isTrackRestoredAndNotPlayed = false
            currentTrack.value?.let { playInternal(it) }
            return
        }

        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        Log.d("PlaybackDebug", "skipNext called")
        queueManager.next()
    }

    fun skipPrevious() {
        Log.d("PlaybackDebug", "skipPrevious called")
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
        mediaController?.volume = clampedVolume
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
        mediaController?.release()
    }

    // ── Infinite Autoplay ──────────────────────────────────
    private fun triggerInfiniteAutoplay(seedTrack: Track, resumePlayback: Boolean) {
        if (isFetchingAutoplay) return
        isFetchingAutoplay = true
        Log.d("SpotifyAutoplay", "Triggering infinite autoplay for seed ${seedTrack.title}")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val similarTracks = musicRepository.getSimilarTracks(seedTrack)
                if (similarTracks.isNotEmpty()) {
                    val existingIds = queueManager.queueState.value.map { it.id }.toSet()
                    val newTracks = similarTracks.filter { it.id !in existingIds }
                    
                    if (newTracks.isNotEmpty()) {
                        queueManager.appendTracks(newTracks)
                        
                        withContext(Dispatchers.Main) {
                            // Check if ExoPlayer ended while we were fetching or if we were explicitly told to resume
                            if (mediaController?.playbackState == Player.STATE_ENDED || resumePlayback) {
                                isSeamlessTransitioning = false
                                queueManager.next(isAutoTransition = true)
                            } else {
                                // We are still playing the last track. Inject the next one into the rolling window!
                                if (mediaController?.mediaItemCount == 1) {
                                    val nextTrack = newTracks.first()
                                    val extrasBundle = android.os.Bundle().apply { if (nextTrack.isrc.isNotBlank()) putString("isrc", nextTrack.isrc) }
                                    val nextMediaItem = MediaItem.Builder()
                                        .setMediaId(nextTrack.id)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(nextTrack.title)
                                                .setArtist(nextTrack.artist)
                                                .setAlbumTitle(nextTrack.album)
                                                .setExtras(extrasBundle)
                                                .build()
                                        ).build()
                                    mediaController?.addMediaItem(nextMediaItem)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SpotifyAutoplay", "Failed infinite autoplay fetch", e)
            } finally {
                isFetchingAutoplay = false
            }
        }
    }
}
