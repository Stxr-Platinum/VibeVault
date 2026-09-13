package com.vibevault.app.ui.screens.album

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.R
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.ArtworkWash
import com.vibevault.app.ui.components.FrostedTopBar
import com.vibevault.app.ui.components.TopFadeBlur
import com.vibevault.app.ui.components.detailSkeleton
import com.vibevault.app.ui.components.topBarContentPadding
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics
import com.vibevault.app.ui.icons.BitChordIcons
import com.vibevault.app.ui.theme.ArtworkPalette
import com.vibevault.app.ui.theme.HEADER_ART_PX
import com.vibevault.app.ui.theme.artworkAt
import com.vibevault.app.ui.theme.rememberArtworkPalette
import com.vibevault.app.utils.rememberPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SLEEVE_RATIO = 0.92f
private val PAGE_GUTTER = 16.dp
private val HEADER_GUTTER = PAGE_GUTTER + 14.dp
private val HEADER_DROP = 44.dp
private val MERGE_BAND = 320.dp
private val MERGE_BLUR = 100.dp
private val DETAIL_TITLE_DROP = 320.dp
private val PILL_SHAPE = RoundedCornerShape(12.dp)
private val ROW_DIVIDER_INSET = PAGE_GUTTER + 52.dp + 14.dp // 82.dp
private const val SEARCH_ITEM_INDEX = 1

