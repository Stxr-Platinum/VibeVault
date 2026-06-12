package com.vibevault.app.player

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var repository: MusicRepository

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return serviceScope.future {
                val resolvedItems = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    try {
                        if (item.localConfiguration?.uri == null) {
                            // No URI — resolve via Qobuz using metadata
                            val title = item.mediaMetadata.title?.toString() ?: ""
                            val artist = item.mediaMetadata.artist?.toString() ?: ""
                            val isrc = item.mediaMetadata.extras?.getString("isrc") ?: ""
                            
                            android.util.Log.d("PlaybackDebug", "Resolving stream for: '$title' by '$artist' [mediaId=${item.mediaId}, isrc=$isrc]")
                            
                            var streamUrl: String? = null
                            
                            // Strategy 1: ISRC-based search (most precise)
                            if (isrc.isNotBlank()) {
                                try {
                                    val qobuzTracks = repository.searchQobuzMusic("isrc:$isrc")
                                    val match = qobuzTracks.firstOrNull()
                                    if (match != null) {
                                        val matchTitle = match.title.lowercase()
                                        val matchArtist = match.artist.lowercase()
                                        val expectedTitle = title.lowercase()
                                        val expectedArtist = artist.lowercase()
                                        
                                        // Sanity check: Qobuz sometimes returns random garbage for valid ISRCs.
                                        // Ensure either the title or artist roughly matches.
                                        val isTitleMatch = matchTitle.contains(expectedTitle) || expectedTitle.contains(matchTitle)
                                        val isArtistMatch = matchArtist.contains(expectedArtist) || expectedArtist.contains(matchArtist)
                                        
                                        if (isTitleMatch || isArtistMatch) {
                                            streamUrl = repository.getQobuzStreamUrl(match.id)
                                            android.util.Log.d("PlaybackDebug", "Resolved via ISRC: ${match.title} → stream OK")
                                        } else {
                                            android.util.Log.w("PlaybackDebug", "ISRC returned mismatched track: '${match.title}' by '${match.artist}'. Falling back to text search.")
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PlaybackDebug", "ISRC search failed for $isrc: ${e.message}")
                                }
                            }
                            
                            // Strategy 2: Artist + Title search
                            if (streamUrl == null && title.isNotBlank()) {
                                try {
                                    val query = if (artist.isNotBlank()) "$artist $title" else title
                                    val qobuzTracks = repository.searchQobuzMusic(query)
                                    val match = qobuzTracks.firstOrNull()
                                    if (match != null) {
                                        streamUrl = repository.getQobuzStreamUrl(match.id)
                                        android.util.Log.d("PlaybackDebug", "Resolved via search '$query': ${match.title} by ${match.artist} → stream OK")
                                    } else {
                                        android.util.Log.w("PlaybackDebug", "No Qobuz results for '$query'")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PlaybackDebug", "Qobuz search failed for '$title': ${e.message}")
                                }
                            }
                            
                            // Strategy 3: Spotify preview URL (30s fallback)
                            if (streamUrl == null) {
                                try {
                                    streamUrl = repository.getSpotifyPreviewUrl(item.mediaId)
                                    if (streamUrl != null) {
                                        android.util.Log.d("PlaybackDebug", "Fell back to Spotify preview for ${item.mediaId}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PlaybackDebug", "Spotify preview also failed: ${e.message}")
                                }
                            }
                            
                            if (streamUrl != null) {
                                val resolvedItem = item.buildUpon()
                                    .setUri(Uri.parse(streamUrl))
                                    .build()
                                resolvedItems.add(resolvedItem)
                            } else {
                                android.util.Log.e("PlaybackDebug", "FAILED to resolve any stream for '$title' by '$artist'")
                            }
                        } else {
                            resolvedItems.add(item)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackDebug", "Unexpected error resolving media item", e)
                    }
                }
                resolvedItems
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    emptyList(),
                    C.INDEX_UNSET,
                    C.TIME_UNSET
                )
            )
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer != null && !currentPlayer.playWhenReady || currentPlayer?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
