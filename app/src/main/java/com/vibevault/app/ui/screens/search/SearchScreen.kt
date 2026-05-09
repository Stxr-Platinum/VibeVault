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
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
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
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(results, key = { it.id }) { track ->
                    SearchResultRow(
                        track = track,
                        onClick = { onTrackClick(track.id, results) }
                    )
                }

                if (results.isEmpty()) {
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
        } else {
            // ── Browse Categories Grid ─────────────────────
            Text(
                text = "Browse all",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val categories = listOf(
                Pair("Podcasts", Color(0xFF006450)),
                Pair("Live Events", Color(0xFF8400E7)),
                Pair("Made For You", Color(0xFF1E3264)),
                Pair("New Releases", Color(0xFFE8115B)),
                Pair("Hindi", Color(0xFF148A08)),
                Pair("Punjabi", Color(0xFFE13300)),
                Pair("Tamil", Color(0xFF537AA1)),
                Pair("Telugu", Color(0xFFDC148C)),
                Pair("Pop", Color(0xFFB02897)),
                Pair("K-Pop", Color(0xFF509BF5)),
                Pair("Hip-Hop", Color(0xFFBA5D07)),
                Pair("Charts", Color(0xFF477D95)),
                Pair("Chill", Color(0xFF477D95)),
                Pair("Indie", Color(0xFF608108)),
                Pair("Discover", Color(0xFF9B6ADE)),
                Pair("Workout", Color(0xFF1E3264))
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(categories) { (name, color) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                            .clickable { viewModel.onQueryChange(name) }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    track: Track,
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
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = VibeOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
