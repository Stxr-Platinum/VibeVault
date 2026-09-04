package com.vibevault.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.R
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.NavigationTitle
import com.vibevault.app.ui.components.RandomizeGridItem
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.viewmodel.HomeViewModel
import com.vibevault.app.ui.viewmodel.QuickPickItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViviHomeScreen(
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onListenTogetherClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwipeToQueue: (Track) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.vibevault.app.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val speedDialPicks by viewModel.speedDialPicks.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var isRefreshing by remember { mutableStateOf(false) }
    var isRandomizing by remember { mutableStateOf(false) }
    var refreshSeed by remember { mutableStateOf(0) }
    val pullRefreshState = rememberPullToRefreshState()

    var selectedChip by remember { mutableStateOf<String?>(null) }
    val categories = listOf("Podcasts", "Workout", "Commute", "Feel good", "Romance", "Focus", "Party")

    val isSpotifyConnected by viewModel.isSpotifyConnected.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSpotifyConnectDialog by remember { mutableStateOf(false) }
    var showSpotifyDisconnectConfirm by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(authState) {
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

    if (showSpotifyConnectDialog) {
        SpotifyConnectDialog(
            onDismiss = { showSpotifyConnectDialog = false },
            onConnect = { spDc, spKey ->
                authViewModel.connectWithCookies(spDc, spKey)
                showSpotifyConnectDialog = false
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

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                refreshSeed++
                viewModel.refresh()
                delay(1000)
                isRefreshing = false
            }
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── 1. Top Header Row (User Avatar + Welcome Greeting + Action Buttons) ──────────────────────────
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar & "Welcome back, {Name}"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.clickable { onProfileClick() }
                        ) {
                            UserAvatar(
                                avatarUrl = userAvatarUrl,
                                displayName = userDisplayName ?: "D",
                                size = 44.dp
                            )

                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Welcome back,",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = userDisplayName?.ifBlank { "User" } ?: "User",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Action Icons: Spotify, Listen Together & Settings
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isSpotifyConnected) {
                                        showSpotifyDisconnectConfirm = true
                                    } else {
                                        showSpotifyConnectDialog = true
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.spotify),
                                    contentDescription = if (isSpotifyConnected) "Disconnect Spotify" else "Connect Spotify",
                                    tint = if (isSpotifyConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onListenTogetherClick) {
                                Icon(
                                    painter = painterResource(R.drawable.group),
                                    contentDescription = "Listen Together",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    painter = painterResource(R.drawable.settings),
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // ── 2. Category Filter Chips ──────────────────────────
                item(key = "chips_row") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            val selected = selectedChip == category
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedChip = if (selected) null else category
                                },
                                label = {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // ── Loading Shimmer ──────────────────────────
                if (isLoading) {
                    item(key = "loading_shimmer") {
                        ViviShimmerHost()
                    }
                }

                // ── 3. Speed dial Section (Personalized 60% Favorites / 40% Fresh Discoveries) ──────────────────────────
                if (quickPicks.isNotEmpty() || recentlyPlayed.isNotEmpty() || trendingTracks.isNotEmpty()) {
                    item(key = "speed_dial_title") {
                        NavigationTitle(
                            title = "Speed dial"
                        )
                    }

                    item(key = "speed_dial_pager") {
                        // Personalized Recommendation Algorithm for Speed Dial:
                        // 60% Familiar Favorites & Similar Songs / 40% Fresh Discoveries, dynamically recalculated on refreshSeed
                        val speedDialItems = remember(speedDialPicks, recentlyPlayed, trendingTracks, refreshSeed) {
                            val familiarFavoritesPool = mutableListOf<QuickPickItem>()

                            // Extract user's favorite artists and albums from history
                            val favoriteArtists = (recentlyPlayed.map { it.artist } + speedDialPicks.mapNotNull { it.track?.artist })
                                .filter { it.isNotBlank() }
                                .toSet()

                            val favoriteAlbums = (recentlyPlayed.map { it.album } + speedDialPicks.mapNotNull { it.track?.album })
                                .filter { it.isNotBlank() }
                                .toSet()

                            // 1. Add direct familiar favorites (frequently played, history, liked)
                            speedDialPicks.forEach { item ->
                                if (familiarFavoritesPool.none { it.id == item.id }) {
                                    familiarFavoritesPool.add(item)
                                }
                            }
                            recentlyPlayed.forEach { track ->
                                if (familiarFavoritesPool.none { it.id == track.id }) {
                                    familiarFavoritesPool.add(
                                        QuickPickItem(
                                            id = track.id,
                                            title = track.title,
                                            coverUrl = track.albumImageUrl,
                                            type = "track",
                                            track = track
                                        )
                                    )
                                }
                            }

                            // 1b. Add Similar Songs (tracks by favorite artists or matching albums)
                            trendingTracks.forEach { track ->
                                val isSimilar = favoriteArtists.any { artist -> track.artist.contains(artist, ignoreCase = true) || artist.contains(track.artist, ignoreCase = true) } ||
                                                favoriteAlbums.any { album -> track.album.equals(album, ignoreCase = true) }
                                if (isSimilar && familiarFavoritesPool.none { it.id == track.id }) {
                                    familiarFavoritesPool.add(
                                        QuickPickItem(
                                            id = track.id,
                                            title = track.title,
                                            coverUrl = track.albumImageUrl,
                                            type = "track",
                                            track = track
                                        )
                                    )
                                }
                            }

                            // 2. Gather fresh discoveries (unseen tracks not in history or similar pools)
                            val freshDiscoveriesPool = trendingTracks
                                .filter { track -> familiarFavoritesPool.none { it.id == track.id } }
                                .map { track ->
                                    QuickPickItem(
                                        id = track.id,
                                        title = track.title,
                                        coverUrl = track.albumImageUrl,
                                        type = "track",
                                        track = track
                                    )
                                }

                            val rng = kotlin.random.Random(refreshSeed * 31 + 17)

                            val shuffledFavorites = familiarFavoritesPool.shuffled(rng)
                            val shuffledDiscoveries = freshDiscoveriesPool.shuffled(rng)

                            // Separate albums and playlists from pure song tracks so Page 0 (1st page) contains ONLY songs
                            val containerItems = mutableListOf<QuickPickItem>() // Albums & Playlists
                            val trackFavorites = mutableListOf<QuickPickItem>()
                            val trackDiscoveries = mutableListOf<QuickPickItem>()

                            shuffledFavorites.forEach { item ->
                                val isContainer = item.type == "album" || item.type == "playlist" ||
                                                  item.id.startsWith("album:") || item.id.startsWith("playlist:") ||
                                                  item.track == null
                                if (isContainer) {
                                    containerItems.add(item)
                                } else {
                                    trackFavorites.add(item)
                                }
                            }

                            shuffledDiscoveries.forEach { item ->
                                val isContainer = item.type == "album" || item.type == "playlist" ||
                                                  item.id.startsWith("album:") || item.id.startsWith("playlist:") ||
                                                  item.track == null
                                if (isContainer) {
                                    containerItems.add(item)
                                } else {
                                    trackDiscoveries.add(item)
                                }
                            }

                            val finalSelection = mutableListOf<QuickPickItem>()

                            // Page 0: Fill up to 8 slots strictly with track songs
                            val targetPage0Count = 8
                            var favIdx = 0
                            var discIdx = 0

                            while (finalSelection.size < targetPage0Count && (favIdx < trackFavorites.size || discIdx < trackDiscoveries.size)) {
                                if (favIdx < trackFavorites.size && finalSelection.size < targetPage0Count) {
                                    finalSelection.add(trackFavorites[favIdx++])
                                }
                                if (discIdx < trackDiscoveries.size && finalSelection.size < targetPage0Count) {
                                    finalSelection.add(trackDiscoveries[discIdx++])
                                }
                            }

                            // Remaining track items and container items (albums & playlists) for Page 1+
                            while (favIdx < trackFavorites.size) {
                                finalSelection.add(trackFavorites[favIdx++])
                            }
                            while (discIdx < trackDiscoveries.size) {
                                finalSelection.add(trackDiscoveries[discIdx++])
                            }
                            finalSelection.addAll(containerItems)

                            finalSelection
                        }

                        val columns = 3
                        val rows = 3
                        val itemsPerPage = columns * rows

                        val pageCount = ((speedDialItems.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                        val pagerState = rememberPagerState(pageCount = { pageCount })

                        val targetItemSize = (maxWidth - 48.dp) / 3

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                pageSpacing = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(targetItemSize * rows + 24.dp)
                            ) { page ->
                                val pageStartIndex = page * itemsPerPage
                                val pageItems = speedDialItems.drop(pageStartIndex).take(itemsPerPage)

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (r in 0 until rows) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (c in 0 until columns) {
                                                val itemIndex = r * columns + c
                                                val isRandomizeSlot = (page == 0 && itemIndex == itemsPerPage - 1)

                                                if (isRandomizeSlot) {
                                                    // 9th slot on Page 0: Randomize 5-dot dice card
                                                    Box(
                                                        modifier = Modifier
                                                            .width(targetItemSize)
                                                            .height(targetItemSize)
                                                    ) {
                                                        RandomizeGridItem(
                                                            isLoading = isRandomizing,
                                                            onClick = {
                                                                scope.launch {
                                                                    isRandomizing = true
                                                                    refreshSeed++
                                                                    delay(800)
                                                                    isRandomizing = false
                                                                    if (speedDialItems.isNotEmpty()) {
                                                                        val randomItem = speedDialItems.random()
                                                                        if (randomItem.type == "playlist" || randomItem.type == "album" || randomItem.id.startsWith("album:") || randomItem.id.startsWith("MPREb_") || randomItem.id.startsWith("OLAK5uy_")) {
                                                                            onPlaylistClick(randomItem.id)
                                                                        } else if (randomItem.track != null) {
                                                                            onTrackClick(randomItem.track)
                                                                        } else {
                                                                            onPlaylistClick(randomItem.id)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                } else if (itemIndex < pageItems.size) {
                                                    val item = pageItems[itemIndex]
                                                    Box(
                                                        modifier = Modifier
                                                            .width(targetItemSize)
                                                            .height(targetItemSize)
                                                    ) {
                                                        SpeedDialCardItem(
                                                            item = item,
                                                            onClick = {
                                                                if (item.type == "playlist" || item.type == "album" || item.id.startsWith("album:") || item.id.startsWith("MPREb_") || item.id.startsWith("OLAK5uy_")) {
                                                                    onPlaylistClick(item.id)
                                                                } else if (item.track != null) {
                                                                    onTrackClick(item.track)
                                                                } else {
                                                                    onPlaylistClick(item.id)
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.width(targetItemSize))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pager indicator dots below grid
                            if (pagerState.pageCount > 1) {
                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(pagerState.pageCount) { iteration ->
                                        val color = if (pagerState.currentPage == iteration)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .size(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. Quick picks Section ──────────────────────────
                if (quickPicks.isNotEmpty()) {
                    item(key = "quick_picks_title") {
                        NavigationTitle(
                            title = "Quick picks",
                            onPlayAllClick = {
                                quickPicks.firstOrNull()?.let { first ->
                                    onTrackClick(first)
                                }
                            }
                        )
                    }

                    item(key = "quick_picks_list") {
                        val songs = remember(quickPicks) { quickPicks.distinctBy { it.id } }
                        val rows = if (songs.size >= 4) 4 else songs.size.coerceAtLeast(1)

                        LazyHorizontalGrid(
                            state = rememberLazyGridState(),
                            rows = GridCells.Fixed(rows),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp * rows)
                        ) {
                            itemsIndexed(
                                items = songs,
                                key = { _, track -> "qp_${track.id}" }
                            ) { index, track ->
                                ViviSongListItem(
                                    track = track,
                                    onClick = {
                                        onTrackClick(track)
                                    },
                                    modifier = Modifier.width(horizontalLazyGridItemWidth)
                                )
                            }
                        }
                    }
                }

                // ── 5. Keep listening Section (2-row Grid) ──────────────────────────
                if (keepListening.isNotEmpty()) {
                    item(key = "keep_listening_title") {
                        NavigationTitle(
                            title = "Keep listening",
                            onPlayAllClick = {
                                keepListening.firstOrNull()?.let { first ->
                                    onTrackClick(first)
                                }
                            }
                        )
                    }

                    item(key = "keep_listening_list") {
                        val rows = 2
                        LazyHorizontalGrid(
                            state = rememberLazyGridState(),
                            rows = GridCells.Fixed(rows),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp * rows)
                        ) {
                            itemsIndexed(
                                items = keepListening,
                                key = { _, track -> "kl_${track.id}" }
                            ) { index, track ->
                                ViviSongListItem(
                                    track = track,
                                    onClick = {
                                        onTrackClick(track)
                                    },
                                    modifier = Modifier.width(horizontalLazyGridItemWidth)
                                )
                            }
                        }
                    }
                }

                // ── 6. Similar To Recommendations Sections ──────────────────────────
                similarRecommendations.forEachIndexed { index, recommendation ->
                    if (recommendation.items.isNotEmpty()) {
                        item(key = "similar_to_title_$index") {
                            NavigationTitle(
                                label = "SIMILAR TO",
                                title = recommendation.title,
                                thumbnail = recommendation.imageUrl?.let { url ->
                                    {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                },
                                onPlayAllClick = {
                                    recommendation.items.firstOrNull()?.let { first ->
                                        onTrackClick(first)
                                    }
                                }
                            )
                        }

                        item(key = "similar_to_list_$index") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = recommendation.items.distinctBy { it.id },
                                    key = { "sim_${index}_${it.id}" }
                                ) { track ->
                                    ViviTrackGridItem(
                                        track = track,
                                        onClick = {
                                            onTrackClick(track)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 7. Trending / Recommended Section (List Format) ──────────────────────────
                if (trendingTracks.isNotEmpty()) {
                    item(key = "trending_title") {
                        NavigationTitle(
                            title = "Trending",
                            onPlayAllClick = {
                                trendingTracks.firstOrNull()?.let { first ->
                                    onTrackClick(first)
                                }
                            }
                        )
                    }

                    item(key = "trending_list") {
                        val trendingSongs = remember(trendingTracks) { trendingTracks.distinctBy { it.id } }
                        val rows = if (trendingSongs.size >= 4) 4 else trendingSongs.size.coerceAtLeast(1)
                        LazyHorizontalGrid(
                            state = rememberLazyGridState(),
                            rows = GridCells.Fixed(rows),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp * rows)
                        ) {
                            itemsIndexed(
                                items = trendingSongs,
                                key = { _, track -> "tr_${track.id}" }
                            ) { index, track ->
                                ViviSongListItem(
                                    track = track,
                                    onClick = {
                                        onTrackClick(track)
                                    },
                                    modifier = Modifier.width(horizontalLazyGridItemWidth)
                                )
                            }
                        }
                    }
                }

                // ── Bottom Spacer ──────────────────────────
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

// ── Speed Dial Card Item ──────────────────────────
@Composable
fun SpeedDialCardItem(
    item: QuickPickItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Title text overlay at bottom left
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
    }
}

// ── Song List Item for Quick Picks ──────────────────────────
@Composable
fun ViviSongListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* Menu */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Track Grid Item ──────────────────────────
@Composable
fun ViviTrackGridItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Shimmer Loading Placeholder ──────────────────────────
@Composable
fun ViviShimmerHost() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Title shimmer
        ShimmerPlaceholder(
            modifier = Modifier
                .width(200.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Grid shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section title shimmer
        ShimmerPlaceholder(
            modifier = Modifier
                .width(250.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        // List item shimmers
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier.background(shimmerColor.copy(alpha = alpha))
    )
}
