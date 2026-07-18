package com.vibevault.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.vibevault.app.playback.queues.YouTubeQueue

interface MusicService {
    var queueTitle: String?
    val playerVolume: MutableStateFlow<Float>
}

interface PlayerConnection {
    val player: Player
    val service: MusicService
    val queueTitle: StateFlow<String?>
    val queueWindows: StateFlow<List<MediaItem>>
    val isMuted: StateFlow<Boolean>
    var allowInternalSync: Boolean
    var shouldBlockPlaybackChanges: (() -> Boolean)?
    var onSkipPrevious: (() -> Unit)?
    var onSkipNext: (() -> Unit)?
    var onRestartSong: (() -> Unit)?

    fun play()
    fun pause()
    fun seekTo(position: Long)
    fun seekToNext()
    fun seekToPrevious()
    fun playQueue(queue: YouTubeQueue)
    fun playNext(mediaItem: MediaItem)
    fun addToQueue(mediaItem: MediaItem)
    fun setMuted(muted: Boolean)
}
