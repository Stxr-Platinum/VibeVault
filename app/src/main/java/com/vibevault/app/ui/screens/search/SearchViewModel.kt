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
                    val result = musicRepository.searchOnline(q)
                    val searchResult = result.getOrNull()?.let { tracks ->
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
                    emit(searchResult)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
