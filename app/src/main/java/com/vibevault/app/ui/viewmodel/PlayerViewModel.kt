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
import com.vibevault.app.data.listentogether.ListenTogetherManager
import com.vibevault.app.playback.PlayerConnection
import com.vibevault.app.playback.MusicService
import com.vibevault.app.extensions.toMediaItem

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
    private val sessionManager: SessionManager,
    private val listenTogetherManager: ListenTogetherManager
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

    // ── ListenTogether Bridge State ───────────────────────
    private val _isMuted = MutableStateFlow(false)
    private val _queueTitle = MutableStateFlow<String?>(null)
    private val _queueWindows = MutableStateFlow<List<MediaItem>>(emptyList())

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

        // 1b. Observe Queue for Infinite Radio Pre-fetching
        var lastFetchedRadioTrackId: String? = null
        viewModelScope.launch {
            combine(queueManager.currentIndex, queueManager.queueState) { idx, q ->
                idx to q
            }.collect { (idx, q) ->
                if (q.isNotEmpty() && idx == q.size - 1) {
                    val track = q[idx]
                    if (track.id != lastFetchedRadioTrackId) {
                        lastFetchedRadioTrackId = track.id
                        Log.d("PlaybackDebug", "Reached end of queue, pre-fetching infinite radio for ${track.title}")
                        try {
                            val result = musicRepository.getSimilarTracks(track)
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
                            Log.e("PlaybackDebug", "Exception in infinite radio pre-fetch", e)
                        }
                    }
                }
            }
        }
        
        // 1c. Observe queue and repeat mode mutations to sync upcoming tracks
        viewModelScope.launch {
            queueManager.queueState.drop(1).collect {
                syncUpcomingTracks()
            }
        }
        
        viewModelScope.launch {
            queueManager.repeatMode.drop(1).collect {
                syncUpcomingTracks()
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
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _isPlaying.value = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0) ?: 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    // Entire ExoPlayer playlist finished. 
                    queueManager.next()
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                
                // We must also handle PLAYLIST_CHANGED to catch ListenTogether track changes!
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    
                    val newId = mediaItem?.mediaId
                    if (newId != null && newId != currentlyPlayingTrackId) {
                        Log.d("PlaybackDebug", "Transitioned to: $newId (reason: $reason)")
                        currentlyPlayingTrackId = newId
                        sessionManager.lastPlayedTrackId = newId
                        
                        viewModelScope.launch {
                            val track = queueManager.queueState.value.find { it.id == newId }
                            if (track != null) {
                                musicRepository.recordPlay(track)
                            }
                        }
                        
                        val existingTrack = queueManager.queueState.value.find { it.id == newId }
                        if (existingTrack != null) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                                queueManager.next() // Auto-advance playlist
                            } else {
                                queueManager.syncExternalTrack(existingTrack) // ListenTogether or external action jumped to a queue item
                            }
                        } else {
                            // Track not in queue! Must be from ListenTogether!
                            // Sync it so the Compose UI updates and shows the mini-player
                            val md = mediaItem.mediaMetadata
                            val externalTrack = com.vibevault.app.domain.model.Track(
                                id = mediaItem.mediaId,
                                title = md.title?.toString() ?: "Unknown",
                                artist = md.artist?.toString() ?: "Unknown",
                                album = md.albumTitle?.toString() ?: "",
                                albumImageUrl = md.artworkUri?.toString() ?: "",
                                durationMs = 0L
                            )
                            queueManager.syncExternalTrack(externalTrack)
                        }
                        
                        ensureUpcomingTracks()
                    }
                }
            }
        })
        
        setupListenTogetherBridge()
    }

    private fun setupListenTogetherBridge() {
        // Map queue updates to MediaItems for ListenTogether
        viewModelScope.launch {
            queueManager.queueState.collect { tracks ->
                _queueWindows.value = tracks.map { it.toMediaItem() }
            }
        }

        val bridge = object : PlayerConnection {
            override val player: Player = this@PlayerViewModel.player!!
            override val service: MusicService = object : MusicService {
                override var queueTitle: String?
                    get() = _queueTitle.value
                    set(value) { _queueTitle.value = value }
                override val playerVolume: MutableStateFlow<Float> = _volume
            }
            override val queueTitle: StateFlow<String?> = _queueTitle.asStateFlow()
            override val queueWindows: StateFlow<List<MediaItem>> = _queueWindows.asStateFlow()
            override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
            override var allowInternalSync: Boolean = false
            override var shouldBlockPlaybackChanges: (() -> Boolean)? = null
            override var onSkipPrevious: (() -> Unit)? = null
            override var onSkipNext: (() -> Unit)? = null
            override var onRestartSong: (() -> Unit)? = null

            override fun play() { player?.play() }
            override fun pause() { player?.pause() }
            override fun seekTo(position: Long) { player?.seekTo(position) }
            override fun seekToNext() { queueManager.next() }
            override fun seekToPrevious() { queueManager.previous() }
            
            override fun playQueue(queue: com.vibevault.app.playback.queues.YouTubeQueue) {
                // In VibeVault, we just play the single track endpoint provided.
                val videoId = queue.endpoint.videoId
                if (videoId != null) {
                    viewModelScope.launch {
                        try {
                            // Try to get track info
                            val localTrackResult = musicRepository.searchTracks(videoId).firstOrNull()?.firstOrNull()
                            if (localTrackResult != null) {
                                playInternal(localTrackResult)
                            } else {
                                // Create a placeholder track if network search fails
                                val placeholder = com.vibevault.app.domain.model.Track(
                                    id = videoId,
                                    title = queue.preloadItem?.title ?: "Unknown",
                                    artist = queue.preloadItem?.artists?.firstOrNull()?.name ?: "Unknown",
                                    album = "",
                                    albumImageUrl = queue.preloadItem?.thumbnailUrl ?: "",
                                    durationMs = (queue.preloadItem?.duration ?: 0) * 1000L
                                )
                                playInternal(placeholder)
                            }
                        } catch (e: Exception) {
                            Log.e("ListenTogether", "Failed to playQueue for $videoId", e)
                        }
                    }
                }
            }
            
            override fun playNext(mediaItem: MediaItem) {
                val track = mediaItemToTrack(mediaItem)
                // Insert after current track
                val q = queueManager.queueState.value.toMutableList()
                val idx = queueManager.currentIndex.value
                if (idx in q.indices) {
                    q.add(idx + 1, track)
                } else {
                    q.add(track)
                }
                viewModelScope.launch { queueManager.setQueue(q, queueManager.currentIndex.value) }
            }
            
            override fun addToQueue(mediaItem: MediaItem) {
                val track = mediaItemToTrack(mediaItem)
                viewModelScope.launch { queueManager.appendTracks(listOf(track)) }
            }
            
            override fun setMuted(muted: Boolean) {
                _isMuted.value = muted
                player?.volume = if (muted) 0f else _volume.value
            }
            
            private fun mediaItemToTrack(mediaItem: MediaItem): com.vibevault.app.domain.model.Track {
                val md = mediaItem.mediaMetadata
                return com.vibevault.app.domain.model.Track(
                    id = mediaItem.mediaId,
                    title = md.title?.toString() ?: "Unknown",
                    artist = md.artist?.toString() ?: "Unknown",
                    album = md.albumTitle?.toString() ?: "Unknown",
                    albumImageUrl = md.artworkUri?.toString() ?: "",
                    durationMs = 0L // Placeholder
                )
            }
        }
        
        listenTogetherManager.setPlayerConnection(bridge)
    }

    // ── Internal Playback Execution ────────────────────────

    private fun stopAllPlayback() {
        player?.stop()
        queueManager.clearQueue()
        player?.clearMediaItems()
        _isPlaying.value = false
        currentlyPlayingTrackId = null
    }

    private fun playInternal(track: Track) {
        if (player == null) {
            pendingTrack = track
            return
        }

        currentlyPlayingTrackId = track.id
        sessionManager.lastPlayedTrackId = track.id
        
        Log.d("PlaybackDebug", "playInternal: ${track.title} (${track.id}) via MediaController")
        
        // Stop currently playing audio immediately to prevent overlap while resolving new stream
        player?.pause()
        
        val mediaItem = buildMediaItem(track)

        player?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        
        ensureUpcomingTracks()
    }
    
    private fun buildMediaItem(track: Track): MediaItem {
        return track.toMediaItem()
    }
    
    private fun syncUpcomingTracks() {
        player?.let { controller ->
            val currentIdx = controller.currentMediaItemIndex
            if (currentIdx >= 0 && currentIdx + 1 < controller.mediaItemCount) {
                controller.removeMediaItems(currentIdx + 1, controller.mediaItemCount)
            }
            ensureUpcomingTracks()
        }
    }
    
    private fun ensureUpcomingTracks() {
        viewModelScope.launch {
            val q = queueManager.queueState.value
            val idx = queueManager.currentIndex.value
            if (idx < 0 || idx >= q.size) return@launch
            
            player?.let { controller ->
                val desiredUpcoming = 2
                val currentControllerCount = controller.mediaItemCount
                val currentControllerIndex = controller.currentMediaItemIndex
                val itemsAhead = currentControllerCount - currentControllerIndex - 1
                
                if (itemsAhead < desiredUpcoming) {
                    for (i in (itemsAhead + 1)..desiredUpcoming) {
                        val repeat = queueManager.repeatMode.value
                        
                        val nextQIdx = if (repeat == Player.REPEAT_MODE_ONE) {
                            idx
                        } else {
                            idx + i
                        }
                        
                        if (nextQIdx < q.size) {
                            val trackToAdd = q[nextQIdx]
                            controller.addMediaItem(buildMediaItem(trackToAdd))
                            Log.d("PlaybackDebug", "Pre-added ${trackToAdd.title} to ExoPlayer playlist")
                        } else if (repeat == Player.REPEAT_MODE_ALL) {
                            val wrapIdx = nextQIdx % q.size
                            if (wrapIdx < q.size) {
                                val trackToAdd = q[wrapIdx]
                                controller.addMediaItem(buildMediaItem(trackToAdd))
                                Log.d("PlaybackDebug", "Pre-added (repeat) ${trackToAdd.title} to ExoPlayer playlist")
                            }
                        }
                    }
                }
            }
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

    fun removeTrackAt(index: Int) {
        queueManager.removeTrackAt(index)
    }

    fun addToQueue(track: Track) {
        queueManager.appendTrack(track)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.playWhenReady) {
            p.pause()
        } else {
            if (p.mediaItemCount == 0 && currentlyPlayingTrackId != null) {
                // The service might have died and lost the media item, restore it
                val track = queueManager.queueState.value.find { it.id == currentlyPlayingTrackId }
                if (track != null) {
                    playInternal(track)
                    return
                }
            }
            
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

    fun stopPlayback() {
        stopAllPlayback()
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
