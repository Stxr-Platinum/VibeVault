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
    val artistTracks: StateFlow<List<Track>> = _artistName
        .flatMapLatest { name ->
            if (name.isBlank()) flowOf(emptyList())
            else musicRepository.getTracksByArtist(name)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadArtist(name: String) {
        _artistName.value = name
    }
}
