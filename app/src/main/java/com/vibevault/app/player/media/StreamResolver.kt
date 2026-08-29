package com.vibevault.app.player.media

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.vibevault.app.constants.AudioQuality
import com.vibevault.app.data.youtube.YTPlayerUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

import com.vibevault.app.utils.StreamClientUtils
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem

@androidx.media3.common.util.UnstableApi
@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : ResolvingDataSource.Resolver {

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoIdCache = ConcurrentHashMap<String, String>()
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>() // videoId -> (url, expiryMs)

    fun invalidateCacheForTrack(key: String) {
        streamUrlCache.remove(key)
        videoIdCache.remove(key)
        Timber.d("StreamResolver invalidated cache for key: $key")
    }

    fun preResolve(title: String, artist: String) {
        if (title.isBlank()) return
        val cleanArtist = if (artist.equals("Unknown", ignoreCase = true) || artist.equals("Unknown Artist", ignoreCase = true)) "" else artist
        val cleanTitle = if (title.startsWith("Track ")) "" else title
        val query = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return

        resolverScope.launch {
            try {
                val videoId = resolveVideoIdInternal(query, "")
                if (videoId.isNotBlank()) {
                    resolveStreamUrlInternal(videoId, query)
                    Timber.d("StreamResolver preResolved successfully for: $query -> $videoId")
                }
            } catch (e: Exception) {
                Timber.w("StreamResolver preResolve background task failed for $query: ${e.message}")
            }
        }
    }

    private suspend fun resolveVideoIdInternal(query: String, cleanTrackId: String): String {
        val sanitizedId = cleanTrackId.trim().split("/").lastOrNull()?.trim().orEmpty()
        if (sanitizedId.length == 11 && !sanitizedId.contains(" ") && !sanitizedId.contains("?") && !sanitizedId.contains("=")) {
            return sanitizedId
        }

        val cacheKey = query.ifBlank { sanitizedId }
        if (cacheKey.isNotBlank()) {
            videoIdCache[cacheKey]?.let { cachedId ->
                return cachedId
            }
        }

        var resolvedId: String? = null
        val isVersionQuery = com.vibevault.app.utils.TrackMatcher.hasVersionModifier(cacheKey)

        if (isVersionQuery) {
            val videoResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
            val summaryResult = YouTube.searchSummary(cacheKey).getOrNull()?.summaries?.flatMap { it.items }.orEmpty()
            val allCandidates = videoResult + summaryResult

            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(cacheKey, allCandidates)
            resolvedId = bestMatch?.id
        } else {
            val songResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
            val videoResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
            val summaryResult = YouTube.searchSummary(cacheKey).getOrNull()?.summaries?.flatMap { it.items }.orEmpty()
            val allCandidates = songResult + videoResult + summaryResult

            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(cacheKey, allCandidates)
            resolvedId = bestMatch?.id
        }

        val validId = resolvedId?.takeIf { it.length == 11 }
            ?: throw Exception("No YouTube video ID found for query: '$cacheKey'")

        if (cacheKey.isNotBlank()) {
            videoIdCache[cacheKey] = validId
        }
        return validId
    }

    private suspend fun resolveStreamUrlInternal(videoId: String, query: String): String {
        val cached = streamUrlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Timber.d("StreamResolver hit memory cache for videoId: $videoId")
            return cached.first
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val result = YTPlayerUtils.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = connectivityManager,
            context = context
        )

        val nonNullPlayback = result.getOrNull()
        val rawStreamUrl = nonNullPlayback?.streamUrl
            ?: throw java.io.IOException("No playable audio stream URL returned for query: '$query'")

        var streamUrl = rawStreamUrl
        if (streamUrl.contains("n=")) {
            try {
                var transformed = com.vibevault.app.utils.sabr.EjsNTransformSolver.transformNParamInUrl(streamUrl)
                if (transformed == streamUrl) {
                    transformed = com.vibevault.app.utils.cipher.CipherDeobfuscator.transformNParamInUrl(streamUrl)
                }
                if (transformed == streamUrl) {
                    transformed = com.music.innertube.pages.YouTubeExtractor.deobfuscateUrlNParam(streamUrl)
                }
                if (transformed != streamUrl) {
                    streamUrl = transformed
                }
            } catch (e: Exception) {
                Timber.w(e, "N-transform in StreamResolver failed")
            }
        }

        val expiresInSec = nonNullPlayback.streamExpiresInSeconds?.toLong() ?: 21600L
        val expiresMs = System.currentTimeMillis() + (expiresInSec * 1000L) - 60000L
        streamUrlCache[videoId] = streamUrl to expiresMs
        return streamUrl
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val targetStreamUrl: String = if (uri.scheme == "vibevault" && uri.authority == "stream") {
            val rawTrackId = uri.getQueryParameter("id") ?: ""
            val cleanTrackId = rawTrackId.split("/").lastOrNull()?.trim() ?: ""
            val rawTitle = uri.getQueryParameter("title") ?: ""
            val rawArtist = uri.getQueryParameter("artist") ?: ""

            val cleanArtist = if (rawArtist.equals("Unknown", ignoreCase = true) || rawArtist.equals("Unknown Artist", ignoreCase = true)) "" else rawArtist
            val cleanTitle = if (rawTitle.startsWith("Track ")) "" else rawTitle
            val query = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")

            runBlocking {
                try {
                    val videoId = resolveVideoIdInternal(query, cleanTrackId)
                    resolveStreamUrlInternal(videoId, query)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to resolve stream for id=$cleanTrackId, query=$query")
                    if (e is java.io.IOException) throw e
                    else throw java.io.IOException("Failed to resolve stream for id=$cleanTrackId: ${e.message}", e)
                }
            }
        } else {
            uri.toString()
        }

        val parsedUri = android.net.Uri.parse(targetStreamUrl)
        val host = parsedUri.host.orEmpty()
        val isYouTubeMediaHost = host.endsWith("googlevideo.com") ||
                host.endsWith("googleusercontent.com") ||
                host.endsWith("youtube.com") ||
                host.endsWith("ytimg.com")

        if (isYouTubeMediaHost) {
            val clientParam = parsedUri.getQueryParameter("c")?.trim().orEmpty()
            val userAgent = StreamClientUtils.resolveUserAgent(clientParam)
            val originReferer = StreamClientUtils.resolveOriginReferer(clientParam)

            val headers = mutableMapOf<String, String>()
            headers.putAll(dataSpec.httpRequestHeaders)
            headers["User-Agent"] = userAgent
            headers["Accept"] = "*/*"
            headers["X-Goog-Api-Format-Version"] = "2"
            originReferer.origin?.let { headers["Origin"] = it }
            originReferer.referer?.let { headers["Referer"] = it }

            return dataSpec.buildUpon()
                .setUri(targetStreamUrl)
                .setHttpRequestHeaders(headers)
                .build()
        }

        return if (targetStreamUrl != uri.toString()) {
            dataSpec.buildUpon().setUri(targetStreamUrl).build()
        } else {
            dataSpec
        }
    }
}
