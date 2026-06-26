package com.vibevault.app.player.media

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class StreamResolver @Inject constructor() : ResolvingDataSource.Resolver {

    private val preloadCache = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val qobuzInstances = listOf(
        "https://api.qobuz.freemyip.com",
        "https://api.qobuz.karpik.pl",
        "https://qobuz.ngrok.app",
        "https://api.qobuz.karpik.pl"
    )

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme == "vibevault" && uri.host == "stream") {
            val title = uri.getQueryParameter("title") ?: ""
            val artist = uri.getQueryParameter("artist") ?: ""
            
            val query = java.net.URLEncoder.encode("$title $artist".trim(), "UTF-8")
            Log.d("StreamResolver", "Attempting to resolve stream for: $title by $artist (Query: $query)")

            val cachedUrl = preloadCache.remove(query)
            if (cachedUrl != null) {
                Log.d("StreamResolver", "Preload cache hit for query: $query")
                return dataSpec.buildUpon().setUri(cachedUrl).build()
            }

            if (query.isNotEmpty()) {
                for (instance in qobuzInstances) {
                    try {
                        val searchUrl = URL("$instance/api/get-music?q=$query&offset=0")
                        val searchConn = searchUrl.openConnection() as HttpURLConnection
                        searchConn.requestMethod = "GET"
                        searchConn.connectTimeout = 5000
                        searchConn.readTimeout = 5000

                        var qobuzTrackId: String? = null
                        if (searchConn.responseCode == 200) {
                            val response = searchConn.inputStream.bufferedReader().readText()
                            val json = JSONObject(response)
                            val tracks = json.optJSONObject("data")?.optJSONObject("tracks")?.optJSONArray("items")
                            if (tracks != null && tracks.length() > 0) {
                                qobuzTrackId = tracks.getJSONObject(0).optString("id")
                                Log.d("StreamResolver", "Found Qobuz Track ID: $qobuzTrackId")
                            }
                        }

                        if (qobuzTrackId != null) {
                            val url = URL("$instance/api/download-music?track_id=$qobuzTrackId&quality=6")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000

                            if (connection.responseCode == 200) {
                                val response = connection.inputStream.bufferedReader().readText()
                                val json = JSONObject(response)
                                val data = json.optJSONObject("data")
                                if (data != null && data.has("url")) {
                                    val streamUrl = data.getString("url")
                                    Log.d("StreamResolver", "Resolved stream URL via $instance")
                                    return dataSpec.buildUpon().setUri(streamUrl).build()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StreamResolver", "Failed to resolve Qobuz via $instance", e)
                    }
                }

                // LISTENFREE / JIOSAAVN FALLBACK
                try {
                    Log.d("StreamResolver", "Trying ListenFree fallback for: $query")
                    val jioUrl = URL("https://zmkvknwtqclvtijdoobh.supabase.co/functions/v1/listenfree-proxy/api/search/songs?limit=1&query=$query")
                    val jioConn = jioUrl.openConnection() as HttpURLConnection
                    jioConn.requestMethod = "GET"
                    jioConn.connectTimeout = 5000
                    jioConn.readTimeout = 5000

                    if (jioConn.responseCode == 200) {
                        val response = jioConn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val results = json.optJSONObject("data")?.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val topResult = results.getJSONObject(0)
                            val downloadUrlArray = topResult.optJSONArray("downloadUrl")
                            if (downloadUrlArray != null && downloadUrlArray.length() > 0) {
                                // Highest quality is usually the last element
                                val highestQuality = downloadUrlArray.getJSONObject(downloadUrlArray.length() - 1)
                                val streamUrl = highestQuality.optString("url")
                                if (streamUrl.isNotEmpty()) {
                                    Log.d("StreamResolver", "Resolved stream URL via ListenFree fallback")
                                    return dataSpec.buildUpon().setUri(streamUrl).build()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StreamResolver", "ListenFree fallback failed", e)
                }
            }
            
            Log.e("StreamResolver", "Could not resolve stream URL for query: $query")
            // Return error uri if unable to resolve
            return dataSpec.buildUpon().setUri("https://error.invalid/stream_not_found.mp3").build()
        }
        
        return dataSpec
    }

    fun preResolve(title: String, artist: String) {
        scope.launch {
            val query = java.net.URLEncoder.encode("$title $artist".trim(), "UTF-8")
            if (query.isEmpty() || preloadCache.containsKey(query)) return@launch
            
            Log.d("StreamResolver", "Pre-resolving stream for: $query")

            for (instance in qobuzInstances) {
                try {
                    val searchUrl = URL("$instance/api/get-music?q=$query&offset=0")
                    val searchConn = searchUrl.openConnection() as HttpURLConnection
                    searchConn.requestMethod = "GET"
                    searchConn.connectTimeout = 5000
                    searchConn.readTimeout = 5000

                    var qobuzTrackId: String? = null
                    if (searchConn.responseCode == 200) {
                        val response = searchConn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val tracks = json.optJSONObject("data")?.optJSONObject("tracks")?.optJSONArray("items")
                        if (tracks != null && tracks.length() > 0) {
                            qobuzTrackId = tracks.getJSONObject(0).optString("id")
                        }
                    }

                    if (qobuzTrackId != null) {
                        val url = URL("$instance/api/download-music?track_id=$qobuzTrackId&quality=6")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        if (connection.responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().readText()
                            val json = JSONObject(response)
                            val data = json.optJSONObject("data")
                            if (data != null && data.has("url")) {
                                val streamUrl = data.getString("url")
                                preloadCache[query] = streamUrl
                                Log.d("StreamResolver", "Successfully pre-resolved via $instance")
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore and fallback
                }
            }

            // LISTENFREE / JIOSAAVN FALLBACK
            try {
                val jioUrl = URL("https://zmkvknwtqclvtijdoobh.supabase.co/functions/v1/listenfree-proxy/api/search/songs?limit=1&query=$query")
                val jioConn = jioUrl.openConnection() as HttpURLConnection
                jioConn.requestMethod = "GET"
                jioConn.connectTimeout = 5000
                jioConn.readTimeout = 5000

                if (jioConn.responseCode == 200) {
                    val response = jioConn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val results = json.optJSONObject("data")?.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val topResult = results.getJSONObject(0)
                        val downloadUrlArray = topResult.optJSONArray("downloadUrl")
                        if (downloadUrlArray != null && downloadUrlArray.length() > 0) {
                            val highestQuality = downloadUrlArray.getJSONObject(downloadUrlArray.length() - 1)
                            val streamUrl = highestQuality.optString("url")
                            if (streamUrl.isNotEmpty()) {
                                preloadCache[query] = streamUrl
                                Log.d("StreamResolver", "Successfully pre-resolved via ListenFree")
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            Log.d("StreamResolver", "Failed to pre-resolve stream for: $query")
        }
    }
}
