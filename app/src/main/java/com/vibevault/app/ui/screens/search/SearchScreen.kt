package com.vibevault.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.theme.*

import com.vibevault.app.ui.screens.search.suggestions.OnlineSearchSuggestionViewModel

/**
 * SearchScreen — Matches Stitch "Search / Browse Categories"
 * and "Search / Active Results".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTrackClick: (String, List<Track>) -> Unit,
    onNavigateToOnlineSearch: (String) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val suggestionViewState by suggestionViewModel.viewState.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("Songs") }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
        // ── Header ──────────────────────────────────────────
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
        )

        // ── Search Bar ─────────────────────────────────────
        TextField(
            value = query,
            onValueChange = { 
                viewModel.onQueryChange(it)
                suggestionViewModel.query.value = it
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { 
                    if (query.isNotBlank()) onNavigateToOnlineSearch(query)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp)),
            placeholder = {
                Text(
                    "What do you want to listen to?",
                    color = Color(0xFF9E9E9E)
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, "Search", tint = Color(0xFF9E9E9E))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { 
                        viewModel.onQueryChange("")
                        suggestionViewModel.query.value = ""
                    }) {
                        Icon(Icons.Default.Close, "Clear", tint = Color(0xFF9E9E9E))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        // ── Results / Browse ──────────────────────────────
        if (query.isNotEmpty()) {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Suggestions
                    if (suggestionViewState.suggestions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Suggestions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(suggestionViewState.suggestions) { suggestion ->
                            val decodedSuggestion = suggestion.replace("+", " ")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onQueryChange(decodedSuggestion)
                                        suggestionViewModel.query.value = decodedSuggestion
                                        onNavigateToOnlineSearch(decodedSuggestion)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9E9E9E))
                                Spacer(Modifier.width(16.dp))
                                Text(text = decodedSuggestion, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.rotate(45f))
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Top Result
                    if (suggestionViewState.items.isNotEmpty()) {
                        item {
                            Text(
                                text = "Top result",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(suggestionViewState.items) { item ->
                            when (item) {
                                is com.music.innertube.models.SongItem -> {
                                    val track = com.vibevault.app.domain.model.Track(
                                        id = item.id,
                                        title = item.title,
                                        artist = item.artists.joinToString(", ") { it.name },
                                        album = item.album?.name ?: "",
                                        durationMs = (item.duration ?: 0) * 1000L,
                                        albumImageUrl = item.thumbnail
                                    )
                                    val subtitleText = if (item.isVideoSong) "Video" else "Song"
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "$subtitleText • ${item.artists.joinToString(", ") { it.name }}",
                                        imageUrl = item.thumbnail,
                                        onClick = { onTrackClick(track.id, listOf(track)) }
                                    )
                                }
                                is com.music.innertube.models.AlbumItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Album • ${item.artists?.joinToString(", ") { it.name } ?: "Unknown"}",
                                        imageUrl = item.thumbnail,
                                        onClick = { onPlaylistClick("album:${item.id}") }
                                    )
                                }
                                is com.music.innertube.models.ArtistItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Artist",
                                        imageUrl = item.thumbnail,
                                        isCircular = true,
                                        onClick = { onArtistClick(item.title) }
                                    )
                                }
                                is com.music.innertube.models.PlaylistItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Playlist • ${item.author?.name ?: "Unknown"}",
                                        imageUrl = item.thumbnail,
                                        onClick = { onPlaylistClick("playlist:${item.id}") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── Browse Categories Grid ─────────────────────
            Text(
                text = "Browse all",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            data class CategoryItem(val name: String, val color: Color, val imageUrl: String)
            val categories = listOf(
                CategoryItem("Podcasts", Color(0xFF006450), "https://images.unsplash.com/photo-1593697821252-0c9137d9fc45?w=200&h=200&fit=crop"),
                CategoryItem("Live Events", Color(0xFF8400E7), "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&h=200&fit=crop"),
                CategoryItem("Made For You", Color(0xFF1E3264), "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=200&h=200&fit=crop"),
                CategoryItem("New Releases", Color(0xFFE8115B), "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=200&h=200&fit=crop"),
                CategoryItem("Hindi", Color(0xFF148A08), "https://images.unsplash.com/photo-1582560475093-ba66cef36eb4?w=200&h=200&fit=crop"),
                CategoryItem("Punjabi", Color(0xFFE13300), "https://images.unsplash.com/photo-1533558701576-23c65e0272fb?w=200&h=200&fit=crop"),
                CategoryItem("Tamil", Color(0xFF537AA1), "https://images.unsplash.com/photo-1583391265517-355bfadbb4cb?w=200&h=200&fit=crop"),
                CategoryItem("Telugu", Color(0xFFDC148C), "https://images.unsplash.com/photo-1516280440503-66f81df96cb8?w=200&h=200&fit=crop"),
                CategoryItem("Pop", Color(0xFFB02897), "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200&h=200&fit=crop"),
                CategoryItem("K-Pop", Color(0xFF509BF5), "https://images.unsplash.com/photo-1627843563095-f6e94676fc06?w=200&h=200&fit=crop"),
                CategoryItem("Hip-Hop", Color(0xFFBA5D07), "https://images.unsplash.com/photo-1603569283847-aa295f0d016a?w=200&h=200&fit=crop"),
                CategoryItem("Charts", Color(0xFF477D95), "https://images.unsplash.com/photo-1619983081563-430f63602796?w=200&h=200&fit=crop"),
                CategoryItem("Chill", Color(0xFF477D95), "https://images.unsplash.com/photo-1499810631641-541e76d678a2?w=200&h=200&fit=crop"),
                CategoryItem("Indie", Color(0xFF608108), "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&h=200&fit=crop"),
                CategoryItem("Discover", Color(0xFF9B6ADE), "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=200&h=200&fit=crop"),
                CategoryItem("Workout", Color(0xFF1E3264), "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=200&h=200&fit=crop")
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                items(categories) { category ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(category.color)
                            .clickable { 
                                viewModel.onQueryChange(category.name)
                                suggestionViewModel.query.value = category.name
                                onNavigateToOnlineSearch(category.name)
                            }
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(top = 12.dp, start = 12.dp)
                        )
                        
                        AsyncImage(
                            model = category.imageUrl,
                            contentDescription = category.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(70.dp)
                                .offset(x = 18.dp, y = 6.dp)
                                .rotate(25f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SearchResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isCircular: Boolean = false,
    onClick: () -> Unit,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .size(48.dp)
                .clip(if (isCircular) RoundedCornerShape(24.dp) else RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onActionClick != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
