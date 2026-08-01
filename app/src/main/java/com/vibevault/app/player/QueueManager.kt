package com.vibevault.app.player

import android.util.Log
import androidx.media3.common.Player
import com.vibevault.app.domain.model.Track
import com.vibevault.app.player.queue.ListQueue
import com.vibevault.app.player.queue.Queue
import com.vibevault.app.player.queue.YouTubeQueue
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
class QueueManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeQueue: Queue? = null

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

    fun setQueue(queue: Queue) {
        activeQueue = queue
        queue.preloadItem?.let { item ->
            setQueueInternal(listOf(item), 0)
        }
        scope.launch {
            try {
                val status = queue.getInitialStatus()
                setQueueInternal(status.items, status.mediaItemIndex)
            } catch (e: Exception) {
                Log.e("QueueManager", "Failed to load initial status for queue", e)
            }
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        setQueue(ListQueue(items = tracks, startIndex = startIndex))
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

        updateState()
        checkAndFetchNextPage()
        debugLog()
    }

    fun playTrack(trackId: String, context: List<Track>) {
        if (context.isEmpty()) return
        val index = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        setQueue(context, index)
    }

    fun playRadio(track: Track) {
        setQueue(YouTubeQueue.radio(track))
    }

    fun next() {
        if (currentQueue.isEmpty()) return

        val newIndex = _currentIndex.value + 1
        if (newIndex < currentQueue.size) {
            _currentIndex.value = newIndex
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
                Player.REPEAT_MODE_ALL -> _currentIndex.value = 0
                else -> {
                    _queueEnded.tryEmit(Unit)
                    return
                }
            }
        }
        updateState()
        debugLog()
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

    private fun fetchNextPage(onComplete: (() -> Unit)? = null) {
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

    fun clearQueue() {
        originalQueue.clear()
        currentQueue.clear()
        _currentIndex.value = -1
        _currentTrack.value = null
        _queueState.value = emptyList()
        activeQueue = null
    }
}
