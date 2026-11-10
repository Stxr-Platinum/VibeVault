package com.vibevault.app.ui.replay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vibevault.app.R
import com.vibevault.app.data.stats.ReplaySummary
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Replay as a run of full-screen cards you tap through.
 */
@Composable
fun ReplayStories(
    summary: ReplaySummary,
    start: ReplayStoryPage,
    onClose: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
    paused: Boolean = false,
) {
    val pages = remember(summary) {
        ReplayStoryPage.ordered.filter { page ->
            when (page) {
                ReplayStoryPage.ALBUMS -> summary.albums.isNotEmpty()
                ReplayStoryPage.GENRES -> summary.genres.isNotEmpty()
                else -> true
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(start).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val current by remember { derivedStateOf { pagerState.currentPage } }

    fun goTo(target: Int, animate: Boolean) {
        val next = target.coerceIn(0, pages.lastIndex)
        scope.launch {
            progress.snapTo(0f)
            if (animate) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(next)
        }
    }

    fun step(forward: Boolean) =
        goTo(pagerState.settledPage + if (forward) 1 else -1, animate = false)

    LaunchedEffect(current) { progress.snapTo(0f) }
    LaunchedEffect(current, held, paused) {
        if (held || paused) return@LaunchedEffect
        if (current >= pages.lastIndex) return@LaunchedEffect
        val remaining = ((1f - progress.value) * PAGE_MILLIS).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        goTo(current + 1, animate = true)
    }

    val page = pages.getOrElse(current) { ReplayStoryPage.INTRO }
    val artwork = summary.storyArtwork(page)
    val palette = rememberArtworkColors(artwork).rotated(storyHue(page))
    val topAlbumArtworks = remember(summary) {
        summary.albums.take(STORY_ROWS)
            .mapNotNull { it.artworkUrl?.trim()?.takeIf { url -> url.isNotEmpty() } }
            .distinct()
    }
    val topAlbumPalettes = rememberMultiArtworkPalettes(topAlbumArtworks)

    val view = LocalView.current
    val activityWindow = remember(view) { view.context.findActivityWindow() }
    DisposableEffect(activityWindow) {
        val controller = activityWindow?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Stage(
            pages = pages,
            pagerState = pagerState,
            summary = summary,
            current = current,
            progress = progress,
            onHold = { held = it },
            onStep = ::step,
            onClose = onClose,
            onShare = onShare,
            palette = palette,
            topAlbumPalettes = topAlbumPalettes,
            artwork = artwork,
            page = page,
        )
    }
}

private tailrec fun Context.findActivityWindow(): android.view.Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}

@Composable
private fun Stage(
    pages: List<ReplayStoryPage>,
    pagerState: PagerState,
    summary: ReplaySummary,
    current: Int,
    progress: Animatable<Float, *>,
    onHold: (Boolean) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
    palette: MeshPalette,
    topAlbumPalettes: List<MeshPalette>,
    artwork: String?,
    page: ReplayStoryPage,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17171A))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    },
                    onLongPress = {},
                    onTap = { offset -> onStep(offset.x >= size.width * BACK_ZONE) },
                )
            },
    ) {
        Crossfade(
            targetState = current,
            animationSpec = tween(550),
            label = "storyBackground",
        ) { pageIndex ->
            when (pageIndex) {
                3 -> LiveMeshStoryBackground(artworkUrl = artwork, palette = palette)
                4 -> GlowingAnimationStoryBackground(
                    palettes = topAlbumPalettes,
                    fallbackPalette = palette,
                )
                else -> MeshGradientBackground(
                    palette = palette,
                    trackKey = pages.getOrNull(pageIndex)?.name ?: page.name,
                    animated = false,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.35f to Color.Black.copy(alpha = 0.10f),
                        1.0f to Color.Black.copy(alpha = 0.34f),
                    ),
                ),
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            StoryPage(page = pages[index], summary = summary, onShare = onShare)
        }

        StoryChrome(
            label = summary.label,
            count = pages.size,
            current = current,
            progress = progress.value,
            onClose = onClose,
        )
    }
}

