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
            val title = uri.getQueryParameter("title") ?: ""
            val artist = uri.getQueryParameter("artist") ?: ""
            val query = "$title $artist".trim()
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return runBlocking {
                try {
                    val videoId = if (cleanTrackId.length == 11 && !cleanTrackId.contains(" ") && !cleanTrackId.contains("/")) {
                        Timber.d("StreamResolver using direct videoId: $cleanTrackId")
                        cleanTrackId
                    } else {
                        val searchQuery = if (query.isNotBlank()) query else cleanTrackId
                        Timber.d("StreamResolver searching YouTube for: $searchQuery")
                        val searchResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG)
                        val searchItems = searchResult.getOrNull()?.items
                        val songItem = searchItems?.firstOrNull { it is SongItem } as? SongItem
                            ?: searchItems?.firstOrNull() as? SongItem
                        
                        songItem?.id ?: cleanTrackId.takeIf { it.isNotBlank() } ?: throw Exception("No video found for query: $searchQuery")
                    }
                    
                    Timber.d("StreamResolver resolving stream for videoId: $videoId")

                    // Resolve the stream URL using the actual video ID
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
                        dataSpec
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to resolve stream for id=$cleanTrackId, query=$query")
                    dataSpec
                }
            }
        }
        return dataSpec
    }
}
