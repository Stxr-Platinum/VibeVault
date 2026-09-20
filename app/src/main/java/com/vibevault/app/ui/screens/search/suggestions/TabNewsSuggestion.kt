package com.vibevault.app.ui.screens.search.suggestions

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.music.innertube.models.AlbumItem
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.NavigationTitle
import com.vibevault.app.ui.components.listItemShape
import com.vibevault.app.ui.components.ShimmerBox
import com.vibevault.app.ui.components.ShelfSkeleton
import kotlinx.coroutines.launch
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsTabContent(
    viewModel: SuggestionsViewModel,
    onTrackPlay: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val suggestionTracks by viewModel.suggestionTracks.collectAsState()
    val suggestionArtists by viewModel.suggestionArtists.collectAsState()
    val suggestionAlbums by viewModel.suggestionAlbums.collectAsState()
    val suggestionVideos by viewModel.suggestionVideos.collectAsState()
    val youtubeNewReleases by viewModel.youtubeNewReleases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isManualLoading by viewModel.isManualLoading.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isManualLoading,
        onRefresh = { viewModel.clearAndRefresh(force = true, isManual = true) },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            // Skeleton loading screen when initial suggestions are fetching
            if ((isLoading || isManualLoading) && suggestionTracks == null) {
                item(key = "suggestions_skeleton") {
                    SuggestionsSkeleton()
                }
            }

            // 1. Suggestions (Personalized with User Habits + Charts in HorizontalPager)
            suggestionTracks?.let { tracks ->
                item(key = "suggestions_pager_section") {
                    TrendingAppleMusicSection(
                        tracks = tracks,
                        onTrackClick = { suggestion ->
                            viewModel.playTrack(suggestion) { resolved ->
                                onTrackPlay(resolved)
                            }
                        },
                        onMoreClick = {
                            val code = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
                            uriHandler.openUri("https://music.apple.com/$code/charts")
                        }
                    )
                }
            }

            // 2. New Releases (YouTube InnerTube prioritized dynamically)
            youtubeNewReleases?.let { albums ->
                item(key = "new_releases_section") {
                    YouTubeLatestAlbumsSection(
                        albums = albums,
                        onAlbumClick = { album ->
                            onAlbumClick(album.id)
                        },
                        onMoreClick = {
                            // All albums
                        }
                    )
                }
            } ?: run {
                if (suggestionTracks != null && (isLoading || isManualLoading)) {
                    item(key = "new_releases_skeleton") {
                        ShelfSkeleton(index = 0)
                    }
                }
            }

            // 3. Trending Artists
            suggestionArtists?.let { artists ->
                item(key = "trending_artists_section") {
                    TopArtistsSection(
                        artists = artists,
                        onArtistClick = { artist ->
                            viewModel.navigateToArtist(artist) { artistIdOrName ->
                                onArtistClick(artistIdOrName)
                            }
                        }
                    )
                }
            }

            // 4. Trending Albums
            suggestionAlbums?.let { albums ->
                item(key = "trending_albums_section") {
                    TrendingAlbumsSection(
                        albums = albums,
                        onAlbumClick = { album ->
                            viewModel.navigateToAlbum(album) { albumId ->
                                onAlbumClick(albumId)
                            }
                        },
                        onMoreClick = {
                            val code = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
                            uriHandler.openUri("https://music.apple.com/$code/charts/albums")
                        }
                    )
                }
            } ?: run {
                if (suggestionTracks != null && (isLoading || isManualLoading)) {
                    item(key = "trending_albums_skeleton") {
                        ShelfSkeleton(index = 1)
                    }
                }
            }

            // 5. Trending Music Videos
            suggestionVideos?.let { videos ->
                item(key = "trending_videos_section") {
                    TrendingVideosSection(
                        videos = videos,
                        onVideoClick = { video ->
                            viewModel.playVideo(video) { resolved ->
                                onTrackPlay(resolved)
                            }
                        },
                        onMoreClick = {
                            val code = java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
                            uriHandler.openUri("https://music.apple.com/$code/charts/videos")
                        }
                    )
                }
            } ?: run {
                if (suggestionTracks != null && (isLoading || isManualLoading)) {
                    item(key = "trending_videos_skeleton") {
                        ShelfSkeleton(index = 2)
                    }
                }
            }

            // Empty state
            if (suggestionTracks == null && suggestionArtists == null && suggestionAlbums == null && suggestionVideos == null && !isLoading && !isManualLoading) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No suggestions available at the moment.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.refresh(force = true) }) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionsSkeleton(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardFill = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.10f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(6.dp))
        // Suggestions Block Header Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(cardFill)
                .border(0.75.dp, cardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .padding(16.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(130.dp)
                    .height(24.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 5-item card skeleton matching the real suggestions layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            val titleFractions = listOf(0.65f, 0.52f, 0.72f, 0.45f, 0.60f)
            val subFractions = listOf(0.35f, 0.42f, 0.28f, 0.38f, 0.30f)
            for (i in 0 until 5) {
                val shape = listItemShape(i, 5, radius = 20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(cardFill)
                        .border(0.75.dp, cardBorder, shape)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        ) {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(titleFractions[i % titleFractions.size])
                                    .height(16.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(subFractions[i % subFractions.size])
                                    .height(13.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(10.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ShelfSkeleton(index = 0)
        Spacer(modifier = Modifier.height(6.dp))
        ShelfSkeleton(index = 1)
    }
}

// ── 1. Trending Apple Music Section ──────────────────────────
@Composable
fun TrendingAppleMusicSection(
    tracks: List<SuggestionTrack>,
    onTrackClick: (SuggestionTrack) -> Unit,
    onMoreClick: () -> Unit
) {
    if (tracks.isEmpty()) return
    val displayTracks = tracks.take(29)
    val totalItems = displayTracks.size + 1
    val pagerState = rememberPagerState(pageCount = { (totalItems + 4) / 5 })
    val coroutineScope = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    val cardFill = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.10f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(cardFill)
                .border(0.75.dp, cardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(8.dp)
                    .background(cardFill.copy(alpha = 0.05f))
            )
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(300, easing = FastOutSlowInEasing))
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                val startIdx = page * 5
                val endIdx = minOf(startIdx + 5, totalItems)
                val countInPage = endIdx - startIdx
                for (i in startIdx until endIdx) {
                    val isMoreCard = i == 29
                    val indexInPage = i - startIdx
                    val shape = listItemShape(indexInPage, countInPage, radius = 20.dp)
                    if (isMoreCard) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(cardFill)
                                .border(0.75.dp, cardBorder, shape)
                                .clickable { onMoreClick() }
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(8.dp)
                                    .background(cardFill.copy(alpha = 0.05f))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "View more on Apple Music",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (i < displayTracks.size) {
                        val track = displayTracks[i]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(cardFill)
                                .border(0.75.dp, cardBorder, shape)
                                .clickable { onTrackClick(track) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(8.dp)
                                    .background(cardFill.copy(alpha = 0.05f))
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "#${track.rank}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        val playCount = remember(track.rank) {
                                            val base = 2_500_000 / (track.rank + 2)
                                            if (base >= 1_000_000) String.format("%.1fM plays", base / 1_000_000f)
                                            else String.format("%dk plays", base / 1_000)
                                        }
                                        Text(
                                            text = playCount,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                if (track.thumbnailUrl != null) {
                                    SubcomposeAsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pager Indicator Pill
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardFill)
                    .border(0.75.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                Text(
                    text = "${pagerState.currentPage + 1} of ${pagerState.pageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    enabled = pagerState.currentPage < pagerState.pageCount - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
    }
}

// ── 2. YouTube Latest Albums (New Releases) ──────────────────────────
@Composable
fun YouTubeLatestAlbumsSection(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit,
    onMoreClick: () -> Unit
) {
    if (albums.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        NavigationTitle(
            title = "New Releases",
            onClick = onMoreClick
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(albums) { album ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onAlbumClick(album) }
                ) {
                    AsyncImage(
                        model = album.thumbnail,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = album.artists?.joinToString { it.name } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── 3. Trending Artists Section ──────────────────────────
@Composable
fun TopArtistsSection(
    artists: List<SuggestionArtist>,
    onArtistClick: (SuggestionArtist) -> Unit
) {
    if (artists.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Trending Artists",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(artists) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onArtistClick(artist) }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        SubcomposeAsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .offset((-2).dp, (-2).dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = artist.rank.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val playCount = remember(artist.rank) {
                        val base = 15_000_000 / (artist.rank + 8)
                        if (base >= 1_000_000) String.format("%.1fM plays", base / 1_000_000f)
                        else String.format("%dk plays", base / 1_000)
                    }
                    Text(
                        text = playCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── 4. Trending Albums Section ──────────────────────────
@Composable
fun TrendingAlbumsSection(
    albums: List<SuggestionAlbum>,
    onAlbumClick: (SuggestionAlbum) -> Unit,
    onMoreClick: () -> Unit
) {
    if (albums.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Trending Albums",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(albums) { album ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onAlbumClick(album) }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        SubcomposeAsyncImage(
                            model = album.thumbnailUrl,
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .offset((-2).dp, (-2).dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = album.rank.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onMoreClick() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── 5. Trending Music Videos Section ──────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingVideosSection(
    videos: List<SuggestionTrack>,
    onVideoClick: (SuggestionTrack) -> Unit,
    onMoreClick: () -> Unit
) {
    if (videos.isEmpty()) return

    val carouselState = rememberCarouselState(itemCount = { videos.size })
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trending Music Videos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "More",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onMoreClick() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 320.dp,
            itemSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val video = videos[i]
            var isCardFocused by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .onGloballyPositioned { coordinates ->
                        val cardCenter = coordinates.boundsInRoot().center.x
                        val screenWidth = context.resources.displayMetrics.widthPixels
                        val screenCenter = screenWidth / 2f
                        isCardFocused = abs(cardCenter - screenCenter) < 150
                    }
                    .clickable { onVideoClick(video) }
            ) {
                // Background Image (Thumbnail)
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = 300f
                            )
                        )
                )

                // Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = video.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
