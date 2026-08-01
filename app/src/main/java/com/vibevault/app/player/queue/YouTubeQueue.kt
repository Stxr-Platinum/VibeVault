package com.vibevault.app.player.queue

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.vibevault.app.domain.model.Track
import com.vibevault.app.extensions.toTrack
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: Track? = null,
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

                    val rawTracks = nextResult.items.map { it.toTrack() }
                    val currentTrackId = preloadItem?.id ?: rawTracks.firstOrNull()?.id
                    val filteredTracks = rawTracks.filterDiverseCharacteristics(
                        maxPerArtist = 1,
                        currentlyPlayingTrackId = currentTrackId
                    ).toMutableList()

                    // If filtering out same-artist tracks left fewer than 10 songs,
                    // fetch YouTube's relatedEndpoint for acoustic & style similarity tracks from other artists
                    if (filteredTracks.size < 10 && nextResult.relatedEndpoint != null) {
                        try {
                            val relatedPage = YouTube.related(nextResult.relatedEndpoint!!).getOrNull()
                            if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                val relatedTracks = relatedPage.songs.map { it.toTrack() }
                                    .filterDiverseCharacteristics(maxPerArtist = 1, currentlyPlayingTrackId = currentTrackId)
                                
                                for (relTrack in relatedTracks) {
                                    if (filteredTracks.none { it.id == relTrack.id }) {
                                        filteredTracks.add(relTrack)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore related fetch exception
                        }
                    }

                    val targetIndex = filteredTracks.indexOfFirst { 
                        it.id == currentTrackId || it.title.equals(preloadItem?.title, ignoreCase = true) 
                    }.coerceAtLeast(0)

                    return@withContext Queue.Status(
                        title = nextResult.title ?: preloadItem?.title ?: "Radio",
                        items = filteredTracks,
                        mediaItemIndex = targetIndex,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0 && preloadItem != null) {
                        try {
                            val searchQuery = "${preloadItem.artist} ${preloadItem.title}".trim()
                            if (searchQuery.isNotBlank()) {
                                val searchPage = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                val resolvedSong = searchPage?.items?.filterIsInstance<com.music.innertube.models.SongItem>()?.firstOrNull()
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
            if (preloadItem != null) {
                return@withContext Queue.Status(
                    title = preloadItem.title,
                    items = listOf(preloadItem),
                    mediaItemIndex = 0,
                )
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<Track> {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toTrack() }
                        .filterDiverseCharacteristics(maxPerArtist = 1)
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
        fun radio(track: Track): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = track.id),
                track
            )
        }
    }
}
