package com.vibevault.app.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.pages.ArtistPage
import com.vibevault.app.domain.model.Album
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _artistName = MutableStateFlow("")
    val artistName: StateFlow<String> = _artistName.asStateFlow()

    private val _artistPage = MutableStateFlow<ArtistPage?>(null)
    val artistPage: StateFlow<ArtistPage?> = _artistPage.asStateFlow()

    private val _subscriberCountText = MutableStateFlow<String?>(null)
    val subscriberCountText: StateFlow<String?> = _subscriberCountText.asStateFlow()

    private val _monthlyListenerCount = MutableStateFlow<String?>(null)
    val monthlyListenerCount: StateFlow<String?> = _monthlyListenerCount.asStateFlow()

    private val _description = MutableStateFlow<String?>(null)
    val description: StateFlow<String?> = _description.asStateFlow()

    private val _topSongs = MutableStateFlow<List<Track>>(emptyList())
    val topSongs: StateFlow<List<Track>> = _topSongs.asStateFlow()

    private val _latestAlbums = MutableStateFlow<List<Album>>(emptyList())
    val latestAlbums: StateFlow<List<Album>> = _latestAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    fun loadArtist(nameOrId: String) {
        if (_artistName.value == nameOrId && _artistPage.value != null) return
        _artistName.value = nameOrId
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Fetch top songs & latest albums from musicRepository for immediate data
            val repoSongs = musicRepository.getArtistTopSongs(nameOrId).getOrDefault(emptyList())
            val repoAlbums = musicRepository.getArtistLatestAlbums(nameOrId).getOrDefault(emptyList())
            _topSongs.value = repoSongs
            _latestAlbums.value = repoAlbums

            // 2. Fetch full ArtistPage from YouTube Music / InnerTube
            val channelId = if (nameOrId.startsWith("UC") || nameOrId.startsWith("FEmusic_artist")) {
                nameOrId
            } else {
                YouTube.search(nameOrId, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    ?.items?.firstOrNull()?.id ?: nameOrId
            }

            YouTube.artist(channelId).onSuccess { page ->
                _artistPage.value = page
                _subscriberCountText.value = page.subscriberCountText
                _monthlyListenerCount.value = page.monthlyListenerCount
                _description.value = page.description
            }.onFailure {
                YouTube.searchSummary(nameOrId).onSuccess { summaryPage ->
                    summaryPage.summaries.firstOrNull { it.title.contains("Artist", ignoreCase = true) }?.items?.firstOrNull()?.id?.let { browseId ->
                        YouTube.artist(browseId).onSuccess { page ->
                            _artistPage.value = page
                            _subscriberCountText.value = page.subscriberCountText
                            _monthlyListenerCount.value = page.monthlyListenerCount
                            _description.value = page.description
                        }
                    }
                }
            }

            _isLoading.value = false
        }
    }

    fun toggleSubscribe() {
        _isSubscribed.value = !_isSubscribed.value
    }
}
