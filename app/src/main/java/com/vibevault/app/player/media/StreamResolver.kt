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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val trackId = uri.getQueryParameter("id")
            val title = uri.getQueryParameter("title") ?: ""
            val artist = uri.getQueryParameter("artist") ?: ""
            val query = "$title $artist"
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return runBlocking {
                try {
                    val videoId = if (!trackId.isNullOrEmpty() && trackId.length == 11 && !trackId.contains(" ")) {
                        Timber.d("StreamResolver using direct videoId: $trackId")
                        trackId
                    } else {
                        // Search YouTube for the video ID using innertube
                        val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        val searchItems = searchResult.getOrNull()?.items
                        val songItem = searchItems?.firstOrNull { it is SongItem } as? SongItem
                            ?: searchItems?.firstOrNull() // Fallback if no SongItem found
                        
                        songItem?.id ?: throw Exception("No video found for query: $query")
                    }
                    
                    Timber.d("StreamResolver resolving stream for videoId: $videoId")

                    // 2. Resolve the stream URL using the actual video ID
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
                    Timber.e(e, "Failed to resolve stream for $query")
                    dataSpec
                }
            }
        }
        return dataSpec
    }
}
