package com.vibevault.app.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ShelfItem(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val videoId: String? = null,
    val browseId: String? = null
)

data class HomeShelf(
    val title: String,
    val items: List<ShelfItem>,
    val subtitle: String = ""
)

@HiltViewModel
class ExploreViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<HomeShelf>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadExplore()
    }

    fun loadExplore() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            fetchExplore()
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            fetchExplore()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchExplore() {
        withContext(Dispatchers.IO) {
            try {
                val exploreDeferred = async {
                    runCatching { YouTube.browse("FEmusic_explore", null).getOrNull() }.getOrNull()
                }
                val chartsDeferred = async {
                    runCatching { YouTube.browse("FEmusic_charts", null).getOrNull() }.getOrNull()
                }
                val newReleasesDeferred = async {
                    runCatching { YouTube.newReleaseAlbums().getOrNull() }.getOrNull()
                }

                val exploreResult = exploreDeferred.await()
                val chartsResult = chartsDeferred.await()
                val newReleases = newReleasesDeferred.await().orEmpty()

                val shelves = mutableListOf<HomeShelf>()

                // Lead shelf: New Releases (hero layout)
                if (newReleases.isNotEmpty()) {
                    val newReleaseItems = newReleases.map { album ->
                        ShelfItem(
                            title = album.title,
                            subtitle = (album.artists?.joinToString(", ") { it.name } ?: "") + if (album.year != null) " • ${album.year}" else "",
                            thumbnailUrl = album.thumbnail,
                            browseId = album.browseId.ifEmpty { album.playlistId }
                        )
                    }
                    shelves.add(
                        HomeShelf(
                            title = "New Releases",
                            items = newReleaseItems,
                            subtitle = "The latest albums & singles"
                        )
                    )
                }

                // Shelves from FEmusic_explore
                exploreResult?.items?.forEach { browseItem ->
                    val shelfTitle = browseItem.title?.trim().orEmpty().ifBlank { "Featured" }
                    val items = browseItem.items.mapNotNull { ytItem -> ytItemToShelfItem(ytItem) }
                    if (items.isNotEmpty()) {
                        shelves.add(HomeShelf(title = shelfTitle, items = items))
                    }
                }

                // Shelves from FEmusic_charts
                chartsResult?.items?.forEach { browseItem ->
                    val shelfTitle = browseItem.title?.trim().orEmpty().ifBlank { "Top Charts" }
                    val items = browseItem.items.mapNotNull { ytItem -> ytItemToShelfItem(ytItem) }
                    if (items.isNotEmpty()) {
                        shelves.add(HomeShelf(title = shelfTitle, items = items))
                    }
                }

                // Deduplicate shelves by title (case-insensitive) just like BitChord
                val seen = mutableSetOf<String>()
                val deduplicated = shelves.filter { seen.add(it.title.lowercase(Locale.ROOT)) }

                if (deduplicated.isNotEmpty()) {
                    _uiState.value = UiState.Success(deduplicated)
                } else {
                    _uiState.value = UiState.Success(defaultExploreShelves())
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Success(defaultExploreShelves())
            }
        }
    }

    private fun ytItemToShelfItem(ytItem: YTItem): ShelfItem? {
        return when (ytItem) {
            is SongItem -> ShelfItem(
                title = ytItem.title,
                subtitle = ytItem.artists.joinToString(", ") { it.name },
                thumbnailUrl = ytItem.thumbnail,
                videoId = ytItem.id
            )
            is AlbumItem -> ShelfItem(
                title = ytItem.title,
                subtitle = (ytItem.artists?.joinToString(", ") { it.name } ?: "") + if (ytItem.year != null) " • ${ytItem.year}" else "",
                thumbnailUrl = ytItem.thumbnail,
                browseId = ytItem.browseId.ifEmpty { ytItem.playlistId }
            )
            is PlaylistItem -> ShelfItem(
                title = ytItem.title,
                subtitle = ytItem.author?.name ?: ytItem.songCountText ?: "Playlist",
                thumbnailUrl = ytItem.thumbnail,
                browseId = ytItem.id
            )
            is ArtistItem -> ShelfItem(
                title = ytItem.title,
                subtitle = "Artist",
                thumbnailUrl = ytItem.thumbnail,
                browseId = ytItem.id
            )
            else -> null
        }
    }

    private fun defaultExploreShelves(): List<HomeShelf> {
        return listOf(
            HomeShelf(
                title = "New Releases",
                subtitle = "The latest albums & singles",
                items = listOf(
                    ShelfItem(
                        title = "Timeless Hits",
                        subtitle = "Global Artists",
                        thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=720&h=720&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Chill Lofi Beats",
                        subtitle = "Relaxation Station",
                        thumbnailUrl = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=720&h=720&fit=crop",
                        browseId = "chill"
                    ),
                    ShelfItem(
                        title = "Midnight Drive",
                        subtitle = "Night Grooves",
                        thumbnailUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=720&h=720&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Electric Surge",
                        subtitle = "Dance / EDM",
                        thumbnailUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=720&h=720&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    )
                )
            ),
            HomeShelf(
                title = "Trending Charts",
                subtitle = "Most played tracks this week",
                items = listOf(
                    ShelfItem(
                        title = "Top 50 - Global",
                        subtitle = "Daily Updates",
                        thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&h=500&fit=crop",
                        browseId = "VLPL4fGSI1pDJn6O1LS0XSdF3RyArnkY6L2I"
                    ),
                    ShelfItem(
                        title = "Viral Hits",
                        subtitle = "Trending on Socials",
                        thumbnailUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=500&h=500&fit=crop",
                        browseId = "VLPLrEnWoR732-DN6gkfZ24XUvUfQ49cM4wS"
                    ),
                    ShelfItem(
                        title = "Hot R&B & Soul",
                        subtitle = "Smooth & Soulful",
                        thumbnailUrl = "https://images.unsplash.com/photo-1520523839898-507125cd53c1?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Indie Discoveries",
                        subtitle = "Fresh Voices",
                        thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    )
                )
            ),
            HomeShelf(
                title = "Moods & Moments",
                subtitle = "Music for whatever you're feeling",
                items = listOf(
                    ShelfItem(
                        title = "Chill",
                        subtitle = "Wind down & relax",
                        thumbnailUrl = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500&h=500&fit=crop",
                        browseId = "chill"
                    ),
                    ShelfItem(
                        title = "Energy Boosters",
                        subtitle = "High energy motivation",
                        thumbnailUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Deep Focus",
                        subtitle = "Coding, study & flow",
                        thumbnailUrl = "https://images.unsplash.com/photo-1539185441755-769473a23570?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Workout",
                        subtitle = "Power up your session",
                        thumbnailUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Feel Good",
                        subtitle = "Pure positive vibes",
                        thumbnailUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Sleep & Serenity",
                        subtitle = "Drift away peacefully",
                        thumbnailUrl = "https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    )
                )
            ),
            HomeShelf(
                title = "Genres & Styles",
                subtitle = "Explore across musical landscapes",
                items = listOf(
                    ShelfItem(
                        title = "Pop",
                        subtitle = "The world's catchiest hooks",
                        thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Hip-Hop",
                        subtitle = "Heavy beats & raw rhymes",
                        thumbnailUrl = "https://images.unsplash.com/photo-1603569283847-aa295f0d016a?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Rock & Metal",
                        subtitle = "Guitars & raw power",
                        thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Bollywood & Desi",
                        subtitle = "Latest film & pop soundtracks",
                        thumbnailUrl = "https://images.unsplash.com/photo-1582560475093-ba66cef36eb4?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    ),
                    ShelfItem(
                        title = "Punjabi Beats",
                        subtitle = "Bhangra & urban Punjabi",
                        thumbnailUrl = "https://images.unsplash.com/photo-1533558701576-23c65e0272fb?w=500&h=500&fit=crop",
                        browseId = "FEmusic_moods_and_genres_category"
                    )
                )
            )
        )
    }
}
