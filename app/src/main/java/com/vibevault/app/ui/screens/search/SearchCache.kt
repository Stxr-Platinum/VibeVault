package com.vibevault.app.ui.screens.search

import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.pages.SearchSummaryPage
import com.vibevault.app.domain.model.SpotifySearchResult
import com.vibevault.app.ui.screens.search.suggestions.SearchSuggestionViewState
import com.vibevault.app.ui.screens.search.suggestions.SuggestionAlbum
import com.vibevault.app.ui.screens.search.suggestions.SuggestionArtist
import com.vibevault.app.ui.screens.search.suggestions.SuggestionTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CachedAlbumItem(
    val id: String = "",
    val title: String = "",
    val artistName: String? = null,
    val artistId: String? = null,
    val thumbnail: String = "",
    val year: Int? = null,
    val explicit: Boolean = false
) {
    fun toAlbumItem(): AlbumItem {
        return AlbumItem(
            browseId = id,
            playlistId = id,
            id = id,
            title = title,
            artists = if (!artistName.isNullOrBlank()) listOf(Artist(name = artistName, id = artistId)) else emptyList(),
            year = year,
            thumbnail = thumbnail,
            explicit = explicit
        )
    }

    companion object {
        fun fromAlbumItem(item: AlbumItem): CachedAlbumItem {
            return CachedAlbumItem(
                id = item.id,
                title = item.title,
                artistName = item.artists?.firstOrNull()?.name,
                artistId = item.artists?.firstOrNull()?.id,
                thumbnail = item.thumbnail,
                year = item.year,
                explicit = item.explicit
            )
        }
    }
}

