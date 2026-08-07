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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.vibevault.app.data.listentogether.ListenTogetherManager
import com.vibevault.app.playback.PlayerConnection
import com.vibevault.app.playback.MusicService
import com.vibevault.app.extensions.toMediaItem

/**
 * PlayerViewModel — Bridges Media3 MediaController to the Compose UI,
 * using QueueManager as the single source of truth for playlist state.
 */
import com.vibevault.app.data.lyrics.LyricsHelper
import com.vibevault.app.data.lyrics.LyricsWithProvider
import com.vibevault.app.data.lyrics.LyricsUtils

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val audioFocusManager: AudioFocusManager,
    private val sessionManager: SessionManager,
    private val listenTogetherManager: ListenTogetherManager,
    private val lyricsHelper: LyricsHelper,
    private val streamResolver: com.vibevault.app.player.media.StreamResolver
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

    private val _currentLyrics = MutableStateFlow<com.vibevault.app.data.lyrics.LyricsEntry?>(null)
    val currentLyrics: StateFlow<com.vibevault.app.data.lyrics.LyricsEntry?> = _currentLyrics.asStateFlow()
    
    private val _lyricsList = MutableStateFlow<List<com.vibevault.app.data.lyrics.LyricsEntry>>(emptyList())
    val lyricsList: StateFlow<List<com.vibevault.app.data.lyrics.LyricsEntry>> = _lyricsList.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    fun startSleepTimer(minutes: Int) {
        PlaybackService.instance?.sleepTimer?.let { timer ->
            timer.start(minutes)
            _sleepTimerActive.value = timer.isActive
        }
    }

    fun clearSleepTimer() {
        PlaybackService.instance?.sleepTimer?.let { timer ->
            timer.clear()
            _sleepTimerActive.value = false
        }
    }

    // ── ListenTogether Bridge State ───────────────────────
    private val _isMuted = MutableStateFlow(false)
    private val _queueTitle = MutableStateFlow<String?>(null)
    private val _queueWindows = MutableStateFlow<List<MediaItem>>(emptyList())

    private var currentlyPlayingTrackId: String? = null

    // ── Media3 ──────────────────────────────────────────────
    private var player: MediaController? = null
    private var pendingTrack: Track? = null
    private var isRestoringSavedState = true

    init {
        // Build MediaController asynchronously
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture.addListener(
            {
                player = controllerFuture.get()
                setupPlayerListener()
                
                // If a track was requested while controller was loading, play it now
                if (pendingTrack != null) {
                    pendingTrack?.let {
                        playInternal(it)
                        pendingTrack = null
                    }
                } else {
                    // Ensure player is strictly paused when initializing state on app launch
                    player?.playWhenReady = false
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        // 0. Load last played track and queue
        viewModelScope.launch {
            isRestoringSavedState = true
            try {
                val savedQueue = sessionManager.lastPlayedQueue
                val savedIndex = sessionManager.lastPlayedQueueIndex
                if (savedQueue.isNotEmpty()) {
                    val validIndex = savedIndex.coerceIn(0, savedQueue.size - 1)
                    val savedTrack = savedQueue[validIndex]
                    currentlyPlayingTrackId = savedTrack.id
                    queueManager.setQueue(savedQueue, validIndex)
                    val pos = sessionManager.lastPlayedPositionMs
                    if (pos > 0) _currentPosition.value = pos
                    if (savedTrack.durationMs > 0) _duration.value = savedTrack.durationMs
                } else {
                    val savedTrack = sessionManager.lastPlayedTrack
                    if (savedTrack != null && savedTrack.id.isNotBlank()) {
                        currentlyPlayingTrackId = savedTrack.id
                        queueManager.syncExternalTrack(savedTrack)
                        val pos = sessionManager.lastPlayedPositionMs
                        if (pos > 0) _currentPosition.value = pos
                        if (savedTrack.durationMs > 0) _duration.value = savedTrack.durationMs
                    } else {
                        sessionManager.lastPlayedTrackId?.let { trackId ->
                            try {
                                val localTrackResult = musicRepository.searchTracks(trackId).firstOrNull()?.firstOrNull()
                                if (localTrackResult != null) {
                                    queueManager.syncExternalTrack(localTrackResult)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            } finally {
                kotlinx.coroutines.yield()
                isRestoringSavedState = false
            }
        }

        // 0b. Observe liked tracks to keep track.isLiked status synchronized with database
        viewModelScope.launch {
            combine(queueManager.currentTrack, musicRepository.getLikedTracks()) { track, likedTracks ->
                if (track != null) {
                    val isLikedInDb = likedTracks.any { 
                        it.id == track.id || 
                        (it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true))
                    }
                    if (track.isLiked != isLikedInDb) {
                        track.copy(isLiked = isLikedInDb)
                    } else null
                } else null
            }.collect { updatedTrack ->
                if (updatedTrack != null) {
                    queueManager.syncExternalTrack(updatedTrack)
                }
            }
        }

        // 1. Observe QueueManager's currentTrack to trigger playback and lyrics fetch
        viewModelScope.launch {
            queueManager.currentTrack.collect { track ->
                val currentExoMediaId = player?.currentMediaItem?.mediaId
                val shouldStartPlayback = !isRestoringSavedState && 
                    track != null && 
                    track.id != currentlyPlayingTrackId && 
                    track.id != currentExoMediaId
                
                if (shouldStartPlayback) {
                    playInternal(track!!)
                    musicRepository.recordPlay(track)
                } else if (track == null) {
                    stopAllPlayback()
                } else if (isRestoringSavedState && track != null) {
                    currentlyPlayingTrackId = track.id
                }

                if (track != null) {
                    fetchLyrics(track)
                } else {
                    lastFetchedLyricsTrackId = null
                    cancelFetchLyrics()
                    _lyricsList.value = emptyList()
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
                player?.let { p ->
                    if (p.isPlaying || p.playWhenReady) {
                        val pos = p.currentPosition.coerceAtLeast(0)
                        _currentPosition.value = pos
                        sessionManager.lastPlayedPositionMs = pos
                        val dur = p.duration.coerceAtLeast(0)
                        if (dur > 0) {
                            _duration.value = dur
                        }
                    }
                }
                _sleepTimerActive.value = PlaybackService.instance?.sleepTimer?.isActive == true
                delay(250)
            }
        }
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _isPlaying.value = playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player?.duration?.coerceAtLeast(0) ?: 0L
                    if (dur > 0) {
                        _duration.value = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    // Entire ExoPlayer playlist finished. 
                    queueManager.next()
                } else if (playbackState == Player.STATE_IDLE) {
                    _isPlaying.value = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackDebug", "[PlayerViewModelError] code=${error.errorCode} (${error.errorCodeName}): ${error.message}", error)
                _isPlaying.value = false
                _currentPosition.value = 0L
                player?.stop()
                player?.clearMediaItems()
                _spotifyError.tryEmit("Playback error [${error.errorCodeName}]: ${error.localizedMessage ?: "Failed to stream track"}")
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                
                val newId = mediaItem?.mediaId
                if (newId != null) {
                    val isNewTrack = newId != currentlyPlayingTrackId
                    currentlyPlayingTrackId = newId
                    
                    val tagMetadata = mediaItem.localConfiguration?.tag as? com.vibevault.app.models.MediaMetadata
                    val md = mediaItem.mediaMetadata
                    val durFromTag = tagMetadata?.duration?.let { if (it > 0) it * 1000L else null }
                    val durFromPlayer = player?.duration?.coerceAtLeast(0)
                    val durationMs = durFromTag ?: (if (durFromPlayer != null && durFromPlayer > 0) durFromPlayer else 0L)
                    
                    if (durationMs > 0) {
                        _duration.value = durationMs
                    }
                    
                    val existingTrack = queueManager.queueState.value.find { it.id == newId }
                    val externalTrack = existingTrack ?: com.vibevault.app.domain.model.Track(
                        id = mediaItem.mediaId,
                        title = md.title?.toString() ?: "Unknown",
                        artist = md.artist?.toString() ?: "Unknown",
                        album = md.albumTitle?.toString() ?: "",
                        albumImageUrl = md.artworkUri?.toString() ?: tagMetadata?.thumbnailUrl ?: "",
                        durationMs = durationMs
                    )
                    
                    sessionManager.lastPlayedTrack = externalTrack
                    
                    val idxInQueue = queueManager.queueState.value.indexOfFirst { it.id == newId }
                    if (idxInQueue >= 0) {
                        queueManager.onMediaItemTransition(idxInQueue)
                    } else {
                        queueManager.syncExternalTrack(externalTrack)
                    }
                    
                    if (isNewTrack) {
                        viewModelScope.launch {
                            musicRepository.recordPlay(externalTrack)
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
        sessionManager.lastPlayedTrack = track
        
        Log.d("ImageSourceDebug", "[PlayerViewModel] Playing Track: '${track.title}' | Source: '${track.source}' | ImageUrl: '${track.albumImageUrl}'")
        Log.d("PlaybackDebug", "playInternal: ${track.title} (${track.id}) via MediaController")

        // Immediately trigger background stream pre-resolution for instant playback start
        streamResolver.preResolve(track.title, track.artist)
        
        // Stop currently playing audio immediately to prevent overlap while resolving new stream
        player?.pause()
        
        val mediaItem = buildMediaItem(track)

        player?.let { controller ->
            if (controller.playbackState == Player.STATE_IDLE || controller.playerError != null) {
                controller.stop()
                controller.clearMediaItems()
            }
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
                // Clean up already played tracks prior to current position in ExoPlayer's playlist
                val currentControllerIndex = controller.currentMediaItemIndex
                if (currentControllerIndex > 0) {
                    controller.removeMediaItems(0, currentControllerIndex)
                }

                val desiredUpcoming = 2
                val currentControllerCount = controller.mediaItemCount
                val activeIndex = controller.currentMediaItemIndex
                val itemsAhead = currentControllerCount - activeIndex - 1
                
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
                            streamResolver.preResolve(trackToAdd.title, trackToAdd.artist)
                            controller.addMediaItem(buildMediaItem(trackToAdd))
                            Log.d("PlaybackDebug", "Pre-added ${trackToAdd.title} to ExoPlayer playlist and preResolved stream")
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

    fun playTrack(trackId: String, context: List<Track> = emptyList(), isAlbumOrPlaylist: Boolean = false) {
        viewModelScope.launch {
            var selectedTrack = context.find { it.id == trackId }
                ?: try {
                    musicRepository.searchTracks(trackId).firstOrNull()?.firstOrNull()
                } catch (e: Exception) {
                    null
                }

            val needsMetadataFetch = selectedTrack == null || 
                selectedTrack.title.isBlank() || 
                selectedTrack.title == "Playing" || 
                selectedTrack.artist.isBlank() || 
                selectedTrack.artist == "Unknown" || 
                selectedTrack.artist == "Unknown Artist" ||
                selectedTrack.albumImageUrl.isBlank()

            if (selectedTrack == null) {
                val contextFirst = context.firstOrNull()
                selectedTrack = Track(
                    id = trackId,
                    title = contextFirst?.title?.takeIf { it.isNotBlank() } ?: "Track $trackId",
                    artist = contextFirst?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                    album = contextFirst?.album?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                    albumImageUrl = contextFirst?.albumImageUrl ?: "",
                    durationMs = contextFirst?.durationMs ?: 0L
                )
            }

            if (isAlbumOrPlaylist && context.size > 1) {
                val index = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                queueManager.setQueue(context, index)
            } else {
                queueManager.playRadio(selectedTrack)
            }

            // Always trigger playInternal so explicit user track selection starts playing immediately
            playInternal(selectedTrack)
            musicRepository.recordPlay(selectedTrack)

            // Asynchronously resolve full metadata if incomplete
            if (needsMetadataFetch) {
                launch(Dispatchers.IO) {
                    try {
                        val onlineResult = musicRepository.searchOnline(trackId).getOrNull()?.firstOrNull { it.id == trackId }
                            ?: musicRepository.searchOnline(trackId).getOrNull()?.firstOrNull()
                            ?: musicRepository.searchTracks(trackId).firstOrNull()?.firstOrNull()
                        
                        if (onlineResult != null && onlineResult.title.isNotBlank()) {
                            val updatedTrack = selectedTrack.copy(
                                title = onlineResult.title.takeIf { it.isNotBlank() } ?: selectedTrack.title,
                                artist = onlineResult.artist.takeIf { it.isNotBlank() && it != "Unknown" } ?: selectedTrack.artist,
                                album = onlineResult.album.takeIf { it.isNotBlank() && it != "Unknown" } ?: selectedTrack.album,
                                albumImageUrl = onlineResult.albumImageUrl.takeIf { it.isNotBlank() } ?: selectedTrack.albumImageUrl,
                                durationMs = if (onlineResult.durationMs > 0) onlineResult.durationMs else selectedTrack.durationMs
                            )
                            withContext(Dispatchers.Main) {
                                queueManager.syncExternalTrack(updatedTrack)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaybackDebug", "Failed async metadata resolution for $trackId", e)
                    }
                }
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
        if (p.isPlaying || p.playWhenReady) {
            p.pause()
        } else {
            if (p.playerError != null || p.playbackState == Player.STATE_IDLE || p.mediaItemCount == 0) {
                val track = queueManager.currentTrack.value 
                    ?: queueManager.queueState.value.find { it.id == currentlyPlayingTrackId }
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
            val newLiked = !track.isLiked
            queueManager.syncExternalTrack(track.copy(isLiked = newLiked))
            musicRepository.toggleLikeTrack(track)
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

    fun addTrackToPlaylistWithCheck(playlistId: String, trackId: String, context: android.content.Context) {
        viewModelScope.launch {
            val alreadyExists = musicRepository.isTrackInPlaylist(playlistId, trackId)
            if (alreadyExists) {
                android.widget.Toast.makeText(context, "Song is already in this playlist", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                musicRepository.addTrackToPlaylist(playlistId, trackId)
                android.widget.Toast.makeText(context, "Added to playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
        }
    }

    private var fetchLyricsJob: Job? = null
    private var lastFetchedLyricsTrackId: String? = null

    private fun cancelFetchLyrics() {
        fetchLyricsJob?.cancel()
        fetchLyricsJob = null
    }

    private fun fetchLyrics(track: Track) {
        if (track.id == lastFetchedLyricsTrackId && _lyricsList.value.isNotEmpty()) {
            return // Skip redundant fetching for the same track
        }

        cancelFetchLyrics()
        fetchLyricsJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingLyrics.value = true
            _lyricsList.value = emptyList()
            lastFetchedLyricsTrackId = track.id
            try {
                val durationSec = (track.durationMs / 1000).toInt()
                val result = lyricsHelper.getLyrics(track.id, track.title, track.artist, durationSec, track.album)
                val parsed = LyricsUtils.parseLyrics(result.lyrics)
                if (lastFetchedLyricsTrackId == track.id) {
                    _lyricsList.value = parsed
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to fetch lyrics for track: ${track.title}", e)
                if (lastFetchedLyricsTrackId == track.id) {
                    _lyricsList.value = emptyList()
                }
            } finally {
                if (lastFetchedLyricsTrackId == track.id) {
                    _isLoadingLyrics.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelFetchLyrics()
        audioFocusManager.abandonFocus()
        player?.release()
    }
}
