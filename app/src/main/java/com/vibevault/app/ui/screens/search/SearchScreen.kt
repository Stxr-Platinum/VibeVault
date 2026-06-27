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

/**
 * SearchScreen — Matches Stitch "Search / Browse Categories"
 * and "Search / Active Results".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("Songs") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
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
            onValueChange = viewModel::onQueryChange,
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
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Close, "Clear", tint = Color(0xFF9E9E9E))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = VibePrimary,
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
                    CircularProgressIndicator(color = VibePrimary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    searchResult?.let { result ->
                        // Filter Pills
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedFilter == "Songs",
                                    onClick = { selectedFilter = "Songs" },
                                    label = { Text("Songs", color = if (selectedFilter == "Songs") Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VibePrimary,
                                        containerColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                FilterChip(
                                    selected = selectedFilter == "Albums",
                                    onClick = { selectedFilter = "Albums" },
                                    label = { Text("Albums", color = if (selectedFilter == "Albums") Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VibePrimary,
                                        containerColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                FilterChip(
                                    selected = selectedFilter == "Artists",
                                    onClick = { selectedFilter = "Artists" },
                                    label = { Text("Artists", color = if (selectedFilter == "Artists") Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VibePrimary,
                                        containerColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }

                        if (selectedFilter == "Songs") {
                            // Songs Section
                            if (result.tracks.isNotEmpty()) {
                                items(result.tracks) { track ->
                                    SearchResultRow(
                                        title = track.title,
                                        subtitle = "${track.artist} • ${track.album}",
                                        imageUrl = track.albumImageUrl,
                                        onClick = { onTrackClick(track.id, listOf(track)) }
                                    )
                                }
                            }
                        } else if (selectedFilter == "Albums") {
                            // Albums Section
                            if (result.albums.isNotEmpty()) {
                                items(result.albums) { album ->
                                    SearchResultRow(
                                        title = album.album,
                                        subtitle = "Album • ${album.artist}",
                                        imageUrl = album.albumImageUrl,
                                        onClick = { onPlaylistClick("album:${album.album}") }
                                    )
                                }
                            }
                        } else if (selectedFilter == "Artists") {
                            // Artists Section
                            if (result.artists.isNotEmpty()) {
                                items(result.artists) { artist ->
                                    SearchResultRow(
                                        title = artist.name,
                                        subtitle = "Artist",
                                        imageUrl = artist.imageUrl,
                                        onClick = { onArtistClick(artist.name) },
                                        isCircular = true
                                    )
                                }
                            }
                        }
                    }

                    if (searchResult == null || (searchResult?.tracks?.isEmpty() == true && searchResult?.albums?.isEmpty() == true && searchResult?.artists?.isEmpty() == true)) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No results found for \"$query\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VibeOnSurfaceVariant
                                )
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
                            .clickable { viewModel.onQueryChange(category.name) }
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
private fun SearchResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isCircular: Boolean = false,
    onClick: () -> Unit
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
                color = VibeOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
