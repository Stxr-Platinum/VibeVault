package com.vibevault.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*

/**
 * HomeScreen — Main dashboard matching Stitch "Home Dashboard (Recreated)".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (String, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    
    val profileViewModel: com.vibevault.app.ui.screens.profile.ProfileViewModel = hiltViewModel()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        viewModel.initializeData()
        profileViewModel.refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // ── Header: Avatar + Filter Chips ──────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserAvatar(
                    avatarUrl = profileState.avatarUrl,
                    displayName = profileState.username ?: profileState.accountHolderName,
                    size = 32.dp,
                    modifier = Modifier.clickable { onProfileClick() }
                )

                val filters = listOf("All", "Music", "Podcasts")
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                filter,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VibePrimaryContainer,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF2A2A2A),
                            labelColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = null
                    )
                }
            }
        }

        // ── Quick-Access 2-Column Grid ─────────────────────
        item {
            val gridTracks = tracks.take(8)
            if (gridTracks.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    gridTracks.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { track ->
                                QuickAccessCard(
                                    track = track,
                                    onClick = { onTrackClick(track.id, tracks) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Jump Back In Section ───────────────────────────
        if (recentTracks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Jump back in",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentTracks, key = { it.id }) { track ->
                        JumpBackInCard(
                            track = track,
                            onClick = { onTrackClick(track.id, recentTracks) }
                        )
                    }
                }
            }
        }

        // ── Recents Section ───────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recents",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "Show all",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeOnSurfaceVariant
                )
            }
        }

        // ── Track List ─────────────────────────────────────
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                onClick = { onTrackClick(track.id, tracks) },
                onArtistClick = { onArtistClick(track.artist) }
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun JumpBackInCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Playlist",
            style = MaterialTheme.typography.labelSmall,
            color = VibeOnSurfaceVariant
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall,
            color = VibeOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = VibeOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onArtistClick)
            )
        }
        IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert, "More",
                tint = VibeOnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
