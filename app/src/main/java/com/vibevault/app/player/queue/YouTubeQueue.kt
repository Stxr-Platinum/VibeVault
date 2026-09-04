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
            var resolvedSongId: String? = null

            // If preloadItem is provided, resolve its YouTube ID if needed
            if (preloadItem != null) {
                val isYouTubeId = preloadItem.id.length == 11 && !preloadItem.id.contains(" ") && !preloadItem.id.contains(":") && !preloadItem.id.contains("?")
                if (!isYouTubeId) {
                    try {
                        val searchQuery = "${preloadItem.title} ${preloadItem.artist}".trim()
                        if (searchQuery.isNotBlank()) {
                            val searchPage = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                                query = searchQuery,
                                items = searchPage?.items.orEmpty(),
                                expectedDurationSec = if (preloadItem.durationMs > 0) (preloadItem.durationMs / 1000L).toInt() else null,
                                targetTitle = preloadItem.title,
                                targetArtist = preloadItem.artist
                            )
                            if (bestMatch != null) {
                                resolvedSongId = bestMatch.id
                                endpoint = WatchEndpoint(
                                    videoId = bestMatch.id,
                                    playlistId = "RDAMVM${bestMatch.id}"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore resolution exception
                    }
                } else {
                    resolvedSongId = preloadItem.id
                    if (endpoint.playlistId == null) {
                        endpoint = WatchEndpoint(
                            videoId = preloadItem.id,
                            playlistId = "RDAMVM${preloadItem.id}"
                        )
                    }
                }
            }

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

                    if (preloadItem != null) {
                        // CRITICAL: Ensure preloadItem is placed at index 0 and mediaItemIndex = 0!
                        // Remove any duplicate of preloadItem in the fetched recommendations
                        val existingIndex = filteredTracks.indexOfFirst { 
                            it.id == preloadItem.id || 
                            (resolvedSongId != null && it.id == resolvedSongId) ||
                            (it.title.equals(preloadItem.title, ignoreCase = true) && 
                             (it.artist.contains(preloadItem.artist, ignoreCase = true) || preloadItem.artist.contains(it.artist, ignoreCase = true)))
                        }
                        if (existingIndex >= 0) {
                            filteredTracks.removeAt(existingIndex)
                        }
                        // Prepend preloadItem as the active first item
                        filteredTracks.add(0, preloadItem)

                        return@withContext Queue.Status(
                            title = nextResult.title ?: preloadItem.title,
                            items = filteredTracks,
                            mediaItemIndex = 0,
                        )
                    }

                    return@withContext Queue.Status(
                        title = nextResult.title ?: "Radio",
                        items = filteredTracks,
                        mediaItemIndex = 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0 && preloadItem != null && resolvedSongId == null) {
                        try {
                            val searchQuery = "${preloadItem.artist} ${preloadItem.title}".trim()
                            if (searchQuery.isNotBlank()) {
                                val searchPage = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                val resolvedSong = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                                    query = searchQuery,
                                    items = searchPage?.items.orEmpty(),
                                    expectedDurationSec = if (preloadItem.durationMs > 0) (preloadItem.durationMs / 1000L).toInt() else null,
                                    targetTitle = preloadItem.title,
                                    targetArtist = preloadItem.artist
                                )
                                if (resolvedSong != null) {
                                    resolvedSongId = resolvedSong.id
                                    endpoint = WatchEndpoint(
                                        videoId = resolvedSong.id,
                                        playlistId = "RDAMVM${resolvedSong.id}"
                                    )
                                }
                            }
                        } catch (se: Exception) {
                            // ignore
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
            val isYouTubeId = track.id.length == 11 && !track.id.contains(" ") && !track.id.contains(":") && !track.id.contains("?")
            return YouTubeQueue(
                WatchEndpoint(videoId = if (isYouTubeId) track.id else null),
                track
            )
        }
    }
}
