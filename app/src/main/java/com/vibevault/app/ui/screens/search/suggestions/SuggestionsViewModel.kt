package com.vibevault.app.ui.screens.search.suggestions

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.ui.screens.search.SearchCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val TAG = "SuggestionsViewModel"
    private var currentLoadedRegion: String? = null

    private val _suggestionTracks = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionTracks: StateFlow<List<SuggestionTrack>?> = _suggestionTracks.asStateFlow()

    private val _suggestionArtists = MutableStateFlow<List<SuggestionArtist>?>(null)
    val suggestionArtists: StateFlow<List<SuggestionArtist>?> = _suggestionArtists.asStateFlow()

    private val _suggestionAlbums = MutableStateFlow<List<SuggestionAlbum>?>(null)
    val suggestionAlbums: StateFlow<List<SuggestionAlbum>?> = _suggestionAlbums.asStateFlow()

    private val _suggestionVideos = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionVideos: StateFlow<List<SuggestionTrack>?> = _suggestionVideos.asStateFlow()

    private val _youtubeNewReleases = MutableStateFlow<List<AlbumItem>?>(null)
    val youtubeNewReleases: StateFlow<List<AlbumItem>?> = _youtubeNewReleases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading.asStateFlow()

    val jumpBackInTracks: StateFlow<List<Track>> = musicRepository.getRecentlyPlayed(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load cache initially so there's zero-delay display while fresh fetch begins
        SearchCache.getCachedSuggestions(context)?.let { cached ->
            if (cached.tracks.isNotEmpty()) {
                _suggestionTracks.value = cached.tracks
                _suggestionArtists.value = cached.artists.ifEmpty { AppleMusicScraper.getTrendingArtists(cached.tracks) }
            }
            if (cached.albums.isNotEmpty()) {
                _suggestionAlbums.value = cached.albums
            }
            if (cached.videos.isNotEmpty()) {
                _suggestionVideos.value = cached.videos
            }
            if (cached.newReleases.isNotEmpty()) {
                _youtubeNewReleases.value = cached.newReleases.map { it.toAlbumItem() }
            }
            if (cached.region.isNotBlank()) {
                currentLoadedRegion = cached.region
            }
        }
        // Immediately trigger refresh logic upon opening: clears cache and fetches fresh data
        clearAndRefresh(force = true)
    }

    /**
     * Called whenever the search screen is opened: clears cache and fetches new data.
     */
    fun onScreenOpened() {
        clearAndRefresh(force = true, isManual = false)
    }

    fun clearAndRefresh(countryCode: String = "system", force: Boolean = true, isManual: Boolean = false) {
        SearchCache.clearSuggestionsCache(context)
        refresh(countryCode = countryCode, force = force, isManual = isManual)
    }

    fun refresh(countryCode: String = "system", force: Boolean = false, isManual: Boolean = false) {
        val resolvedCode = if (countryCode == "system") {
            java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
        } else {
            countryCode.lowercase()
        }

        if (!force && currentLoadedRegion == resolvedCode && _suggestionTracks.value != null) return
        if (!_isLoading.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            _isManualLoading.value = isManual

            if (force) {
                _suggestionTracks.value = null
                _suggestionArtists.value = null
                _suggestionAlbums.value = null
                _suggestionVideos.value = null
                _youtubeNewReleases.value = null
            }

            try {
                // STEP 1: Fetch suggestions FIRST and display immediately
                try {
                    coroutineScope {
                        val habitDeferred = async { runCatching { fetchHabitBasedSimilarSongs() }.getOrDefault(emptyList()) }
                        val chartDeferred = async { runCatching { AppleMusicScraper.fetchTopSongs(resolvedCode) }.getOrDefault(emptyList()) }

                        val habitTracks = habitDeferred.await()
                        val chartTracks = chartDeferred.await()

                        val mergedTracks = mutableListOf<SuggestionTrack>()
                        val seenKeys = mutableSetOf<String>()

                        // 1. Prioritize habit-based similar songs first
                        for (t in habitTracks) {
                            val key = "${t.title.lowercase().trim()}_${t.artist.lowercase().trim()}"
                            if (seenKeys.add(key)) {
                                mergedTracks.add(t)
                            }
                        }

                        // 2. Interleave and append trending chart songs
                        for (t in chartTracks) {
                            val key = "${t.title.lowercase().trim()}_${t.artist.lowercase().trim()}"
                            if (seenKeys.add(key)) {
                                mergedTracks.add(t)
                            }
                        }

                        val unifiedSuggestions = mergedTracks.mapIndexed { idx, track ->
                            track.copy(rank = idx + 1)
                        }

                        if (unifiedSuggestions.isNotEmpty()) {
                            _suggestionTracks.value = unifiedSuggestions
                            val artists = AppleMusicScraper.getTrendingArtists(unifiedSuggestions)
                            _suggestionArtists.value = artists
                            SearchCache.saveSuggestions(context, region = resolvedCode, tracks = unifiedSuggestions, artists = artists)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch unified suggestions", e)
                }

                // STEP 2: THEN fetch everything else in search concurrently
                coroutineScope {
                    launch {
                        try {
                            val albums = AppleMusicScraper.fetchTopAlbums(resolvedCode)
                            if (albums.isNotEmpty()) {
                                _suggestionAlbums.value = albums
                                SearchCache.saveSuggestions(context, region = resolvedCode, albums = albums)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch albums", e)
                        }
                    }

                    launch {
                        try {
                            var videos = AppleMusicScraper.fetchTopVideos(resolvedCode)
                            if (videos.isEmpty()) {
                                // Secondary fallback: YouTube Music Videos so videos are NEVER empty
                                val ytVideos = YouTube.search("trending music videos", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                                val songItems = ytVideos?.items?.filterIsInstance<SongItem>().orEmpty()
                                if (songItems.isNotEmpty()) {
                                    videos = songItems.take(20).mapIndexed { idx, s ->
                                        SuggestionTrack(
                                            rank = idx + 1,
                                            title = s.title,
                                            artist = s.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                                            thumbnailUrl = s.thumbnail.replace(Regex("(\\d+)x(\\d+)"), "1920x1080"),
                                            appleMusicUrl = "https://www.youtube.com/watch?v=${s.id}"
                                        )
                                    }
                                }
                            }
                            if (videos.isNotEmpty()) {
                                _suggestionVideos.value = videos
                                SearchCache.saveSuggestions(context, region = resolvedCode, videos = videos)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch videos", e)
                        }
                    }

                    launch {
                        try {
                            YouTube.newReleaseAlbums().onSuccess { albums ->
                                // Dynamically prioritize by user's listening history artists
                                val recentTracks = musicRepository.getRecentlyPlayed(50).firstOrNull().orEmpty()
                                val listenedArtists = recentTracks.map { it.artist.lowercase().trim() }
                                    .filter { it.isNotBlank() && it != "unknown" }
                                    .toSet()

                                val sortedAlbums = albums.sortedByDescending { album ->
                                    val albumArtist = album.artists?.firstOrNull()?.name?.lowercase()?.trim() ?: ""
                                    if (listenedArtists.any { it.contains(albumArtist) || albumArtist.contains(it) }) 1 else 0
                                }
                                _youtubeNewReleases.value = sortedAlbums
                                SearchCache.saveSuggestions(context, region = resolvedCode, newReleases = sortedAlbums)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch YouTube new releases", e)
                        }
                    }
                }

                currentLoadedRegion = resolvedCode
            } catch (e: Exception) {
                Log.e(TAG, "Error loading suggestions", e)
                currentLoadedRegion = null
            } finally {
                _isLoading.value = false
                _isManualLoading.value = false
            }
        }
    }

    private suspend fun fetchHabitBasedSimilarSongs(): List<SuggestionTrack> {
        val recentTracks = runCatching { musicRepository.getRecentlyPlayed(20).firstOrNull() }.getOrNull().orEmpty()
        if (recentTracks.isEmpty()) return emptyList()

        val recentIds = recentTracks.map { it.id }.toSet()
        val candidateTracks = mutableListOf<Track>()

        // 1. Take up to 3 distinct seed tracks from recent listening for YouTube related recommendations
        val seedTracks = recentTracks.distinctBy { it.artist.lowercase().trim() }.take(3)
        for (seed in seedTracks) {
            val similar = runCatching { musicRepository.getSimilarTracks(seed).getOrNull() }.getOrNull().orEmpty()
            for (track in similar) {
                if (!recentIds.contains(track.id) && candidateTracks.none { it.id == track.id }) {
                    candidateTracks.add(track)
                }
            }
        }

        // 2. Identify top favorite artists from user's listening habits
        val topArtists = recentTracks
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Artist", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(2)

        for (artist in topArtists) {
            if (candidateTracks.size >= 16) break
            val artistSongs = runCatching {
                YouTube.search("$artist songs", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items?.filterIsInstance<SongItem>().orEmpty()
            }.getOrDefault(emptyList())

            for (song in artistSongs) {
                if (!recentIds.contains(song.id) && candidateTracks.none { it.id == song.id }) {
                    candidateTracks.add(
                        Track(
                            id = song.id,
                            title = song.title,
                            artist = song.artists.joinToString(", ") { it.name }.ifBlank { artist },
                            album = song.album?.name ?: "",
                            albumImageUrl = song.thumbnail,
                            durationMs = (song.duration ?: 0) * 1000L,
                            source = "youtube"
                        )
                    )
                }
            }
        }

        return candidateTracks.distinctBy { it.id }.take(16).mapIndexed { idx, track ->
            SuggestionTrack(
                rank = idx + 1,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.albumImageUrl,
                appleMusicUrl = "https://www.youtube.com/watch?v=${track.id}"
            )
        }
    }

    private fun artistMatches(ytArtistName: String, appleMusicArtist: String): Boolean {
        val ytNorm = ytArtistName.trim().lowercase()
        val apNorm = appleMusicArtist.trim().lowercase()
        return apNorm.contains(ytNorm) || ytNorm.contains(apNorm)
    }

    fun playTrack(track: SuggestionTrack, onPlay: (Track) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${track.title} ${track.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>()
                val bestMatch = songs.firstOrNull { s ->
                    s.title.equals(track.title, ignoreCase = true) &&
                            s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?: songs.firstOrNull { s ->
                    s.title.contains(track.title, ignoreCase = true) &&
                            s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?: songs.firstOrNull { s ->
                    s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?: songs.firstOrNull()

                if (bestMatch != null) {
                    val resolvedTrack = Track(
                        id = bestMatch.id,
                        title = bestMatch.title,
                        artist = bestMatch.artists.joinToString(", ") { it.name }.ifBlank { track.artist },
                        album = bestMatch.album?.name ?: "",
                        albumImageUrl = bestMatch.thumbnail.ifBlank { track.thumbnailUrl ?: "" },
                        durationMs = (bestMatch.duration ?: 0) * 1000L,
                        isVideo = bestMatch.isVideoSong
                    )
                    withContext(Dispatchers.Main) {
                        onPlay(resolvedTrack)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "\"${track.title}\" not found on YouTube Music", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search failed for \"${track.title}\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun playVideo(video: SuggestionTrack, onPlay: (Track) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${video.title} ${video.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).onSuccess { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>()
                val bestMatch = songs.firstOrNull { s ->
                    s.title.equals(video.title, ignoreCase = true) &&
                            s.artists.any { a -> artistMatches(a.name, video.artist) }
                } ?: songs.firstOrNull { s ->
                    s.title.contains(video.title, ignoreCase = true) &&
                            s.artists.any { a -> artistMatches(a.name, video.artist) }
                } ?: songs.firstOrNull { s ->
                    s.artists.any { a -> artistMatches(a.name, video.artist) }
                } ?: songs.firstOrNull()

                if (bestMatch != null) {
                    val resolvedTrack = Track(
                        id = bestMatch.id,
                        title = bestMatch.title,
                        artist = bestMatch.artists.joinToString(", ") { it.name }.ifBlank { video.artist },
                        album = bestMatch.album?.name ?: "",
                        albumImageUrl = bestMatch.thumbnail.ifBlank { video.thumbnailUrl ?: "" },
                        durationMs = (bestMatch.duration ?: 0) * 1000L,
                        isVideo = true
                    )
                    withContext(Dispatchers.Main) {
                        onPlay(resolvedTrack)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Video \"${video.title}\" not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search failed for video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun navigateToArtist(artist: SuggestionArtist, onNavigate: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { searchResult ->
                val firstArtist = searchResult.items.filterIsInstance<ArtistItem>().firstOrNull()
                withContext(Dispatchers.Main) {
                    onNavigate(firstArtist?.id ?: artist.name)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onNavigate(artist.name)
                }
            }
        }
    }

    fun navigateToAlbum(album: SuggestionAlbum, onNavigate: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${album.title} ${album.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).onSuccess { searchResult ->
                val firstAlbum = searchResult.items.filterIsInstance<AlbumItem>().firstOrNull()
                withContext(Dispatchers.Main) {
                    if (firstAlbum != null) {
                        onNavigate(firstAlbum.id)
                    } else {
                        Toast.makeText(context, "Album \"${album.title}\" not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search failed for album", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
