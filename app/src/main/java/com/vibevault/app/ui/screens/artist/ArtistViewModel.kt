package com.vibevault.app.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _artistName = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSongs: StateFlow<List<Track>> = _artistName
        .flatMapLatest { name ->
            if (name.isBlank()) flowOf(emptyList())
            else kotlinx.coroutines.flow.flow {
                emit(musicRepository.getArtistTopSongs(name).getOrDefault(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestAlbums: StateFlow<List<com.vibevault.app.domain.model.Album>> = _artistName
        .flatMapLatest { name ->
            if (name.isBlank()) flowOf(emptyList())
            else kotlinx.coroutines.flow.flow {
                emit(musicRepository.getArtistLatestAlbums(name).getOrDefault(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadArtist(name: String) {
        _artistName.value = name
    }
}
