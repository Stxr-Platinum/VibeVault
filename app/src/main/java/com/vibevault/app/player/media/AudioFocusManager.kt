package com.vibevault.app.player.media

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioFocusManager — Handles audio focus changes for ducking and pausing.
 *
 * Responds to:
 *   - AUDIOFOCUS_LOSS: Pause (another app took permanent focus)
 *   - AUDIOFOCUS_LOSS_TRANSIENT: Pause (phone call, navigation prompt)
 *   - AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Lower volume to 20%
 *   - AUDIOFOCUS_GAIN: Restore full volume and resume if was playing
 */
@Singleton
class AudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioFocusManager"
        private const val DUCK_VOLUME = 0.2f
        private const val FULL_VOLUME = 1.0f
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    /**
     * Request audio focus before starting playback.
     * Returns true if focus was granted.
     */
    fun requestFocus(player: ExoPlayer): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d(TAG, "Focus GAINED — restoring volume")
                    player.volume = FULL_VOLUME
                    if (wasPlayingBeforeFocusLoss) {
                        player.play()
                        wasPlayingBeforeFocusLoss = false
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(TAG, "Focus LOST (permanent) — pausing")
                    wasPlayingBeforeFocusLoss = player.isPlaying
                    player.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.d(TAG, "Focus LOST (transient) — pausing")
                    wasPlayingBeforeFocusLoss = player.isPlaying
                    player.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Focus LOST (can duck) — lowering volume")
                    player.volume = DUCK_VOLUME
                }
            }
        }

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(listener)
            .build()

        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Abandon audio focus when playback stops.
     */
    fun abandonFocus() {
        focusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            Log.d(TAG, "Audio focus abandoned")
        }
        focusRequest = null
    }
}