@Composable
private fun StoryChrome(
    label: String,
    count: Int,
    current: Int,
    progress: Float,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Close Replay",
                tint = Color.White,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { index ->
                Segment(
                    fraction = when {
                        index < current -> 1f
                        index > current -> 0f
                        else -> progress
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (label.length == 4 && label.all { it.isDigit() }) {
                    "Replay'${label.takeLast(2)}"
                } else {
                    "Replay · $label"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "VibeVault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun Segment(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(2.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(Color.White),
        )
    }
}

@Composable
private fun StoryPage(
    page: ReplayStoryPage,
    summary: ReplaySummary,
    onShare: (ReplayStoryPage) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = CHROME_HEIGHT, bottom = 18.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                val headline = summary.storyHeadline(page)
                when (page) {
                    ReplayStoryPage.INTRO -> Intro(summary, headline)
                    ReplayStoryPage.MINUTES -> Minutes(summary, headline)
                    ReplayStoryPage.SONGS ->
                        Leaderboard(headline, summary.songRows(STORY_ROWS), circular = false)
                    ReplayStoryPage.ARTISTS ->
                        Leaderboard(headline, summary.artistRows(STORY_ROWS), circular = true)
                    ReplayStoryPage.ALBUMS ->
                        Leaderboard(headline, summary.albumRows(STORY_ROWS), circular = false)
                    ReplayStoryPage.GENRES -> Genres(summary, headline)
                    ReplayStoryPage.HABITS -> Habits(summary, headline)
                    ReplayStoryPage.SUMMARY -> Recap(summary, headline)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onShare(page) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = "Share my Replay",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Headline(parts: List<HeadlineRun>) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { run ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.W800 else FontWeight.W600,
                        color = if (run.bold) Color.White else Color.White.copy(alpha = 0.62f),
                    ),
                ) { append(run.text) }
            }
        },
        style = MaterialTheme.typography.displayLarge,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.Intro(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.weight(1f))
    ArtworkCollage(summary)
    Spacer(Modifier.weight(1f))
    Text(
        text = "Counted here on your phone. Nothing was sent anywhere to work it out.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.5f),
    )
}

@Composable
private fun ColumnScope.Minutes(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.weight(1f))
    ArtworkCollage(summary)
    Spacer(Modifier.weight(1f))
    Text(
        text = buildString {
            if (summary.hours >= 1) {
                append("That's ${grouped(summary.hours)} hours across ")
            } else {
                append("Across ")
            }
            append(countOf(summary.totalPlays, "play"))
            append(".")
            summary.peakHour?.let { append(" Mostly around ${formatHour(it)}.") }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.62f),
    )
}

@Composable
private fun ColumnScope.Leaderboard(
    headline: List<HeadlineRun>,
    rows: List<ReplayRow>,
    circular: Boolean,
) {
    val lead = rows.firstOrNull() ?: return
    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    Headline(headline)
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(lead.artworkUrl, lead.title, 116.dp, shape, HEADER_ART_PX, elevated = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = lead.title,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.W800,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            lead.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatListening(lead.ms)} · ${countOf(lead.plays, "play")}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.weight(1f))
    rows.drop(1).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(row.rank, AccentRed)
            Cover(row.artworkUrl, row.title, 36.dp, shape, ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatListening(row.ms),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ColumnScope.Genres(summary: ReplaySummary, headline: List<HeadlineRun>) {
    val rows = summary.genreRows(STORY_ROWS)
    val lead = rows.firstOrNull() ?: return
    Headline(headline)
    Spacer(Modifier.weight(1f))
    Text(
        text = lead.title,
        style = MaterialTheme.typography.displayLarge,
        fontSize = 60.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.W800,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = formatListening(lead.ms),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(22.dp))
    rows.drop(1).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(row.rank, AccentRed)
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatListening(row.ms),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.Habits(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.weight(1f))
    if (summary.distinctAlbums > 0) {
        BigStat(grouped(summary.distinctAlbums.toLong()), "different albums")
    }
    summary.busiestDay?.let {
        BigStat(formatDay(it), "your biggest day — ${formatListening(summary.busiestDayMs)}")
    }
    summary.peakHour?.let { BigStat(formatHour(it), "when you listen most") }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BigStat(value: String, label: String) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.58f),
        )
    }
}

