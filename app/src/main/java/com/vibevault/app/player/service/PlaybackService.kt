package com.vibevault.app.player.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.media.StreamResolver
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.net.Uri
import com.vibevault.app.domain.model.Track
import com.vibevault.app.extensions.toMediaItem
import com.vibevault.app.extensions.setOffloadEnabled
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import com.vibevault.app.constants.AudioNormalizationKey
import com.vibevault.app.constants.AudioOffload
import com.vibevault.app.constants.AutoSkipNextOnErrorKey
import com.vibevault.app.constants.CrossfadeDurationKey
import com.vibevault.app.constants.CrossfadeEnabledKey
import com.vibevault.app.constants.CrossfadeGaplessKey
import com.vibevault.app.constants.CrossfadeManualSkipKey
import com.vibevault.app.constants.CrossfadeCurveKey
import com.vibevault.app.constants.CrossfadeCurve
import com.vibevault.app.constants.PauseOnMute
import com.vibevault.app.constants.ResumeOnBluetoothConnectKey
import com.vibevault.app.constants.SkipSilenceKey
import com.vibevault.app.constants.StopMusicOnTaskClearKey
import com.vibevault.app.constants.EnableSponsorBlockKey
import com.vibevault.app.constants.SponsorBlockSkipNonMusicKey
import com.vibevault.app.constants.SponsorBlockSkipSponsorKey
import com.vibevault.app.constants.SponsorBlockSkipSelfPromoKey
import com.vibevault.app.constants.SponsorBlockSkipInteractionKey
import com.vibevault.app.constants.SponsorBlockSkipIntroOutroKey
import com.vibevault.app.constants.SponsorBlockSkipPreviewFillerKey
import com.vibevault.app.constants.SponsorBlockShowToastKey
import com.vibevault.app.constants.SponsorBlockServerUrlKey
import com.vibevault.app.sponsorblock.SponsorBlockApi
import com.vibevault.app.R
import com.vibevault.app.utils.dataStore
import com.vibevault.app.utils.get
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.LoadControl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import javax.inject.Inject

