package com.vibevault.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vibevault.app.ui.components.FrostedTopBar
import com.vibevault.app.ui.components.TopFadeBlur
import com.vibevault.app.ui.components.topBarHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.music.innertube.YouTube
import com.vibevault.app.R
import com.vibevault.app.ui.components.MessageState
import com.vibevault.app.ui.components.PAGE_GUTTER
import com.vibevault.app.ui.components.ShimmerBox
import com.vibevault.app.ui.components.feedSkeleton
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One server-defined group of the buttons shown on Explore. */
data class MoodGenreSection(
    val title: String,
    val items: List<MoodGenre>,
)

/** A mood or genre button and the exact browse request that it represents. */
data class MoodGenre(
    val title: String,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null,
    /** Real cover from the category's playlist shelves, loaded in the background or preloaded. */
    val thumbnailUrl: String? = null,
)

/** Compatibility alias for legacy callers */
data class ExploreCategoryCard(
    val title: String,
    val color: Color,
    val imageUrl: String,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}

/**
 * Exact BitChord ExploreScreen signature accepting UiState<List<MoodGenreSection>>.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: UiState<List<MoodGenreSection>>,
    listState: LazyListState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = stringResource(R.string.explore),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                )
            }
            when (state) {
                UiState.Loading -> item { ExploreSkeleton() }
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }
                is UiState.Success -> state.data.forEach { section ->
                    item(key = section.title) {
                        MoodGenreGrid(section = section, onCategoryClick = onCategoryClick)
                    }
                }
            }
        }
    }
}

/** In-memory and persistent disk cache for resolved artwork URLs across app launches */
private val exploreArtworkCache = ConcurrentHashMap<String, String>()
private var exploreSectionCache: List<MoodGenreSection>? = null
private const val EXPLORE_PREFS = "explore_artwork_cache_prefs"

private fun loadPersistedArtwork(context: Context): Map<String, String> {
    return runCatching {
        val prefs = context.getSharedPreferences(EXPLORE_PREFS, Context.MODE_PRIVATE)
        prefs.all.mapNotNull { (key, value) ->
            if (value is String && value.isNotBlank()) key to value else null
        }.toMap()
    }.getOrDefault(emptyMap())
}

