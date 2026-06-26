package com.vibevault.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.SpotifySearchResult
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResult: StateFlow<SpotifySearchResult?> = _query
        .debounce(500)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                _isSearching.value = false
                flowOf<SpotifySearchResult?>(null)
            } else {
                _isSearching.value = true
                flow {
                    val result = musicRepository.searchSpotify(q)
                    val searchResult = result.getOrNull()?.let { tracks ->
                        SpotifySearchResult(
                            tracks = tracks,
                            albums = emptyList(),
                            artists = emptyList(),
                            playlists = emptyList()
                        )
                    }
                    _isSearching.value = false
                    emit(searchResult)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
