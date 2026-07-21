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

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

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
            MiniPlayerBackgroundLayer(
                style = playerBackground,
                track = track,
                gradientColors = gradientColors
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
                            activeLyricColor = dynamicActiveLyricColor,
                            inactiveLyricColor = dynamicInactiveLyricColor
                        )
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
                                .clickable { showInlineLyrics = false },
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
                            IconButton(onClick = { viewModel.toggleLike() }) {
                                Icon(
                                    if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (track.isLiked) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.White
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
                                    onClick = { viewModel.toggleLike() },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = buttonColor,
                                        contentColor = iconColor,
                                    ),
                                    modifier = Modifier.size(width = 50.dp, height = 42.dp),
                                ) {
                                    Icon(
                                        if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                        contentDescription = "Like", 
                                        modifier = Modifier.size(24.dp),
                                        tint = if (track.isLiked) MaterialTheme.colorScheme.primary else iconColor
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
