package com.vibevault.app.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vibevault.app.constants.*
import com.vibevault.app.ui.components.MiniPlayerBackgroundLayer
import com.vibevault.app.utils.rememberPreference
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.ui.theme.PlayerColorExtractor
import com.vibevault.app.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    trackId: String?,
    onBackClick: () -> Unit,
    onListenTogetherClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showInlineLyrics by remember { mutableStateOf(false) }
    var localIsLiked by remember(currentTrack?.id, currentTrack?.isLiked) {
        mutableStateOf(currentTrack?.isLiked ?: false)
    }

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val lyricsList by viewModel.lyricsList.collectAsStateWithLifecycle()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsStateWithLifecycle()
    val lyricsOffset by viewModel.lyricsOffset.collectAsStateWithLifecycle()

    // Preferences
    val (useNewPlayerDesign) = rememberPreference(UseNewPlayerDesignKey, defaultValue = false)
    val (sliderStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val (playerBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.GRADIENT)

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
                        val extractedColors = if (playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
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

    if (showAddToPlaylistDialog && currentTrack != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add to Playlist", color = Color.White) },
            text = {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        Text(
                            text = playlist.title,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addTrackToPlaylist(playlist.id, currentTrack!!.id)
                                    showAddToPlaylistDialog = false
                                }
                                .padding(16.dp)
                        )
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

    LaunchedEffect(trackId) {
        if (trackId != null && currentTrack?.id != trackId) {
            viewModel.playTrack(trackId)
        }
    }

    if (currentTrack == null) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
        return
    }

    val track = currentTrack!!

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -30f) {
                            showQueueSheet = true
                        }
                    }
                }
        ) {
            FullPlayerBackgroundLayer(
                playerBackground = playerBackground,
                track = track,
                gradientColors = gradientColors,
                showInlineLyrics = showInlineLyrics
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        var totalDragDistance = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDragDistance = 0f },
                            onDragCancel = { totalDragDistance = 0f },
                            onVerticalDrag = { change, dragAmount -> 
                                totalDragDistance += dragAmount 
                            },
                            onDragEnd = {
                                if (totalDragDistance > 100f) {
                                    onBackClick()
                                }
                            }
                        )
                    }
            ) {
                // Top Bar
                if (!showInlineLyrics) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.KeyboardArrowDown, "Close", tint = Color.White)
                        }
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Row {
                            if (!useNewPlayerDesign) {
                                IconButton(onClick = onListenTogetherClick) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.group),
                                        contentDescription = "Listen Together",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = { showPlaylistMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                val primaryGradientColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
                val dynamicActiveLyricColor = remember(primaryGradientColor) {
                    val argb = primaryGradientColor.toArgb()
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(argb, hsv)
                    val textHsv = floatArrayOf(
                        hsv[0],
                        (hsv[1] * 0.55f).coerceIn(0.25f, 0.5f),
                        0.96f
                    )
                    Color(android.graphics.Color.HSVToColor(textHsv))
                }
                val dynamicInactiveLyricColor = remember(primaryGradientColor, dynamicActiveLyricColor) {
                    dynamicActiveLyricColor.copy(alpha = 0.35f)
                }
                val dynamicButtonContainerColor = remember(primaryGradientColor) {
                    val argb = primaryGradientColor.toArgb()
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(argb, hsv)
                    val bgHsv = floatArrayOf(
                        hsv[0],
                        (hsv[1] * 0.45f).coerceIn(0.18f, 0.55f),
                        (hsv[2] * 0.4f).coerceIn(0.15f, 0.35f)
                    )
                    Color(android.graphics.Color.HSVToColor(bgHsv))
                }

                if (showInlineLyrics || playerBackground == PlayerBackgroundStyle.APPLE_MUSIC) {
                    if (showInlineLyrics) {
                        // Borderless Vivi-Music Inline Lyrics View
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            InlineLyricsView(
                                track = track,
                                positionMs = position,
                                onSeekTo = { viewModel.seekTo(it) },
                                lyricsEntries = lyricsList,
                                isLoading = isLoadingLyrics,
                                lyricsOffset = lyricsOffset,
                                onOffsetChange = { viewModel.setLyricsOffset(it) },
                                activeLyricColor = dynamicActiveLyricColor,
                                inactiveLyricColor = dynamicInactiveLyricColor
                            )
                        }
                    } else {
                        // Apple Music background mode without lyrics: empty spacer taking full remaining height to shift bar to bottom
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Compact Mini Track Info Bar (Image 2 Vivi-Music design)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.albumImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showInlineLyrics = !showInlineLyrics },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val shareShape = RoundedCornerShape(
                            topStart = 50.dp, bottomStart = 50.dp,
                            topEnd = 3.dp, bottomEnd = 3.dp
                        )
                        val favShape = RoundedCornerShape(
                            topStart = 3.dp, bottomStart = 3.dp,
                            topEnd = 50.dp, bottomEnd = 50.dp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (showInlineLyrics) {
                                FilledIconButton(
                                    onClick = { showInlineLyrics = false },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                ) {
                                    Icon(Icons.Default.CropFree, "Collapse Lyrics", modifier = Modifier.size(20.dp))
                                }
                                FilledIconButton(
                                    onClick = { showPlaylistMenu = true },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                ) {
                                    Icon(Icons.Default.MoreHoriz, "More Options", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                FilledIconButton(
                                    onClick = { showAddToPlaylistDialog = true },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Add to playlist", modifier = Modifier.size(20.dp))
                                }
                                 FilledIconButton(
                                    onClick = { 
                                        localIsLiked = !localIsLiked
                                        viewModel.toggleLike() 
                                    },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                ) {
                                    Icon(
                                        if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        "Like",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (localIsLiked) MaterialTheme.colorScheme.primary else Color.Black
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Standard Artwork View
                    AsyncImage(
                        model = track.albumImageUrl,
                        contentDescription = track.album,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.height(24.dp))

                    // Title and Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        if (!useNewPlayerDesign) {
                            IconButton(onClick = { 
                                localIsLiked = !localIsLiked
                                viewModel.toggleLike() 
                            }) {
                                Icon(
                                    if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (localIsLiked) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            var isAddAnimating by remember { mutableStateOf(false) }
                            val addScale by animateFloatAsState(
                                targetValue = if (isAddAnimating) 1.2f else 1f,
                                finishedListener = { if (isAddAnimating) isAddAnimating = false },
                                label = "addScale"
                            )

                            Box {
                                IconButton(
                                    onClick = { 
                                        isAddAnimating = true
                                        showAddToPlaylistDialog = true 
                                    },
                                    modifier = Modifier.scale(addScale)
                                ) {
                                    Icon(Icons.Default.Add, "Add to playlist", tint = Color.White)
                                }
                            }
                        } else {
                            val shareShape = RoundedCornerShape(
                                topStart = 50.dp, bottomStart = 50.dp,
                                topEnd = 3.dp, bottomEnd = 3.dp
                            )

                            val favShape = RoundedCornerShape(
                                topStart = 3.dp, bottomStart = 3.dp,
                                topEnd = 50.dp, bottomEnd = 50.dp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val buttonColor = dynamicButtonContainerColor
                                val iconColor = Color.White
                                
                                FilledIconButton(
                                    onClick = { showAddToPlaylistDialog = true },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = buttonColor,
                                        contentColor = iconColor,
                                    ),
                                    modifier = Modifier.size(width = 50.dp, height = 42.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add to playlist", modifier = Modifier.size(24.dp))
                                }
                                FilledIconButton(
                                    onClick = { 
                                        localIsLiked = !localIsLiked
                                        viewModel.toggleLike() 
                                    },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = buttonColor,
                                        contentColor = iconColor,
                                    ),
                                    modifier = Modifier.size(width = 50.dp, height = 42.dp),
                                ) {
                                    Icon(
                                        if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                        contentDescription = "Like", 
                                        modifier = Modifier.size(24.dp),
                                        tint = if (localIsLiked) MaterialTheme.colorScheme.primary else iconColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Progress Bar
                var isDragging by remember { mutableStateOf(false) }
                var dragPosition by remember { mutableStateOf(0f) }
                
                // Use duration from viewModel or fallback to track.durationMs
                val safeDuration = (if (duration > 0) duration else track.durationMs).toFloat().coerceAtLeast(1f)
                val displayPosition = if (isDragging) dragPosition else position.toFloat()

                val activeSliderColor = if (useNewPlayerDesign) Color.White else Color.White.copy(alpha = 0.7f)
                val inactiveSliderColor = if (useNewPlayerDesign) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f)

                when (sliderStyle) {
                    SliderStyle.DEFAULT -> {
                        Slider(
                            value = displayPosition.coerceIn(0f, safeDuration),
                            onValueChange = { 
                                isDragging = true
                                dragPosition = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                viewModel.seekTo(dragPosition.toLong())
                            },
                            valueRange = 0f..safeDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = activeSliderColor,
                                activeTrackColor = activeSliderColor,
                                inactiveTrackColor = inactiveSliderColor
                            )
                        )
                    }
                    SliderStyle.SLIM -> {
                        Slider(
                            value = displayPosition.coerceIn(0f, safeDuration),
                            onValueChange = { 
                                isDragging = true
                                dragPosition = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                viewModel.seekTo(dragPosition.toLong())
                            },
                            valueRange = 0f..safeDuration,
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            colors = SliderDefaults.colors(
                                thumbColor = activeSliderColor,
                                activeTrackColor = activeSliderColor,
                                inactiveTrackColor = inactiveSliderColor
                            )
                        )
                    }
                    SliderStyle.WAVY -> {
                        com.vibevault.app.ui.components.WavySlider(
                            value = displayPosition.coerceIn(0f, safeDuration),
                            onValueChange = { 
                                isDragging = true
                                dragPosition = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                viewModel.seekTo(dragPosition.toLong())
                            },
                            valueRange = 0f..safeDuration,
                            isPlaying = isPlaying,
                            colors = com.vibevault.app.ui.theme.PlayerSliderColors.getSliderColors(
                                activeColor = activeSliderColor,
                                playerBackground = playerBackground,
                                useDarkTheme = true
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(displayPosition.toLong()), style = MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(safeDuration.toLong()), style = MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(20.dp))

                // Playback Controls
                if (useNewPlayerDesign) {
                    val playPauseShape = RoundedCornerShape(50)
                    val sideShape = RoundedCornerShape(50)
                    val playPauseColor = Color.White
                    val playPauseIconColor = Color.Black
                    val sideButtonColor = dynamicButtonContainerColor
                    val sideIconColor = Color.White

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { viewModel.skipPrevious() },
                            shape = sideShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = sideButtonColor,
                                contentColor = sideIconColor,
                            ),
                            modifier = Modifier
                                .height(68.dp)
                                .weight(0.45f)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            shape = playPauseShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = playPauseColor,
                                contentColor = playPauseIconColor,
                            ),
                            modifier = Modifier
                                .height(68.dp)
                                .weight(1.3f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPlaying) "Pause" else "Play",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = playPauseIconColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        FilledIconButton(
                            onClick = { viewModel.skipNext() },
                            shape = sideShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = sideButtonColor,
                                contentColor = sideIconColor,
                            ),
                            modifier = Modifier
                                .height(68.dp)
                                .weight(0.45f)
                        ) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    
                    // Bottom buttons (Queue, Sleep, Lyrics, Shuffle, Repeat)
                    val buttonSize = 42.dp
                    val iconSize = 24.dp
                    val queueShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 3.dp, bottomEnd = 3.dp
                    )
                    val bottomMiddleShape = RoundedCornerShape(3.dp)
                    val repeatShape = RoundedCornerShape(
                        topStart = 3.dp, bottomStart = 3.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )
                    
                    val bottomButtonColor = dynamicButtonContainerColor
                    val activeColor = dynamicActiveLyricColor
                    val bottomIconColor = Color.White
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { showQueueSheet = true },
                            shape = queueShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showQueueSheet) activeColor else bottomButtonColor,
                                contentColor = if (showQueueSheet) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(Icons.Default.QueueMusic, "Queue", modifier = Modifier.size(iconSize))
                        }
                        
                        FilledIconButton(
                            onClick = { showSleepTimerDialog = true },
                            shape = bottomMiddleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showSleepTimerDialog) activeColor else bottomButtonColor,
                                contentColor = if (showSleepTimerDialog) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(Icons.Default.AccessTime, "Sleep Timer", modifier = Modifier.size(iconSize))
                        }

                        FilledIconButton(
                            onClick = onListenTogetherClick,
                            shape = bottomMiddleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = bottomButtonColor,
                                contentColor = bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.group),
                                contentDescription = "Listen Together",
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        
                        FilledIconButton(
                            onClick = { showInlineLyrics = !showInlineLyrics },
                            shape = bottomMiddleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showInlineLyrics) Color.White else bottomButtonColor,
                                contentColor = if (showInlineLyrics) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(Icons.Default.GraphicEq, "Lyrics", modifier = Modifier.size(iconSize))
                        }
                        
                        FilledIconButton(
                            onClick = { viewModel.toggleShuffle() },
                            shape = bottomMiddleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (shuffleModeEnabled) activeColor else bottomButtonColor,
                                contentColor = if (shuffleModeEnabled) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(iconSize))
                        }
                        
                        FilledIconButton(
                            onClick = { viewModel.cycleRepeatMode() },
                            shape = repeatShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (repeatMode != 0) activeColor else bottomButtonColor,
                                contentColor = if (repeatMode != 0) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                "Repeat",
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(bottomButtonColor)
                                .clickable { showPlaylistMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MoreVert, "More", tint = bottomIconColor, modifier = Modifier.size(iconSize))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                            Icon(Icons.Default.Repeat, "Repeat", tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            onDismiss = { showQueueSheet = false },
            onTrackClick = { clickedTrack ->
                val idx = queue.indexOf(clickedTrack)
                if (idx >= 0) viewModel.loadQueue(queue, idx)
            },
            onRemoveTrack = { index ->
                viewModel.removeTrackAt(index)
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { /* Sleep timer selected */ }
        )
    }

    if (showLyricsSheet) {
        LyricsBottomSheet(
            track = track,
            onDismiss = { showLyricsSheet = false }
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun FullPlayerBackgroundLayer(
    playerBackground: PlayerBackgroundStyle,
    track: com.vibevault.app.domain.model.Track?,
    gradientColors: List<Color>,
    showInlineLyrics: Boolean = false
) {
    val context = LocalContext.current
    val thumbnailUrl = track?.albumImageUrl

    when (playerBackground) {
        PlayerBackgroundStyle.BLUR -> {
            AnimatedContent(
                targetState = thumbnailUrl,
                transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(800))) },
                label = "blurBackground"
            ) { url ->
                if (url != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(256, 256)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(80.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF121212))
                            )
                        }
                    }
                }
            }
        }
        PlayerBackgroundStyle.GRADIENT -> {
            AnimatedContent(
                targetState = gradientColors,
                transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(800))) },
                label = "gradientBackground"
            ) { colors ->
                if (colors.isNotEmpty()) {
                    val gradientColorStops = if (colors.size >= 3) {
                        arrayOf(
                            0.0f to colors[0],
                            0.5f to colors[1],
                            1.0f to colors[2]
                        )
                    } else {
                        arrayOf(
                            0.0f to colors[0],
                            0.6f to colors[0].copy(alpha = 0.7f),
                            1.0f to Color.Black
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colorStops = gradientColorStops))
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                }
            }
        }
        PlayerBackgroundStyle.GLOW_ANIMATED -> {
            AnimatedContent(
                targetState = gradientColors,
                transitionSpec = { fadeIn(tween(1200)).togetherWith(fadeOut(tween(1200))) },
                label = "glowAnimatedContent"
            ) { colors ->
                if (colors.isNotEmpty()) {
                    val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")
                    val progress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(20000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "glowProgress"
                    )

                    fun rotatedColorAt(index: Int): Color {
                        val size = colors.size
                        val idx = index.toFloat() + progress * size
                        val a = kotlin.math.floor(idx.toDouble()).toInt() % size
                        val b = (a + 1) % size
                        val frac = idx - kotlin.math.floor(idx.toDouble()).toFloat()
                        return lerp(
                            colors.getOrElse(a) { Color.DarkGray },
                            colors.getOrElse(b) { Color.DarkGray },
                            frac
                        )
                    }

                    fun oscillate(min: Float, max: Float, phase: Float, speed: Float = 1f): Float {
                        val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase))
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
                                    radius = width * r1
                                )
                                val brush2 = Brush.radialGradient(
                                    colors = listOf(color2.copy(alpha = 0.8f), color2.copy(alpha = 0.45f), Color.Transparent),
                                    center = Offset(width * o2x, height * o2y),
                                    radius = width * r2
                                )
                                val brush3 = Brush.radialGradient(
                                    colors = listOf(color3.copy(alpha = 0.75f), color3.copy(alpha = 0.4f), Color.Transparent),
                                    center = Offset(width * o3x, height * o3y),
                                    radius = width * r3
                                )
                                val brush4 = Brush.radialGradient(
                                    colors = listOf(color4.copy(alpha = 0.7f), color4.copy(alpha = 0.35f), Color.Transparent),
                                    center = Offset(width * o4x, height * o4y),
                                    radius = width * r4
                                )
                                val brush5 = Brush.radialGradient(
                                    colors = listOf(color5.copy(alpha = 0.65f), color5.copy(alpha = 0.3f), Color.Transparent),
                                    center = Offset(width * o5x, height * o5y),
                                    radius = width * r5
                                )
                                val brush6 = Brush.radialGradient(
                                    colors = listOf(color6.copy(alpha = 0.6f), color6.copy(alpha = 0.25f), Color.Transparent),
                                    center = Offset(width * o6x, height * o6y),
                                    radius = width * r6
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
                            }
                    )
                }
            }
        }
        PlayerBackgroundStyle.APPLE_MUSIC -> {
            AnimatedContent(
                targetState = thumbnailUrl,
                transitionSpec = { fadeIn(tween(1200)).togetherWith(fadeOut(tween(1200))) },
                label = "appleMusicBackground"
            ) { url ->
                if (url != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(128, 128)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(150.dp)
                            )
                        }

                        val clearArtworkAlpha by animateFloatAsState(
                            targetValue = if (showInlineLyrics) 0f else 1f,
                            animationSpec = tween(500),
                            label = "clearArtworkAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.65f)
                                .alpha(clearArtworkAlpha)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.Black,
                                                0.75f to Color.Black,
                                                0.92f to Color.Black.copy(alpha = 0.4f),
                                                1.00f to Color.Transparent,
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(512)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.05f),
                                            Color.Black.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
        PlayerBackgroundStyle.LIVE_MESH -> {
            val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")
            val anchorRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(80000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "anchorRotation"
            )
            val fastRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(40000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fastRotation"
            )
            val slowRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(60000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "slowRotation"
            )

            AnimatedContent(
                targetState = thumbnailUrl,
                transitionSpec = { fadeIn(tween(1500)).togetherWith(fadeOut(tween(1500))) },
                label = "liveMeshBackground"
            ) { url ->
                if (url != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.7f
                                scaleY = 1.7f
                            }
                    ) {
                        val matrix = remember {
                            val m = ColorMatrix()
                            m.setToSaturation(1.8f)
                            m
                        }
                        val colorFilter = ColorFilter.colorMatrix(matrix)

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(128, 128)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(100.dp)
                                    .graphicsLayer { rotationZ = anchorRotation }
                            )

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(128, 128)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                alignment = Alignment.TopStart,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(120.dp)
                                    .graphicsLayer {
                                        rotationZ = fastRotation
                                        alpha = 0.6f
                                    }
                            )

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(128, 128)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                alignment = Alignment.BottomEnd,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(120.dp)
                                    .graphicsLayer {
                                        rotationZ = slowRotation
                                        alpha = 0.5f
                                    }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.25f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
        PlayerBackgroundStyle.DEFAULT -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F))
            )
        }
    }
}
