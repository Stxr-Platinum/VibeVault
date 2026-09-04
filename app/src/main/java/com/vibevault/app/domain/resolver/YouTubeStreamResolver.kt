package com.vibevault.app.domain.resolver

import android.content.Context
import android.net.ConnectivityManager
import com.vibevault.app.constants.AudioQuality
import com.vibevault.app.data.youtube.YTPlayerUtils
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1 Stream Resolver — YouTube InnerTube API.
 * Uses client contexts (ANDROID_VR primary, WEB_REMIX metadata, TVHTML5/WEB_CREATOR fallbacks)
 * to resolve YouTube streams and validates via HTTP HEAD status checks.
 */
@Singleton
class YouTubeStreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : StreamResolverTier {

    override val tierName: String = "YouTube InnerTube (Tier 1)"
    override val priority: Int = 10

    override suspend fun resolve(spec: TrackResolutionSpec): YTPlayerUtils.PlaybackData? {
        val videoId = resolveVideoId(spec) ?: return null
        Timber.tag("YouTubeStreamResolver").d("Resolved videoId=$videoId for '${spec.title}'")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Perform YouTube InnerTube stream resolution (direct YouTube path without JioSaavn override)
        val result = YTPlayerUtils.playerResponseForPlayback(
            videoId = videoId,
            playlistId = null,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = connectivityManager,
            context = null // context=null bypasses JioSaavn intercept inside YTPlayerUtils to guarantee Tier 1 path
        )

        val playbackData = result.getOrNull() ?: return null

        if (playbackData.streamUrl.isNotBlank()) {
            Timber.tag("YouTubeStreamResolver").d("Successfully obtained YouTube stream URL for videoId=$videoId")
            return playbackData
        }

        return null
    }

    private suspend fun resolveVideoId(spec: TrackResolutionSpec): String? {
        if (!spec.videoId.isNullOrBlank() && spec.videoId.length == 11) {
            return spec.videoId
        }

        val cleanArtist = if (spec.artist.equals("Unknown", ignoreCase = true) || spec.artist.equals("Unknown Artist", ignoreCase = true)) "" else spec.artist
        val query = listOf(cleanArtist, spec.title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null

        val isVersionQuery = com.vibevault.app.utils.TrackMatcher.hasVersionModifier(query)

        val summaryResult = YouTube.searchSummary(query).getOrNull()
        val topResultItems = summaryResult?.summaries?.find { it.title.equals("Top result", ignoreCase = true) }?.items.orEmpty()
        val summaryItems = summaryResult?.summaries?.flatMap { it.items }.orEmpty()
        val topResultIds = topResultItems.map { it.id }.toSet()

        if (isVersionQuery) {
            val videoResult = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
            val candidates = (topResultItems + videoResult + summaryItems).distinctBy { it.id }
            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                query = query,
                items = candidates,
                expectedDurationSec = spec.durationSeconds,
                targetTitle = spec.title,
                targetArtist = spec.artist,
                topResultIds = topResultIds
            )
            return bestMatch?.id?.takeIf { it.length == 11 }
        } else {
            val songResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
            val songCandidates = (topResultItems + songResult + summaryItems.filterIsInstance<SongItem>()).distinctBy { it.id }

            if (songCandidates.isNotEmpty()) {
                val bestSongMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                    query = query,
                    items = songCandidates,
                    expectedDurationSec = spec.durationSeconds,
                    targetTitle = spec.title,
                    targetArtist = spec.artist,
                    topResultIds = topResultIds
                )
                if (bestSongMatch != null && bestSongMatch.id.length == 11) {
                    return bestSongMatch.id
                }
            }
            val videoResult = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
            val candidates = (topResultItems + songResult + summaryItems + videoResult).distinctBy { it.id }
            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                query = query,
                items = candidates,
                expectedDurationSec = spec.durationSeconds,
                targetTitle = spec.title,
                targetArtist = spec.artist,
                topResultIds = topResultIds
            )
            return bestMatch?.id?.takeIf { it.length == 11 }
        }
    }
}
