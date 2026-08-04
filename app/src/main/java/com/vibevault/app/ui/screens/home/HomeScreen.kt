package com.vibevault.app.ui.screens.home

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onListenTogetherClick: () -> Unit,
    onSettingsClick: () -> Unit,
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

    val isSpotifyConnected by viewModel.isSpotifyConnected.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSpotifyCookieDialog by remember { mutableStateOf(false) }
    var showSpotifyDisconnectConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is com.vibevault.app.ui.viewmodel.AuthViewModel.AuthState.SpotifySuccess -> {
                android.widget.Toast.makeText(context, "Spotify Connected Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.refresh()
            }
            is com.vibevault.app.ui.viewmodel.AuthViewModel.AuthState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    if (showSpotifyCookieDialog) {
        SpotifyConnectDialog(
            onDismiss = { showSpotifyCookieDialog = false },
            onConnect = { spDc, spKey ->
                authViewModel.connectWithCookies(spDc, spKey)
                showSpotifyCookieDialog = false
            }
        )
    }

    if (showSpotifyDisconnectConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSpotifyDisconnectConfirm = false },
            title = { Text("Disconnect Spotify") },
            text = { Text("Are you sure you want to disconnect your Spotify account?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.disconnectSpotify()
                        showSpotifyDisconnectConfirm = false
                        android.widget.Toast.makeText(context, "Spotify Disconnected", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSpotifyDisconnectConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Top Header and Chips ──────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.weight(1f))
                
                // Listen Together Button
                IconButton(onClick = onListenTogetherClick) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.group),
                        contentDescription = "Listen Together",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                
                // Settings Button
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                
                // Connect to Spotify Button
                if (!isSpotifyConnected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            .clickable { showSpotifyCookieDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Connect to Spotify",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                    }
                } else {
                    // Connected indicator (Click to disconnect)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { showSpotifyDisconnectConfirm = true }
                            .padding(4.dp) // extra touch target
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                            contentDescription = "Spotify Connected",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Spotify Connected",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Horizontally scrolling chips
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ChipFilter("All", true) }
                item { ChipFilter("Music", false) }
                item { ChipFilter("Podcasts", false) }
                item { ChipFilter("Workout", false) }
                item { ChipFilter("Feel good", false) }
                item { ChipFilter("Romance", false) }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (searchQuery.isNotBlank()) {
            // Search UI
            SearchHeader(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange
            )
            
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
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
                val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                HomeFeed(
                    quickPicks = quickPicks,
                    recentlyPlayed = recentlyPlayed,
                    trendingTracks = trendingTracks,
                    playlists = playlists,
                    onTrackClick = onTrackClick,
                    onPlaylistClick = onPlaylistClick,
                    onSearchClick = { /* Scroll to top or focus search */ },
                    onProfileClick = onProfileClick,
                    onSwipeToQueue = onSwipeToQueue,
                    onAddToPlaylist = { playlistId, track -> viewModel.addTrackToPlaylist(playlistId, track) },
                    onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
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
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha))
    )
}

@Composable
fun HomeFeed(
    quickPicks: List<com.vibevault.app.ui.viewmodel.QuickPickItem>,
    recentlyPlayed: List<Track>,
    trendingTracks: List<Track>,
    playlists: List<PlaylistEntity>,
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSwipeToQueue: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit,
    onCreatePlaylist: (name: String) -> Unit
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
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
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
                                    if (picks[l].type == "playlist" || picks[l].type == "album") onPlaylistClick(picks[l].id)
                                    else picks[l].track?.let { onTrackClick(it) }
                                }, Modifier.weight(1f))
                            }
                            if (r < picks.size) {
                                QuickPickItemCard(picks[r], onClick = {
                                    if (picks[r].type == "playlist" || picks[r].type == "album") onPlaylistClick(picks[r].id)
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
                        TrendingTrackRow(
                            track = track,
                            index = index + 1,
                            onClick = { onTrackClick(track) },
                            onSwipeToQueue = { onSwipeToQueue(track) },
                            playlists = playlists,
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) },
                            onCreatePlaylist = onCreatePlaylist
                        )
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
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

@Composable
fun ChipFilter(text: String, isSelected: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
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
                    Icon(Icons.Filled.LibraryMusic, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = item.title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
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
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingTrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit,
    onSwipeToQueue: () -> Unit,
    playlists: List<PlaylistEntity>,
    onAddToPlaylist: (playlistId: String) -> Unit,
    onCreatePlaylist: (name: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Add to Playlist Dialog
    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add to Playlist", color = Color.White) },
            text = {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddToPlaylistDialog = false
                                    showCreatePlaylistDialog = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Create new playlist", color = androidx.compose.material3.MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddToPlaylist(playlist.id)
                                    showAddToPlaylistDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text = playlist.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${playlist.trackCount} tracks", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (playlists.isEmpty()) {
                        item {
                            Text("No playlists available", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Close", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = Color(0xFF282828)
        )
    }

    // Create New Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false; newPlaylistName = "" },
            title = { Text("Create Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            android.widget.Toast.makeText(context, "Playlist \"${newPlaylistName.trim()}\" created", android.widget.Toast.LENGTH_SHORT).show()
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create", color = if (newPlaylistName.isNotBlank()) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Gray)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false; newPlaylistName = "" }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF282828)
        )
    }

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
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Transparent,
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
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = index.toString(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF282828))
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist", color = Color.White) },
                        onClick = {
                            showMenu = false
                            showAddToPlaylistDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.White) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onSwipeToQueue()
                            android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White) }
                    )
                }
            }
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
        placeholder = { Text("Search for songs, artists, albums...", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
            focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
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
            items(tracks.filter { it.id != null }) { dto ->
                val track = Track(
                    id = dto.id!!,
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
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
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
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dto.artists.firstOrNull()?.name ?: "Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
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
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Transparent,
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
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface) // Ensure solid background over swipe background
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
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpotifyConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (spDc: String, spKey: String) -> Unit
) {
    var captured by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF1C1B20),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Spotify Login",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Log in below to connect your Spotify account:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(12.dp))

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    factory = { context ->
                        WebView(context).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S921U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

                            fun checkCookies(): Boolean {
                                if (captured) return true
                                cookieManager.flush()
                                val c1 = cookieManager.getCookie("https://open.spotify.com") ?: ""
                                val c2 = cookieManager.getCookie("https://accounts.spotify.com") ?: ""
                                val c3 = cookieManager.getCookie("https://spotify.com") ?: ""
                                val combined = "$c1;$c2;$c3"
                                val cookies = combined.split(";").associate {
                                    val parts = it.split("=")
                                    val key = parts.firstOrNull()?.trim().orEmpty()
                                    val valStr = parts.drop(1).joinToString("=").trim()
                                    key to valStr
                                }
                                val spDc = cookies["sp_dc"].orEmpty()
                                if (spDc.isNotBlank()) {
                                    captured = true
                                    onConnect(spDc, cookies["sp_key"].orEmpty())
                                    onDismiss()
                                    return true
                                }
                                return false
                            }

                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            val checkRunnable = object : Runnable {
                                override fun run() {
                                    if (!captured && checkCookies()) {
                                        return
                                    }
                                    if (!captured) {
                                        handler.postDelayed(this, 1000)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    return checkCookies()
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    checkCookies()
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    checkCookies()
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    checkCookies()
                                }
                            }
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()
                            handler.post(checkRunnable)
                            loadUrl(com.music.spotify.SpotifyAuth.LOGIN_URL)
                        }
                    }
                )
            }
        }
    }
}