private fun persistArtwork(context: Context, title: String, url: String) {
    runCatching {
        val prefs = context.getSharedPreferences(EXPLORE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(title, url).apply()
    }
}

/**
 * Convenience overload for navigation from AppNavHost with BitChord UI, in-memory & disk caching,
 * and frosted top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onCategoryClick: (title: String, browseId: String, params: String?) -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val persisted = remember { loadPersistedArtwork(context) }
    val haptics = rememberHaptics()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val exploreHaze = remember { HazeState() }

    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 32
        }
    }

    val initialSections = remember {
        exploreSectionCache ?: listOf(
            MoodGenreSection(
                title = "Moods & moments",
                items = defaultMoods.map { item ->
                    val cached = GenreCategoryCache.getFirstSongThumbnail(context, item.title)
                        ?: exploreArtworkCache[item.title]
                        ?: persisted[item.title]
                    if (cached != null) {
                        exploreArtworkCache[item.title] = cached
                        item.copy(thumbnailUrl = cached)
                    } else item
                }
            ),
            MoodGenreSection(
                title = "Genres",
                items = defaultGenres.map { item ->
                    val cached = GenreCategoryCache.getFirstSongThumbnail(context, item.title)
                        ?: exploreArtworkCache[item.title]
                        ?: persisted[item.title]
                    if (cached != null) {
                        exploreArtworkCache[item.title] = cached
                        item.copy(thumbnailUrl = cached)
                    } else item
                }
            )
        )
    }
    var sections by remember { mutableStateOf(initialSections) }

    var refreshTrigger by remember { mutableStateOf(0) }

    // Dynamic background artwork update: dynamically displays the album cover of the first song
    // listed on each genre's detail page with local caching
    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            // 1. Fetch server-defined categories if available
            val remoteSections = YouTube.moodAndGenres().getOrNull()
            if (!remoteSections.isNullOrEmpty()) {
                sections = remoteSections.map { section ->
                    MoodGenreSection(
                        title = section.title,
                        items = section.items.map { item ->
                            val cached = GenreCategoryCache.getFirstSongThumbnail(context, item.title)
                                ?: exploreArtworkCache[item.title]
                                ?: persisted[item.title]
                            MoodGenre(
                                title = item.title,
                                browseId = item.endpoint.browseId,
                                params = item.endpoint.params,
                                thumbnailUrl = cached
                            )
                        }
                    )
                }
            }

            // 2. Fetch the album cover of the first song listed on that genre's detail page
            val limiter = Semaphore(4)
            sections.flatMap { it.items }.forEach { item ->
                val cachedArtwork = GenreCategoryCache.getFirstSongThumbnail(context, item.title)
                    ?: exploreArtworkCache[item.title]
                    ?: persisted[item.title]

                if (cachedArtwork == null || refreshTrigger > 0) {
                    launch {
                        runCatching {
                            val artwork = limiter.withPermit {
                                GenreCategoryCache.resolveFirstSongThumbnail(context, item.title)
                            }
                            if (artwork != null) {
                                exploreArtworkCache[item.title] = artwork
                                persistArtwork(context, item.title, artwork)

                                sections = sections.map { sec ->
                                    sec.copy(
                                        items = sec.items.map { cur ->
                                            if (cur.title.equals(item.title, ignoreCase = true)) {
                                                cur.copy(thumbnailUrl = artwork)
                                            } else {
                                                cur
                                            }
                                        }
                                    )
                                }
                                exploreSectionCache = sections
                            }
                        }
                    }
                } else {
                    // Pre-warm Coil cache for persisted category images
                    GenreCategoryCache.preloadThumbnail(context, cachedArtwork)
                }
            }
        }
        isRefreshing = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshTrigger++
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(exploreHaze),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = topBarHeight() + 8.dp,
                    bottom = 150.dp
                )
            ) {
                // Main "Explore" heading exactly matching BitChord
                item(key = "main_explore_title") {
                    Text(
                        text = stringResource(R.string.explore),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp)
                    )
                }

                // Grids for each section (Moods & moments, Genres) exactly matching BitChord
                sections.forEach { section ->
                    item(key = section.title) {
                        MoodGenreGrid(
                            section = section,
                            onCategoryClick = { item ->
                                haptics.play(Haptic.Select)
                                onCategoryClick(item.title, item.browseId, item.params)
                            }
                        )
                    }
                }
            }
        }

        // Frosty Top Bar with top fade blur glass
        TopFadeBlur(
            hazeState = exploreHaze,
            pageColor = MaterialTheme.colorScheme.background,
            scrimColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FrostedTopBar(
            title = stringResource(R.string.explore),
            scrolled = scrolled,
            refreshing = isRefreshing,
            pullFraction = { pullRefreshState.distanceFraction },
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                IconButton(
                    onClick = {
                        haptics.play(Haptic.Select)
                        onDownloadsClick?.invoke()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Downloads",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            unscrolledStartContent = {
                IconButton(
                    onClick = {
                        haptics.play(Haptic.Select)
                        onSettingsClick?.invoke()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )
    }
}


@Composable
private fun MoodGenreGrid(
    section: MoodGenreSection,
    onCategoryClick: (MoodGenre) -> Unit,
) {
    Column(Modifier.padding(bottom = 22.dp)) {
        SectionHeader(section.title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - PAGE_GUTTER * 2 - 12.dp) / 2
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = PAGE_GUTTER),
            ) {
                section.items.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            MoodGenreCard(
                                item = item,
                                onClick = { onCategoryClick(item) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodGenreCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = moodColor(item.title)
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(red = color.red * .68f, green = color.green * .68f, blue = color.blue * .68f)),
                ),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                // Push a rotated square beyond the corner, exactly like a
                // cropped album sleeve rather than a floating rectangle.
                .offset(x = 10.dp, y = 12.dp)
                .size(82.dp)
                .graphicsLayer { rotationZ = 16f }
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = .22f)),
        ) {
            item.thumbnailUrl?.let { artwork ->
                val context = LocalContext.current
                val imageRequest = remember(artwork, item.title) {
                    ImageRequest.Builder(context)
                        .data(artwork)
                        .memoryCacheKey(artwork)
                        .diskCacheKey(artwork)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(end = 48.dp),
        )
    }
}

private fun moodColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 8) {
    0 -> Color(0xFFE64A19)
    1 -> Color(0xFFEC0B65)
    2 -> Color(0xFF8664AC)
    3 -> Color(0xFF6B4EFF)
    4 -> Color(0xFFBE6100)
    5 -> Color(0xFF233C78)
    6 -> Color(0xFF4D97E5)
    else -> Color(0xFFAA267E)
}

@Composable
private fun ExploreSkeleton() {
    Column(Modifier.padding(horizontal = PAGE_GUTTER)) {
        repeat(5) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                repeat(2) {
                    Box(Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(8.dp))) {
                        ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
                        ShimmerBox(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 10.dp, y = 12.dp)
                                .size(82.dp)
                                .graphicsLayer { rotationZ = 16f },
                            RoundedCornerShape(7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodGenrePlaylistsScreen(
    title: String,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            )
        }
        when (state) {
            UiState.Loading -> feedSkeleton()
            is UiState.Error -> item {
                MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
            }
            is UiState.Success -> items(state.data, key = { it.title }) { shelf ->
                Shelf(shelf = shelf, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun Shelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(shelf.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items) { item ->
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onItemClick(item) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val context = LocalContext.current
                        val imageRequest = remember(item.thumbnailUrl) {
                            ImageRequest.Builder(context)
                                .data(item.thumbnailUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val defaultMoods = listOf(
    MoodGenre("Chill", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Commute", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Energize", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Feel good", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Focus", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Gaming", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Party", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Romance", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Sad", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Sleep", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz"),
    MoodGenre("Workout", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX21vb2Rz")
)

private val defaultGenres = listOf(
    MoodGenre("African", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Arabic", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Bengali", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Bhojpuri", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Carnatic classical", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Classical", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Country & Americana", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Dance & electronic", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Decades", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Desi hip-hop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Devotional", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Family", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Folk & acoustic", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Ghazal/Sufi", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Gujarati", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Haryanvi", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Hindi", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Hindustani classical", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Hip-Hop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Indian Indie", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Indie pop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Indie & alternative", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("J-Pop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Jazz", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("K-Pop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Kannada", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Latin", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Malayalam", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Marathi", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Metal", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Monsoon", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Pop", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Punjabi", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("R&B & soul", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Reggae & caribbean", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Rock", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Tamil", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl"),
    MoodGenre("Telugu", "FEmusic_moods_and_genres_category", "ggMPOg1leHBsb3JlX2dlbnJl")
)
