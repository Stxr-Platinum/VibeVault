package com.vibevault.app.player.queue

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.RadioChip
import com.vibevault.app.domain.model.Track
import com.vibevault.app.extensions.toTrack
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class YouTubeQueue(
    var endpoint: WatchEndpoint,
    override val preloadItem: Track? = null,
) : Queue {
    private val _radioChips = MutableStateFlow<List<RadioChip>>(emptyList())
    override val radioChips: StateFlow<List<RadioChip>> = _radioChips.asStateFlow()

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
                                    playlistId = "RDAMVM${bestMatch.id}",
                                    params = endpoint.params
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore resolution exception
                    }
                } else {
                    resolvedSongId = preloadItem.id
                    if (endpoint.videoId == null) {
                        endpoint = WatchEndpoint(
                            videoId = preloadItem.id,
                            playlistId = "RDAMVM${preloadItem.id}",
                            params = endpoint.params
                        )
                    }
                }
            }

            // Always use RDAMVM for radio queues to ensure YouTube Radio Mix and Radio Chips are provided
            if (endpoint.videoId != null && endpoint.playlistId == null) {
                endpoint = WatchEndpoint(
                    videoId = endpoint.videoId,
                    playlistId = "RDAMVM${endpoint.videoId}",
                    params = endpoint.params
                )
            }

            for (attempt in 0..maxRetries) {
                try {
                    var nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                    // Fallback for radio chips if initial endpoint returned empty chips
                    if (nextResult.radioChips.isEmpty() && endpoint.videoId != null) {
                        try {
                            val fallbackResult = YouTube.next(WatchEndpoint(videoId = endpoint.videoId)).getOrNull()
                            if (fallbackResult != null && fallbackResult.radioChips.isNotEmpty()) {
                                nextResult = nextResult.copy(radioChips = fallbackResult.radioChips)
                            }
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }

                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    _radioChips.value = nextResult.radioChips
                    retryCount = 0

                    val rawTracks = nextResult.items.map { it.toTrack() }.toMutableList()
                    val activeTrack = preloadItem ?: rawTracks.firstOrNull()
                    val currentTrackId = activeTrack?.id ?: resolvedSongId

                    // Incorporate relatedEndpoint songs to expand the recommendation pool
                    if (nextResult.relatedEndpoint != null) {
                        try {
                            val relatedPage = YouTube.related(nextResult.relatedEndpoint!!).getOrNull()
                            if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                val relatedTracks = relatedPage.songs.map { it.toTrack() }
                                for (relTrack in relatedTracks) {
                                    if (rawTracks.none { it.id == relTrack.id }) {
                                        rawTracks.add(relTrack)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore related fetch exception
                        }
                    }

                    // Remove any occurrences of the currently playing song from the upcoming candidate pool
                    val upcomingCandidates = rawTracks.filter { cand ->
                        val isSameId = cand.id == currentTrackId || (resolvedSongId != null && cand.id == resolvedSongId)
                        val isSameTitleAndArtist = activeTrack != null &&
                            cand.title.equals(activeTrack.title, ignoreCase = true) &&
                            (cand.artist.contains(activeTrack.artist, ignoreCase = true) || activeTrack.artist.contains(cand.artist, ignoreCase = true))
                        !isSameId && !isSameTitleAndArtist
                    }

                    // Apply diversity filter (1 per artist max) to candidate pool
                    val diversePool = upcomingCandidates.filterDiverseCharacteristics(
                        maxPerArtist = 1,
                        currentlyPlayingTrackId = currentTrackId
                    )

                    // Shuffle the diverse upcoming pool so that replaying the same song produces a fresh, dynamic queue
                    val randomizedUpcoming = diversePool.shuffled()

                    val finalTracks = if (preloadItem != null) {
                        listOf(preloadItem) + randomizedUpcoming
                    } else if (rawTracks.isNotEmpty()) {
                        listOf(rawTracks.first()) + randomizedUpcoming
                    } else {
                        randomizedUpcoming
                    }

                    return@withContext Queue.Status(
                        title = nextResult.title ?: preloadItem?.title ?: "Radio",
                        items = finalTracks,
                        mediaItemIndex = 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0) {
                        if (preloadItem != null && resolvedSongId == null) {
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
                        if (endpoint.videoId != null && endpoint.playlistId == null) {
                            endpoint = WatchEndpoint(
                                videoId = endpoint.videoId,
                                playlistId = "RDAMVM${endpoint.videoId}"
                            )
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
            val videoId = if (isYouTubeId) track.id else null
            return YouTubeQueue(
                WatchEndpoint(
                    videoId = videoId,
                    playlistId = if (videoId != null) "RDAMVM$videoId" else null
                ),
                track
            )
        }
    }
}
