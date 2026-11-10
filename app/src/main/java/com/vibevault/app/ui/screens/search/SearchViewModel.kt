package com.vibevault.app.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vibevault.app.data.local.dao.SearchCacheDao
import com.vibevault.app.data.local.entity.SearchCacheEntity
import com.vibevault.app.domain.model.SpotifySearchResult
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val searchCacheDao: SearchCacheDao
) : ViewModel() {

    private val gson = Gson()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResult: StateFlow<SpotifySearchResult?> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                _isSearching.value = false
                flowOf<SpotifySearchResult?>(null)
            } else {
                flow {
                    val cacheKey = "query:${q.trim().lowercase()}"
                    // 1. Check L1/file cache, then Room cache for instant zero-latency display
                    var cached = SearchCache.getCachedSearchResult(context, q)
                    if (cached == null) {
                        cached = withContext(Dispatchers.IO) {
                            searchCacheDao.getEntry(cacheKey)?.let { entity ->
                                runCatching { gson.fromJson(entity.payloadJson, SpotifySearchResult::class.java) }.getOrNull()
                            }
                        }
                        if (cached != null) {
                            SearchCache.saveSearchResult(context, q, cached)
                        }
                    }

                    if (cached != null) {
                        emit(cached)
                        _isSearching.value = false
                    } else {
                        _isSearching.value = true
                    }

                    // 2. Background fetch & diff-update without flickering
                    val result = musicRepository.searchOnline(q)
                    val freshResult = result.getOrNull()?.let { tracks ->
                        val artists = tracks.filter { it.artist.isNotBlank() }
                            .distinctBy { it.artist }
                            .map {
                                com.vibevault.app.domain.model.Artist(
                                    id = it.artist,
                                    name = it.artist,
                                    imageUrl = it.albumImageUrl
                                )
                            }

                        SpotifySearchResult(
                            tracks = tracks,
                            albums = tracks.filter { it.album.isNotBlank() }.distinctBy { it.album },
                            artists = artists,
                            playlists = emptyList()
                        )
                    }
                    _isSearching.value = false
                    if (freshResult != null) {
                        if (freshResult != cached) {
                            SearchCache.saveSearchResult(context, q, freshResult)
                            withContext(Dispatchers.IO) {
                                searchCacheDao.upsertEntry(
                                    SearchCacheEntity(cacheKey, gson.toJson(freshResult))
                                )
                            }
                            emit(freshResult)
                        }
                    } else if (cached == null) {
                        emit(null)
                    }
                }.distinctUntilChanged()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
