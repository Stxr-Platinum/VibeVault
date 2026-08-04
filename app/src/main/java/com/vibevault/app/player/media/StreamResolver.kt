package com.vibevault.app.player.media

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.vibevault.app.constants.AudioQuality
import com.vibevault.app.data.youtube.YTPlayerUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem

@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : ResolvingDataSource.Resolver {

    fun preResolve(title: String, artist: String) {
        // Optional pre-fetching
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
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return runBlocking {
                try {
                    val videoId = if (cleanTrackId.length == 11 && !cleanTrackId.contains(" ") && !cleanTrackId.contains("/")) {
                        Timber.d("StreamResolver using direct videoId: $cleanTrackId")
                        cleanTrackId
                    } else {
                        val searchQuery = query.ifBlank { cleanTrackId }
                        Timber.d("StreamResolver searching YouTube for: $searchQuery")
                        
                        var resolvedId: String? = null

                        // 1. Try YouTube song filter search
                        val songResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                        resolvedId = songResult?.firstOrNull { it is SongItem }?.id
                            ?: songResult?.firstOrNull()?.id

                        // 2. Try YouTube video filter search
                        if (resolvedId == null) {
                            Timber.d("StreamResolver song filter yielded no items, trying video filter search for: $searchQuery")
                            val videoResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items
                            resolvedId = videoResult?.firstOrNull { it is SongItem }?.id
                                ?: videoResult?.firstOrNull()?.id
                        }

                        // 3. Fallback to summary search
                        if (resolvedId == null) {
                            Timber.d("StreamResolver video filter yielded no items, trying summary search for: $searchQuery")
                            val summaryResult = YouTube.searchSummary(searchQuery).getOrNull()?.summaries
                            val summaryItems = summaryResult?.flatMap { it.items }.orEmpty()
                            resolvedId = summaryItems.firstOrNull { it is SongItem }?.id
                                ?: summaryItems.firstOrNull()?.id
                        }

                        // 4. Fallback to searching title only
                        if (resolvedId == null && cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                            Timber.d("StreamResolver trying title-only search for: $cleanTitle")
                            val titleResult = YouTube.search(cleanTitle, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                            resolvedId = titleResult?.firstOrNull { it is SongItem }?.id
                                ?: titleResult?.firstOrNull()?.id
                        }

                        resolvedId?.takeIf { it.length == 11 }
                            ?: throw Exception("No YouTube video ID found for query: '$searchQuery'")
                    }
                    
                    Timber.d("StreamResolver resolving stream for videoId: $videoId")

                    // Resolve the stream URL using the actual 11-char video ID
                    val result = YTPlayerUtils.playerResponseForPlayback(
                        videoId = videoId,
                        audioQuality = AudioQuality.HIGH,
                        connectivityManager = connectivityManager,
                        context = context
                    )
                    
                    val streamUrl = result.getOrNull()?.streamUrl
                    if (streamUrl != null) {
                        dataSpec.buildUpon().setUri(streamUrl).build()
                    } else {
                        throw java.io.IOException("No playable audio stream URL returned for query: '$query'")
                    }
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