@Composable
private fun ColumnScope.Recap(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.height(20.dp))
    RecapLine("Minutes", formatMinutes(summary.totalMs))
    summary.songs.firstOrNull()?.let { RecapLine("Top song", it.song.title) }
    summary.artists.firstOrNull()?.let { RecapLine("Top artist", it.title) }
    summary.albums.firstOrNull()?.let { RecapLine("Top album", it.title) }
    summary.genres.firstOrNull()?.let { RecapLine("Top genre", it.title) }
    Spacer(Modifier.weight(1f))
    Text(
        text = "Tap share to turn all of this into one picture.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun RecapLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Cover(
    url: String?,
    fallbackText: String,
    size: Dp,
    shape: Shape,
    px: Int,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    bordered: Boolean = false,
) {
    val base = modifier
        .size(size)
        .let { if (elevated) it.shadow(16.dp, shape, clip = false) else it }
        .clip(shape)
        .let { if (bordered) it.border(1.dp, Color.White.copy(alpha = 0.16f), shape) else it }
    when {
        url != null -> AsyncImage(
            model = url.artworkAt(px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
        fallbackText.isNotBlank() -> Box(base) { InitialTile(fallbackText, size, shape) }
        else -> Box(base.background(Color.White.copy(alpha = 0.10f)))
    }
}

private data class CollageSlot(
    val cx: Float,
    val cy: Float,
    val size: Dp,
    val angle: Float = 0f,
)

@Composable
private fun ArtworkCollage(summary: ReplaySummary, modifier: Modifier = Modifier) {
    val covers = remember(summary) {
        (summary.albums.mapNotNull { it.artworkUrl?.trim()?.takeIf { url -> url.isNotEmpty() } } +
            summary.songs.map { it.song.albumImageUrl.trim() }.filter { it.isNotEmpty() })
            .distinct()
            .take(7)
    }
    val faces = remember(summary) {
        val allArtists = summary.artists
            .mapNotNull { it.artworkUrl?.trim()?.takeIf { url -> url.isNotEmpty() } }
            .distinct()
        allArtists.filterNot { it in covers }.ifEmpty { allArtists }.take(6)
    }
    if (covers.isEmpty() && faces.isEmpty()) return

    val squareSlots = remember {
        listOf(
            CollageSlot(cx = 0.49f, cy = 0.48f, size = 124.dp, angle = -3f), // Hero album/track center
            CollageSlot(cx = 0.18f, cy = 0.17f, size = 80.dp, angle = -8f),
            CollageSlot(cx = 0.52f, cy = 0.14f, size = 64.dp, angle = 4f),
            CollageSlot(cx = 0.83f, cy = 0.18f, size = 82.dp, angle = 7f),
            CollageSlot(cx = 0.13f, cy = 0.52f, size = 66.dp, angle = 6f),
            CollageSlot(cx = 0.21f, cy = 0.84f, size = 82.dp, angle = -5f),
            CollageSlot(cx = 0.81f, cy = 0.81f, size = 86.dp, angle = 5f),
        )
    }
    val circleSlots = remember {
        listOf(
            CollageSlot(cx = 0.33f, cy = 0.31f, size = 56.dp),
            CollageSlot(cx = 0.69f, cy = 0.31f, size = 58.dp),
            CollageSlot(cx = 0.87f, cy = 0.52f, size = 66.dp),
            CollageSlot(cx = 0.35f, cy = 0.70f, size = 54.dp),
            CollageSlot(cx = 0.54f, cy = 0.86f, size = 64.dp),
            CollageSlot(cx = 0.68f, cy = 0.65f, size = 56.dp),
        )
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(340.dp)) {
        val w = maxWidth
        val h = maxHeight

        // Render surrounding album/track tiles first so the center hero tile stays unobstructed
        for (index in 1 until covers.size.coerceAtMost(squareSlots.size)) {
            val slot = squareSlots[index]
            val shape = RoundedCornerShape(8.dp)
            Cover(
                url = covers[index],
                fallbackText = "",
                size = slot.size,
                shape = shape,
                px = CARD_ART_PX,
                modifier = Modifier
                    .offset(x = w * slot.cx - slot.size / 2, y = h * slot.cy - slot.size / 2)
                    .rotate(slot.angle),
                elevated = true,
                bordered = true,
            )
        }

        // Artist portrait circles nested into balanced pockets
        faces.forEachIndexed { index, url ->
            val slot = circleSlots[index]
            Cover(
                url = url,
                fallbackText = "",
                size = slot.size,
                shape = CircleShape,
                px = CARD_ART_PX,
                modifier = Modifier
                    .offset(x = w * slot.cx - slot.size / 2, y = h * slot.cy - slot.size / 2),
                elevated = true,
                bordered = true,
            )
        }

        // Focal hero artwork anchored on top
        covers.firstOrNull()?.let { heroUrl ->
            val hero = squareSlots[0]
            Cover(
                url = heroUrl,
                fallbackText = "",
                size = hero.size,
                shape = RoundedCornerShape(10.dp),
                px = CARD_ART_PX,
                modifier = Modifier
                    .offset(x = w * hero.cx - hero.size / 2, y = h * hero.cy - hero.size / 2)
                    .rotate(hero.angle),
                elevated = true,
                bordered = true,
            )
        }
    }
}

@Composable
private fun LiveMeshStoryBackground(artworkUrl: String?, palette: MeshPalette) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "storyLiveMesh")
    val anchorRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(80_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "storyAnchorRotation",
    )
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "storyFastRotation",
    )
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "storySlowRotation",
    )

    Box(Modifier.fillMaxSize()) {
        // Animated mesh base (also acts as clean fallback on Android < 12 or missing artwork)
        MeshGradientBackground(
            palette = palette,
            trackKey = artworkUrl ?: "live_mesh",
            continuous = true,
            blurRadius = 64.dp,
            animated = true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !artworkUrl.isNullOrBlank()) {
            val saturationFilter = remember {
                val matrix = ColorMatrix().apply { setToSaturation(1.8f) }
                ColorFilter.colorMatrix(matrix)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.7f
                        scaleY = 1.7f
                    },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUrl.artworkAt(HEADER_ART_PX))
                        .size(128, 128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturationFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .graphicsLayer { rotationZ = anchorRotation },
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUrl.artworkAt(HEADER_ART_PX))
                        .size(128, 128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturationFilter,
                    alignment = Alignment.TopStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = fastRotation
                            alpha = 0.6f
                        },
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUrl.artworkAt(HEADER_ART_PX))
                        .size(128, 128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturationFilter,
                    alignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = slowRotation
                            alpha = 0.5f
                        },
                )
            }
        }
    }
}

