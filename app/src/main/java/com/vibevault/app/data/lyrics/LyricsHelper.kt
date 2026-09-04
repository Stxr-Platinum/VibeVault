package com.vibevault.app.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LyricsHelper"
        private const val MAX_CACHE_SIZE = 50
        const val LYRICS_NOT_FOUND = ""
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFetches = mutableMapOf<String, Deferred<LyricsWithProvider>>()
    private val fetchesMutex = Mutex()

    private fun getMetadataCacheKey(title: String, artist: String): String =
        "$artist-$title".replace(" ", "").lowercase()

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): LyricsWithProvider {
        currentLyricsJob?.cancel()

        // 1. Check in-memory LRU cache by ID or artist-title key
        val metaKey = getMetadataCacheKey(title, artist)
        val cachedById = cache.get(id)?.firstOrNull()
        if (cachedById != null && cachedById.lyrics.isNotBlank() && cachedById.lyrics != LYRICS_NOT_FOUND) {
            return LyricsWithProvider(cachedById.lyrics, cachedById.providerName)
        }

        val cachedByMeta = cache.get(metaKey)?.firstOrNull()
        if (cachedByMeta != null && cachedByMeta.lyrics.isNotBlank() && cachedByMeta.lyrics != LYRICS_NOT_FOUND) {
            return LyricsWithProvider(cachedByMeta.lyrics, cachedByMeta.providerName)
        }

        val deferred = fetchesMutex.withLock {
            activeFetches.getOrPut(id) {
                helperScope.async {
                    val providers = LyricsProviderRegistry.getOrderedProviders()
                    for (provider in providers) {
                        if (provider.isEnabled(context)) {
                            try {
                                val result = provider.getLyrics(id, title, artist, duration, album)
                                val lyrics = result.getOrNull()
                                if (!lyrics.isNullOrBlank() && lyrics != LYRICS_NOT_FOUND) {
                                    val resultObj = LyricsResult(provider.name, lyrics)
                                    cache.put(id, listOf(resultObj))
                                    cache.put(metaKey, listOf(resultObj))
                                    return@async LyricsWithProvider(lyrics, provider.name)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Provider ${provider.name} threw exception for '$title'", e)
                            }
                        }
                    }
                    LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
                }
            }
        }

        return try {
            deferred.await()
        } finally {
            fetchesMutex.withLock {
                activeFetches.remove(id)
            }
        }
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = getMetadataCacheKey(songTitle, songArtists)
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = LyricsProviderRegistry.getOrderedProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            if (lyrics.isNotBlank() && lyrics != LYRICS_NOT_FOUND) {
                                val result = LyricsResult(provider.name, lyrics)
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider ${provider.name} threw exception in getAllLyrics", e)
                    }
                }
            }
            if (allResult.isNotEmpty()) {
                cache.put(cacheKey, allResult)
                if (mediaId.isNotBlank()) {
                    cache.put(mediaId, allResult)
                }
            }
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
