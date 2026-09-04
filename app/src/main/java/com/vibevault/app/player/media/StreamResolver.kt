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
import kotlinx.coroutines.async
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
    private val inFlightResolutions = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String>>()

    fun invalidateCacheForTrack(key: String) {
        streamUrlCache.remove(key)
        videoIdCache.remove(key)
        inFlightResolutions.remove(key)?.cancel()
        Timber.d("StreamResolver invalidated cache for key: $key")
    }

    private fun buildQuery(title: String, artist: String): String {
        val cleanArtist = if (artist.equals("Unknown", ignoreCase = true) || artist.equals("Unknown Artist", ignoreCase = true)) "" else artist
        val cleanTitle = if (title.startsWith("Track ", ignoreCase = true)) "" else title
        return listOf(cleanArtist, cleanTitle).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun preResolve(trackId: String, title: String, artist: String, durationMs: Long = 0L) {
        val cleanTrackId = trackId.split("/").lastOrNull()?.trim() ?: ""
        val query = buildQuery(title, artist)
        if (cleanTrackId.isBlank() && query.isBlank()) return

        if (cleanTrackId.length == 11 && !cleanTrackId.contains(" ") && !cleanTrackId.contains("?") && !cleanTrackId.contains("=")) {
            val cached = streamUrlCache[cleanTrackId]
            if (cached != null && cached.second > System.currentTimeMillis()) return
        }

        resolverScope.launch {
            try {
                val expectedDurationSec = if (durationMs > 0) (durationMs / 1000L).toInt() else null
                val videoId = resolveVideoIdInternal(query, cleanTrackId, expectedDurationSec, title, artist)
                if (videoId.isNotBlank()) {
                    resolveStreamUrlInternal(videoId, query)
                    Timber.d("StreamResolver preResolved successfully for: $query / $cleanTrackId -> $videoId")
                }
            } catch (e: Exception) {
                Timber.w("StreamResolver preResolve background task failed for $query / $cleanTrackId: ${e.message}")
            }
        }
    }

    fun preResolve(trackId: String, title: String, artist: String) {
        preResolve(trackId, title, artist, 0L)
    }

    fun preResolve(title: String, artist: String) {
        preResolve("", title, artist, 0L)
    }

    private suspend fun resolveVideoIdInternal(
        query: String,
        cleanTrackId: String,
        expectedDurationSec: Int? = null,
        targetTitle: String? = null,
        targetArtist: String? = null
    ): String {
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
        if (sanitizedId.isNotBlank()) {
            videoIdCache[sanitizedId]?.let { cachedId ->
                return cachedId
            }
        }

        var resolvedId: String? = null
        val isVersionQuery = com.vibevault.app.utils.TrackMatcher.hasVersionModifier(cacheKey)

        val summaryResult = YouTube.searchSummary(cacheKey).getOrNull()
        val topResultItems = summaryResult?.summaries?.find { it.title.equals("Top result", ignoreCase = true) }?.items.orEmpty()
        val summaryItems = summaryResult?.summaries?.flatMap { it.items }.orEmpty()
        val topResultIds = topResultItems.map { it.id }.toSet()

        if (isVersionQuery) {
            val videoResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
            val allCandidates = (topResultItems + videoResult + summaryItems).distinctBy { it.id }

            val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                query = cacheKey,
                items = allCandidates,
                expectedDurationSec = expectedDurationSec,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                topResultIds = topResultIds
            )
            resolvedId = bestMatch?.id
        } else {
            val songResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
            val songCandidates = (topResultItems + songResult + summaryItems.filterIsInstance<com.music.innertube.models.SongItem>()).distinctBy { it.id }

            if (songCandidates.isNotEmpty()) {
                val bestSongMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                    query = cacheKey,
                    items = songCandidates,
                    expectedDurationSec = expectedDurationSec,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    topResultIds = topResultIds
                )
                if (bestSongMatch != null && bestSongMatch.id.length == 11) {
                    resolvedId = bestSongMatch.id
                }
            }
            if (resolvedId == null) {
                val videoResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty()
                val allCandidates = (topResultItems + songResult + summaryItems + videoResult).distinctBy { it.id }

                val bestMatch = com.vibevault.app.utils.TrackMatcher.selectBestMatch(
                    query = cacheKey,
                    items = allCandidates,
                    expectedDurationSec = expectedDurationSec,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    topResultIds = topResultIds
                )
                resolvedId = bestMatch?.id
            }
        }

        val validId = resolvedId?.takeIf { it.length == 11 }
            ?: throw Exception("No YouTube video ID found for query: '$cacheKey'")

        if (cacheKey.isNotBlank()) {
            videoIdCache[cacheKey] = validId
        }
        if (sanitizedId.isNotBlank()) {
            videoIdCache[sanitizedId] = validId
        }
        return validId
    }

    private suspend fun resolveStreamUrlInternal(videoId: String, query: String): String {
        val cached = streamUrlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Timber.d("StreamResolver hit memory cache for videoId: $videoId")
            return cached.first
        }

        val deferred = inFlightResolutions.computeIfAbsent(videoId) {
            resolverScope.async {
                try {
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
                    streamUrl
                } finally {
                    inFlightResolutions.remove(videoId)
                }
            }
        }

        return deferred.await()
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val targetStreamUrl: String = if (uri.scheme == "vibevault" && uri.authority == "stream") {
            val rawTrackId = uri.getQueryParameter("id") ?: ""
            val cleanTrackId = rawTrackId.split("/").lastOrNull()?.trim() ?: ""
            val rawTitle = uri.getQueryParameter("title") ?: ""
            val rawArtist = uri.getQueryParameter("artist") ?: ""
            val expectedDurationSec = uri.getQueryParameter("duration")?.toIntOrNull()

            val query = buildQuery(rawTitle, rawArtist)

            runBlocking {
                try {
                    val videoId = resolveVideoIdInternal(query, cleanTrackId, expectedDurationSec, rawTitle, rawArtist)
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
