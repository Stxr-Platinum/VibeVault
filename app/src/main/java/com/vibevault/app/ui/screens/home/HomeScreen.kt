package com.vibevault.app.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.vibevault.app.domain.model.Track
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.data.remote.dto.SpotifySearchResponse
import com.vibevault.app.ui.theme.*
import com.vibevault.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val discoveryTracks by viewModel.discoveryTracks.collectAsStateWithLifecycle()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsStateWithLifecycle()
    val newReleases by viewModel.newReleases.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val top50Tracks by viewModel.top50Tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
    ) {
        // ── Top Bar (Always Visible) ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(VibePrimary, VibePrimaryLight)))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("D", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            ChipFilter("All", true)
            Spacer(Modifier.width(8.dp))
            ChipFilter("Music", false)
            Spacer(Modifier.width(8.dp))
            ChipFilter("Podcasts", false)
        }

        if (searchQuery.isNotBlank()) {
            // Search UI
            SearchHeader(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange
            )
            
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VibePrimary)
                }
            } else {
                SearchResults(searchResults, onTrackClick = { viewModel.playTrack(it); onTrackClick(it) })
            }
        } else {
            // Home Feed Logic
            if (isLoading) {
                Box(Modifier.fillMaxSize()) {
                    SkeletonFeed()
                    
                    // Center Refresh Button if empty for too long
                    Column(
                        Modifier.align(Alignment.Center).padding(bottom = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Looking for music...", color = VibeOnSurfaceMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = VibePrimary)
                        ) {
                            Text("Refresh Now", color = Color.Black)
                        }
                    }
                }
            } else {
                HomeFeed(
                    recentlyPlayed = recentlyPlayed,
                    likedSongs = likedSongs,
                    discoveryTracks = discoveryTracks,
                    featuredPlaylists = featuredPlaylists,
                    newReleases = newReleases,
                    topArtists = topArtists,
                    playlists = playlists,
                    top50Tracks = top50Tracks,
                    onTrackClick = { viewModel.playTrack(it); onTrackClick(it) },
                    onPlaylistClick = onPlaylistClick,
                    onSearchClick = { /* Scroll to top or focus search */ },
                    onProfileClick = onProfileClick
                )
            }
        }
    }
}

