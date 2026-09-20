package com.vibevault.app.player.queue

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
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

                    // Expand candidate pool via related songs if available
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

                    // Further expand pool via continuation if available to ensure large pool for true randomization
                    if (rawTracks.size < 35 && continuation != null) {
                        try {
                            val nextBatch = YouTube.next(endpoint, continuation).getOrNull()
                            if (nextBatch != null) {
                                continuation = nextBatch.continuation
                                endpoint = nextBatch.endpoint
                                for (batchItem in nextBatch.items) {
                                    val t = batchItem.toTrack()
                                    if (rawTracks.none { it.id == t.id }) {
                                        rawTracks.add(t)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore continuation exception
                        }
                    }

                    // If still under-populated, add search recommendations based on artist
                    if (rawTracks.size < 30 && activeTrack != null && activeTrack.artist.isNotBlank()) {
                        try {
                            val artistQuery = "${activeTrack.artist} song"
                            val searchResults = YouTube.search(artistQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            searchResults?.items?.filterIsInstance<SongItem>()?.forEach { item ->
                                val t = item.toTrack()
                                if (rawTracks.none { it.id == t.id }) {
                                    rawTracks.add(t)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore search exception
                        }
                    }

                    // Remove occurrences of the currently playing seed song from upcoming candidate pool
                    val upcomingCandidates = rawTracks.filter { cand ->
                        val isSameId = cand.id == currentTrackId || (resolvedSongId != null && cand.id == resolvedSongId)
                        val isSameTitleAndArtist = activeTrack != null &&
                            cand.title.equals(activeTrack.title, ignoreCase = true) &&
                            (cand.artist.contains(activeTrack.artist, ignoreCase = true) || activeTrack.artist.contains(cand.artist, ignoreCase = true))
                        !isSameId && !isSameTitleAndArtist
                    }

                    // Apply diversity filter (1 per artist max)
                    val diversePool = upcomingCandidates.filterDiverseCharacteristics(
                        maxPerArtist = 1,
                        currentlyPlayingTrackId = currentTrackId
                    )

                    // Partition into fresh (not recently recommended) and seen (recently recommended)
                    val freshPool = diversePool.filterNot { isRecentlyRecommended(it.id) }.shuffled()
                    val seenPool = diversePool.filter { isRecentlyRecommended(it.id) }.shuffled()

                    // Prioritize fresh tracks to prevent repeated queues, then fill with seen tracks
                    val randomizedUpcoming = (freshPool + seenPool).take(25)
                    recordRecommended(randomizedUpcoming.map { it.id })

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
        private val recentlyRecommendedIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
        private const val MAX_RECENT_TRACKS = 150

        fun recordRecommended(trackIds: Collection<String>) {
            synchronized(recentlyRecommendedIds) {
                recentlyRecommendedIds.addAll(trackIds)
                while (recentlyRecommendedIds.size > MAX_RECENT_TRACKS) {
                    val iter = recentlyRecommendedIds.iterator()
                    if (iter.hasNext()) {
                        iter.next()
                        iter.remove()
                    } else break
                }
            }
        }

        fun isRecentlyRecommended(trackId: String): Boolean {
            return recentlyRecommendedIds.contains(trackId)
        }

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