private data class SponsorBlockConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "https://sponsor.ajay.app",
    val skipNonMusic: Boolean = true,
    val skipSponsor: Boolean = true,
    val skipSelfPromo: Boolean = true,
    val skipInteraction: Boolean = true,
    val skipIntroOutro: Boolean = true,
    val skipPreviewFiller: Boolean = false,
    val showToast: Boolean = true
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var streamResolver: StreamResolver

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var autoMediaBrowserTree: AutoMediaBrowserTree

    @Inject
    lateinit var queueManager: com.vibevault.app.player.QueueManager

    @Inject
    lateinit var sessionManager: com.vibevault.app.core.session.SessionManager

    @Inject
    lateinit var playerAudioAttributes: AudioAttributes

    @Inject
    lateinit var playerLoadControl: LoadControl

    @Inject
    lateinit var playerMediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f // ms
    private var crossfadeGapless = true
    private var crossfadeManualSkipEnabled = true
    private var crossfadeCurve = CrossfadeCurve.EQUAL_POWER
    private var isCrossfading = false
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var secondaryAudioProcessor: com.vibevault.app.player.audio.CustomEqualizerAudioProcessor? = null
    private var fadingAudioProcessor: com.vibevault.app.player.audio.CustomEqualizerAudioProcessor? = null
    private var crossfadeTriggerJob: Job? = null
    private var crossfadeJob: Job? = null

    private var mediaLibrarySession: MediaLibrarySession? = null
    @Volatile
    private var isServiceDestroyed = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    lateinit var sleepTimer: com.vibevault.app.player.SleepTimer
        private set

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var audioManager: android.media.AudioManager? = null
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false

    private var hasAudioFocus = false
    private var wasPlayingBeforeAudioFocusLoss = false
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }

    private fun setupAudioFocusRequest() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN,
            android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true
                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }
                player.volume = 1f
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                    fadingPlayer?.pause()
                }
                abandonAudioFocus()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                    fadingPlayer?.pause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.volume = 0.2f
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val am = audioManager ?: return false
        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.requestAudioFocus(it) } ?: android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createExoPlayer(): ExoPlayer {
        val processor = com.vibevault.app.player.audio.AudioEffectManager.createAudioProcessor()
        secondaryAudioProcessor = processor

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(
                            processor
                        )
                    )
                    .build()
            }
        }

        val p = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(playerAudioAttributes, /* handleAudioFocus = */ false)
            .setLoadControl(playerLoadControl)
            .setMediaSourceFactory(playerMediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()

        p.skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
        p.setOffloadEnabled(if (crossfadeEnabled) false else dataStore.get(AudioOffload, false))
        return p
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == androidx.media3.common.C.INDEX_UNSET || nextIndex >= player.mediaItemCount) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        val currentAlbum = current.albumTitle?.toString()?.trim()
        val nextAlbum = next.albumTitle?.toString()?.trim()
        return !currentAlbum.isNullOrBlank() && currentAlbum.equals(nextAlbum, ignoreCase = true)
    }

    fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        if (!crossfadeEnabled || player.duration == androidx.media3.common.C.TIME_UNSET || player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) {
            Log.d("PlaybackService", "Crossfade bypassed: next track is from same gapless album (${player.currentMediaItem?.mediaMetadata?.albumTitle})")
            return
        }
        if (!player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE) return

        val triggerTime = player.duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeTriggerJob = serviceScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && sleepTimer.isActive != true) {
                startCrossfade(CrossfadeTrigger.AUTO)
            }
        }
    }

    private enum class CrossfadeTrigger {
        AUTO,
        MANUAL_NEXT,
        MANUAL_PREVIOUS
    }

    private fun startCrossfade(trigger: CrossfadeTrigger = CrossfadeTrigger.AUTO) {
        if (isCrossfading || secondaryPlayer != null) return

        val targetIndex = when {
            trigger == CrossfadeTrigger.MANUAL_PREVIOUS -> {
                if (!player.hasPreviousMediaItem()) return
                player.previousMediaItemIndex
            }
            trigger == CrossfadeTrigger.AUTO && player.repeatMode == Player.REPEAT_MODE_ONE -> {
                player.currentMediaItemIndex
            }
            else -> player.nextMediaItemIndex
        }
        if (targetIndex == androidx.media3.common.C.INDEX_UNSET || targetIndex < 0 || targetIndex >= player.mediaItemCount) return

        val secPlayer = createExoPlayer()
        secondaryPlayer = secPlayer

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        secPlayer.setMediaItems(items)
        secPlayer.seekTo(targetIndex, 0L)
        secPlayer.volume = 0f
        secPlayer.repeatMode = player.repeatMode
        secPlayer.shuffleModeEnabled = player.shuffleModeEnabled

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()
    }

    private fun performCrossfadeSwap() {
        isCrossfading = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        fadingAudioProcessor = secondaryAudioProcessor
        player = nextPlayer
        secondaryPlayer = null

        fadingPlayer?.removeListener(servicePlayerListener)
        fadingPlayer?.removeListener(sleepTimer)

        // Add listener to sync play/pause state
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfading && fadingPlayer != null) {
                    if (isPlaying) {
                        fadingPlayer?.play()
                    } else {
                        fadingPlayer?.pause()
                    }
                } else {
                    player.removeListener(this)
                }
            }
        })

        nextPlayer.addListener(servicePlayerListener)
        nextPlayer.addListener(sleepTimer)
        sleepTimer.player = player

        try {
            mediaLibrarySession?.player = player
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to swap player in MediaLibrarySession", e)
        }

        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch(Dispatchers.Main) {
            val duration = crossfadeDuration.toLong().coerceAtLeast(500L)
            val steps = 25
            val stepTime = duration / steps
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch (e: Exception) { 1f }

            // Wait until new player is actually producing audio before fading out old track
            var waitCount = 0
            while (!player.isPlaying && isActive && waitCount < 100) {
                delay(100)
                waitCount++
            }

            for (i in 0..steps) {
                if (!isActive) break
                while (!player.isPlaying && isActive) {
                    delay(100)
                }

                val progress = i / steps.toFloat()
                val fadeIn = crossfadeCurve.fadeIn(progress)
                val fadeOut = crossfadeCurve.fadeOut(progress)

                try {
                    player.volume = (startVolume * fadeIn).coerceIn(0f, 1f)
                    fadingPlayer?.volume = (startVolume * fadeOut).coerceIn(0f, 1f)
                } catch (e: Exception) {
                    break
                }
                delay(stepTime)
            }

            try {
                fadingPlayer?.volume = 0f
                player.volume = startVolume
                cleanupCrossfade()
            } catch (e: Exception) {
                cleanupCrossfade()
            }
        }
    }

    private fun cleanupCrossfade() {
        fadingAudioProcessor?.let {
            com.vibevault.app.player.audio.AudioEffectManager.removeAudioProcessor(it)
        }
        fadingAudioProcessor = null
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading = false
        Log.d("PlaybackService", "Crossfade completed successfully")
    }

    fun manualSkipNextWithCrossfade(): Boolean {
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (!player.isPlaying) return false
        if (!player.hasNextMediaItem()) return false
        if (player.duration == androidx.media3.common.C.TIME_UNSET) return false
        if (crossfadeGapless && isNextItemGapless()) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfade(CrossfadeTrigger.MANUAL_NEXT)
        return isCrossfading
    }

    fun manualSkipToPreviousWithCrossfade(): Boolean {
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (!player.isPlaying) return false
        if (!player.hasPreviousMediaItem()) return false
        if (player.duration == androidx.media3.common.C.TIME_UNSET) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfade(CrossfadeTrigger.MANUAL_PREVIOUS)
        return isCrossfading
    }

    fun manualSkipToNextWithCrossfade(): Boolean = manualSkipNextWithCrossfade()

    fun manualSeekToIndexWithCrossfade(targetIndex: Int): Boolean {
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (targetIndex < 0 || targetIndex >= player.mediaItemCount) return false
        if (targetIndex == player.currentMediaItemIndex) return false
        if (player.duration == androidx.media3.common.C.TIME_UNSET) return false
        if (!player.isPlaying) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfadeToIndex(targetIndex)
        return isCrossfading
    }

    private fun startCrossfadeToIndex(targetIndex: Int) {
        if (isCrossfading || secondaryPlayer != null) return
        if (targetIndex < 0 || targetIndex >= player.mediaItemCount) return

        val secPlayer = createExoPlayer()
        secondaryPlayer = secPlayer

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        secPlayer.setMediaItems(items)
        secPlayer.seekTo(targetIndex, 0L)
        secPlayer.volume = 0f
        secPlayer.repeatMode = player.repeatMode
        secPlayer.shuffleModeEnabled = player.shuffleModeEnabled

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()
    }

    private val servicePlayerListener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e("PlaybackService", "[ExoPlayerError] code=${error.errorCode} (${error.errorCodeName}): ${error.message}", error)
            com.vibevault.app.data.stats.ListeningRecorder.onStopped()
            handlePlaybackError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            Log.d("PlaybackService", "[PlaybackState] state=$stateName, playWhenReady=${player.playWhenReady}, item=${player.currentMediaItem?.mediaId}")

            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                com.vibevault.app.data.stats.ListeningRecorder.onStopped()
            }
            if (playbackState == Player.STATE_ENDED) {
                val previousIndex = queueManager.currentIndex.value
                queueManager.next()
                val nextIndex = queueManager.currentIndex.value
                val queue = queueManager.queueState.value
                if (nextIndex in queue.indices && nextIndex != previousIndex) {
                    if (nextIndex in 0 until player.mediaItemCount) {
                        player.seekTo(nextIndex, 0L)
                        player.prepare()
                        player.playWhenReady = true
                        player.play()
                    }
                }
            }

            if (playbackState == Player.STATE_READY) {
                scheduleCrossfade()
                val sessionId = (player as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0
                if (sessionId > 0) {
                    com.vibevault.app.player.audio.AudioEffectManager.attachAudioSession(this@PlaybackService, sessionId)
                    setupLoudnessEnhancer()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestAudioFocus()
                scheduleCrossfade()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                scheduleCrossfade()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("PlaybackService", "[IsPlaying] isPlaying=$isPlaying")
            if (!isPlaying) {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    com.vibevault.app.data.stats.ListeningRecorder.onStopped()
                } else {
                    com.vibevault.app.data.stats.ListeningRecorder.onPaused()
                }
            } else {
                scheduleCrossfade()
            }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            super.onDeviceVolumeChanged(volume, muted)
            val pauseOnMute = dataStore.get(PauseOnMute, false)

            if ((volume == 0 || muted) && pauseOnMute) {
                if (player.isPlaying) {
                    wasPlayingBeforeVolumeMute = true
                    isPausedByVolumeMute = true
                    player.pause()
                }
            } else if (volume > 0 && !muted && pauseOnMute) {
                if (wasPlayingBeforeVolumeMute && !player.isPlaying) {
                    wasPlayingBeforeVolumeMute = false
                    isPausedByVolumeMute = false
                    player.play()
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            Log.d("PlaybackService", "[TimelineChanged] windowCount=${timeline.windowCount}, reason=$reason")
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(currentIndex)
                val cleanId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
                val track = queueManager.queueState.value.find { it.id == cleanId } ?: item.toDomainTrack()
                sessionManager.lastPlayedTrack = track
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            scheduleCrossfade()
            val playerIndex = player.currentMediaItemIndex
            val queue = queueManager.queueState.value
            val cleanTransitionId = mediaItem?.mediaId?.split("/")?.lastOrNull() ?: mediaItem?.mediaId
            val resolvedIndex = when {
                player.mediaItemCount == queue.size && playerIndex in queue.indices -> playerIndex
                !cleanTransitionId.isNullOrBlank() -> queue.indexOfFirst { it.id == cleanTransitionId }
                else -> -1
            }
            if (resolvedIndex >= 0) {
                queueManager.onMediaItemTransition(resolvedIndex)
            }
            if (mediaItem != null) {
                val cleanId = cleanTransitionId ?: mediaItem.mediaId
                val track = queue.getOrNull(resolvedIndex)
                    ?: queue.find { it.id == cleanId }
                    ?: mediaItem.toDomainTrack()
                sessionManager.lastPlayedTrack = track
                val pos = player.currentPosition.coerceAtLeast(0L)
                sessionManager.lastPlayedPositionMs = pos
            }
            // Pre-resolve next 2 upcoming tracks for zero-latency sequential transitions
            val baseIdx = if (resolvedIndex >= 0) resolvedIndex else player.currentMediaItemIndex
            for (offset in 1..2) {
                val upcomingTrack = queue.getOrNull(baseIdx + offset)
                if (upcomingTrack != null && upcomingTrack.title.isNotBlank() && upcomingTrack.artist.isNotBlank()) {
                    streamResolver.preResolve(upcomingTrack.id, upcomingTrack.title, upcomingTrack.artist, upcomingTrack.durationMs)
                }
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
                player.mediaItemCount - player.currentMediaItemIndex <= 5
            ) {
                queueManager.fetchNextPage()
            }
        }
    }

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    triggerAutoPlayOnAndroidAuto("BluetoothConnect")
                }
            }
        }
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            return
        }

        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) {
                Log.e("PlaybackService", "Failed to create LoudnessEnhancer", e)
                loudnessEnhancer = null
                return
            }
        }

        val normalizeAudio = dataStore.get(AudioNormalizationKey, true)
        try {
            if (normalizeAudio) {
                loudnessEnhancer?.setTargetGain(300)
                loudnessEnhancer?.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to apply LoudnessEnhancer setting", e)
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {}
        loudnessEnhancer = null
    }

    companion object {
        var instance: PlaybackService? = null
            private set
    }

    private fun isAutoController(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName.lowercase()
        if (pkg == packageName.lowercase() || pkg.contains("vibevault")) {
            return false
        }
        if (pkg.contains("gearhead") || 
            pkg.contains("car") || 
            pkg.contains("projection") ||
            pkg.contains("android.auto") ||
            pkg.contains("automotive") ||
            pkg.contains("auto") ||
            pkg.contains("assistant") ||
            pkg.contains("googlequicksearchbox") ||
            pkg.contains("bluetooth")
        ) {
            return true
        }
        val hints = controller.connectionHints
        if (hints.containsKey("android.media.extras.MEDIA_ART_SIZE_HINT_PIXELS") ||
            hints.containsKey(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE) ||
            hints.containsKey(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE) ||
            hints.getBoolean("android.media.extras.AUTOMOTIVE_KEY", false)
        ) {
            return true
        }
        return !pkg.contains("systemui")
    }

    private suspend fun restoreQueueInternal(): List<Track> {
        val qmTracks = queueManager.queueState.value
        if (qmTracks.isNotEmpty()) return qmTracks

        val smTracks = sessionManager.lastPlayedQueue
        if (smTracks.isNotEmpty()) {
            val smIndex = sessionManager.lastPlayedQueueIndex.coerceIn(0, smTracks.size - 1)
            withContext(Dispatchers.Main) {
                queueManager.setQueue(smTracks, smIndex)
            }
            return smTracks
        }

        val lastTrack = sessionManager.lastPlayedTrack
        if (lastTrack != null && lastTrack.id.isNotBlank()) {
            withContext(Dispatchers.Main) {
                queueManager.setQueue(listOf(lastTrack), 0)
            }
            return listOf(lastTrack)
        }

        // First launch / no prior session: keep queue completely empty and MiniPlayer hidden
        return emptyList()
    }

    private fun ensureQueueRestored(autoPlay: Boolean = false) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (player.mediaItemCount > 0) {
                    if (autoPlay && !player.isPlaying) {
                        requestAudioFocus()
                        if (player.playbackState == Player.STATE_IDLE) {
                            player.prepare()
                        }
                        player.playWhenReady = true
                        player.play()
                    }
                    return@launch
                }

                val tracks = restoreQueueInternal()
                if (tracks.isNotEmpty()) {
                    val targetIndex = queueManager.currentIndex.value.coerceIn(0, tracks.size - 1)
                    val rawPos = sessionManager.lastPlayedPositionMs.coerceAtLeast(0L)
                    val curr = tracks[targetIndex]
                    val safePos = if (curr.durationMs > 0 && rawPos >= curr.durationMs - 2000L) {
                        0L
                    } else {
                        rawPos
                    }

                    val mediaItems = tracks.map { it.toMediaItem(path = "track", isGrid = false) }

                    streamResolver.preResolve(curr.id, curr.title, curr.artist, curr.durationMs)
                    if (targetIndex + 1 < tracks.size) {
                        val next = tracks[targetIndex + 1]
                        streamResolver.preResolve(next.id, next.title, next.artist, next.durationMs)
                    }

                    if (!autoPlay) {
                        player.playWhenReady = false
                    }
                    player.setMediaItems(mediaItems, targetIndex, safePos)
                    player.playlistMetadata = MediaMetadata.Builder().setTitle("Playback Queue").build()
                    player.prepare()

                    if (autoPlay && !player.isPlaying) {
                        requestAudioFocus()
                        player.playWhenReady = true
                        player.play()
                    } else {
                        player.playWhenReady = false
                    }
                    Log.d("PlaybackService", "ensureQueueRestored: Loaded ${tracks.size} tracks into ExoPlayer, index=$targetIndex, autoPlay=$autoPlay, pos=$safePos")
                    mediaLibrarySession?.let { refreshAndroidAutoBrowseTree(it) }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "ensureQueueRestored failed", e)
            }
        }
    }

    private suspend fun synchronizeQueueAndMetadataForAuto(controller: MediaSession.ControllerInfo, autoPlay: Boolean = false) {
        if (isServiceDestroyed) return
        withContext(Dispatchers.Main) {
            try {
                if (isServiceDestroyed) return@withContext
                Log.d("PlaybackService", "synchronizeQueueAndMetadataForAuto: controller=${controller.packageName}, autoPlay=$autoPlay, count=${player.mediaItemCount}")

                if (player.mediaItemCount == 0) {
                    val tracks = restoreQueueInternal()
                    if (tracks.isNotEmpty()) {
                        val targetIndex = queueManager.currentIndex.value.coerceIn(0, tracks.size - 1)
                        val rawPos = sessionManager.lastPlayedPositionMs.coerceAtLeast(0L)
                        val curr = tracks[targetIndex]
                        val safePos = if (curr.durationMs > 0 && rawPos >= curr.durationMs - 2000L) 0L else rawPos

                        val mediaItems = tracks.map { it.toMediaItem(path = "track", isGrid = false) }

                        streamResolver.preResolve(curr.id, curr.title, curr.artist, curr.durationMs)
                        if (targetIndex + 1 < tracks.size) {
                            val next = tracks[targetIndex + 1]
                            streamResolver.preResolve(next.id, next.title, next.artist, next.durationMs)
                        }

                        player.setMediaItems(mediaItems, targetIndex, safePos)
                        player.playlistMetadata = MediaMetadata.Builder().setTitle("Playback Queue").build()
                        player.prepare()

                        if (autoPlay && !player.isPlaying) {
                            requestAudioFocus()
                            player.playWhenReady = true
                            player.play()
                        }
                        Log.d("PlaybackService", "synchronizeQueueAndMetadataForAuto: Populated ${tracks.size} tracks into ExoPlayer, targetIndex=$targetIndex")
                    }
                } else {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    if (player.playlistMetadata.title.isNullOrBlank()) {
                        player.playlistMetadata = MediaMetadata.Builder().setTitle("Playback Queue").build()
                    }
                    if (autoPlay && !player.isPlaying) {
                        requestAudioFocus()
                        player.playWhenReady = true
                        player.play()
                    }
                }

                // Ensure current track metadata and sessionManager are up to date
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex in 0 until player.mediaItemCount) {
                    val currentItem = player.getMediaItemAt(currentIndex)
                    val cleanId = currentItem.mediaId.split("/").lastOrNull() ?: currentItem.mediaId
                    val track = queueManager.queueState.value.find { it.id == cleanId } ?: currentItem.toDomainTrack()
                    sessionManager.lastPlayedTrack = track
                    sessionManager.lastPlayedPositionMs = player.currentPosition.coerceAtLeast(0L)
                }

                mediaLibrarySession?.let { refreshAndroidAutoBrowseTree(it) }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error in synchronizeQueueAndMetadataForAuto", e)
            }
        }
    }

    private fun refreshAndroidAutoBrowseTree(session: MediaLibrarySession) {
        if (isServiceDestroyed || mediaLibrarySession == null) return
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (isServiceDestroyed || mediaLibrarySession == null) return@launch
                session.notifyChildrenChanged(AutoMediaBrowserTree.ROOT_ID, 4, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.HOME_ID, 20, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.RECENTS_ID, 20, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.BROWSE_ID, 20, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.LIBRARY_ID, 10, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.LIKED_SONGS_ID, 20, null)
                session.notifyChildrenChanged(AutoMediaBrowserTree.PLAYLISTS_ID, 20, null)
                Log.d("PlaybackService", "Refreshed Android Auto browse tree root & categories")
            } catch (e: Exception) {
                Log.e("PlaybackService", "Failed to refresh Android Auto browse tree", e)
            }
        }
    }

    private fun triggerAutoPlayOnAndroidAuto(reason: String) {
        if (isServiceDestroyed) return
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (isServiceDestroyed) return@launch
                if (player.isPlaying) {
                    Log.d("PlaybackService", "Android Auto ($reason): Already playing.")
                    return@launch
                }

                requestAudioFocus()

                if (player.mediaItemCount > 0) {
                    Log.d("PlaybackService", "Android Auto ($reason): Resuming existing queue with ${player.mediaItemCount} items.")
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    player.playWhenReady = true
                    player.play()
                    return@launch
                }

                Log.d("PlaybackService", "Android Auto ($reason): Restoring queue and auto-playing...")
                ensureQueueRestored(autoPlay = true)
            } catch (e: Exception) {
                Log.e("PlaybackService", "Android Auto ($reason): Failed to auto-play", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.vibevault.app.data.stats.ListeningStats.init(this)
        com.vibevault.app.utils.cipher.CipherDeobfuscator.initialize(this)
        com.vibevault.app.utils.BotDetectionMitigator.initialize(this)
        com.music.innertube.pages.YouTubeExtractor.cacheDir = cacheDir
        com.vibevault.app.player.audio.AudioEffectManager.init(this)
        Log.d("PlaybackService", "onCreate called — initializing fresh ExoPlayer and MediaLibrarySession")

        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        setupAudioFocusRequest()
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)

        serviceScope.launch(Dispatchers.Main) {
            dataStore.data.map { it[SkipSilenceKey] ?: false }
                .distinctUntilChanged()
                .collect { skipSilence ->
                    player.skipSilenceEnabled = skipSilence
                }
        }

        serviceScope.launch(Dispatchers.Main) {
            dataStore.data.map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged()
                .collect {
                    setupLoudnessEnhancer()
                }
        }

        serviceScope.launch(Dispatchers.Main) {
            combine(
                dataStore.data.map { it[CrossfadeEnabledKey] ?: false },
                dataStore.data.map { it[CrossfadeDurationKey] ?: 5f },
                dataStore.data.map { it[CrossfadeGaplessKey] ?: true },
                dataStore.data.map { it[CrossfadeManualSkipKey] ?: true },
                dataStore.data.map { it[CrossfadeCurveKey] ?: CrossfadeCurve.EQUAL_POWER.name }
            ) { enabled, duration, gapless, manualSkip, curveName ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f
                crossfadeGapless = gapless
                crossfadeManualSkipEnabled = manualSkip
                crossfadeCurve = try {
                    CrossfadeCurve.valueOf(curveName)
                } catch (_: Exception) {
                    CrossfadeCurve.EASE_OUT_QUAD
                }
                if (enabled) {
                    player.setOffloadEnabled(false)
                    secondaryPlayer?.setOffloadEnabled(false)
                }
                scheduleCrossfade()
            }.collectLatest { }
        }

        serviceScope.launch(Dispatchers.Main) {
            combine(
                dataStore.data.map { it[AudioOffload] ?: false },
                dataStore.data.map { it[CrossfadeEnabledKey] ?: false }
            ) { offload, crossfade ->
                if (crossfade) false else offload
            }.distinctUntilChanged().collect { offload ->
                player.setOffloadEnabled(offload)
                secondaryPlayer?.setOffloadEnabled(offload)
            }
        }

        // Keep ExoPlayer's playlist synchronized with QueueManager in real-time (startup, replacements, pagination)
        serviceScope.launch(Dispatchers.Main) {
            queueManager.queueState.collect { queue ->
                if (queue.isEmpty() || isServiceDestroyed) return@collect
                val currentIds = (0 until player.mediaItemCount).map {
                    player.getMediaItemAt(it).mediaId.split("/").lastOrNull() ?: ""
                }
                val queueIds = queue.map { it.id }

                if (currentIds != queueIds) {
                    val targetIndex = queueManager.currentIndex.value.coerceIn(0, queue.size - 1)
                    val currentPos = if (player.currentMediaItemIndex == targetIndex) {
                        player.currentPosition.coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val mediaItems = queue.map { it.toMediaItem(path = "track", isGrid = false) }

                    if (player.mediaItemCount == 0) {
                        player.playWhenReady = false
                        player.setMediaItems(mediaItems, targetIndex, currentPos)
                        player.playlistMetadata = MediaMetadata.Builder().setTitle("Playback Queue").build()
                        player.prepare()
                        Log.d("PlaybackService", "Synchronized initial queue of ${mediaItems.size} items to ExoPlayer (paused)")
                    } else if (currentIds.size < queueIds.size && currentIds == queueIds.take(currentIds.size)) {
                        val itemsToAdd = queue.drop(currentIds.size).map { it.toMediaItem(path = "track", isGrid = false) }
                        player.addMediaItems(itemsToAdd)
                        Log.d("PlaybackService", "Appended ${itemsToAdd.size} new tracks from QueueManager to ExoPlayer")
                    } else {
                        val shouldPlay = player.playWhenReady || player.isPlaying
                        player.setMediaItems(mediaItems, targetIndex, currentPos)
                        player.playlistMetadata = MediaMetadata.Builder().setTitle("Playback Queue").build()
                        player.prepare()
                        if (shouldPlay) {
                            player.play()
                        } else {
                            player.playWhenReady = false
                        }
                        Log.d("PlaybackService", "Replaced ExoPlayer queue with ${mediaItems.size} items from QueueManager (index=$targetIndex, shouldPlay=$shouldPlay)")
                    }
                    queue.drop(targetIndex).take(3).forEach { t ->
                        if (t.title.isNotBlank() && t.artist.isNotBlank()) {
                            streamResolver.preResolve(t.id, t.title, t.artist, t.durationMs)
                        }
                    }
                    mediaLibrarySession?.let { refreshAndroidAutoBrowseTree(it) }
                }
            }
        }

        // Ensure ExoPlayer is pre-hydrated on service startup without waiting for UI
        ensureQueueRestored(autoPlay = false)

        // SponsorBlock automatic segment skipping flow
        var sponsorBlockJob: Job? = null
        serviceScope.launch(Dispatchers.Main) {
            combine(
                queueManager.currentTrack,
                dataStore.data.map { prefs ->
                    SponsorBlockConfig(
                        enabled = prefs[EnableSponsorBlockKey] ?: false,
                        serverUrl = prefs[SponsorBlockServerUrlKey] ?: "https://sponsor.ajay.app",
                        skipNonMusic = prefs[SponsorBlockSkipNonMusicKey] ?: true,
                        skipSponsor = prefs[SponsorBlockSkipSponsorKey] ?: true,
                        skipSelfPromo = prefs[SponsorBlockSkipSelfPromoKey] ?: true,
                        skipInteraction = prefs[SponsorBlockSkipInteractionKey] ?: true,
                        skipIntroOutro = prefs[SponsorBlockSkipIntroOutroKey] ?: true,
                        skipPreviewFiller = prefs[SponsorBlockSkipPreviewFillerKey] ?: false,
                        showToast = prefs[SponsorBlockShowToastKey] ?: true
                    )
                }.distinctUntilChanged()
            ) { track, config ->
                track to config
            }.collectLatest { (track, config) ->
                sponsorBlockJob?.cancel()
                sponsorBlockJob = null

                if (!config.enabled || track == null || track.id.isBlank()) {
                    return@collectLatest
                }

                val categories = mutableListOf<String>()
                if (config.skipNonMusic) categories.add("music_offtopic")
                if (config.skipSponsor) categories.add("sponsor")
                if (config.skipSelfPromo) categories.add("selfpromo")
                if (config.skipInteraction) categories.add("interaction")
                if (config.skipIntroOutro) {
                    categories.add("intro")
                    categories.add("outro")
                }
                if (config.skipPreviewFiller) {
                    categories.add("preview")
                    categories.add("filler")
                }

                if (categories.isEmpty()) {
                    return@collectLatest
                }

                val segments = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    SponsorBlockApi.getSkipSegments(track.id, categories, config.serverUrl)
                        .getOrDefault(emptyList())
                }

                if (segments.isNotEmpty()) {
                    sponsorBlockJob = serviceScope.launch(Dispatchers.Main) {
                        val skippedUuids = mutableSetOf<String>()
                        while (isActive) {
                            if (player.isPlaying) {
                                val currentPos = player.currentPosition
                                val segmentToSkip = segments.find { segment ->
                                    currentPos >= segment.startMs && currentPos < (segment.endMs - 300) && !skippedUuids.contains(segment.uuid)
                                }

                                if (segmentToSkip != null) {
                                    skippedUuids.add(segmentToSkip.uuid)
                                    player.seekTo(segmentToSkip.endMs)
                                    if (config.showToast) {
                                        val messageRes = when (segmentToSkip.category) {
                                            "music_offtopic" -> R.string.sponsorblock_skipped_non_music
                                            "sponsor" -> R.string.sponsorblock_skipped_sponsor
                                            "selfpromo" -> R.string.sponsorblock_skipped_selfpromo
                                            "interaction" -> R.string.sponsorblock_skipped_interaction
                                            "intro" -> R.string.sponsorblock_skipped_intro
                                            "outro" -> R.string.sponsorblock_skipped_outro
                                            else -> R.string.sponsorblock_skipped_segment
                                        }
                                        android.widget.Toast.makeText(this@PlaybackService, getString(messageRes), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            delay(250)
                        }
                    }
                }
            }
        }

        player.addListener(servicePlayerListener)

        sleepTimer = com.vibevault.app.player.SleepTimer(CoroutineScope(Dispatchers.Main), player)
        player.addListener(sleepTimer)
        
        val callback = object : MediaLibrarySession.Callback {

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
                val isAuto = isAutoController(controller)
                if (isAuto) {
                    triggerAutoPlayOnAndroidAuto("PlaybackResumption: ${controller.packageName}")
                }
                return serviceScope.future {
                    try {
                        if (isServiceDestroyed || mediaLibrarySession == null) {
                            return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                        }
                        if (player.mediaItemCount > 0) {
                            val count = player.mediaItemCount
                            val items = (0 until count).map { player.getMediaItemAt(it) }
                            val currIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
                            val currPos = player.currentPosition.coerceAtLeast(0L)
                            if (isAuto) {
                                withContext(Dispatchers.Main) {
                                    if (!isServiceDestroyed) {
                                        requestAudioFocus()
                                        if (player.playbackState == Player.STATE_IDLE) {
                                            player.prepare()
                                        }
                                        player.playWhenReady = true
                                        player.play()
                                    }
                                }
                            }
                            MediaSession.MediaItemsWithStartPosition(items, currIndex, currPos)
                        } else {
                            val restored = restoreQueueInternal()
                            if (restored.isNotEmpty()) {
                                val targetIndex = queueManager.currentIndex.value.coerceIn(0, restored.size - 1)
                                val targetPos = sessionManager.lastPlayedPositionMs.coerceAtLeast(0L)
                                val curr = restored[targetIndex]
                                val safePos = if (curr.durationMs > 0 && targetPos >= curr.durationMs - 2000L) 0L else targetPos
                                val mediaItems = restored.map { it.toMediaItem(path = "track", isGrid = false) }
                                withContext(Dispatchers.Main) {
                                    if (!isServiceDestroyed) {
                                        streamResolver.preResolve(curr.id, curr.title, curr.artist, curr.durationMs)
                                        player.setMediaItems(mediaItems, targetIndex, safePos)
                                        player.prepare()
                                        if (isAuto) {
                                            requestAudioFocus()
                                            player.playWhenReady = true
                                            player.play()
                                        }
                                    }
                                }
                                MediaSession.MediaItemsWithStartPosition(mediaItems, targetIndex, safePos)
                            } else {
                                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error during onPlaybackResumption", e)
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                    }
                }
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                if (isServiceDestroyed) {
                    return MediaSession.ConnectionResult.reject()
                }
                val isAuto = isAutoController(controller)
                Log.d("PlaybackService", "onConnect from ${controller.packageName}, isAuto=$isAuto")

                if (isAuto) {
                    try {
                        serviceScope.launch(Dispatchers.Main) {
                            synchronizeQueueAndMetadataForAuto(controller, autoPlay = false)
                        }
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error in auto initialization onConnect", e)
                    }
                }

                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(SessionCommand("com.vibevault.LIKE", Bundle.EMPTY))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                    .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
                    .build()

                val playerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                        Player.COMMAND_SET_SHUFFLE_MODE,
                        Player.COMMAND_SET_REPEAT_MODE,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_CHANGE_MEDIA_ITEMS
                    )
                    .build()

                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    playerCommands
                )
            }

            override fun onDisconnected(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                Log.d("PlaybackService", "Controller disconnected: ${controller.packageName}")
                super.onDisconnected(session, controller)
            }

            // 1. Root Request - Return Root MediaItem with isBrowsable = true
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_SESSION_DISCONNECTED))
                }
                return try {
                    if (isAutoController(browser)) {
                        serviceScope.launch(Dispatchers.Main) {
                            synchronizeQueueAndMetadataForAuto(browser, autoPlay = false)
                        }
                    }

                    val rootExtras = Bundle().apply {
                        putInt("androidx.media.utils.CONTENT_STYLE_BROWSABLE_HINT", 1)
                        putInt("androidx.media.utils.CONTENT_STYLE_PLAYABLE_HINT", 1)
                        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, 1)
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, 1)
                        putBundle("androidx.car.app.CarAppInfo", Bundle())
                    }

                    val rootItem = MediaItem.Builder()
                        .setMediaId(AutoMediaBrowserTree.ROOT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("VibeVault")
                                .setArtworkUri(AutoMediaBrowserTree.safeArtworkUri(null))
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setExtras(rootExtras)
                                .build()
                        )
                        .build()
                    Futures.immediateFuture(
                        LibraryResult.ofItem(rootItem, params.withContentStyleHints())
                    )
                } catch (e: Throwable) {
                    Log.e("PlaybackService", "Error in onGetLibraryRoot", e)
                    val fallbackRoot = MediaItem.Builder()
                        .setMediaId(AutoMediaBrowserTree.ROOT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("VibeVault")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                    Futures.immediateFuture(LibraryResult.ofItem(fallbackRoot, params.withContentStyleHints()))
                }
            }

            // 2. Children Request - Return browsable folders or playable track items
            // Guaranteed to NEVER return null and NEVER throw an uncaught exception.
            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                    )
                }
                if (!AutoMediaBrowserTree.isValidParentId(parentId)) {
                    Log.w("PlaybackService", "onGetChildren: Unknown or invalid parentId: '$parentId'")
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                    )
                }
                return serviceScope.future {
                    try {
                        if (isServiceDestroyed || mediaLibrarySession == null) {
                            return@future LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                        }
                        val items = autoMediaBrowserTree.getChildren(parentId)
                        val paginated = items.paginate(page, pageSize)
                        LibraryResult.ofItemList(ImmutableList.copyOf(paginated), params.withContentStyleHints())
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Exception in onGetChildren for parentId=$parentId", e)
                        LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                    }
                }
            }

            // 3. Individual Item Request - Handle DHU requests for single items
            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_SESSION_DISCONNECTED))
                }
                return serviceScope.future {
                    try {
                        val item = autoMediaBrowserTree.resolvePlayableItem(mediaId, MediaItem.Builder().setMediaId(mediaId).build())
                        val cleanId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
                        val title = item.mediaMetadata.title?.toString() ?: ""
                        val artist = item.mediaMetadata.artist?.toString() ?: ""
                        
                        val uri = item.requestMetadata.mediaUri
                            ?: item.localConfiguration?.uri
                            ?: android.net.Uri.Builder()
                                .scheme("vibevault")
                                .authority("stream")
                                .appendQueryParameter("id", cleanId)
                                .appendQueryParameter("title", title)
                                .appendQueryParameter("artist", artist)
                                .build()

                        val playableItem = item.buildUpon()
                            .setUri(uri)
                            .setMediaMetadata(
                                item.mediaMetadata.buildUpon()
                                    .setArtworkUri(AutoMediaBrowserTree.safeArtworkUri(item.mediaMetadata.artworkUri?.toString()))
                                    .build()
                            )
                            .build()

                        LibraryResult.ofItem(playableItem, null)
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error resolving item $mediaId", e)
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(LibraryResult.ofVoid())
                }
                return serviceScope.future {
                    try {
                        val results = autoMediaBrowserTree.search(query)
                        if (!isServiceDestroyed && mediaLibrarySession != null) {
                            session.notifySearchResultChanged(browser, query, results.size, params)
                        }
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error during onSearch for query '$query'", e)
                    }
                    LibraryResult.ofVoid()
                }
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints()))
                }
                return serviceScope.future {
                    try {
                        val results = autoMediaBrowserTree.search(query)
                        val paginated = results.paginate(page, pageSize)
                        LibraryResult.ofItemList(ImmutableList.copyOf(paginated), params.withContentStyleHints())
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error in onGetSearchResult for query '$query'", e)
                        LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                    }
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaItemsWithStartPosition> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs))
                }
                return serviceScope.future {
                    val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
                    try {
                        val firstItem = mediaItems.firstOrNull() ?: return@future defaultResult

                        val (playableItems, targetIndex) = if (mediaItems.size > 1) {
                            mediaItems.toList() to startIndex.coerceIn(0, mediaItems.size - 1)
                        } else {
                            val rawId = firstItem.mediaId
                            val path = rawId.split("/")
                            val category = path.firstOrNull() ?: ""
                            val targetTrackId = path.lastOrNull() ?: rawId

                            val categoryItems = autoMediaBrowserTree.getChildren(category)
                            
                            if (categoryItems.isNotEmpty() && categoryItems.any { it.mediaId.endsWith(targetTrackId) }) {
                                val items = categoryItems.filter { it.mediaMetadata.isPlayable == true }
                                val idx = items.indexOfFirst { it.mediaId.endsWith(targetTrackId) || it.mediaId == targetTrackId }
                                    .coerceAtLeast(0)
                                items to idx
                            } else {
                                val singleItem = autoMediaBrowserTree.resolvePlayableItem(rawId, firstItem)
                                listOf(singleItem) to 0
                            }
                        }

                        val resolvedList = playableItems.map { item ->
                            val cleanId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
                            val title = item.mediaMetadata.title?.toString() ?: ""
                            val artist = item.mediaMetadata.artist?.toString() ?: ""
                            val isLiked = item.mediaMetadata.extras?.getBoolean("isLiked") ?: false
                            
                            val durMs = item.mediaMetadata.extras?.getLong("durationMs") ?: 0L
                            val durationSec = if (durMs > 0) durMs / 1000L else 0L

                            if (title.isNotBlank() && artist.isNotBlank()) {
                                streamResolver.preResolve(cleanId, title, artist, durMs)
                            }

                            val streamUri = item.requestMetadata.mediaUri
                                ?: item.localConfiguration?.uri
                                ?: android.net.Uri.Builder()
                                    .scheme("vibevault")
                                    .authority("stream")
                                    .appendQueryParameter("id", cleanId)
                                    .appendQueryParameter("title", title)
                                    .appendQueryParameter("artist", artist)
                                    .apply {
                                        if (durationSec > 0) appendQueryParameter("duration", durationSec.toString())
                                    }
                                    .build()

                            item.buildUpon()
                                .setUri(streamUri)
                                .setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setArtworkUri(AutoMediaBrowserTree.safeArtworkUri(item.mediaMetadata.artworkUri?.toString()))
                                        .setExtras(Bundle().apply {
                                            putBoolean("isLiked", isLiked)
                                            if (durMs > 0) putLong("durationMs", durMs)
                                        })
                                        .build()
                                )
                                .build()
                        }

                        // Sync newly set queue to QueueManager so state is shared across app and Android Auto
                        val domainTracks = resolvedList.map { it.toDomainTrack() }
                        withContext(Dispatchers.Main) {
                            if (!isServiceDestroyed) {
                                queueManager.setQueue(domainTracks, targetIndex)
                            }
                        }

                        MediaItemsWithStartPosition(resolvedList, targetIndex, startPositionMs)
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error during onSetMediaItems", e)
                        defaultResult
                    }
                }
            }

            // 4. Add MediaItems Request - Convert browser MediaItems into fully populated MediaItems with URIs for ExoPlayer
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                if (isServiceDestroyed || mediaLibrarySession == null) {
                    return Futures.immediateFuture(mediaItems.toList())
                }
                return serviceScope.future {
                    try {
                        mediaItems.toList().map { item ->
                            val rawId = item.mediaId
                            val cleanId = rawId.split("/").lastOrNull() ?: rawId
                            
                            if (rawId.startsWith("http://") || rawId.startsWith("https://")) {
                                return@map item
                            }
                            
                            val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Track $cleanId"
                            val artist = item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Artist"
                            val album = item.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                            val safeArtUri = AutoMediaBrowserTree.safeArtworkUri(item.mediaMetadata.artworkUri?.toString())
                            val isLiked = item.mediaMetadata.extras?.getBoolean("isLiked") ?: false
                            val durMs = item.mediaMetadata.extras?.getLong("durationMs") ?: 0L
                            val durationSec = if (durMs > 0) durMs / 1000L else 0L
                            
                            if (title.isNotBlank() && artist != "Unknown Artist") {
                                streamResolver.preResolve(cleanId, title, artist, durMs)
                            }
                            
                            val uri = item.requestMetadata.mediaUri
                                ?: item.localConfiguration?.uri
                                ?: android.net.Uri.Builder()
                                    .scheme("vibevault")
                                    .authority("stream")
                                    .appendQueryParameter("id", cleanId)
                                    .appendQueryParameter("title", title)
                                    .appendQueryParameter("artist", artist)
                                    .apply {
                                        if (durationSec > 0) appendQueryParameter("duration", durationSec.toString())
                                    }
                                    .build()
                                
                            item.buildUpon()
                                .setUri(uri)
                                .setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setTitle(title)
                                        .setSubtitle(artist)
                                        .setArtist(artist)
                                        .setAlbumTitle(album)
                                        .setAlbumArtist(artist)
                                        .setDisplayTitle(title)
                                        .setArtworkUri(safeArtUri)
                                        .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
                                            putBoolean("isLiked", isLiked)
                                            safeArtUri?.toString()?.let { putString("artwork_uri", it) }
                                        })
                                        .build()
                                )
                                .build()
                        }.toMutableList()
                    } catch (e: Throwable) {
                        Log.e("PlaybackService", "Error during onAddMediaItems", e)
                        mediaItems.toList()
                    }
                }
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                if (isServiceDestroyed) return SessionResult.RESULT_ERROR_INVALID_STATE
                when (playerCommand) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        serviceScope.launch(Dispatchers.Main) {
                            if (!isServiceDestroyed && !manualSkipNextWithCrossfade()) {
                                queueManager.next()
                            }
                        }
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        serviceScope.launch(Dispatchers.Main) {
                            if (!isServiceDestroyed && !manualSkipToPreviousWithCrossfade()) {
                                queueManager.previous()
                            }
                        }
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customAction: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (isServiceDestroyed) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED))
                }
                return when (customAction.customAction) {
                    "com.vibevault.LIKE" -> {
                        val trackId = args.getString("trackId")
                            ?: player.currentMediaItem?.mediaId?.split("/")?.lastOrNull()
                        if (trackId == null) {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        } else {
                            serviceScope.future {
                                try {
                                    musicRepository.toggleLike(trackId)
                                    SessionResult(SessionResult.RESULT_SUCCESS)
                                } catch (e: Throwable) {
                                    Log.e("PlaybackService", "Error toggling like for $trackId", e)
                                    SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                                }
                            }
                        }
                    }
                    else -> super.onCustomCommand(session, controller, customAction, args)
                }
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val stopMusicOnTaskClear = dataStore.get(StopMusicOnTaskClearKey, false)
        if (stopMusicOnTaskClear) {
            player.stop()
            stopSelf()
            return
        }
        val p = mediaLibrarySession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        serviceScope.coroutineContext.cancelChildren()
        abandonAudioFocus()
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        releaseLoudnessEnhancer()
        if (::sleepTimer.isInitialized) {
            player.removeListener(sleepTimer)
            sleepTimer.clear()
        }
        player.removeListener(servicePlayerListener)
        if (instance == this) {
            instance = null
        }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private fun LibraryParams?.withContentStyleHints(): LibraryParams {
        val extras = Bundle(this?.extras ?: Bundle()).apply {
            putInt("androidx.media.utils.CONTENT_STYLE_BROWSABLE_HINT", 1)
            putInt("androidx.media.utils.CONTENT_STYLE_PLAYABLE_HINT", 1)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                1
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                1
            )
            putBundle("androidx.car.app.CarAppInfo", Bundle())
        }

        return LibraryParams.Builder()
            .setOffline(this?.isOffline ?: false)
            .setRecent(this?.isRecent ?: false)
            .setSuggested(this?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }

    private val retryCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException) {
        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId.ifBlank { return }

        val retries = retryCounts.getOrDefault(mediaId, 0)
        val autoSkip = dataStore.get(AutoSkipNextOnErrorKey, false)

        if (retries >= 3 || autoSkip) {
            Log.w("PlaybackService", "Max retries reached or autoSkipNextOnError enabled for $mediaId — advancing to next track.")
            retryCounts.remove(mediaId)
            serviceScope.launch(Dispatchers.Main) {
                queueManager.next()
            }
            return
        }

        retryCounts[mediaId] = retries + 1
        Log.d("PlaybackService", "Attempting playback error recovery for $mediaId (retry ${retries + 1}/3)...")

        serviceScope.launch(Dispatchers.Main) {
            val currentPos = player.currentPosition.coerceAtLeast(0L)
            
            streamResolver.invalidateCacheForTrack(mediaId)
            com.vibevault.app.data.youtube.YTPlayerUtils.forceRefreshForVideo(mediaId)

            player.seekTo(currentPos)
            player.prepare()
            player.play()
        }
    }
}