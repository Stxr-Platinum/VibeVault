package com.vibevault.app.ui.screens.search.suggestions

import android.content.Context
import android.util.Log
import com.vibevault.app.ui.screens.search.SearchCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * SuggestionsPreloader — Warms cache from disk and fetches fresh recommendations/charts
 * asynchronously on app open so the search suggestions screen displays instantaneously
 * with zero lag or skeleton waiting when navigated to.
 */
object SuggestionsPreloader {
    private const val TAG = "SuggestionsPreloader"
    @Volatile private var isPreloading = false

    suspend fun preload(context: Context) = withContext(Dispatchers.IO) {
        if (isPreloading) return@withContext
        isPreloading = true
        try {
            // 1. Immediately warm up memory cache from disk
            val cached = SearchCache.getCachedSuggestions(context)
            if (cached != null) {
                Log.d(TAG, "Warmed ${cached.tracks.size} tracks from disk cache")
            }

            // 2. Resolve country region code
            val countryCode = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }

            // 3. Pre-fetch fresh suggestions concurrently in background
            coroutineScope {
                val songsDeferred = async { runCatching { AppleMusicScraper.fetchTopSongs(countryCode) }.getOrDefault(emptyList()) }
                val albumsDeferred = async { runCatching { AppleMusicScraper.fetchTopAlbums(countryCode) }.getOrDefault(emptyList()) }
                val videosDeferred = async { runCatching { AppleMusicScraper.fetchTopVideos(countryCode) }.getOrDefault(emptyList()) }

                val songs = songsDeferred.await()
                val albums = albumsDeferred.await()
                val videos = videosDeferred.await()

                if (songs.isNotEmpty()) {
                    val artists = AppleMusicScraper.getTrendingArtists(songs)
                    SearchCache.saveSuggestions(
                        context = context,
                        region = countryCode,
                        tracks = songs,
                        artists = artists,
                        albums = albums.ifEmpty { null },
                        videos = videos.ifEmpty { null }
                    )
                    Log.d(TAG, "Preloaded and cached ${songs.size} fresh tracks for region '$countryCode'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during suggestions preloading", e)
        } finally {
            isPreloading = false
        }
    }
}
