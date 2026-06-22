package com.vibevault.app.player.service

import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
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

    private fun resolveStreamUrl(item: MediaItem): String? {
        val trackId = item.mediaId
        
        val title = item.mediaMetadata.title?.toString() ?: ""
        val artist = item.mediaMetadata.artist?.toString() ?: ""
        val query = java.net.URLEncoder.encode("$title $artist".trim(), "UTF-8")

        Log.d("PlaybackService", "Attempting to resolve stream for: $title by $artist (Query: $query)")

        if (query.isNotEmpty()) {
            for (instance in qobuzInstances) {
                try {
                    val searchUrl = URL("$instance/api/get-music?q=$query&offset=0")
                    val searchConn = searchUrl.openConnection() as HttpURLConnection
                    searchConn.requestMethod = "GET"
                    searchConn.connectTimeout = 5000
                    searchConn.readTimeout = 5000

                    var qobuzTrackId: String? = null
                    if (searchConn.responseCode == 200) {
                        val response = searchConn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val tracks = json.optJSONObject("data")?.optJSONObject("tracks")?.optJSONArray("items")
                        if (tracks != null && tracks.length() > 0) {
                            qobuzTrackId = tracks.getJSONObject(0).optString("id")
                            Log.d("PlaybackService", "Found Qobuz Track ID: $qobuzTrackId")
                        }
                    }

                    if (qobuzTrackId != null) {
                        val url = URL("$instance/api/download-music?track_id=$qobuzTrackId&quality=6")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        if (connection.responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().readText()
                            val json = JSONObject(response)
                            val data = json.optJSONObject("data")
                            if (data != null && data.has("url")) {
                                val streamUrl = data.getString("url")
                                Log.d("PlaybackService", "Resolved stream URL via $instance")
                                return streamUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Failed to resolve Qobuz via $instance", e)
                }
            }

            // LISTENFREE / JIOSAAVN FALLBACK
            try {
                Log.d("PlaybackService", "Trying ListenFree fallback for: $query")
                val jioUrl = URL("https://zmkvknwtqclvtijdoobh.supabase.co/functions/v1/listenfree-proxy/api/search/songs?limit=1&query=$query")
                val jioConn = jioUrl.openConnection() as HttpURLConnection
                jioConn.requestMethod = "GET"
                jioConn.connectTimeout = 5000
                jioConn.readTimeout = 5000

                if (jioConn.responseCode == 200) {
                    val response = jioConn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val results = json.optJSONObject("data")?.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val topResult = results.getJSONObject(0)
                        val downloadUrlArray = topResult.optJSONArray("downloadUrl")
                        if (downloadUrlArray != null && downloadUrlArray.length() > 0) {
                            // Highest quality is usually the last element
                            val highestQuality = downloadUrlArray.getJSONObject(downloadUrlArray.length() - 1)
                            val streamUrl = highestQuality.optString("url")
                            if (streamUrl.isNotEmpty()) {
                                Log.d("PlaybackService", "Resolved stream URL via ListenFree fallback")
                                return streamUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "ListenFree fallback failed", e)
            }
        }
        
        Log.e("PlaybackService", "Could not resolve stream URL for $trackId")
        return null
    }

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
                        
                        // If it's already a direct URL, don't re-resolve it
                        if (trackId.startsWith("http://") || trackId.startsWith("https://")) {
                            return@map item
                        }
                        
                        // Resolve the stream URL using the fallback cascade
                        val resolvedUrl = resolveStreamUrl(item)
                        
                        if (resolvedUrl != null) {
                            item.buildUpon()
                                .setUri(resolvedUrl)
                                .build()
                        } else {
                            // Return an item with a safe dummy URI to prevent ExoPlayer NullPointerException
                            item.buildUpon()
                                .setUri("https://error.invalid/stream_not_found.mp3")
                                .build()
                        }
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
        player.pause()
        stopSelf()
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
