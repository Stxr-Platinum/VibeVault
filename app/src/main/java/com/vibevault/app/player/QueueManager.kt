package com.vibevault.app.player

import android.util.Log
import androidx.media3.common.Player
import com.vibevault.app.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor() {

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

    fun setQueue(tracks: List<Track>, startIndex: Int) {
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
        debugLog()
    }

    fun playTrack(trackId: String, context: List<Track>) {
        if (context.isEmpty()) return
        val index = context.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        setQueue(context, index)
    }

    fun next() {
        if (currentQueue.isEmpty()) return

        val newIndex = _currentIndex.value + 1
        if (newIndex < currentQueue.size) {
            _currentIndex.value = newIndex
        } else {
            when (_repeatMode.value) {
                Player.REPEAT_MODE_ALL -> _currentIndex.value = 0
                else -> {
                    // Queue ended, don't loop, trigger infinite radio
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

        // Note: The 5-second rule will be checked by PlayerViewModel.
        // QueueManager simply moves the index back.
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
        
        val nextIdx = _currentIndex.value + 1
        val nextTrackId = if (nextIdx in currentQueue.indices) currentQueue[nextIdx].id else "end"
        Log.d("PlaybackDebug", "  Next Track ID = $nextTrackId")
        
        val prevIdx = _currentIndex.value - 1
        val prevTrackId = if (prevIdx >= 0) currentQueue[prevIdx].id else "start"
        Log.d("PlaybackDebug", "  Previous Track ID = $prevTrackId")
    }

    fun syncExternalTrack(track: Track) {
        val index = currentQueue.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            _currentIndex.value = index
        } else {
            // If it's a completely external track, just update the current track state
            // without breaking the queue
            _currentTrack.value = track
        }
        debugLog()
    }

    fun appendTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        originalQueue.addAll(tracks)
        currentQueue.addAll(tracks)
        _queueState.value = currentQueue.toList()
        debugLog()
    }

    fun appendTrack(track: Track) {
        val wasEmpty = currentQueue.isEmpty()
        originalQueue.add(track)
        currentQueue.add(track)
        _queueState.value = currentQueue.toList()
        debugLog()
        
        if (wasEmpty) {
            // Queue was empty before
            _currentIndex.value = 0
            updateState()
        }
    }

    fun removeTrackAt(index: Int) {
        if (index in currentQueue.indices) {
            val trackToRemove = currentQueue[index]
            currentQueue.removeAt(index)
            originalQueue.remove(trackToRemove)
            
            if (index < _currentIndex.value) {
                _currentIndex.value -= 1
            } else if (index == _currentIndex.value) {
                // Currently playing track was removed!
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
}