@Composable
fun AlbumScreen(
    albumId: String,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    onSwipeToQueue: ((Track) -> Unit)? = null,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val albumDetails by viewModel.albumDetails.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(albumId) {
        if (albumDetails?.id != albumId) {
            viewModel.loadAlbumDetails(albumId)
        }
    }

    val details = albumDetails
    val thumbnail = details?.albumImageUrl ?: details?.coverUrl ?: ""
    val palette = rememberArtworkPalette(thumbnail, artPx = HEADER_ART_PX)
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    val pageHaze = remember { HazeState() }
    val contentHaze = remember { HazeState() }

    val detailTitleDropPx = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val scrolled by remember(lazyListState, detailTitleDropPx) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > detailTitleDropPx
        }
    }

    var searching by rememberSaveable(albumId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(albumId) { mutableStateOf("") }
    var focusSearch by remember(albumId) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        searchQuery = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    LaunchedEffect(searching) {
        if (searching) lazyListState.animateScrollToItem(SEARCH_ITEM_INDEX, -searchStop)
    }

    var showShareMenu by remember { mutableStateOf(false) }
    val shareAlbum = {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/browse/$albumId")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Album"))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val artHeight = maxWidth / SLEEVE_RATIO

        // Container captured by contentHaze for TopFadeBlur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contentHaze)
        ) {
            // ── Layer 1: Page Background (Artwork + Wash) ────────────────────────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .hazeSource(pageHaze)
            ) {
                ArtworkWash(palette = palette, modifier = Modifier.matchParentSize())

                if (thumbnail.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(artHeight)
                            .offset {
                                IntOffset(0, lazyListState.headerTop(artHeight.toPx()).roundToInt())
                            }
                    ) {
                        AsyncImage(
                            model = thumbnail.artworkAt(HEADER_ART_PX),
                            contentDescription = details?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .background(palette.elevated)
                        )

                        // Shade under top bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.28f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(palette.background.copy(alpha = 0.55f), Color.Transparent)
                                    )
                                )
                        )

                        // Settles the foot of the picture onto the wash colour
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.55f to Color.Transparent,
                                        1.00f to palette.wash.copy(alpha = 0.88f)
                                    )
                                )
                        )
                    }
                }
            }

            // ── Layer 2: MergeBand (Glass Blur Across Picture Join) ───────────────
            if (!reduceDynamicBlur && thumbnail.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MERGE_BAND)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (
                                    lazyListState.headerTop(artHeight.toPx()) +
                                        artHeight.toPx() - MERGE_BAND.toPx() / 2f
                                    ).roundToInt()
                            )
                        }
                        .hazeEffect(pageHaze) {
                            canDrawArea = { true }
                            blurRadius = MERGE_BLUR
                            noiseFactor = 0f
                            tints = listOf(HazeTint(Color.Transparent))
                            backgroundColor = palette.wash
                            mask = Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.50f to Color.Black,
                                1.00f to Color.Transparent
                            )
                        }
                )
            }

            // ── Layer 3: LazyColumn Content ──────────────────────────────────────
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Header item
                item(key = "album_header") {
                    ReleaseHeader(
                        title = details?.title ?: "",
                        artist = details?.artist ?: "",
                        year = details?.year,
                        trackCount = details?.tracks?.size ?: 0,
                        artHeight = artHeight,
                        palette = palette,
                        onPlay = { viewModel.playAlbum(0) },
                        onShuffle = {
                            val tracks = details?.tracks ?: emptyList()
                            if (tracks.isNotEmpty()) {
                                viewModel.playAlbum(tracks.indices.random())
                            }
                        },
                        searching = searching,
                        onSearchToggle = {
                            if (searching) {
                                closeSearch()
                            } else {
                                searching = true
                                focusSearch = true
                            }
                        },
                        onArtistClick = {
                            val artistName = details?.artist ?: ""
                            if (onArtistClick != null) {
                                onArtistClick(artistName)
                            } else {
                                onAlbumClick(artistName)
                            }
                        },
                        onMore = { showShareMenu = true }
                    )
                }

                // In-place Search Field
                if (searching) {
                    item(key = "album_search_field") {
                        DetailSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = closeSearch,
                            autoFocus = focusSearch,
                            onFocused = { focusSearch = false },
                            palette = palette
                        )
                    }
                }

                // Editorial Blurb / Description
                if (!details?.description.isNullOrBlank()) {
                    item(key = "album_description") {
                        AboutAlbumSection(
                            text = details?.description ?: "",
                            palette = palette
                        )
                    }
                }

                // Track list or Skeleton
                if (isLoading && details == null) {
                    detailSkeleton(isArtist = false)
                } else if (details != null) {
                    val allTracks = details.tracks
                    val filtered = if (searchQuery.isBlank()) {
                        allTracks.mapIndexed { idx, tr -> idx to tr }
                    } else {
                        allTracks.mapIndexed { idx, tr -> idx to tr }.filter { (_, tr) ->
                            tr.title.contains(searchQuery, ignoreCase = true) ||
                                tr.artist.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filtered.isEmpty() && allTracks.isNotEmpty()) {
                        item(key = "no_match") {
                            Text(
                                text = "Nothing here matches “$searchQuery”",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.onBackgroundVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp)
                            )
                        }
                    } else {
                        itemsIndexed(filtered, key = { _, pair -> "track_${pair.second.id}_${pair.first}" }) { position, (originalIndex, track) ->
                            SongRow(
                                trackNumber = originalIndex + 1,
                                track = track,
                                palette = palette,
                                onClick = { onTrackClick(track) },
                                onSwipeToQueue = onSwipeToQueue?.let { queueAction -> { queueAction(track) } },
                                onShare = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                                }
                            )
                            if (position < filtered.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                    thickness = 0.5.dp,
                                    color = palette.divider,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Layer 4: TopFadeBlur ─────────────────────────────────────────────
        TopFadeBlur(
            hazeState = contentHaze,
            pageColor = palette.wash,
            scrimColor = palette.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // ── Layer 5: Frosted Top Bar ─────────────────────────────────────────
        FrostedTopBar(
            title = details?.title ?: "Album",
            scrolled = scrolled,
            onBack = onBackClick,
            actions = {
                Box {
                    IconButton(
                        onClick = { showShareMenu = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Album") },
                            onClick = {
                                showShareMenu = false
                                shareAlbum()
                            }
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * An album release header: title, credit, metadata, and action buttons
 * positioned over the foot of the artwork matching BitChord.
 */
@Composable
private fun ReleaseHeader(
    title: String,
    artist: String,
    year: String?,
    trackCount: Int,
    artHeight: Dp,
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    searching: Boolean,
    onSearchToggle: () -> Unit,
    onArtistClick: () -> Unit,
    onMore: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_GUTTER),
            )

            if (artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = HEADER_GUTTER)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onArtistClick() }
                )
            }

            val metaText = buildString {
                append("Album")
                if (!year.isNullOrBlank()) {
                    append(" • ").append(year)
                }
                if (trackCount > 0) {
                    append(" • ").append(trackCount).append(" songs")
                }
            }

            if (metaText.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = palette.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = HEADER_GUTTER),
                )
            }

            if (trackCount > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HEADER_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(
                        icon = BitChordIcons.Shuffle,
                        contentDescription = "Shuffle",
                        palette = palette,
                        onClick = onShuffle,
                        haptic = Haptic.Resume,
                        size = 50.dp,
                    )

                    PlayPill(
                        palette = palette,
                        onClick = onPlay,
                        horizontalPadding = 24.dp,
                    )

                    CircleIconButton(
                        icon = if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                        contentDescription = if (searching) "Close search" else "Search this album",
                        palette = palette,
                        onClick = onSearchToggle,
                        size = 50.dp,
                    )

                    CircleIconButton(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        palette = palette,
                        onClick = onMore,
                        size = 50.dp,
                    )
                }
            }
        }
    }
}

