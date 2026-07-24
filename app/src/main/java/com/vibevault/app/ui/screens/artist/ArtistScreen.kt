package com.vibevault.app.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.theme.*

/**
 * ArtistScreen — Matches Stitch "Artist Page" (0876b195).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }



    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val latestAlbums by viewModel.latestAlbums.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── Top Bar ────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                // Background Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    androidx.compose.material3.MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Artist avatar
                    val avatarUrl = topSongs.firstOrNull()?.albumImageUrl ?: ""
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = artistName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = artistName.firstOrNull()?.toString() ?: "A",
                                style = MaterialTheme.typography.displaySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Action Row ─────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { topSongs.firstOrNull()?.let { onTrackClick(it.id, topSongs) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play All", style = MaterialTheme.typography.titleSmall)
                }

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // ── Track Listing ──────────────────────────────────
        item {
            Text(
                text = "Popular",
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        items(topSongs, key = { it.id }) { track ->
            ArtistTrackRow(
                track = track,
                index = topSongs.indexOf(track) + 1,
                onClick = { onTrackClick(track.id, topSongs) }
            )
        }

        if (latestAlbums.isNotEmpty()) {
            item {
                Text(
                    text = "Latest Releases",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(latestAlbums, key = { it.id }) { album ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onPlaylistClick(album.id) }
                        ) {
                            AsyncImage(
                                model = album.coverUrl,
                                contentDescription = album.title,
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = album.releaseDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.album,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
