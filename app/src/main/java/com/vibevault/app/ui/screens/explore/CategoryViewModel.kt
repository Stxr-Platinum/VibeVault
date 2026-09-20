package com.vibevault.app.ui.screens.explore
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.BrowseResult
import com.vibevault.app.data.local.dao.HistoryDao
import com.vibevault.app.ui.screens.explore.GenreCategoryCache.toAlbumItem
import com.vibevault.app.ui.screens.explore.GenreCategoryCache.toPlaylistItem
import com.vibevault.app.ui.screens.explore.GenreCategoryCache.toSongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CategoryUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val songs: List<SongItem> = emptyList(),
    val featuredPlaylists: List<PlaylistItem> = emptyList(),
    val communityPlaylists: List<PlaylistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val shelves: List<BrowseResult.Item> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private var currentBrowseId: String? = null
    private var currentParams: String? = null
    private var currentTitle: String = ""

    fun loadCategory(title: String, browseId: String, params: String?) {
        currentTitle = title
        currentBrowseId = browseId
        currentParams = params?.takeIf { it.isNotBlank() }

        // 1. Instantaneous render from local disk cache if available
        val cached = GenreCategoryCache.getCachedCategory(context, title)
        if (cached != null && cached.songs.isNotEmpty()) {
            _uiState.value = CategoryUiState(
                title = title,
                isLoading = false,
                isRefreshing = false,
                songs = cached.songs.map { it.toSongItem() },
                featuredPlaylists = cached.featuredPlaylists.map { it.toPlaylistItem() },
                communityPlaylists = cached.communityPlaylists.map { it.toPlaylistItem() },
                albums = cached.albums.map { it.toAlbumItem() },
                errorMessage = null
            )

            // Preload thumbnails into Coil memory and disk cache
            val thumbs = cached.songs.map { it.thumbnail } +
                    cached.featuredPlaylists.mapNotNull { it.thumbnail } +
                    cached.albums.map { it.thumbnail }
            GenreCategoryCache.preloadThumbnails(context, thumbs)
        } else {
            _uiState.value = CategoryUiState(
                title = title,
                isLoading = true,
                errorMessage = null
            )
        }

        // Dynamically fetch a fresh, varied set of songs every time the genre is opened
        viewModelScope.launch {
            fetchCategoryData(isRefresh = false)
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        viewModelScope.launch {
            fetchCategoryData(isRefresh = true)
        }
    }

    private suspend fun fetchCategoryData(isRefresh: Boolean) {
        withContext(Dispatchers.IO) {
            val browseId = currentBrowseId.orEmpty()
            val params = currentParams
            val title = currentTitle

            try {
                // 1. Inspect recent listening history to extract user's preferred artists
                val recentHistory = runCatching { historyDao.getRecentHistory().firstOrNull() }.getOrNull().orEmpty()
                val favoriteArtists = recentHistory
                    .map { it.artist.trim() }
                    .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Artist", ignoreCase = true) }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }

                // 2. Fetch all 4 sections concurrently + browse endpoint if present
                val songsDeferred = async {
                    fetchPersonalizedSongs(title, favoriteArtists)
                }
                val featuredPlaylistsDeferred = async {
                    fetchFeaturedPlaylists(title)
                }
                val communityPlaylistsDeferred = async {
                    fetchCommunityPlaylists(title)
                }
                val albumsDeferred = async {
                    fetchAlbums(title)
                }
                val browseDeferred = async {
                    if (browseId.isNotBlank()) {
                        runCatching { YouTube.browse(browseId, params).getOrNull() }.getOrNull()
                    } else null
                }

                val songs = songsDeferred.await()
                var featuredPlaylists = featuredPlaylistsDeferred.await()
                var communityPlaylists = communityPlaylistsDeferred.await()
                var albums = albumsDeferred.await()
                val browseResult = browseDeferred.await()

                val extraShelves = mutableListOf<BrowseResult.Item>()

                // If browse returned items, merge them
                browseResult?.items?.forEach { shelf ->
                    val playlistItems = shelf.items.filterIsInstance<PlaylistItem>()
                    val albumItems = shelf.items.filterIsInstance<AlbumItem>()
                    val songItems = shelf.items.filterIsInstance<SongItem>()

                    if (featuredPlaylists.isEmpty() && playlistItems.isNotEmpty()) {
                        featuredPlaylists = playlistItems
                    }
                    if (albums.isEmpty() && albumItems.isNotEmpty()) {
                        albums = albumItems
                    }
                    if (playlistItems.isEmpty() && albumItems.isEmpty() && songItems.isEmpty() && shelf.items.isNotEmpty()) {
                        extraShelves.add(shelf)
                    }
                }

                val hasAnyContent = songs.isNotEmpty() ||
                        featuredPlaylists.isNotEmpty() ||
                        communityPlaylists.isNotEmpty() ||
                        albums.isNotEmpty() ||
                        extraShelves.isNotEmpty()

                if (hasAnyContent) {
                    GenreCategoryCache.saveCategory(
                        context = context,
                        title = title,
                        songs = songs,
                        featuredPlaylists = featuredPlaylists,
                        communityPlaylists = communityPlaylists,
                        albums = albums
                    )
                }

                _uiState.value = CategoryUiState(
                    title = title,
                    isLoading = false,
                    isRefreshing = false,
                    songs = songs,
                    featuredPlaylists = featuredPlaylists,
                    communityPlaylists = communityPlaylists,
                    albums = albums,
                    shelves = extraShelves,
                    errorMessage = if (!hasAnyContent) "No music found for $title" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.message ?: "Failed to load category"
                )
            }
        }
    }

    private suspend fun fetchPersonalizedSongs(title: String, favoriteArtists: List<String>): List<SongItem> {
        // Dynamically rotate multiple genre query variants emphasizing recent and trending music
        val queryVariants = GenreCategoryCache.getDynamicGenreQueries(title).shuffled()
        val primaryQuery = queryVariants.firstOrNull() ?: "$title latest trending songs"
        val secondaryQuery = queryVariants.getOrNull(1) ?: "$title new hits 2024 2025"

        val primarySongs = runCatching {
            YouTube.search(primaryQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>().orEmpty()
        }.getOrDefault(emptyList())

        val secondarySongs = runCatching {
            YouTube.search(secondaryQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>().orEmpty()
        }.getOrDefault(emptyList())

        // Discover songs for user's favorite artists ONLY if the artist relates to this genre
        val artistGenreSongs = if (favoriteArtists.isNotEmpty()) {
            val matchingArtist = favoriteArtists.firstOrNull { fav ->
                primarySongs.any { it.artists.any { a -> a.name.contains(fav, ignoreCase = true) } } ||
                secondarySongs.any { it.artists.any { a -> a.name.contains(fav, ignoreCase = true) } }
            }
            if (matchingArtist != null) {
                runCatching {
                    YouTube.search("$title $matchingArtist latest trending", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items?.filterIsInstance<SongItem>().orEmpty()
                }.getOrDefault(emptyList())
            } else emptyList()
        } else emptyList()

        val allCandidates = (primarySongs + secondarySongs + artistGenreSongs)
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }

        if (allCandidates.isEmpty()) {
            return emptyList()
        }

        // 1. STRICT GENRE FILTERING: Keep only songs strictly belonging to the requested genre
        // 2. EXCLUDE VERY OLD SONGS: Filter out old/retro tracks for modern genres
        val filteredGenreSongs = allCandidates.filter { song ->
            GenreCategoryCache.matchesGenre(song, title) && !GenreCategoryCache.isOldSong(song.title, song.album?.name, title)
        }

        val pool = if (filteredGenreSongs.isNotEmpty()) {
            filteredGenreSongs
        } else {
            // Fallback: relax keyword matching slightly but still exclude very old songs
            allCandidates.filter { !GenreCategoryCache.isOldSong(it.title, it.album?.name, title) }
        }

        // Prioritize recent and trending tracks that align with user's listening habits
        val matchingHabitSongs = mutableListOf<SongItem>()
        val generalGenreSongs = mutableListOf<SongItem>()

        for (song in pool) {
            val matchesUserArtist = favoriteArtists.any { fav ->
                song.artists.any { it.name.contains(fav, ignoreCase = true) }
            }
            if (matchesUserArtist) {
                matchingHabitSongs.add(song)
            } else {
                generalGenreSongs.add(song)
            }
        }

        // Dynamically blend habit-aligned tracks at the top followed by trending genre tracks
        val dynamicSelection = (matchingHabitSongs.shuffled() + generalGenreSongs.shuffled()).distinctBy { it.id }
        return dynamicSelection.take(24)
    }

    private suspend fun fetchFeaturedPlaylists(title: String): List<PlaylistItem> {
        val result = runCatching {
            YouTube.search(title, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
        }.getOrNull()
        val items = result?.items?.filterIsInstance<PlaylistItem>().orEmpty()
        if (items.isNotEmpty()) return items

        // Fallback search
        return runCatching {
            YouTube.search("$title playlist", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                ?.items?.filterIsInstance<PlaylistItem>().orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchCommunityPlaylists(title: String): List<PlaylistItem> {
        val result = runCatching {
            YouTube.search(title, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
        }.getOrNull()
        val items = result?.items?.filterIsInstance<PlaylistItem>().orEmpty()
        if (items.isNotEmpty()) return items

        return runCatching {
            YouTube.search("$title mix", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                ?.items?.filterIsInstance<PlaylistItem>().orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchAlbums(title: String): List<AlbumItem> {
        val result = runCatching {
            YouTube.search(title, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
        }.getOrNull()
        val items = result?.items?.filterIsInstance<AlbumItem>().orEmpty()
        if (items.isNotEmpty()) return items

        return runCatching {
            YouTube.search("$title album", YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                ?.items?.filterIsInstance<AlbumItem>().orEmpty()
        }.getOrDefault(emptyList())
    }
}