@Composable
private fun GlowingAnimationStoryBackground(
    palettes: List<MeshPalette>,
    fallbackPalette: MeshPalette,
) {
    val defaultColors = listOf(
        Color(0xFF3A1C71),
        Color(0xFFD76D77),
        Color(0xFF2B5876),
        Color(0xFFFFAF7B),
    )

    fun expandSix(colors: List<Color>): List<Color> {
        val base = colors.ifEmpty { defaultColors }
        return List(6) { idx -> base[idx % base.size] }
    }

    val albumSwatches = remember(palettes, fallbackPalette) {
        val source = palettes.ifEmpty { listOf(fallbackPalette) }
        source.map { expandSix(it.colors) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "storyGlowAnimation")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "storyGlowProgress",
    )
    // Smoothly cycles through Top 1 -> Top 5 albums across the 6s story window
    val albumTransitionProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PAGE_MILLIS.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "storyGlowAlbumTransition",
    )

    val activePalette: List<Color> = if (albumSwatches.size == 1) {
        albumSwatches[0]
    } else {
        val count = albumSwatches.size
        val scaled = albumTransitionProgress * count
        val fromIdx = floor(scaled).toInt() % count
        val toIdx = (fromIdx + 1) % count
        val blend = scaled - floor(scaled)
        val fromColors = albumSwatches[fromIdx]
        val toColors = albumSwatches[toIdx]
        List(6) { idx -> lerp(fromColors[idx], toColors[idx], blend) }
    }

    fun rotatedColorAt(index: Int): Color {
        val count = activePalette.size
        val idx = index.toFloat() + progress * count
        val a = floor(idx).toInt() % count
        val b = (a + 1) % count
        val frac = idx - floor(idx)
        return lerp(activePalette[a], activePalette[b], frac)
    }

    fun oscillate(min: Float, max: Float, phase: Float): Float {
        val v = sin(2f * PI.toFloat() * (progress + phase))
        return min + (max - min) * ((v + 1f) * 0.5f)
    }

    val color1 = rotatedColorAt(0)
    val color2 = rotatedColorAt(1)
    val color3 = rotatedColorAt(2)
    val color4 = rotatedColorAt(3)
    val color5 = rotatedColorAt(4)
    val color6 = rotatedColorAt(5)

    val o1x = oscillate(0.0f, 1.0f, 0.00f)
    val o1y = oscillate(0.0f, 0.5f, 0.07f)
    val r1 = oscillate(0.8f, 1.6f, 0.12f)

    val o2x = oscillate(1.0f, 0.0f, 0.20f)
    val o2y = oscillate(0.5f, 1.0f, 0.25f)
    val r2 = oscillate(0.7f, 1.5f, 0.18f)

    val o3x = oscillate(0.2f, 0.8f, 0.33f)
    val o3y = oscillate(0.8f, 0.2f, 0.36f)
    val r3 = oscillate(0.6f, 1.4f, 0.29f)

    val o4x = oscillate(0.3f, 0.7f, 0.44f)
    val o4y = oscillate(0.2f, 0.8f, 0.41f)
    val r4 = oscillate(0.9f, 1.7f, 0.47f)

    val o5x = oscillate(0.4f, 0.6f, 0.55f)
    val o5y = oscillate(0.0f, 1.0f, 0.51f)
    val r5 = oscillate(0.7f, 1.5f, 0.58f)

    val o6x = oscillate(0.0f, 1.0f, 0.66f)
    val o6y = oscillate(0.5f, 0.7f, 0.62f)
    val r6 = oscillate(0.8f, 1.8f, 0.69f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val width = size.width
                val height = size.height
                val baseColor = Color(0xFF050505)

                val brush1 = Brush.radialGradient(
                    colors = listOf(color1.copy(alpha = 0.85f), color1.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(width * o1x, height * o1y),
                    radius = width * r1,
                )
                val brush2 = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = 0.80f), color2.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(width * o2x, height * o2y),
                    radius = width * r2,
                )
                val brush3 = Brush.radialGradient(
                    colors = listOf(color3.copy(alpha = 0.75f), color3.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(width * o3x, height * o3y),
                    radius = width * r3,
                )
                val brush4 = Brush.radialGradient(
                    colors = listOf(color4.copy(alpha = 0.70f), color4.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(width * o4x, height * o4y),
                    radius = width * r4,
                )
                val brush5 = Brush.radialGradient(
                    colors = listOf(color5.copy(alpha = 0.65f), color5.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(width * o5x, height * o5y),
                    radius = width * r5,
                )
                val brush6 = Brush.radialGradient(
                    colors = listOf(color6.copy(alpha = 0.60f), color6.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(width * o6x, height * o6y),
                    radius = width * r6,
                )

                onDrawBehind {
                    drawRect(color = baseColor)
                    drawRect(brush = brush1)
                    drawRect(brush = brush2)
                    drawRect(brush = brush3)
                    drawRect(brush = brush4)
                    drawRect(brush = brush5)
                    drawRect(brush = brush6)
                }
            },
    )
}

private fun MeshPalette.rotated(degrees: Float): MeshPalette {
    if (degrees == 0f) return this
    return MeshPalette(
        colors.map { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            hsl[0] = (hsl[0] + degrees) % 360f
            Color(ColorUtils.HSLToColor(hsl))
        },
    )
}

private val CHROME_HEIGHT = 96.dp
private const val STORY_ROWS = 5
private const val STORY_ASPECT = 9f / 16f
private const val PAGE_MILLIS = 6_000f
private const val BACK_ZONE = 0.32f
