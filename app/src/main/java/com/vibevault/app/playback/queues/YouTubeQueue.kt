package com.vibevault.app.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.vibevault.app.extensions.toMediaItem
import com.vibevault.app.models.MediaMetadata
import com.vibevault.app.utils.TrackMatcher
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext Queue.Status(
                        title = nextResult.title ?: preloadItem?.title ?: "Radio",
                        items = nextResult.items.map { it.toMediaItem() },
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0 && preloadItem != null) {
                        try {
                            val artistString = preloadItem.artists.joinToString(" ") { it.name }
                            val searchQuery = "$artistString ${preloadItem.title}".trim()
                            if (searchQuery.isNotBlank()) {
                                val searchPage = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                val resolvedSong = TrackMatcher.selectBestMatch(searchQuery, searchPage?.items.orEmpty())
                                if (resolvedSong != null) {
                                    endpoint = WatchEndpoint(
                                        videoId = resolvedSong.id,
                                        playlistId = "RDAMVM${resolvedSong.id}"
                                    )
                                } else if (endpoint.videoId != null && endpoint.playlistId == null) {
                                    endpoint = WatchEndpoint(
                                        videoId = endpoint.videoId,
                                        playlistId = "RDAMVM${endpoint.videoId}"
                                    )
                                }
                            }
                        } catch (se: Exception) {
                            if (endpoint.videoId != null && endpoint.playlistId == null) {
                                endpoint = WatchEndpoint(
                                    videoId = endpoint.videoId,
                                    playlistId = "RDAMVM${endpoint.videoId}"
                                )
                            }
                        }
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
