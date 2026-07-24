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
        private const val MAX_CACHE_SIZE = 3
        const val LYRICS_NOT_FOUND = ""
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFetches = mutableMapOf<String, Deferred<LyricsWithProvider>>()
    private val fetchesMutex = Mutex()

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val deferred = fetchesMutex.withLock {
            activeFetches.getOrPut(id) {
                helperScope.async {
                    val providers = LyricsProviderRegistry.getOrderedProviders()
                    for (provider in providers) {
                        if (provider.isEnabled(context)) {
                            try {
                                val result = provider.getLyrics(id, title, artist, duration, album)
                                result.onSuccess { lyrics ->
                                    return@async LyricsWithProvider(lyrics, provider.name)
                                }.onFailure { e ->
                                    Log.w(TAG, "Provider ${provider.name} failed", e)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Provider ${provider.name} threw exception", e)
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

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = LyricsProviderRegistry.getOrderedProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider ${provider.name} threw exception in getAllLyrics", e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
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
