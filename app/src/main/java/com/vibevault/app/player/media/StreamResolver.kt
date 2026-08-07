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

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem

@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : ResolvingDataSource.Resolver {

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoIdCache = ConcurrentHashMap<String, String>()
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>() // videoId -> (url, expiryMs)

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
        val cacheKey = query.ifBlank { cleanTrackId }
        videoIdCache[cacheKey]?.let { cachedId ->
            return cachedId
        }

        if (cleanTrackId.length == 11 && !cleanTrackId.contains(" ") && !cleanTrackId.contains("/")) {
            videoIdCache[cacheKey] = cleanTrackId
            return cleanTrackId
        }

        var resolvedId: String? = null

        // 1. Try YouTube song filter search
        val songResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
        resolvedId = songResult?.firstOrNull { it is SongItem }?.id ?: songResult?.firstOrNull()?.id

        // 2. Try YouTube video filter search
        if (resolvedId == null) {
            val videoResult = YouTube.search(cacheKey, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items
            resolvedId = videoResult?.firstOrNull { it is SongItem }?.id ?: videoResult?.firstOrNull()?.id
        }

        // 3. Fallback to summary search
        if (resolvedId == null) {
            val summaryResult = YouTube.searchSummary(cacheKey).getOrNull()?.summaries
            val summaryItems = summaryResult?.flatMap { it.items }.orEmpty()
            resolvedId = summaryItems.firstOrNull { it is SongItem }?.id ?: summaryItems.firstOrNull()?.id
        }

        val validId = resolvedId?.takeIf { it.length == 11 }
            ?: throw Exception("No YouTube video ID found for query: '$cacheKey'")

        videoIdCache[cacheKey] = validId
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
        val streamUrl = nonNullPlayback?.streamUrl
            ?: throw java.io.IOException("No playable audio stream URL returned for query: '$query'")

        val expiresInSec = nonNullPlayback.streamExpiresInSeconds?.toLong() ?: 21600L
        val expiresMs = System.currentTimeMillis() + (expiresInSec * 1000L) - 60000L
        streamUrlCache[videoId] = streamUrl to expiresMs
        return streamUrl
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme == "vibevault" && uri.authority == "stream") {
            val rawTrackId = uri.getQueryParameter("id") ?: ""
            val cleanTrackId = rawTrackId.split("/").lastOrNull()?.trim() ?: ""
            val rawTitle = uri.getQueryParameter("title") ?: ""
            val rawArtist = uri.getQueryParameter("artist") ?: ""

            val cleanArtist = if (rawArtist.equals("Unknown", ignoreCase = true) || rawArtist.equals("Unknown Artist", ignoreCase = true)) "" else rawArtist
            val cleanTitle = if (rawTitle.startsWith("Track ")) "" else rawTitle
            val query = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")

            return runBlocking {
                try {
                    val videoId = resolveVideoIdInternal(query, cleanTrackId)
                    val streamUrl = resolveStreamUrlInternal(videoId, query)
                    dataSpec.buildUpon().setUri(streamUrl).build()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to resolve stream for id=$cleanTrackId, query=$query")
                    if (e is java.io.IOException) throw e
                    else throw java.io.IOException("Failed to resolve stream for id=$cleanTrackId: ${e.message}", e)
                }
            }
        }
        return dataSpec
    }
}
