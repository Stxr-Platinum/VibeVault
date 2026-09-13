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

                val topSongsSection = page.sections.find {
                    it.title.equals("Top songs", ignoreCase = true) ||
                    it.title.equals("Songs", ignoreCase = true) ||
                    it.items.firstOrNull() is com.music.innertube.models.SongItem
                }
                val initialMapped = topSongsSection?.items?.filterIsInstance<com.music.innertube.models.SongItem>()?.map { song ->
                    Track(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name }.ifEmpty { page.artist.title },
                        album = song.album?.name ?: "",
                        albumImageUrl = song.thumbnail,
                        durationMs = (song.duration ?: 0) * 1000L,
                        isExplicit = song.explicit
                    )
                } ?: emptyList()

                if (initialMapped.isNotEmpty()) {
                    _topSongs.value = initialMapped
                }

                loadFullTopSongs(page, topSongsSection, initialMapped)
            }.onFailure {
                YouTube.searchSummary(nameOrId).onSuccess { summaryPage ->
                    summaryPage.summaries.firstOrNull { it.title.contains("Artist", ignoreCase = true) }?.items?.firstOrNull()?.id?.let { browseId ->
                        YouTube.artist(browseId).onSuccess { page ->
                            _artistPage.value = page
                            _subscriberCountText.value = page.subscriberCountText
                            _monthlyListenerCount.value = page.monthlyListenerCount
                            _description.value = page.description

                            val topSongsSection = page.sections.find {
                                it.title.equals("Top songs", ignoreCase = true) ||
                                it.title.equals("Songs", ignoreCase = true) ||
                                it.items.firstOrNull() is com.music.innertube.models.SongItem
                            }
                            val initialMapped = topSongsSection?.items?.filterIsInstance<com.music.innertube.models.SongItem>()?.map { song ->
                                Track(
                                    id = song.id,
                                    title = song.title,
                                    artist = song.artists.joinToString(", ") { it.name }.ifEmpty { page.artist.title },
                                    album = song.album?.name ?: "",
                                    albumImageUrl = song.thumbnail,
                                    durationMs = (song.duration ?: 0) * 1000L,
                                    isExplicit = song.explicit
                                )
                            } ?: emptyList()

                            if (initialMapped.isNotEmpty()) {
                                _topSongs.value = initialMapped
                            }

                            loadFullTopSongs(page, topSongsSection, initialMapped)
                        }
                    }
                }
            }

            _isLoading.value = false
        }
    }

    private suspend fun loadFullTopSongs(
        page: ArtistPage,
        topSongsSection: com.music.innertube.pages.ArtistSection?,
        initialSongs: List<Track>
    ) {
        val resultList = initialSongs.toMutableList()
        val seenIds = resultList.map { it.id }.toMutableSet()

        val moreEndpoint = topSongsSection?.moreEndpoint
        var fetchedTracks: List<Track> = emptyList()

        if (moreEndpoint != null) {
            val browseId = moreEndpoint.browseId
            if (browseId.startsWith("VL") || browseId.startsWith("PL")) {
                val playlistId = browseId.removePrefix("VL")
                val songs = YouTube.playlist(playlistId).getOrNull()?.songs
                    ?: YouTube.artistItems(moreEndpoint).getOrNull()?.items?.filterIsInstance<com.music.innertube.models.SongItem>()
                if (!songs.isNullOrEmpty()) {
                    fetchedTracks = songs.map { s ->
                        Track(
                            id = s.id,
                            title = s.title,
                            artist = s.artists.joinToString(", ") { it.name }.ifEmpty { page.artist.title },
                            album = s.album?.name ?: "",
                            albumImageUrl = s.thumbnail,
                            durationMs = (s.duration ?: 0) * 1000L,
                            isExplicit = s.explicit
                        )
                    }
                }
            } else {
                val songs = YouTube.artistItems(moreEndpoint).getOrNull()?.items?.filterIsInstance<com.music.innertube.models.SongItem>()
                    ?: YouTube.playlist(browseId).getOrNull()?.songs
                if (!songs.isNullOrEmpty()) {
                    fetchedTracks = songs.map { s ->
                        Track(
                            id = s.id,
                            title = s.title,
                            artist = s.artists.joinToString(", ") { it.name }.ifEmpty { page.artist.title },
                            album = s.album?.name ?: "",
                            albumImageUrl = s.thumbnail,
                            durationMs = (s.duration ?: 0) * 1000L,
                            isExplicit = s.explicit
                        )
                    }
                }
            }
        }

        if (fetchedTracks.isNotEmpty()) {
            val ordered = mutableListOf<Track>()
            val set = mutableSetOf<String>()
            for (track in fetchedTracks) {
                if (set.add(track.id)) {
                    ordered.add(track)
                }
            }
            for (track in initialSongs) {
                if (set.add(track.id)) {
                    ordered.add(track)
                }
            }
            resultList.clear()
            resultList.addAll(ordered)
            seenIds.clear()
            seenIds.addAll(set)
        }

        // If still fewer than 20 songs, supplement with artist song search (BitChord pattern)
        if (resultList.size < 20 && page.artist.title.isNotBlank()) {
            val searchResults = YouTube.search(page.artist.title, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            searchResults?.items?.filterIsInstance<com.music.innertube.models.SongItem>()?.forEach { s ->
                if (resultList.size < 20 && seenIds.add(s.id)) {
                    resultList.add(
                        Track(
                            id = s.id,
                            title = s.title,
                            artist = s.artists.joinToString(", ") { it.name }.ifEmpty { page.artist.title },
                            album = s.album?.name ?: "",
                            albumImageUrl = s.thumbnail,
                            durationMs = (s.duration ?: 0) * 1000L,
                            isExplicit = s.explicit
                        )
                    )
                }
            }
        }

        if (resultList.isNotEmpty()) {
            _topSongs.value = resultList.take(20)
        }
    }

    fun toggleSubscribe() {
        _isSubscribed.value = !_isSubscribed.value
    }
}