data class CachedSuggestionsPayload(
    val region: String = "",
    val tracks: List<SuggestionTrack> = emptyList(),
    val artists: List<SuggestionArtist> = emptyList(),
    val albums: List<SuggestionAlbum> = emptyList(),
    val videos: List<SuggestionTrack> = emptyList(),
    val newReleases: List<CachedAlbumItem> = emptyList(),
    val personalizedTracks: List<SuggestionTrack> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

object SearchCache {
    private const val TAG = "SearchCache"
    private const val CACHE_DIR_NAME = "search_cache"
    private const val SUGGESTIONS_FILE = "cached_suggestions.json"
    private const val TTL_SUGGESTIONS_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val gson by lazy { Gson() }

    // Reactive StateFlow for instant UI observation
    private val _cachedSuggestionsFlow = MutableStateFlow<CachedSuggestionsPayload?>(null)
    val cachedSuggestionsFlow: StateFlow<CachedSuggestionsPayload?> = _cachedSuggestionsFlow.asStateFlow()

    // Memory caches for immediate zero-latency UI display
    private var memorySuggestions: CachedSuggestionsPayload? = null
    private val memoryQueryResults = ConcurrentHashMap<String, SpotifySearchResult>()
    private val memoryOnlineSummaries = ConcurrentHashMap<String, SearchSummaryPage>()
    private val memorySuggestionStates = ConcurrentHashMap<String, SearchSuggestionViewState>()
    private val memoryQuerySuggestions = ConcurrentHashMap<String, List<String>>()

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── Suggestions Cache ─────────────────────────────────────────────

    fun clearSuggestionsCache(context: Context) {
        memorySuggestions = null
        _cachedSuggestionsFlow.value = null
        try {
            val file = File(getCacheDir(context), SUGGESTIONS_FILE)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting suggestions cache file", e)
        }
    }

    fun getCachedSuggestions(context: Context): CachedSuggestionsPayload? {
        memorySuggestions?.let { 
            _cachedSuggestionsFlow.value = it
            return it 
        }
        return try {
            val file = File(getCacheDir(context), SUGGESTIONS_FILE)
            if (!file.exists()) return null
            val json = file.readText()
            val type = object : TypeToken<CachedSuggestionsPayload>() {}.type
            val payload: CachedSuggestionsPayload? = gson.fromJson(json, type)
            if (payload != null) {
                // Safely filter any item that failed clean deserialization
                val safeReleases = payload.newReleases.mapNotNull { item ->
                    try {
                        if (item.id.isNotBlank()) item else null
                    } catch (_: Throwable) {
                        null
                    }
                }
                val safePayload = payload.copy(newReleases = safeReleases)
                memorySuggestions = safePayload
                _cachedSuggestionsFlow.value = safePayload
                safePayload
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "Error reading suggestions cache, purging file", t)
            try {
                val file = File(getCacheDir(context), SUGGESTIONS_FILE)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            null
        }
    }

    @Synchronized
    fun saveSuggestions(
        context: Context,
        region: String = "",
        tracks: List<SuggestionTrack>? = null,
        artists: List<SuggestionArtist>? = null,
        albums: List<SuggestionAlbum>? = null,
        videos: List<SuggestionTrack>? = null,
        newReleases: List<AlbumItem>? = null,
        personalizedTracks: List<SuggestionTrack>? = null
    ) {
        try {
            val current = getCachedSuggestions(context) ?: CachedSuggestionsPayload()
            val cachedNewReleases = newReleases?.map { CachedAlbumItem.fromAlbumItem(it) } ?: current.newReleases
            val updated = CachedSuggestionsPayload(
                region = region.ifBlank { current.region },
                tracks = tracks ?: current.tracks,
                artists = artists ?: current.artists,
                albums = albums ?: current.albums,
                videos = videos ?: current.videos,
                newReleases = cachedNewReleases,
                personalizedTracks = personalizedTracks ?: current.personalizedTracks,
                timestamp = System.currentTimeMillis()
            )
            memorySuggestions = updated
            _cachedSuggestionsFlow.value = updated

            val file = File(getCacheDir(context), SUGGESTIONS_FILE)
            file.writeText(gson.toJson(updated))

            // Preload video thumbnails into Coil cache asynchronously
            val imageLoader = context.imageLoader
            updated.videos.forEach { video ->
                video.thumbnailUrl?.let { url ->
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(req)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving suggestions cache", e)
        }
    }

    // ── Autocomplete Suggestions Cache ───────────────────────────────

    fun getCachedAutocompleteSuggestions(context: Context, query: String): List<String>? {
        val key = query.trim().lowercase()
        if (key.isBlank()) return null
        memoryQuerySuggestions[key]?.let { return it }

        return try {
            val file = File(getCacheDir(context), "sug_${key.hashCode()}.json")
            if (!file.exists()) return null
            val json = file.readText()
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String>? = gson.fromJson(json, type)
            if (list != null) {
                memoryQuerySuggestions[key] = list
                list
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun saveAutocompleteSuggestions(context: Context, query: String, suggestions: List<String>) {
        val key = query.trim().lowercase()
        if (key.isBlank() || suggestions.isEmpty()) return
        memoryQuerySuggestions[key] = suggestions
        try {
            val file = File(getCacheDir(context), "sug_${key.hashCode()}.json")
            file.writeText(gson.toJson(suggestions))
        } catch (_: Throwable) {}
    }

    // ── Query Results Cache ──────────────────────────────────────────

    fun getCachedSearchResult(context: Context, query: String): SpotifySearchResult? {
        val key = query.trim().lowercase()
        if (key.isBlank()) return null
        memoryQueryResults[key]?.let { return it }

        return try {
            val file = File(getCacheDir(context), "query_${key.hashCode()}.json")
            if (!file.exists()) return null
            val json = file.readText()
            val type = object : TypeToken<SpotifySearchResult>() {}.type
            val result: SpotifySearchResult? = gson.fromJson(json, type)
            if (result != null) {
                memoryQueryResults[key] = result
                result
            } else null
        } catch (t: Throwable) {
            try {
                val file = File(getCacheDir(context), "query_${key.hashCode()}.json")
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            null
        }
    }

    fun saveSearchResult(context: Context, query: String, result: SpotifySearchResult) {
        val key = query.trim().lowercase()
        if (key.isBlank()) return
        memoryQueryResults[key] = result
        try {
            val file = File(getCacheDir(context), "query_${key.hashCode()}.json")
            file.writeText(gson.toJson(result))
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving query result cache", e)
        }
    }

    // ── Online Search Summary Cache ──────────────────────────────────

    fun getOnlineSummary(query: String): SearchSummaryPage? {
        return memoryOnlineSummaries[query.trim().lowercase()]
    }

    fun saveOnlineSummary(query: String, page: SearchSummaryPage) {
        memoryOnlineSummaries[query.trim().lowercase()] = page
    }

    // ── Suggestion ViewState Cache ───────────────────────────────────

    fun getSuggestionState(query: String): SearchSuggestionViewState? {
        return memorySuggestionStates[query.trim().lowercase()]
    }

    fun saveSuggestionState(query: String, state: SearchSuggestionViewState) {
        if (state.suggestions.isNotEmpty() || state.items.isNotEmpty()) {
            memorySuggestionStates[query.trim().lowercase()] = state
        }
    }
}