/**
 * Prominent pill-shaped Play button matching BitChord.
 */
@Composable
private fun PlayPill(
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Play",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Small circular icon button matching BitChord.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: Haptic? = Haptic.Tap,
    size: Dp = 50.dp,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptic?.let { haptics.play(it) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * In-place live search input field matching BitChord.
 */
@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(PILL_SHAPE)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), PILL_SHAPE)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search this album",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = if (query.isEmpty()) "Close search" else "Clear search",
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Editorial About Section matching BitChord.
 */
@Composable
private fun AboutAlbumSection(
    text: String,
    palette: ArtworkPalette,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER, vertical = 12.dp)
    ) {
        Text(
            text = "About the album",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable { expanded = !expanded }
        )
    }
}

/**
 * Numbered album track row matching BitChord:
 * 52dp box for track number, track title, artist, duration, more button, and swipe to queue.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    trackNumber: Int,
    track: Track,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onSwipeToQueue: (() -> Unit)? = null,
    onShare: () -> Unit,
) {
    val haptics = rememberHaptics()
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && onSwipeToQueue != null) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (e: Exception) { 0f }
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    onSwipeToQueue()
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    if (onSwipeToQueue != null) {
        LaunchedEffect(swipeState, boxWidth) {
            if (boxWidth <= 0f) return@LaunchedEffect
            val armAt = boxWidth * 0.45f
            var armed = false
            snapshotFlow { try { swipeState.requireOffset() } catch (e: Exception) { 0f } }
                .collect { offset ->
                    val travelled = abs(offset)
                    when {
                        !armed && travelled >= armAt -> {
                            armed = true
                            haptics.play(Haptic.Tick)
                        }
                        armed && travelled < armAt * 0.8f -> armed = false
                    }
                }
        }

        SwipeToDismissBox(
            state = swipeState,
            modifier = Modifier.onSizeChanged { boxWidth = it.width.toFloat() },
            backgroundContent = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val offset = try { swipeState.requireOffset() } catch (e: Exception) { 0f }
                            if (offset > 0f) {
                                clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            } else if (offset < 0f) {
                                clipRect(left = size.width + offset, top = 0f, right = size.width, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .padding(horizontal = PAGE_GUTTER + 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        ) {
            SongRowContent(
                trackNumber = trackNumber,
                track = track,
                palette = palette,
                onClick = onClick,
                onShare = onShare
            )
        }
    } else {
        SongRowContent(
            trackNumber = trackNumber,
            track = track,
            palette = palette,
            onClick = onClick,
            onShare = onShare
        )
    }
}

@Composable
private fun SongRowContent(
    trackNumber: Int,
    track: Track,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onShare: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 52dp box matching BitChord metrics
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$trackNumber",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onBackgroundVariant,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.onBackgroundVariant.copy(alpha = 0.20f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = palette.onBackgroundVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val durationText = formatTrackDuration(track.durationMs)
        if (durationText.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onBackgroundVariant,
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = palette.onBackgroundVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        onShare()
                    }
                )
            }
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f
