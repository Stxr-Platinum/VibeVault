package com.vibevault.app.domain.resolver

import android.content.Context
import com.vibevault.app.constants.EnableSaavnStreamingKey
import com.vibevault.app.constants.SaavnAudioQuality
import com.vibevault.app.constants.SaavnAudioQualityKey
import com.vibevault.app.data.youtube.YTPlayerUtils
import com.vibevault.app.utils.dataStore
import com.vibevault.app.utils.get
import com.music.jiosaavn.SaavnService
import com.music.jiosaavn.SaavnSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 3 Secondary Provider Intercept — JioSaavn.
 * When enabled via user setting, searches JioSaavn for high-bitrate AAC streams (up to 320kbps),
 * decrypts DES-ECB CDN links, and merges the stream into the standard PlaybackData structure.
 */
@Singleton
class JioSaavnStreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : StreamResolverTier {

    override val tierName: String = "JioSaavn Intercept (Tier 3)"
    override val priority: Int = 1 // Highest priority when enabled

    override val isEnabled: Boolean
        get() = runBlocking {
            context.dataStore.get(EnableSaavnStreamingKey, false)
        }

    override suspend fun resolve(spec: TrackResolutionSpec): YTPlayerUtils.PlaybackData? {
        if (!isEnabled) {
            Timber.tag("JioSaavnStreamResolver").d("JioSaavn streaming is disabled in user settings. Skipping Tier 3.")
            return null
        }

        val title = spec.title
        val artist = if (spec.artist.equals("Unknown", ignoreCase = true)) "" else spec.artist
        if (title.isBlank() || com.vibevault.app.utils.TrackMatcher.hasVersionModifier(title)) {
            return null
        }

        Timber.tag("JioSaavnStreamResolver").d("Attempting JioSaavn intercept for: '$title' by '$artist'")

        val searchResult = SaavnService.searchSongs("$title $artist".trim()).getOrNull()
            ?: return null

        val wantedTitleLower = title.lowercase(Locale.US)
        val wantedArtistLower = artist.lowercase(Locale.US)

        val bestSong = searchResult.firstOrNull { candidate ->
            val candidateTitle = candidate.name.lowercase(Locale.US)
            val candidateArtists = candidate.artists.primary.map { it.name.lowercase(Locale.US) }
            val titleMatches = candidateTitle == wantedTitleLower || candidateTitle.contains(wantedTitleLower)
            val artistMatches = candidateArtists.any { it.contains(wantedArtistLower) || wantedArtistLower.contains(it) }
            titleMatches && (artistMatches || artist.isBlank())
        } ?: searchResult.firstOrNull() ?: return null

        val qualityKey = context.dataStore.get(SaavnAudioQualityKey, SaavnAudioQuality.QUALITY_320.name)
        val quality = runCatching { SaavnAudioQuality.valueOf(qualityKey) }
            .getOrDefault(SaavnAudioQuality.QUALITY_320)

        var streamUrl = SaavnService.selectBestUrl(bestSong.downloadUrl, quality.toApiValue())
        if (streamUrl.isNullOrBlank()) {
            streamUrl = SaavnService.getBestStreamUrl(bestSong.id, quality.toApiValue())
        }

        if (streamUrl.isNullOrBlank()) {
            Timber.tag("JioSaavnStreamResolver").d("No playable stream URL returned from JioSaavn for songId=${bestSong.id}")
            return null
        }

        val contentLength = SaavnService.getContentLength(streamUrl)

        Timber.tag("JioSaavnStreamResolver").i("Successfully resolved JioSaavn stream: $streamUrl (quality=${quality.toApiValue()}, length=$contentLength)")

        return YTPlayerUtils.PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format = com.music.innertube.models.response.PlayerResponse.StreamingData.Format(
                itag = when (quality) {
                    SaavnAudioQuality.QUALITY_320 -> 141
                    SaavnAudioQuality.QUALITY_160 -> 140
                    SaavnAudioQuality.QUALITY_96 -> 139
                },
                url = streamUrl,
                mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                bitrate = when (quality) {
                    SaavnAudioQuality.QUALITY_320 -> 320_000
                    SaavnAudioQuality.QUALITY_160 -> 160_000
                    SaavnAudioQuality.QUALITY_96 -> 96_000
                },
                width = null,
                height = null,
                contentLength = contentLength,
                quality = quality.toApiValue(),
                fps = null,
                qualityLabel = null,
                averageBitrate = null,
                audioQuality = quality.toApiValue(),
                approxDurationMs = null,
                audioSampleRate = null,
                audioChannels = null,
                loudnessDb = null,
                lastModified = null,
                signatureCipher = null,
                cipher = null,
                audioTrack = null
            ),
            streamUrl = streamUrl,
            streamExpiresInSeconds = 3600,
            isSaavnStream = true
        )
    }
}
