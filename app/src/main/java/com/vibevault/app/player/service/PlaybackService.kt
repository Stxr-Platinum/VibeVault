package com.vibevault.app.player.service

import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.vibevault.app.player.media.StreamResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * PlaybackService — Foreground service for uninterrupted audio playback.
 *
 * Implements a background network interceptor: When the UI sends a MediaItem
 * containing only a Qobuz Track ID, this service intercepts the item, executes
 * a multi-instance cascade fetch to resolve the direct FLAC URL, and provides
 * it to the ExoPlayer.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var streamResolver: StreamResolver

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val tidalInstances = listOf(
        "https://eu-central.monochrome.tf",
        "https://us-west.monochrome.tf",
        "https://api.monochrome.tf",
        "https://monochrome-api.samidy.com"
    )

    private val qobuzInstances = listOf(
        "https://qobuz.kennyy.com.br",
        "https://mono.scavengerfurs.net"
    )

    override fun onCreate() {
        super.onCreate()
        
        val callback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                return serviceScope.future {
                    mediaItems.map { item ->
                        val trackId = item.mediaId
                        
                        // If it's already a direct URL, don't touch it
                        if (trackId.startsWith("http://") || trackId.startsWith("https://")) {
                            return@map item
                        }
                        
                        val title = item.mediaMetadata.title?.toString() ?: ""
                        val artist = item.mediaMetadata.artist?.toString() ?: ""
                        val titleEnc = java.net.URLEncoder.encode(title, "UTF-8")
                        val artistEnc = java.net.URLEncoder.encode(artist, "UTF-8")
                        
                        // Fire off proactive URL resolution in the background!
                        streamResolver.preResolve(title, artist)
                        
                        // Defer resolution to StreamResolver via custom scheme
                        item.buildUpon()
                            .setUri("vibevault://stream?title=$titleEnc&artist=$artistEnc")
                            .build()
                    }
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