@Composable
fun SkeletonFeed() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(0.dp, 58.dp, 10.dp, Modifier.weight(1f))
                        ShimmerBox(0.dp, 58.dp, 10.dp, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(top = 36.dp)) {
                ShimmerBox(120.dp, 24.dp, 4.dp, Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) {
                        item {
                            Column(Modifier.width(148.dp)) {
                                ShimmerBox(148.dp, 148.dp, 12.dp)
                                Spacer(Modifier.height(10.dp))
                                ShimmerBox(100.dp, 16.dp, 4.dp)
                                Spacer(Modifier.height(6.dp))
                                ShimmerBox(60.dp, 12.dp, 4.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .then(if (width > 0.dp) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(VibeSurfaceElevated.copy(alpha = alpha))
    )
}

@Composable
fun HomeFeed(
    recentlyPlayed: List<Track>,
    likedSongs: List<Track>,
    discoveryTracks: List<Track>,
    featuredPlaylists: List<com.vibevault.app.domain.model.Playlist>,
    newReleases: List<Track>,
    topArtists: List<com.vibevault.app.domain.model.Artist>,
    playlists: List<PlaylistEntity>,
    top50Tracks: List<Track> = emptyList(),
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Quick Picks grid (using Liked Songs)
        if (likedSongs.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val picks = likedSongs.take(6)
                    for (i in 0 until (picks.size + 1) / 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val l = i * 2; val r = i * 2 + 1
                            if (l < picks.size) QuickPickItem(picks[l], { onTrackClick(picks[l]) }, Modifier.weight(1f))
                            if (r < picks.size) QuickPickItem(picks[r], { onTrackClick(picks[r]) }, Modifier.weight(1f))
                            else if (picks.size > 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Discovery Section (Spotify Data)
        if (discoveryTracks.isNotEmpty()) {
            item {
                SectionHeader("Made For You", Modifier.padding(top = 36.dp))
                HorizontalCarousel(discoveryTracks, onTrackClick)
            }
        }

        // Global Top 50
        if (top50Tracks.isNotEmpty()) {
            item {
                SectionHeader("Global Top 50", Modifier.padding(top = 36.dp))
                HorizontalCarousel(top50Tracks, onTrackClick)
            }
        }

        // New Releases
        if (newReleases.isNotEmpty()) {
            item {
                SectionHeader("New Releases", Modifier.padding(top = 36.dp))
                HorizontalCarousel(newReleases, onTrackClick)
            }
        }

        // Top Artists
        if (topArtists.isNotEmpty()) {
            item {
                SectionHeader("Top Artists", Modifier.padding(top = 36.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(topArtists) { artist ->
                        ArtistCard(artist, onClick = { /* Navigate to Artist */ })
                    }
                }
            }
        }

        // Jump back in (Recently Played)
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader("Jump back in", Modifier.padding(top = 36.dp))
                HorizontalCarousel(recentlyPlayed, onTrackClick)
            }
        }

        // Featured Playlists
        if (featuredPlaylists.isNotEmpty()) {
            item {
                SectionHeader("Featured Playlists", Modifier.padding(top = 32.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(featuredPlaylists) { playlist ->
                        PlaylistCardDomain(playlist, onClick = { onPlaylistClick(playlist.id) })
                    }
                }
            }
        }

        // Your Playlists
        if (playlists.isNotEmpty()) {
            item {
                SectionHeader("Your Playlists", Modifier.padding(top = 32.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistCard(playlist, onClick = { onPlaylistClick(playlist.id) })
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = VibeOnSurface,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

@Composable
fun ChipFilter(text: String, isSelected: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) VibePrimary else VibeSurfaceElevated,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.Black else VibeOnSurfaceMedium,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun QuickPickItem(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = VibeSurfaceElevated
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.albumImageUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
            )
            Text(
                text = track.title,
                color = VibeOnSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .weight(1f)
            )
        }
    }
}

@Composable
fun HorizontalCarousel(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(tracks) { track ->
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .clickable { onTrackClick(track) }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VibeSurfaceElevated,
                    modifier = Modifier.size(148.dp)
                ) {
                    AsyncImage(
                        model = track.albumImageUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = track.title,
                    color = VibeOnSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = VibeOnSurfaceDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VibeSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = VibePrimary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = playlist.title,
            color = VibeOnSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist",
            color = VibeOnSurfaceDim,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun PlaylistCardDomain(playlist: com.vibevault.app.domain.model.Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = playlist.title,
            color = VibeOnSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist • ${playlist.ownerName}",
            color = VibeOnSurfaceDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCard(artist: com.vibevault.app.domain.model.Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = artist.name,
            color = VibeOnSurface,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artist",
            color = VibeOnSurfaceDim,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHeader(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Search for songs, artists, albums...", color = VibeOnSurfaceDim) },
        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = VibeOnSurfaceMedium) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear", tint = VibeOnSurfaceMedium)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = VibeSurfaceElevated,
            unfocusedContainerColor = VibeSurfaceElevated,
            focusedTextColor = VibeOnSurface,
            unfocusedTextColor = VibeOnSurface,
            cursorColor = VibePrimary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(14.dp),
        singleLine = true
    )
}

@Composable
private fun SearchResults(searchResponse: SpotifySearchResponse?, onTrackClick: (Track) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        searchResponse?.tracks?.items?.let { tracks ->
            item { SectionHeader("Tracks") }
            items(tracks) { dto ->
                val track = Track(
                    id = dto.id,
                    title = dto.name,
                    artist = dto.artists.firstOrNull()?.name ?: "Unknown",
                    album = dto.album?.name ?: "Unknown",
                    albumImageUrl = dto.album?.images?.firstOrNull()?.url ?: "",
                    audioUrl = dto.previewUrl,
                    durationMs = dto.durationMs,
                    isLiked = false,
                    source = "spotify",
                    externalUrl = dto.externalUrls["spotify"]
                )
                TrackRow(track, onClick = { onTrackClick(track) })
            }
        }

        searchResponse?.artists?.items?.let { artists ->
            item { SectionHeader("Artists") }
            items(artists) { dto ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = dto.images.firstOrNull()?.url,
                        contentDescription = dto.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = dto.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = VibeOnSurface
                    )
                }
            }
        }

        searchResponse?.albums?.items?.let { albums ->
            item { SectionHeader("Albums") }
            items(albums) { dto ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = dto.images.firstOrNull()?.url,
                        contentDescription = dto.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dto.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = VibeOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dto.artists.firstOrNull()?.name ?: "Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeOnSurfaceDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
                color = VibeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = VibeOnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}