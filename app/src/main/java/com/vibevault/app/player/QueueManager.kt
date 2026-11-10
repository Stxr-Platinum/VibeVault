package com.vibevault.app.player

import android.util.Log
import androidx.media3.common.Player
import com.vibevault.app.domain.model.Track
import com.vibevault.app.player.queue.ListQueue
import com.vibevault.app.player.queue.Queue
import com.vibevault.app.player.queue.YouTubeQueue
import com.vibevault.app.extensions.toTrack
import com.vibevault.app.player.queue.filterDiverseCharacteristics
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeQueue: Queue? = null
    private var dynamicRefreshJob: Job? = null
    private var lastRefreshedTrackId: String? = null

    private val originalQueue = mutableListOf<Track>()
    private val currentQueue = mutableListOf<Track>()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _queueEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queueEnded = _queueEnded.asSharedFlow()

    private val _queueState = MutableStateFlow<List<Track>>(emptyList())
    val queueState: StateFlow<List<Track>> = _queueState.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isLoadingNextPage = MutableStateFlow(false)
    val isLoadingNextPage: StateFlow<Boolean> = _isLoadingNextPage.asStateFlow()

    private val _radioChips = MutableStateFlow<List<com.music.innertube.pages.RadioChip>>(emptyList())
    val radioChips: StateFlow<List<com.music.innertube.pages.RadioChip>> = _radioChips.asStateFlow()
    private var radioChipsJob: Job? = null

    init {
        restorePersistedQueue()
    }

    private fun restorePersistedQueue() {
        try {
            val savedQueue = sessionManager.lastPlayedQueue
            val savedIndex = sessionManager.lastPlayedQueueIndex
            val savedTrack = sessionManager.lastPlayedTrack
            if (savedQueue.isNotEmpty()) {
                val validIndex = savedIndex.coerceIn(0, savedQueue.size - 1)
                originalQueue.clear()
                originalQueue.addAll(savedQueue)
                currentQueue.clear()
                currentQueue.addAll(savedQueue)
                _currentIndex.value = validIndex
                _currentTrack.value = savedQueue[validIndex]
                _queueState.value = savedQueue.toList()
                lastRefreshedTrackId = savedQueue[validIndex].id
                Log.d("QueueManager", "Restored persisted queue: ${savedQueue.size} tracks, index=$validIndex, currentTrack=${savedQueue[validIndex].title}")
            } else if (savedTrack != null && savedTrack.id.isNotBlank()) {
                originalQueue.clear()
                originalQueue.add(savedTrack)
                currentQueue.clear()
                currentQueue.add(savedTrack)
                _currentIndex.value = 0
                _currentTrack.value = savedTrack
                _queueState.value = listOf(savedTrack)
                lastRefreshedTrackId = savedTrack.id
                Log.d("QueueManager", "Restored persisted track: ${savedTrack.title}")
            }
        } catch (e: Exception) {
            Log.e("QueueManager", "Failed to restore persisted queue in init", e)
        }
    }

    fun setQueue(queue: Queue, forceDisableShuffle: Boolean = false) {
        if (forceDisableShuffle) {
            _shuffleModeEnabled.value = false
        }
        activeQueue = queue
        observeRadioChips(queue)
        queue.preloadItem?.let { item ->
            setQueueInternal(listOf(item), 0)
        }
        scope.launch {
            try {
                val status = queue.getInitialStatus()
                val current = _currentTrack.value
                val preload = queue.preloadItem
                if (preload != null && current != null && (current.id == preload.id || current.title.equals(preload.title, ignoreCase = true))) {
                    val matchingIndex = status.items.indexOfFirst {
                        it.id == current.id || (it.title.equals(current.title, ignoreCase = true) && it.artist.equals(current.artist, ignoreCase = true))
                    }
                    val finalItems = if (matchingIndex >= 0) {
                        status.items
                    } else {
                        listOf(current) + status.items
                    }
                    val finalIndex = if (matchingIndex >= 0) matchingIndex else 0
                    setQueueInternal(finalItems, finalIndex)
                } else {
                    setQueueInternal(status.items, status.mediaItemIndex)
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to load initial status for queue", e)
            }
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null) {
        if (shuffle != null) {
            _shuffleModeEnabled.value = shuffle
        }
        val listQueue = ListQueue(items = tracks, startIndex = startIndex)
        activeQueue = listQueue
        observeRadioChips(listQueue)
        setQueueInternal(tracks, startIndex)
    }

    private fun setQueueInternal(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return

        val validIndex = startIndex.coerceIn(0, tracks.size - 1)
        val selectedTrack = tracks[validIndex]

        originalQueue.clear()
        originalQueue.addAll(tracks)

        currentQueue.clear()
        if (_shuffleModeEnabled.value) {
            val shuffled = originalQueue.toMutableList()
            shuffled.remove(selectedTrack)
            shuffled.shuffle()
            currentQueue.add(selectedTrack)
            currentQueue.addAll(shuffled)
            _currentIndex.value = 0
        } else {
            currentQueue.addAll(originalQueue)
            _currentIndex.value = validIndex
        }

        lastRefreshedTrackId = selectedTrack.id
        updateState()
        checkAndFetchNextPage()
        debugLog()
    }

    fun playTrack(trackId: String, context: List<Track>) {
        if (context.isEmpty()) return
        val index = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        setQueue(context, index)
    }

    fun playTrackAtIndex(index: Int) {
        if (index in currentQueue.indices) {
            _currentIndex.value = index
            updateState()
            debugLog()
        }
    }

    fun playRadio(track: Track) {
        setQueue(YouTubeQueue.radio(track))
    }

    private fun observeRadioChips(queue: Queue) {
        radioChipsJob?.cancel()
        radioChipsJob = null
        val chipsFlow = queue.radioChips
        if (chipsFlow != null) {
            radioChipsJob = scope.launch {
                chipsFlow.collect {
                    _radioChips.value = it
                }
            }
        } else {
            _radioChips.value = emptyList()
        }
    }

    fun applyRadioChip(chip: com.music.innertube.pages.RadioChip) {
        val current = _currentTrack.value ?: return
        val currentIdx = _currentIndex.value
        scope.launch {
            try {
                val endpoint = com.music.innertube.models.WatchEndpoint(
                    videoId = current.id,
                    playlistId = "RDAMVM${current.id}",
                    params = chip.params
                )
                val newQueue = YouTubeQueue(endpoint = endpoint, preloadItem = current)
                val status = withContext(Dispatchers.IO) {
                    newQueue.getInitialStatus()
                }
                _radioChips.value = _radioChips.value.map { rc ->
                    rc.copy(isSelected = (rc.title == chip.title))
                }
                val freshItems = status.items.filter { it.id != current.id }
                if (freshItems.isNotEmpty()) {
                    val preserved = currentQueue.take(currentIdx + 1).toMutableList()
                    preserved.addAll(freshItems)
                    currentQueue.clear()
                    currentQueue.addAll(preserved)
                    originalQueue.clear()
                    originalQueue.addAll(preserved)
                    activeQueue = newQueue
                    observeRadioChips(newQueue)
                    updateState()
                    debugLog()
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to apply radio chip", e)
            }
        }
    }

    fun refreshDynamicQueue(targetTrack: Track? = null, force: Boolean = false) {
        val track = targetTrack ?: _currentTrack.value ?: return
        if (!force && track.id == lastRefreshedTrackId) {
            return
        }
        lastRefreshedTrackId = track.id

        dynamicRefreshJob?.cancel()
        dynamicRefreshJob = scope.launch {
            try {
                val radioQueue = YouTubeQueue.radio(track)
                val status = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                }

                val currentIdx = _currentIndex.value
                val historyIds = currentQueue.take(currentIdx + 1).map { it.id }.toSet()

                // Filter out tracks in current history / active track
                val freshCandidates = status.items.filter { it.id !in historyIds && it.id != track.id }
                val diverseFresh = freshCandidates.filterDiverseCharacteristics(maxPerArtist = 1, currentlyPlayingTrackId = track.id).shuffled()

                if (diverseFresh.isNotEmpty()) {
                    activeQueue = radioQueue
                    observeRadioChips(radioQueue)

                    // Preserve history up to current track, replace upcoming with fresh recommendations
                    val newQueue = currentQueue.take(currentIdx + 1).toMutableList()
                    newQueue.addAll(diverseFresh)

                    currentQueue.clear()
                    currentQueue.addAll(newQueue)

                    originalQueue.clear()
                    originalQueue.addAll(newQueue)

                    updateState()
                    debugLog()
                    Log.d("QueueManager", "Dynamically refreshed queue with ${diverseFresh.size} recommendations for ${track.title}")
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to dynamically refresh queue for track ${track.title}", e)
            }
        }
    }

    fun next() {
        if (currentQueue.isEmpty()) return

        val newIndex = _currentIndex.value + 1
        if (newIndex < currentQueue.size) {
            _currentIndex.value = newIndex
            updateState()
            checkAndFetchNextPage()
        } else {
            val queue = activeQueue
            if (queue != null && queue.hasNextPage()) {
                fetchNextPage {
                    next()
                }
                return
            }
            when (_repeatMode.value) {
                Player.REPEAT_MODE_ALL -> {
                    _currentIndex.value = 0
                    updateState()
                }
                else -> {
                    _queueEnded.tryEmit(Unit)
                    autoPlaySimilarSongs()
                    return
                }
            }
        }
        updateState()
        debugLog()
    }

    private fun autoPlaySimilarSongs() {
        val seedTrack = _currentTrack.value ?: currentQueue.lastOrNull() ?: originalQueue.lastOrNull() ?: return
        Log.d("QueueManager", "Playlist ended. Auto-filling queue with similar songs based on: ${seedTrack.title}")
        
        scope.launch {
            try {
                val radioQueue = YouTubeQueue.radio(seedTrack)
                val status = radioQueue.getInitialStatus()
                val similarTracks = status.items.filter { newTrack -> 
                    currentQueue.none { it.id == newTrack.id } && newTrack.id != seedTrack.id 
                }
                if (similarTracks.isNotEmpty()) {
                    activeQueue = radioQueue
                    originalQueue.addAll(similarTracks)
                    currentQueue.addAll(similarTracks)
                    _currentIndex.value = _currentIndex.value + 1
                    updateState()
                    debugLog()
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed auto-filling queue with similar songs", e)
            }
        }
    }

    fun previous(forcePrevious: Boolean = false) {
        if (currentQueue.isEmpty()) return

        val newIndex = _currentIndex.value - 1
        if (newIndex >= 0) {
            _currentIndex.value = newIndex
        } else {
            when (_repeatMode.value) {
                Player.REPEAT_MODE_ALL -> _currentIndex.value = currentQueue.size - 1
                else -> _currentIndex.value = 0
            }
        }
        updateState()
        debugLog()
    }

    fun toggleShuffle() {
        val currentTrack = _currentTrack.value ?: return
        val newShuffleState = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newShuffleState

        currentQueue.clear()
        if (newShuffleState) {
            val shuffled = originalQueue.toMutableList()
            shuffled.remove(currentTrack)
            shuffled.shuffle()
            currentQueue.add(currentTrack)
            currentQueue.addAll(shuffled)
            _currentIndex.value = 0
        } else {
            currentQueue.addAll(originalQueue)
            _currentIndex.value = currentQueue.indexOf(currentTrack).coerceAtLeast(0)
        }

        _queueState.value = currentQueue.toList()
        debugLog()
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        debugLog()
    }

    private fun checkAndFetchNextPage() {
        val queue = activeQueue ?: return
        if (!queue.hasNextPage() || _isLoadingNextPage.value) return
        val remaining = currentQueue.size - 1 - _currentIndex.value
        if (remaining <= 3) {
            fetchNextPage()
        }
    }

    fun fetchNextPage(onComplete: (() -> Unit)? = null) {
        val queue = activeQueue ?: return
        if (!queue.hasNextPage() || _isLoadingNextPage.value) return

        _isLoadingNextPage.value = true
        scope.launch {
            try {
                val newTracks = queue.nextPage()
                if (newTracks.isNotEmpty()) {
                    appendTracks(newTracks)
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to fetch next page of queue", e)
            } finally {
                _isLoadingNextPage.value = false
                onComplete?.invoke()
            }
        }
    }

    private fun updateState() {
        val index = _currentIndex.value
        if (index in currentQueue.indices) {
            _currentTrack.value = currentQueue[index]
        } else {
            _currentTrack.value = null
        }
        _queueState.value = currentQueue.toList()

        if (currentQueue.isNotEmpty()) {
            try {
                sessionManager.lastPlayedQueue = currentQueue.toList()
                sessionManager.lastPlayedQueueIndex = _currentIndex.value.coerceAtLeast(0)
                _currentTrack.value?.let { track ->
                    sessionManager.lastPlayedTrack = track
                }
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to persist queue state to SessionManager", e)
            }
        }
    }

    private fun debugLog() {
        Log.d("PlaybackDebug", "QueueManager State Update:")
        Log.d("PlaybackDebug", "  Queue Size = ${currentQueue.size}")
        Log.d("PlaybackDebug", "  Current Index = ${_currentIndex.value}")
        Log.d("PlaybackDebug", "  Current Track ID = ${_currentTrack.value?.id ?: "null"}")
        Log.d("PlaybackDebug", "  Shuffle Enabled = ${_shuffleModeEnabled.value}")
        Log.d("PlaybackDebug", "  Repeat Mode = ${_repeatMode.value}")
    }

    fun onMediaItemTransition(index: Int) {
        if (index in currentQueue.indices && index != _currentIndex.value) {
            _currentIndex.value = index
            updateState()
            checkAndFetchNextPage()
            debugLog()
        }
    }

    fun syncExternalTrack(track: Track) {
        val index = currentQueue.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            currentQueue[index] = track
            val origIdx = originalQueue.indexOfFirst { it.id == track.id }
            if (origIdx >= 0) {
                originalQueue[origIdx] = track
            }
            _currentIndex.value = index
            updateState()
        } else {
            _currentTrack.value = track
        }
        debugLog()
    }

    fun appendTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        val newUniqueTracks = tracks.filter { newTrack -> originalQueue.none { it.id == newTrack.id } }
        if (newUniqueTracks.isEmpty()) return

        originalQueue.addAll(newUniqueTracks)
        currentQueue.addAll(newUniqueTracks)
        _queueState.value = currentQueue.toList()
        debugLog()
    }

    fun appendTrack(track: Track) {
        val wasEmpty = currentQueue.isEmpty()

        if (wasEmpty) {
            originalQueue.add(track)
            currentQueue.add(track)
            _currentIndex.value = 0
            updateState()
        } else {
            val insertIndex = _currentIndex.value + 1
            originalQueue.add(insertIndex.coerceAtMost(originalQueue.size), track)
            currentQueue.add(insertIndex.coerceAtMost(currentQueue.size), track)
        }

        _queueState.value = currentQueue.toList()
        debugLog()
    }

    fun addToQueueEnd(track: Track) {
        val wasEmpty = currentQueue.isEmpty()
        if (wasEmpty) {
            originalQueue.add(track)
            currentQueue.add(track)
            _currentIndex.value = 0
            updateState()
        } else {
            originalQueue.add(track)
            currentQueue.add(track)
        }
        _queueState.value = currentQueue.toList()
        debugLog()
    }

    fun removeTrackAt(index: Int) {
        if (index in currentQueue.indices) {
            val trackToRemove = currentQueue[index]
            currentQueue.removeAt(index)
            originalQueue.remove(trackToRemove)

            if (index < _currentIndex.value) {
                _currentIndex.value -= 1
            } else if (index == _currentIndex.value) {
                if (currentQueue.isEmpty()) {
                    _currentIndex.value = -1
                    _currentTrack.value = null
                } else if (_currentIndex.value >= currentQueue.size) {
                    _currentIndex.value = 0
                }
                updateState()
            }

            _queueState.value = currentQueue.toList()
        }
    }

    fun clearQueue(clearPersisted: Boolean = false) {
        originalQueue.clear()
        currentQueue.clear()
        _currentIndex.value = -1
        _currentTrack.value = null
        _queueState.value = emptyList()
        activeQueue = null
        lastRefreshedTrackId = null
        if (clearPersisted) {
            sessionManager.lastPlayedQueue = emptyList()
            sessionManager.lastPlayedQueueIndex = 0
            sessionManager.lastPlayedTrack = null
            sessionManager.lastPlayedTrackId = null
            sessionManager.lastPlayedPositionMs = 0L
        }
    }
}
