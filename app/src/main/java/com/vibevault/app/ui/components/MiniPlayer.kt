package com.vibevault.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vibevault.app.ui.theme.*
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.constants.SwipeSensitivityKey
import com.vibevault.app.utils.rememberPreference
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset

import com.vibevault.app.constants.PlayerBackgroundStyle

/**
 * MiniPlayer â€” Persistent playback bar docked above the bottom nav.
 * Redesigned to be a floating "pill" with swipe-to-skip gestures.
 */
@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val position by playerViewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by playerViewModel.duration.collectAsStateWithLifecycle()
    val (dynamicBackground) = rememberPreference(DynamicBackgroundKey, defaultValue = true)
    val (swipeSensitivity) = rememberPreference(SwipeSensitivityKey, 0.73f)
    
    val (miniPlayerBackground) = rememberEnumPreference(
        com.vibevault.app.constants.MiniPlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    
    val fallbackColor = com.vibevault.app.ui.theme.LocalSolidColorScheme.current.surfaceContainer.toArgb()
    val (gradientColors, onGradientColorsChange) = remember { mutableStateOf<List<Color>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(currentTrack?.id) {
        val track = currentTrack
        if (track?.albumImageUrl != null) {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(track.albumImageUrl)
                    .size(100)
                    .allowHardware(false)
                    .build()

                val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                if (result != null) {
                    val drawable = (result as? SuccessResult)?.drawable
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = withContext(Dispatchers.Default) {
                            androidx.palette.graphics.Palette.from(bitmap)
                                .maximumColorCount(8)
                                .resizeBitmapArea(100 * 100)
                                .generate()
                        }
                        val extractedColors = if (miniPlayerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
                            listOfNotNull(
                                palette.getVibrantColor(fallbackColor).let { Color(it) },
                                palette.getLightVibrantColor(fallbackColor).let { Color(it) },
                                palette.getDarkVibrantColor(fallbackColor).let { Color(it) },
                                palette.getMutedColor(fallbackColor).let { Color(it) },
                                palette.getLightMutedColor(fallbackColor).let { Color(it) },
                                palette.getDarkMutedColor(fallbackColor).let { Color(it) }
                            ).distinct()
                        } else {
                            PlayerColorExtractor.extractGradientColors(
                                palette = palette,
                                fallbackColor = fallbackColor
                            )
                        }
                        withContext(Dispatchers.Main) { onGradientColorsChange(extractedColors) }
                    }
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetXAnimatable = remember { Animatable(0f) }
    val offsetYAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    val autoSwipeThreshold = remember(swipeSensitivity) {
        // Linearly map sensitivity 0.0f -> 1.0f to threshold 500px -> 150px
        (500f - (350f * swipeSensitivity)).roundToInt()
    }

    val animationSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        currentTrack?.let { track ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStartTime = System.currentTimeMillis()
                                totalDragDistance = 0f
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(0f, animationSpec)
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val tryingToSwipeRight = dragAmount > 0
                                val tryingToSwipeLeft = dragAmount < 0
                                val canSkipPrevious = true
                                val canSkipNext = true
                                val allowLeft = tryingToSwipeLeft && canSkipNext
                                val allowRight = tryingToSwipeRight && canSkipPrevious

                                val canReturnToCenter =
                                    (tryingToSwipeRight && !canSkipPrevious && offsetXAnimatable.value < 0) ||
                                            (tryingToSwipeLeft && !canSkipNext && offsetXAnimatable.value > 0)

                                if (allowLeft || allowRight || canReturnToCenter) {
                                    totalDragDistance += kotlin.math.abs(dragAmount)
                                    coroutineScope.launch {
                                        offsetXAnimatable.snapTo(offsetXAnimatable.value + dragAmount)
                                    }
                                }
                            },
                            onDragEnd = {
                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                                val currentOffset = offsetXAnimatable.value
                                val minDistanceThreshold = 40f
                                val velocityThreshold = 0.5f - (0.4f * swipeSensitivity)

                                val shouldChangeSong = (kotlin.math.abs(currentOffset) > minDistanceThreshold && velocity > velocityThreshold) ||
                                    (kotlin.math.abs(currentOffset) > autoSwipeThreshold)

                                if (shouldChangeSong) {
                                    if (currentOffset > 0) {
                                        playerViewModel.skipPrevious()
                                    } else if (currentOffset <= 0) {
                                        playerViewModel.skipNext()
                                    }
                                }
                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(0f, animationSpec)
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var totalDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalDragY = 0f
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetYAnimatable.animateTo(0f, animationSpec)
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDragY += dragAmount
                                // Restrict upwards drag to -40f (slight bounce), let downwards drag be free
                                val newOffset = (offsetYAnimatable.value + dragAmount).coerceAtLeast(-40f)
                                coroutineScope.launch {
                                    offsetYAnimatable.snapTo(newOffset)
                                }
                            },
                            onDragEnd = {
                                if (totalDragY < -40f) {
                                    onExpand(track.id)
                                    coroutineScope.launch {
                                        offsetYAnimatable.snapTo(0f)
                                    }
                                } else if (totalDragY > 100f) {
                                    playerViewModel.stopPlayback()
                                    coroutineScope.launch {
                                        offsetYAnimatable.snapTo(0f)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        offsetYAnimatable.animateTo(0f, animationSpec)
                                    }
                                }
                            }
                        )
                    }
            ) {
                // The main Pill
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetXAnimatable.value.roundToInt(), offsetYAnimatable.value.roundToInt()) }
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(com.vibevault.app.ui.theme.LocalSolidColorScheme.current.surfaceContainer)
                        .clickable { onExpand(track.id) }
                ) {
                    MiniPlayerBackgroundLayer(
                        style = miniPlayerBackground,
                        track = track,
                        gradientColors = gradientColors
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album art thumbnail with progress ring
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = track.albumImageUrl,
                            contentDescription = track.album,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        // Progress ring overlay
                        if (duration > 0) {
                            CircularProgressIndicator(
                                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Track info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Play/Pause button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            .clickable { playerViewModel.togglePlayPause() }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Like button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            .clickable { playerViewModel.toggleLike() }
                    ) {
                        Icon(
                            imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (track.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun MiniPlayerBackgroundLayer(
    style: PlayerBackgroundStyle,
    track: com.vibevault.app.domain.model.Track?,
    gradientColors: List<Color>
) {
    val context = LocalContext.current
    
    when (style) {
        PlayerBackgroundStyle.BLUR -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track?.albumImageUrl)
                        .size(128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }
        PlayerBackgroundStyle.GRADIENT -> {
            if (gradientColors.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors))
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
        }
        PlayerBackgroundStyle.GLOW_ANIMATED -> {
            if (gradientColors.isNotEmpty()) {
                val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")
                val progress = infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(20000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "glowProgress"
                )

                val colors = gradientColors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val p = progress.value
                            val width = size.width
                            val height = size.height
                            
                            fun rotatedColorAt(index: Int): Color {
                                val size = colors.size
                                val idx = index.toFloat() + p * size
                                val a = kotlin.math.floor(idx.toDouble()).toInt() % size
                                val b = (a + 1) % size
                                val frac = idx - kotlin.math.floor(idx.toDouble()).toFloat()
                                return lerp(colors[a], colors[b], frac)
                            }

                            fun oscillate(min: Float, max: Float, phase: Float): Float {
                                val v = kotlin.math.sin(2.0 * Math.PI * (p + phase).toDouble()).toFloat()
                                return min + (max - min) * ((v + 1f) * 0.5f)
                            }

                            val c1 = rotatedColorAt(0)
                            val c2 = rotatedColorAt(1)

                            val o1x = oscillate(0.0f, 1.0f, 0.0f)
                            val o1y = oscillate(0.0f, 0.5f, 0.1f)
                            val o2x = oscillate(1.0f, 0.0f, 0.2f)
                            val o2y = oscillate(0.5f, 1.0f, 0.3f)

                            val b1 = Brush.radialGradient(
                                colors = listOf(c1.copy(alpha = 0.8f), Color.Transparent),
                                center = Offset(width * o1x, height * o1y),
                                radius = width * 1.2f
                            )
                            val b2 = Brush.radialGradient(
                                colors = listOf(c2.copy(alpha = 0.7f), Color.Transparent),
                                center = Offset(width * o2x, height * o2y),
                                radius = width * 1.0f
                            )
                            
                            drawRect(Color(0xFF050505))
                            drawRect(b1)
                            drawRect(b2)
                        }
                )
            }
        }
        PlayerBackgroundStyle.LIVE_MESH -> {
            val infiniteTransition = rememberInfiniteTransition(label = "liveMesh")
            val rotation = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(60000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                    }
            ) {
                val matrix = remember { ColorMatrix().apply { setToSaturation(1.6f) } }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track?.albumImageUrl)
                        .size(128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(matrix),
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .graphicsLayer { rotationZ = rotation.value }
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
        }
        else -> {}
    }
}

