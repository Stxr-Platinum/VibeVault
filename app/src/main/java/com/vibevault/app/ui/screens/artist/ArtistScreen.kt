package com.vibevault.app.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.music.innertube.models.AlbumItem
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.ArtworkWash
import com.vibevault.app.ui.components.FrostedTopBar
import com.vibevault.app.ui.components.NavigationTitle
import com.vibevault.app.ui.components.TopFadeBlur
import com.vibevault.app.ui.components.detailSkeleton
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
import kotlin.math.roundToInt

private const val ARTIST_PHOTO_RATIO = 0.95f
private val PAGE_GUTTER = 16.dp
private val HEADER_GUTTER = PAGE_GUTTER + 14.dp
private val HEADER_DROP = 44.dp
private val MERGE_BAND = 320.dp
private val MERGE_BLUR = 100.dp
private val DETAIL_TITLE_DROP = 320.dp
private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4

@Composable
fun ArtistScreen(
    artistName: String,
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val decodedArtistName = remember(artistName) {
        try {
            java.net.URLDecoder.decode(artistName, "UTF-8")
        } catch (e: Exception) {
            artistName
        }
    }

    LaunchedEffect(decodedArtistName) {
        viewModel.loadArtist(decodedArtistName)
    }

    val artistPage by viewModel.artistPage.collectAsStateWithLifecycle()
    val subscriberCountText by viewModel.subscriberCountText.collectAsStateWithLifecycle()
    val monthlyListenerCount by viewModel.monthlyListenerCount.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val latestAlbums by viewModel.latestAlbums.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    val displayArtistName = artistPage?.artist?.title?.ifBlank { decodedArtistName } ?: decodedArtistName
    // Single-fetch: only use artist profile photo, never fall back to album art of first song
    val thumbnail = remember(artistPage) {
        artistPage?.artist?.thumbnail?.ifBlank { null } ?: ""
    }

    val palette = rememberArtworkPalette(thumbnail, artPx = HEADER_ART_PX)
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    val photoHaze = remember { HazeState() }
    val contentHaze = remember { HazeState() }

    val displaySections = remember(artistPage) {
        val sections = artistPage?.sections.orEmpty().mapNotNull { section ->
            if (section.title.equals("Top songs", ignoreCase = true) ||
                section.title.equals("Songs", ignoreCase = true)
            ) return@mapNotNull null

            val validItems = section.items.filter { item ->
                item is AlbumItem || item is com.music.innertube.models.PlaylistItem || item is com.music.innertube.models.ArtistItem || item is com.music.innertube.models.SongItem
            }
            if (validItems.isEmpty()) return@mapNotNull null
            section.copy(items = validItems)
        }

        sections.sortedWith(
            compareBy { section ->
                val title = section.title.lowercase()
                when {
                    title.contains("album") -> 1
                    title.contains("single") || title.contains("ep") -> 2
                    title.contains("video") -> 3
                    title.contains("live") -> 4
                    title.contains("featured") || title.contains("appear") -> 5
                    title.contains("playlist") -> 6
                    title.contains("fan") || title.contains("like") || title.contains("similar") -> 7
                    else -> 2
                }
            }
        )
    }

    val detailTitleDropPx = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val scrolled by remember(lazyListState, detailTitleDropPx) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > detailTitleDropPx
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val artHeight = maxWidth / ARTIST_PHOTO_RATIO

        // Container captured by contentHaze for TopFadeBlur so LazyColumn rows blur rather than get masked
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contentHaze)
        ) {
            // ── Layer 1: Page Background (Photo + Wash) ──────────────────────────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .hazeSource(photoHaze)
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
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .background(palette.elevated)
                        )

                        // Shade under the glass bar
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

            // ── Layer 2: MergeBand Glass Blur across the bottom of the photo ─────
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
                        .hazeEffect(photoHaze) {
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

            // ── Layer 3: Lazy list content ───────────────────────────────────────
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
            // ── Large Artist Header ─────────────────────────────────────
            item(key = "header") {
                Box(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))
                    Text(
                        text = displayArtistName,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.W800,
                            fontSize = 34.sp,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = palette.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp)
                    )
                }
            }

            // ── Statistics Badges Row ───────────────────────────────────
            val subsText = subscriberCountText ?: artistPage?.subscriberCountText
            val listenersText = monthlyListenerCount ?: artistPage?.monthlyListenerCount

            if (!subsText.isNullOrBlank() || !listenersText.isNullOrBlank()) {
                item(key = "artist_stats") {
                    ArtistStatsRow(
                        subscriberCountText = subsText,
                        monthlyListenerCount = listenersText,
                        palette = palette
                    )
                }
            }

            if (isLoading && artistPage == null && topSongs.isEmpty()) {
                detailSkeleton(isArtist = true)
            } else {
                // ── Action Controls: Shuffle + Play ─────────────────────────
                if (topSongs.isNotEmpty()) {
                    item(key = "actions") {
                        ActionRow(
                            palette = palette,
                            onPlay = {
                                topSongs.firstOrNull()?.let { onTrackClick(it.id, topSongs) }
                            },
                            onShuffle = {
                                if (topSongs.isNotEmpty()) {
                                    val shuffled = topSongs.shuffled()
                                    shuffled.firstOrNull()?.let { onTrackClick(it.id, shuffled) }
                                }
                            },
                            bottomSpace = if (description.isNullOrBlank()) 22.dp else 11.dp
                        )
                    }
                }

                // ── About Section ───────────────────────────────────────────
                if (!description.isNullOrBlank()) {
                    item(key = "about") {
                        AboutSection(
                            title = "About the artist",
                            text = description!!,
                            palette = palette
                        )
                    }
                }

                // ── Top Songs Shelf (chunked in 4-track columns like BitChord)
                if (topSongs.isNotEmpty()) {
                    val top = topSongs.take(MAX_ARTIST_SONGS)
                    item(key = "top_songs_heading") {
                        Text(
                            text = "Top songs",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = palette.onBackground,
                            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 12.dp, bottom = 10.dp)
                        )
                    }

                    item(key = "top_songs_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                Column(Modifier.fillParentMaxWidth(0.88f)) {
                                    column.forEach { song ->
                                        CompactArtistSongRow(
                                            track = song,
                                            palette = palette,
                                            onClick = { onTrackClick(song.id, top) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Artist Sections (Albums, Singles, EPs, Videos, Live performances, etc.) ────────
                if (displaySections.isNotEmpty()) {
                    displaySections.forEach { section ->
                        val isVideoSection = section.items.any { it is com.music.innertube.models.SongItem } ||
                            section.title.contains("video", ignoreCase = true) ||
                            section.title.contains("live", ignoreCase = true)

                        item(key = "section_${section.title}") {
                            Column(Modifier.padding(top = 18.dp)) {
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                                    onClick = section.moreEndpoint?.let {
                                        { onPlaylistClick(it.browseId) }
                                    }
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isVideoSection) {
                                        val videoTracks = section.items.filterIsInstance<com.music.innertube.models.SongItem>().map { song ->
                                            Track(
                                                id = song.id,
                                                title = song.title,
                                                artist = song.artists.joinToString(", ") { it.name }.ifEmpty { artistPage?.artist?.title.orEmpty() },
                                                album = song.album?.name ?: "",
                                                albumImageUrl = song.thumbnail,
                                                durationMs = (song.duration ?: 0) * 1000L,
                                                isExplicit = song.explicit,
                                                isVideo = true
                                            )
                                        }

                                        items(section.items) { gridItem ->
                                            if (gridItem is com.music.innertube.models.SongItem) {
                                                val subtitleText = gridItem.artists.joinToString(", ") { it.name }
                                                    .ifEmpty { artistPage?.artist?.title.orEmpty() }

                                                Column(
                                                    modifier = Modifier
                                                        .width(196.dp)
                                                        .clickable {
                                                            onTrackClick(gridItem.id, videoTracks)
                                                        }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(16f / 9f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(palette.elevated),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        AsyncImage(
                                                            model = gridItem.thumbnail.artworkAt(480),
                                                            contentDescription = gridItem.title,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        // Overlay play button from vivi-music
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.55f))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.PlayArrow,
                                                                contentDescription = "Play",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        text = gridItem.title,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = palette.onBackground,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = subtitleText,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = palette.onBackgroundVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(section.items) { gridItem ->
                                            val itemTitle = when (gridItem) {
                                                is AlbumItem -> gridItem.title
                                                is com.music.innertube.models.PlaylistItem -> gridItem.title
                                                is com.music.innertube.models.ArtistItem -> gridItem.title
                                                else -> ""
                                            }
                                            val itemSubtitle = when (gridItem) {
                                                is AlbumItem -> gridItem.year?.toString() ?: "Album"
                                                is com.music.innertube.models.PlaylistItem -> "Playlist"
                                                is com.music.innertube.models.ArtistItem -> "Artist"
                                                else -> ""
                                            }
                                            val itemThumb = gridItem.thumbnail

                                            Column(
                                                modifier = Modifier
                                                    .width(134.dp)
                                                    .clickable {
                                                        when (gridItem) {
                                                            is AlbumItem -> onPlaylistClick(gridItem.id)
                                                            is com.music.innertube.models.PlaylistItem -> onPlaylistClick(gridItem.id)
                                                            is com.music.innertube.models.ArtistItem -> onArtistClick(gridItem.title)
                                                            else -> {}
                                                        }
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = itemThumb.artworkAt(480),
                                                    contentDescription = itemTitle,
                                                    modifier = Modifier
                                                        .size(134.dp)
                                                        .clip(if (gridItem is com.music.innertube.models.ArtistItem) CircleShape else RoundedCornerShape(12.dp))
                                                        .background(palette.elevated),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = itemTitle,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = palette.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = itemSubtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = palette.onBackgroundVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (latestAlbums.isNotEmpty()) {
                    item(key = "albums_title") {
                        NavigationTitle(
                            title = "Releases",
                            onClick = { }
                        )
                    }

                    item(key = "albums_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(latestAlbums) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(134.dp)
                                        .clickable { onPlaylistClick(album.id) }
                                ) {
                                    AsyncImage(
                                        model = album.coverUrl.artworkAt(480),
                                        contentDescription = album.title,
                                        modifier = Modifier
                                            .size(134.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(palette.elevated),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = palette.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.releaseDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.onBackgroundVariant,
                                        maxLines = 1
                                    )
                                }
                            }
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
            title = displayArtistName,
            scrolled = scrolled,
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/** Offset helper for background parallax/tracking relative to header scroll */
private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

/** Artist statistics chips: "1.2M subscribers" and "3.4M monthly listeners" (BitChord design) */
@Composable
private fun ArtistStatsRow(
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    palette: ArtworkPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        subscriberCountText?.let {
            val count = it.substringBefore(' ')
            StatChip(
                icon = Icons.Rounded.Person,
                text = "$count subscribers",
                palette = palette
            )
        }
        monthlyListenerCount?.let {
            val count = it.substringBefore(' ')
            StatChip(
                icon = Icons.Rounded.GraphicEq,
                text = "$count monthly listeners",
                palette = palette
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Action Controls: Circular Shuffle + Pill Play (BitChord design) */
@Composable
private fun ActionRow(
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: Dp = 22.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = bottomSpace),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = BitChordIcons.Shuffle,
            contentDescription = "Shuffle",
            palette = palette,
            onClick = onShuffle,
        )

        PlayPill(
            palette = palette,
            onClick = onPlay,
        )
    }
}

@Composable
private fun PlayPill(
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick)
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

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    size: Dp = 50.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Editorial About section (BitChord design) */
@Composable
private fun AboutSection(
    title: String,
    text: String,
    palette: ArtworkPalette,
) {
    var expanded by remember { mutableStateOf(false) }
    var clipped by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = palette.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 20.sp,
                fontSize = 14.sp
            ),
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m }
        )
        if (clipped || expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}

/** Explicit badge square [E] */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 10.sp
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

/** Compact song row used in Top songs horizontal column shelf (BitChord design) */
@Composable
private fun CompactArtistSongRow(
    track: Track,
    palette: ArtworkPalette,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumImageUrl.artworkAt(160),
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.elevated),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (track.isExplicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp
                ),
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp)
            )

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        val shareLink = "https://music.youtube.com/watch?v=${track.id}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Song Link", shareLink)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                )
            }
        }
    }
}
