package com.vibevault.app.player.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * PlaybackService — Foreground service for uninterrupted audio playback.
 *
 * Extends MediaSessionService so that:
 *   - Playback continues when the app is backgrounded or screen is off.
 *   - Lock screen / notification controls are automatically provided.
 *   - Bluetooth/car head-unit integration works via MediaSession.
 *
 * ExoPlayer and MediaSession are injected by Hilt from MediaModule.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var mediaSession: MediaSession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
