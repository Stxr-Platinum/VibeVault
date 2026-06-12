package com.vibevault.app.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Album
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.theme.*

/**
 * ArtistScreen — Spotify-style artist page.
 * Full-bleed artist image hero, popular tracks, discography.
 */
@Composable
fun ArtistScreen(
    artistName: String,
    onTrackClick: (String, List<Track>) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VibePrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ── Hero Section ─────────────────────────────────
                item {
                    ArtistHero(
                        artistName = artist?.name ?: artistName,
                        artistImageUrl = artist?.imageUrl,
                        onBack = onBack
                    )
                }

                // ── Action Buttons ───────────────────────────────
                item {
                    ActionRow(
                        onPlayAll = {
                            topTracks.firstOrNull()?.let { onTrackClick(it.id, topTracks) }
                        },
                        onShuffle = {
                            if (topTracks.isNotEmpty()) {
                                val shuffled = topTracks.shuffled()
                                onTrackClick(shuffled.first().id, shuffled)
                            }
                        }
                    )
                }

                // ── Popular Tracks ───────────────────────────────
                if (topTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Popular",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = VibeOnSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    itemsIndexed(
                        topTracks.take(5),
                        key = { _, track -> track.id }
                    ) { index, track ->
                        PopularTrackRow(
                            track = track,
                            index = index + 1,
                            onClick = { onTrackClick(track.id, topTracks) }
                        )
                    }

                    // "See more" if more than 5 tracks
                    if (topTracks.size > 5) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            if (!expanded) {
                                Text(
                                    text = "See more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VibeOnSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                        .clickable { expanded = true }
                                )
                            }
                        }
                    }
                }

                // ── Discography / Albums ─────────────────────────
                if (albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Discography",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = VibeOnSurface,
                            modifier = Modifier.padding(
                                start = 20.dp, end = 20.dp,
                                top = 32.dp, bottom = 12.dp
                            )
                        )
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(albums) { album ->
                                AlbumCard(album)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Hero: Full-bleed artist image with gradient ──────────
@Composable
private fun ArtistHero(
    artistName: String,
    artistImageUrl: String?,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Artist image background (full-bleed)
        if (artistImageUrl != null) {
            AsyncImage(
                model = artistImageUrl,
                contentDescription = artistName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                VibePrimary.copy(alpha = 0.4f),
                                VibeBg
                            )
                        )
                    )
            )
        }

        // Gradient overlay (bottom fade to background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            VibeBg.copy(alpha = 0.6f),
                            VibeBg
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Top gradient for status bar readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Artist name at the bottom of the hero
        Text(
            text = artistName,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

// ── Action Row: Play All + Shuffle ───────────────────────
@Composable
private fun ActionRow(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle button
            IconButton(
                onClick = onShuffle,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = VibeOnSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Large green play FAB (Spotify style)
        FloatingActionButton(
            onClick = onPlayAll,
            containerColor = VibePrimary,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ── Popular Track Row ────────────────────────────────────
@Composable
private fun PopularTrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = VibeOnSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.width(12.dp))

        // Album art
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(14.dp))

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = VibeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.album,
                style = MaterialTheme.typography.bodySmall,
                color = VibeOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More button
        IconButton(
            onClick = { /* TODO: track options */ },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = VibeOnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Album Card ───────────────────────────────────────────
@Composable
private fun AlbumCard(album: Album) {
    Column(
        modifier = Modifier.width(148.dp)
    ) {
        // Album cover
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VibeSurfaceElevated,
            modifier = Modifier.size(148.dp)
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Album name
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = VibeOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Album type/year
        Text(
            text = album.releaseDate?.take(4) ?: "Album",
            style = MaterialTheme.typography.bodySmall,
            color = VibeOnSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
