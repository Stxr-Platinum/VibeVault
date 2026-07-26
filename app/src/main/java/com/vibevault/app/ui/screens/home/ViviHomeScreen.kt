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
import kotlin.random.Random

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
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var isRefreshing by remember { mutableStateOf(false) }
    var isRandomizing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var selectedChip by remember { mutableStateOf<String?>(null) }
    val categories = listOf("Podcasts", "Workout", "Commute", "Feel good", "Romance", "Focus", "Party")

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
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

                        // Action Icons: Listen Together & Settings
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                // ── 3. Speed dial Section (Exact 3x3 Grid with Pager & 5-Dot Dice Card) ──────────────────────────
                if (quickPicks.isNotEmpty() || recentlyPlayed.isNotEmpty()) {
                    item(key = "speed_dial_title") {
                        NavigationTitle(
                            title = "Speed dial"
                        )
                    }

                    item(key = "speed_dial_pager") {
                        // Combine available items for Speed Dial
                        val speedDialItems = remember(quickPicks, recentlyPlayed) {
                            val combined = mutableListOf<QuickPickItem>()
                            combined.addAll(quickPicks)
                            recentlyPlayed.forEach { track ->
                                if (combined.none { it.id == track.id }) {
                                    combined.add(
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
                            combined
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
                                                                    delay(800)
                                                                    isRandomizing = false
                                                                    if (speedDialItems.isNotEmpty()) {
                                                                        val randomItem = speedDialItems.random()
                                                                        if (randomItem.track != null) {
                                                                            viewModel.playTrack(randomItem.track)
                                                                            onTrackClick(randomItem.track)
                                                                        } else if (randomItem.type == "playlist") {
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
                                                                if (item.track != null) {
                                                                    viewModel.playTrack(item.track)
                                                                    onTrackClick(item.track)
                                                                } else if (item.type == "playlist") {
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
                if (quickPicks.isNotEmpty() || recentlyPlayed.isNotEmpty()) {
                    item(key = "quick_picks_title") {
                        NavigationTitle(
                            title = "Quick picks",
                            onPlayAllClick = {
                                (quickPicks.mapNotNull { it.track } + recentlyPlayed).firstOrNull()?.let { first ->
                                    viewModel.playTrack(first)
                                    onTrackClick(first)
                                }
                            }
                        )
                    }

                    item(key = "quick_picks_list") {
                        val songs = remember(quickPicks, recentlyPlayed) {
                            (quickPicks.mapNotNull { it.track } + recentlyPlayed).distinctBy { it.id }
                        }

                        LazyHorizontalGrid(
                            state = rememberLazyGridState(),
                            rows = GridCells.Fixed(4),
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp * 4)
                        ) {
                            itemsIndexed(
                                items = songs,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                ViviSongListItem(
                                    track = track,
                                    onClick = {
                                        viewModel.playTrack(track)
                                        onTrackClick(track)
                                    },
                                    modifier = Modifier.width(horizontalLazyGridItemWidth)
                                )
                            }
                        }
                    }
                }

                // ── 5. Trending / Recommended Section ──────────────────────────
                if (trendingTracks.isNotEmpty()) {
                    item(key = "trending_title") {
                        NavigationTitle(
                            title = "Trending",
                            onPlayAllClick = {
                                trendingTracks.firstOrNull()?.let { first ->
                                    viewModel.playTrack(first)
                                    onTrackClick(first)
                                }
                            }
                        )
                    }

                    item(key = "trending_list") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = trendingTracks.distinctBy { it.id },
                                key = { it.id }
                            ) { track ->
                                ViviTrackGridItem(
                                    track = track,
                                    onClick = {
                                        viewModel.playTrack(track)
                                        onTrackClick(track)
                                    }
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
