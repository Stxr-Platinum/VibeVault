package com.vibevault.app.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*
import com.vibevault.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSwipeToQueue: (Track) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.vibevault.app.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()

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
            UserAvatar(
                avatarUrl = userAvatarUrl,
                displayName = userDisplayName ?: "D",
                size = 38.dp,
                modifier = Modifier.clickable { onProfileClick() }
            )
            Spacer(Modifier.width(12.dp))
            ChipFilter("All", true)
            Spacer(Modifier.width(8.dp))
            ChipFilter("Music", false)
            Spacer(Modifier.weight(1f))
            // Connect to Spotify Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1DB954))
                    .clickable { uriHandler.openUri(authViewModel.getSpotifyAuthUrl()) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Connect",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
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
                SearchResults(viewModel = viewModel, searchResponse = searchResults, onTrackClick = { onTrackClick(it) }, onSwipeToQueue = onSwipeToQueue)
            }
        } else {
            if (isLoading) {
                Box(Modifier.fillMaxSize()) {
                    SkeletonFeed()
                }
            } else {
                HomeFeed(
                    quickPicks = quickPicks,
                    recentlyPlayed = recentlyPlayed,
                    trendingTracks = trendingTracks,
                    onTrackClick = { viewModel.playTrack(it); onTrackClick(it) },
                    onPlaylistClick = onPlaylistClick,
                    onSearchClick = { /* Scroll to top or focus search */ },
                    onProfileClick = onProfileClick,
                    onSwipeToQueue = onSwipeToQueue
                )
            }
        }
    }
}

@Composable
fun SkeletonFeed() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
    ) {
        // Greeting Skeleton
        item {
            ShimmerBox(192.dp, 36.dp, 6.dp)
            Spacer(Modifier.height(24.dp))
        }
        
        // Quick Picks Skeleton
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))) {
                            ShimmerBox(64.dp, 64.dp, 0.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBox(0.dp, 12.dp, 4.dp, Modifier.fillMaxWidth(0.8f))
                                ShimmerBox(0.dp, 10.dp, 4.dp, Modifier.fillMaxWidth(0.5f))
                            }
                        }
                        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))) {
                            ShimmerBox(64.dp, 64.dp, 0.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBox(0.dp, 12.dp, 4.dp, Modifier.fillMaxWidth(0.8f))
                                ShimmerBox(0.dp, 10.dp, 4.dp, Modifier.fillMaxWidth(0.5f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Recently Played Skeleton
        item {
            ShimmerBox(160.dp, 28.dp, 6.dp)
            Spacer(Modifier.height(16.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(5) {
                    item {
                        Column(Modifier.width(140.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(16.dp)) {
                            ShimmerBox(108.dp, 108.dp, 6.dp)
                            Spacer(Modifier.height(16.dp))
                            ShimmerBox(80.dp, 16.dp, 4.dp)
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(50.dp, 12.dp, 4.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Trending Skeleton
        item {
            ShimmerBox(160.dp, 28.dp, 6.dp)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(40.dp, 40.dp, 4.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            ShimmerBox(100.dp, 16.dp, 4.dp)
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(60.dp, 12.dp, 4.dp)
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
    quickPicks: List<com.vibevault.app.ui.viewmodel.QuickPickItem>,
    recentlyPlayed: List<Track>,
    trendingTracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSwipeToQueue: (Track) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
    ) {
        // Greeting
        item {
            Text(
                text = "Good afternoon",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VibeOnSurface,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)
            )
        }

        // Quick Picks grid
        if (quickPicks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val picks = quickPicks.take(8)
                    for (i in 0 until (picks.size + 1) / 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val l = i * 2; val r = i * 2 + 1
                            if (l < picks.size) {
                                QuickPickItemCard(picks[l], onClick = {
                                    if (picks[l].type == "playlist") onPlaylistClick(picks[l].id)
                                    else picks[l].track?.let { onTrackClick(it) }
                                }, Modifier.weight(1f))
                            }
                            if (r < picks.size) {
                                QuickPickItemCard(picks[r], onClick = {
                                    if (picks[r].type == "playlist") onPlaylistClick(picks[r].id)
                                    else picks[r].track?.let { onTrackClick(it) }
                                }, Modifier.weight(1f))
                            } else if (picks.size > 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader("Recently Played", Modifier.padding(horizontal = 4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recentlyPlayed) { track ->
                        PlaylistCardTrack(track, onClick = { onTrackClick(track) })
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // Trending Now
        if (trendingTracks.isNotEmpty()) {
            item {
                SectionHeader("Trending Now", Modifier.padding(horizontal = 4.dp))
            }
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(vertical = 8.dp)
                ) {
                    trendingTracks.forEachIndexed { index, track ->
                        TrendingTrackRow(track, index + 1, onClick = { onTrackClick(track) }, onSwipeToQueue = { onSwipeToQueue(track) })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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
fun QuickPickItemCard(item: com.vibevault.app.ui.viewmodel.QuickPickItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                    .background(VibePrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.LibraryMusic, null, tint = VibePrimary)
                }
            }
            Text(
                text = item.title,
                color = VibeOnSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            )
        }
    }
}

@Composable
fun PlaylistCardTrack(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = track.title,
            color = VibeOnSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist,
            color = VibeOnSurfaceDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingTrackRow(track: Track, index: Int, onClick: () -> Unit, onSwipeToQueue: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToQueue()
                android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color(0xFF1DB954) else Color.Transparent,
                label = "swipeBgColor"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Add to Queue", tint = Color.White)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeBg)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = index.toString(),
                color = VibeOnSurfaceDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(16.dp))
            AsyncImage(
                model = track.albumImageUrl,
                contentDescription = track.album,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = VibeOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeOnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = VibeOnSurfaceDim, modifier = Modifier.size(20.dp))
        }
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
private fun SearchResults(viewModel: HomeViewModel, searchResponse: SpotifySearchResponse?, onTrackClick: (Track) -> Unit, onSwipeToQueue: (Track) -> Unit) {
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
                TrackRow(track, onClick = { onTrackClick(track) }, onSwipeToQueue = { onSwipeToQueue(track) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(track: Track, onClick: () -> Unit, onSwipeToQueue: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToQueue()
                android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                false // Bounce back
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color(0xFF1DB954) else Color.Transparent,
                label = "swipeBgColor"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = "Add to Queue",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeSurface) // Ensure solid background over swipe background
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
}