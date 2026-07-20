package com.vibevault.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.*
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.viewmodel.OnlineSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onBack: () -> Unit,
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: OnlineSearchViewModel = hiltViewModel()
) {
    val filter by viewModel.filter.collectAsState()
    val filterKey = filter?.value ?: "all"
    val currentPage = viewModel.viewStateMap[filterKey]
    val items = currentPage?.items ?: emptyList()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // Search Bar matching the search screen UI
            TextField(
                value = query,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF9E9E9E))
                    }
                },
                colors = TextFieldDefaults.colors(
                    disabledContainerColor = Color(0xFF1A1A1A),
                    disabledTextColor = Color.White,
                    disabledIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Pills
            ScrollableTabRow(
                selectedTabIndex = getTabIndexForFilter(filter),
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                indicator = {},
                divider = {}
            ) {
                val filters = listOf(
                    null to "All",
                    YouTube.SearchFilter.FILTER_SONG to "Songs",
                    YouTube.SearchFilter.FILTER_VIDEO to "Videos",
                    YouTube.SearchFilter.FILTER_ALBUM to "Albums",
                    YouTube.SearchFilter.FILTER_ARTIST to "Artists",
                    YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST to "Playlists"
                )

                filters.forEachIndexed { index, (f, label) ->
                    val selected = filter == f
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.filter.value = f },
                        label = { Text(label, color = if (selected) Color.Black else Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            containerColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            if (filter == null) {
                if (viewModel.summaryPage == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                    ) {
                        viewModel.summaryPage?.summaries?.forEach { summary ->
                            item {
                                Text(
                                    text = summary.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(summary.items) { item ->
                                ytItemContent(item, onTrackClick, onPlaylistClick, onArtistClick)
                            }
                        }
                    }
                }
            } else {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                    ) {
                        items(items) { item ->
                            ytItemContent(item, onTrackClick, onPlaylistClick, onArtistClick)
                        }

                        if (currentPage?.continuation != null) {
                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.loadMore()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ytItemContent(
    item: YTItem,
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    when (item) {
        is SongItem -> {
            val track = item.toTrack()
            SearchResultRow(
                title = item.title,
                subtitle = "Song • ${item.artists.joinToString(", ") { it.name }}",
                imageUrl = item.thumbnail,
                onClick = { onTrackClick(track.id, listOf(track)) },
                onActionClick = null
            )
        }
        is AlbumItem -> {
            SearchResultRow(
                title = item.title,
                subtitle = "Album • ${item.artists?.joinToString(", ") { it.name } ?: "Unknown"}",
                imageUrl = item.thumbnail,
                onClick = { onPlaylistClick("album:${item.id}") },
                onActionClick = null
            )
        }
        is ArtistItem -> {
            SearchResultRow(
                title = item.title,
                subtitle = "Artist",
                imageUrl = item.thumbnail,
                isCircular = true,
                onClick = { onArtistClick(item.title) },
                onActionClick = null
            )
        }
        is PlaylistItem -> {
            SearchResultRow(
                title = item.title,
                subtitle = "Playlist • ${item.author?.name ?: "Unknown"}",
                imageUrl = item.thumbnail,
                onClick = { onPlaylistClick("playlist:${item.id}") },
                onActionClick = null
            )
        }
    }
}

private fun getTabIndexForFilter(filter: YouTube.SearchFilter?): Int {
    return when (filter) {
        null -> 0
        YouTube.SearchFilter.FILTER_SONG -> 1
        YouTube.SearchFilter.FILTER_VIDEO -> 2
        YouTube.SearchFilter.FILTER_ALBUM -> 3
        YouTube.SearchFilter.FILTER_ARTIST -> 4
        YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST -> 5
        else -> 0
    }
}

private fun SongItem.toTrack(): Track {
    return Track(
        id = this.id,
        title = this.title,
        artist = this.artists.joinToString(", ") { it.name },
        album = this.album?.name ?: "",
        durationMs = (this.duration ?: 0) * 1000L,
        albumImageUrl = this.thumbnail
    )
}
