package com.vibevault.app.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.media.AudioManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.unit.sp
import com.vibevault.app.ui.components.BitChordThinSlider
import com.vibevault.app.ui.icons.BitChordIcons
import com.vibevault.app.ui.screens.player.components.BitChordLiveLyricStrip
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
import com.vibevault.app.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    trackId: String?,
    onBackClick: () -> Unit,
    onListenTogetherClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
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
    var showSongDetailsDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
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
    val sleepTimerActive by viewModel.sleepTimerActive.collectAsStateWithLifecycle()

    // Preferences
    val (useNewPlayerDesign) = rememberPreference(UseNewPlayerDesignKey, defaultValue = true)
    val (useMinimalisticPlayerDesign) = rememberPreference(UseMinimalisticPlayerDesignKey, defaultValue = false)
    val (sliderStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.WAVY)
    val (playerBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.LIVE_MESH)

    val fallbackColor = com.vibevault.app.ui.theme.LocalSolidColorScheme.current.surfaceContainer.toArgb()
    val (gradientColors, onGradientColorsChange) = remember { mutableStateOf<List<Color>>(emptyList()) }
    val context = LocalContext.current

    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    var systemVolume by remember {
        mutableFloatStateOf(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume
        )
    }

    DisposableEffect(audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = (current.toFloat() / maxVolume).coerceIn(0f, 1f)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {}
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

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
                        val extractedColors = if (playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED || playerBackground == PlayerBackgroundStyle.GLOW) {
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
        var showCreateDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }
        val addContext = LocalContext.current

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
                title = { Text("Create Playlist", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName.trim())
                                android.widget.Toast.makeText(addContext, "Playlist \"${newPlaylistName.trim()}\" created", android.widget.Toast.LENGTH_SHORT).show()
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) {
                        Text("Create", color = if (newPlaylistName.isNotBlank()) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF282828)
            )
        }

        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add to Playlist", color = Color.White) },
            text = {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Create new playlist", color = androidx.compose.material3.MaterialTheme.colorScheme.primary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addTrackToPlaylistWithCheck(playlist.id, currentTrack!!.id, addContext)
                                    showAddToPlaylistDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text = playlist.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                Text(text = "${playlist.trackCount} tracks", color = Color.White.copy(alpha = 0.5f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
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
    val (cropAlbumArt) = com.vibevault.app.utils.rememberPreference(com.vibevault.app.constants.CropAlbumArtKey, defaultValue = false)

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
                    if (useMinimalisticPlayerDesign) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Now Playing",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.album.ifBlank { track.title },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (!useNewPlayerDesign) {
                        // BitChord / Apple Music top drag handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f))
                                    .clickable(onClick = onBackClick)
                            )
                        }
                    } else {
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
                            IconButton(onClick = { showPlaylistMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
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
                                isLoadingLyrics = isLoadingLyrics,
                                activeLyricColor = dynamicActiveLyricColor,
                                inactiveLyricColor = dynamicInactiveLyricColor
                            )
                        }
                    } else {
                        // Apple Music background mode without lyrics: empty spacer taking full remaining height to shift bar to bottom
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(8.dp))

                    if (showInlineLyrics) {
                        // Compact Mini Track Info Bar when lyrics view is open
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.albumImageUrl.resize(544, 544),
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
                        // Apple Music Background Style track title, artist and circular more button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .clickable { showPlaylistMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = "More options",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Standard Artwork View (High Resolution 1200x1200px)
                    val artworkShape = if (useMinimalisticPlayerDesign) RoundedCornerShape(20.dp) else RoundedCornerShape(10.dp)
                    val artworkPadding = if (useMinimalisticPlayerDesign || !useNewPlayerDesign) 24.dp else 0.dp

                    if (useMinimalisticPlayerDesign || !useNewPlayerDesign) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = track.albumImageUrl.resize(1200, 1200),
                                contentDescription = track.album,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = artworkPadding)
                                    .aspectRatio(1f)
                                    .clip(artworkShape),
                                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    } else {
                        AsyncImage(
                            model = track.albumImageUrl.resize(1200, 1200),
                            contentDescription = track.album,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = artworkPadding)
                                .aspectRatio(1f)
                                .clip(artworkShape),
                            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    // Title and Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        
                        if (useMinimalisticPlayerDesign) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        val intent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, null))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.size(width = 44.dp, height = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.share),
                                            contentDescription = "Share",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        localIsLiked = !localIsLiked
                                        viewModel.toggleLike()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (localIsLiked) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.size(width = 44.dp, height = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (localIsLiked) MaterialTheme.colorScheme.error else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { showPlaylistMenu = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.size(width = 44.dp, height = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.more_horiz),
                                            contentDescription = "More options",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        } else if (!useNewPlayerDesign) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { showPlaylistMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
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

                if (!useNewPlayerDesign && !useMinimalisticPlayerDesign && !showInlineLyrics) {
                    Spacer(Modifier.height(6.dp))
                    BitChordLiveLyricStrip(
                        lyricsList = lyricsList,
                        isLoadingLyrics = isLoadingLyrics,
                        positionMs = position,
                        isPlaying = isPlaying,
                        durationMs = duration,
                        trackKey = track.id,
                        onClick = { showInlineLyrics = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(2.dp))
                } else {
                    Spacer(Modifier.height(if (useMinimalisticPlayerDesign) 12.dp else if (!useNewPlayerDesign) 6.dp else 16.dp))
                }

                // Progress Bar
                var isDragging by remember { mutableStateOf(false) }
                var dragPosition by remember { mutableStateOf(0f) }
                
                // Use duration from viewModel or fallback to track.durationMs
                val safeDuration = (if (duration > 0) duration else track.durationMs).toFloat().coerceAtLeast(1f)
                val displayPosition = if (isDragging) dragPosition else position.toFloat()

                val activeSliderColor = if (useNewPlayerDesign) Color.White else Color.White.copy(alpha = 0.7f)
                val inactiveSliderColor = if (useNewPlayerDesign) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f)

                if (!useNewPlayerDesign && !useMinimalisticPlayerDesign && sliderStyle == SliderStyle.DEFAULT) {
                    BitChordThinSlider(
                        value = (displayPosition / safeDuration).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            isDragging = true
                            dragPosition = fraction * safeDuration
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            viewModel.seekTo(dragPosition.toLong())
                        },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        activeColor = Color.White.copy(alpha = 0.95f),
                        inactiveColor = Color.White.copy(alpha = 0.25f)
                    )
                } else {
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
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(displayPosition.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )

                    if (!useNewPlayerDesign && !useMinimalisticPlayerDesign) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Hi-Quality",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Text(
                        if (!useNewPlayerDesign && !useMinimalisticPlayerDesign) {
                            val remaining = (safeDuration - displayPosition).toLong().coerceAtLeast(0L)
                            "-${formatTime(remaining)}"
                        } else {
                            formatTime(safeDuration.toLong())
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }

                Spacer(Modifier.height(if (useMinimalisticPlayerDesign) 12.dp else if (!useNewPlayerDesign) 14.dp else 20.dp))

                // Playback Controls
                if (useMinimalisticPlayerDesign) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        val baseLarge = 56.dp
                        val baseSmall = 46.dp
                        val baseGap = 12.dp
                        val baseLargeIcon = 28.dp
                        val baseSmallIcon = 22.dp
                        val baseLargeRadius = 18.dp
                        val baseSmallRadius = 16.dp
                        val centerSize = 88.dp
                        val centerPadding = 40.dp
                        val sideTotal = (maxWidth - centerSize - centerPadding) / 2f
                        val scale = ((sideTotal - baseGap) / (baseLarge + baseSmall)).coerceAtMost(1f).coerceAtLeast(0.6f)
                        val large = baseLarge * scale
                        val small = baseSmall * scale
                        val gap = baseGap * scale
                        val largeIcon = baseLargeIcon * scale
                        val smallIcon = baseSmallIcon * scale
                        val largeRadius = baseLargeRadius * scale
                        val smallRadius = baseSmallRadius * scale

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { viewModel.toggleShuffle() },
                                    shape = RoundedCornerShape(smallRadius),
                                    color = Color.White.copy(alpha = if (shuffleModeEnabled) 0.2f else 0.08f),
                                    modifier = Modifier.size(small)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.shuffle),
                                            contentDescription = "Shuffle",
                                            tint = Color.White.copy(alpha = if (shuffleModeEnabled) 1f else 0.6f),
                                            modifier = Modifier.size(smallIcon)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(gap))

                                Surface(
                                    onClick = { viewModel.skipPrevious() },
                                    shape = RoundedCornerShape(largeRadius),
                                    color = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(large)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.skip_previous),
                                            contentDescription = "Previous",
                                            tint = Color.White,
                                            modifier = Modifier.size(largeIcon)
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = { viewModel.togglePlayPause() },
                                shape = RoundedCornerShape(28.dp),
                                color = dynamicActiveLyricColor,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .size(88.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            if (isPlaying) com.vibevault.app.R.drawable.pause else com.vibevault.app.R.drawable.play
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { viewModel.skipNext() },
                                    shape = RoundedCornerShape(largeRadius),
                                    color = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(large)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.skip_next),
                                            contentDescription = "Next",
                                            tint = Color.White,
                                            modifier = Modifier.size(largeIcon)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(gap))

                                Surface(
                                    onClick = { viewModel.cycleRepeatMode() },
                                    shape = RoundedCornerShape(smallRadius),
                                    color = Color.White.copy(alpha = if (repeatMode != 0) 0.2f else 0.08f),
                                    modifier = Modifier.size(small)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(com.vibevault.app.R.drawable.repeat),
                                            contentDescription = "Repeat",
                                            tint = Color.White.copy(alpha = if (repeatMode != 0) 1f else 0.6f),
                                            modifier = Modifier.size(smallIcon)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom Action Row (Queue, Sleep Timer, Lyrics)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Queue Pill
                        Surface(
                            onClick = { showQueueSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showQueueSheet) Color.White else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = "Queue",
                                    tint = if (showQueueSheet) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Queue",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (showQueueSheet) Color.Black else Color.White
                                )
                            }
                        }

                        // Sleep Timer Button
                        val isSleepTimerActive = sleepTimerActive || showSleepTimerDialog
                        Surface(
                            onClick = { showSleepTimerDialog = true },
                            shape = CircleShape,
                            color = if (isSleepTimerActive) dynamicActiveLyricColor else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.NightsStay,
                                    contentDescription = "Sleep Timer",
                                    tint = if (isSleepTimerActive) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Lyrics Pill
                        Surface(
                            onClick = { showInlineLyrics = !showInlineLyrics },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showInlineLyrics) Color.White else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Lyrics",
                                    tint = if (showInlineLyrics) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Lyrics",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (showInlineLyrics) Color.Black else Color.White
                                )
                            }
                        }
                    }
                } else if (useNewPlayerDesign) {
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
                    // Bottom buttons (Queue, Sleep, Listen Together, Lyrics, Shuffle, Repeat, More)
                    val buttonSize = 40.dp
                    val iconSize = 22.dp
                    val queueShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 3.dp, bottomEnd = 3.dp
                    )
                    val bottomMiddleShape = RoundedCornerShape(3.dp)
                    val moreShape = RoundedCornerShape(
                        topStart = 3.dp, bottomStart = 3.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )
                    
                    val bottomButtonColor = dynamicButtonContainerColor
                    val activeColor = dynamicActiveLyricColor
                    val bottomIconColor = Color.White
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                        
                        val isSleepTimerActive = sleepTimerActive || showSleepTimerDialog
                        FilledIconButton(
                            onClick = { showSleepTimerDialog = true },
                            shape = bottomMiddleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isSleepTimerActive) activeColor else bottomButtonColor,
                                contentColor = if (isSleepTimerActive) Color.Black else bottomIconColor
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
                            shape = bottomMiddleShape,
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
                        
                        FilledIconButton(
                            onClick = { showPlaylistMenu = true },
                            shape = moreShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showPlaylistMenu) activeColor else bottomButtonColor,
                                contentColor = if (showPlaylistMenu) Color.Black else bottomIconColor
                            ),
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                } else {
                    // ---- BitChord Classic Playback Controls ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous (<<)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.skipPrevious() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        // Play/Pause (Large borderless glyph)
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(62.dp)
                            )
                        }

                        // Next (>>)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.skipNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // ---- BitChord Volume Slider ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        BitChordThinSlider(
                            value = systemVolume,
                            onValueChange = {
                                systemVolume = it
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
                                }
                            },
                            idleHeight = 6.dp,
                            activeHeight = 10.dp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // ---- BitChord Bottom Action Bar (Shuffle · Repeat · Infinity · Queue) ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (shuffleModeEnabled) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable { viewModel.toggleShuffle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = BitChordIcons.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White.copy(alpha = if (shuffleModeEnabled) 1f else 0.75f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Repeat
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (repeatMode != 0) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable { viewModel.cycleRepeatMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (repeatMode == 2) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = BitChordIcons.Repeat,
                                    contentDescription = "Repeat",
                                    tint = Color.White.copy(alpha = if (repeatMode != 0) 1f else 0.75f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // AutoPlay / Infinity
                        var autoPlayActive by remember { mutableStateOf(true) }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (autoPlayActive) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                                .clickable { autoPlayActive = !autoPlayActive },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = BitChordIcons.Infinity,
                                contentDescription = "AutoPlay",
                                tint = Color.White.copy(alpha = if (autoPlayActive) 1f else 0.7f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Queue
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (showQueueSheet) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable { showQueueSheet = !showQueueSheet },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = Color.White.copy(alpha = if (showQueueSheet) 1f else 0.75f),
                                modifier = Modifier.size(24.dp)
                            )
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
            onConfirm = { minutes ->
                viewModel.startSleepTimer(minutes)
            }
        )
    }

    if (showLyricsSheet) {
        LyricsBottomSheet(
            track = track,
            onDismiss = { showLyricsSheet = false }
        )
    }

    if (showPlaylistMenu) {
        PlayerMenuBottomSheet(
            track = track,
            isLiked = localIsLiked,
            onToggleLike = {
                localIsLiked = !localIsLiked
                viewModel.toggleLike()
            },
            onDismiss = { showPlaylistMenu = false },
            onViewArtist = { artistName ->
                onBackClick()
                onArtistClick(artistName)
            },
            onViewAlbum = { albumTarget ->
                onBackClick()
                onAlbumClick(albumTarget)
            },
            onOpenEqualizer = {
                showEqualizerDialog = true
            },
            onAddToPlaylist = {
                showAddToPlaylistDialog = true
            },
            onPlayNext = { t ->
                viewModel.playNext(t)
            },
            onAddToQueue = { t ->
                viewModel.addToQueue(t)
            },
            onShowDetails = {
                showSongDetailsDialog = true
            }
        )
    }

    if (showSongDetailsDialog) {
        SongDetailsDialog(
            track = track,
            onDismiss = { showSongDetailsDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerModalDialog(
            track = track,
            onDismiss = { showEqualizerDialog = false }
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
        PlayerBackgroundStyle.BLUR_GRADIENT -> {
            AnimatedContent(
                targetState = thumbnailUrl,
                transitionSpec = { fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000))) },
                label = "blurGradientBackground"
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
                                    .blur(65.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF121212))
                            )
                        }
                        val gradientColorStops = com.vibevault.app.ui.theme.PlayerBackgroundColorUtils.buildBlurGradientStops(gradientColors)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(colorStops = gradientColorStops))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.05f))
                        )
                    }
                }
            }
        }
        PlayerBackgroundStyle.GLOW -> {
            AnimatedContent(
                targetState = gradientColors,
                transitionSpec = { fadeIn(tween(1200)).togetherWith(fadeOut(tween(1200))) },
                label = "glowBackground"
            ) { colors ->
                if (colors.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val width = size.width
                                val height = size.height
                                val baseColor = Color(0xFF050505)

                                val color1 = colors.getOrElse(0) { Color.DarkGray }
                                val color2 = colors.getOrElse(1) { color1 }
                                val color3 = colors.getOrElse(2) { color2 }
                                val color4 = colors.getOrElse(3) { color1 }
                                val color5 = colors.getOrElse(4) { color2 }
                                val color6 = colors.getOrElse(5) { color3 }

                                val brush1 = Brush.radialGradient(
                                    colors = listOf(color1.copy(alpha = 0.8f), color1.copy(alpha = 0.5f), Color.Transparent),
                                    center = Offset(width * 0.2f, height * 0.25f),
                                    radius = width * 1.2f
                                )
                                val brush2 = Brush.radialGradient(
                                    colors = listOf(color2.copy(alpha = 0.75f), color2.copy(alpha = 0.45f), Color.Transparent),
                                    center = Offset(width * 0.85f, height * 0.8f),
                                    radius = width * 1.1f
                                )
                                val brush3 = Brush.radialGradient(
                                    colors = listOf(color3.copy(alpha = 0.7f), color3.copy(alpha = 0.4f), Color.Transparent),
                                    center = Offset(width * 0.9f, height * 0.15f),
                                    radius = width * 1.0f
                                )
                                val brush4 = Brush.radialGradient(
                                    colors = listOf(color4.copy(alpha = 0.65f), color4.copy(alpha = 0.35f), Color.Transparent),
                                    center = Offset(width * 0.1f, height * 0.9f),
                                    radius = width * 1.0f
                                )
                                val brush5 = Brush.radialGradient(
                                    colors = listOf(color5.copy(alpha = 0.6f), color5.copy(alpha = 0.3f), Color.Transparent),
                                    center = Offset(width * 0.5f, height * 0.1f),
                                    radius = width * 0.9f
                                )
                                val brush6 = Brush.radialGradient(
                                    colors = listOf(color6.copy(alpha = 0.6f), color6.copy(alpha = 0.3f), Color.Transparent),
                                    center = Offset(width * 0.5f, height * 0.95f),
                                    radius = width * 0.9f
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
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "GlowAnimatedContent"
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
                            val a = kotlin.math.floor(idx).toInt() % size
                            val b = (a + 1) % size
                            val frac = idx - kotlin.math.floor(idx)
                            return androidx.compose.ui.graphics.lerp(colors.getOrElse(a) { Color.DarkGray }, colors.getOrElse(b) { Color.DarkGray }, frac)
                        }

                        fun oscillate(min: Float, max: Float, phase: Float, speed: Float = 1f): Float {
                            // speed MUST be an integer to ensure seamless looping when progress wraps from 1f to 0f.
                            val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase)).toFloat()
                            return min + (max - min) * ((v + 1f) * 0.5f)
                        }

                        val color1 = rotatedColorAt(0)
                        val color2 = rotatedColorAt(1)
                        val color3 = rotatedColorAt(2)
                        val color4 = rotatedColorAt(3)
                        val color5 = rotatedColorAt(4)
                        val color6 = rotatedColorAt(5)

                        val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                        val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                        val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                        val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                        val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                        val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                        val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                        val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                        val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                        val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                        val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                        val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                        val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                        val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                        val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                        val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                        val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                        val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

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
                    val highResUrl = remember(url) { url.resize(1200, 1200) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(highResUrl)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(120.dp)
                            )
                        }

                        val clearArtworkAlpha by animateFloatAsState(
                            targetValue = if (showInlineLyrics) 0.0f else 1.0f,
                            animationSpec = tween(500),
                            label = "clearArtworkAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.72f)
                                .alpha(clearArtworkAlpha)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.Black,
                                                0.65f to Color.Black,
                                                0.88f to Color.Black.copy(alpha = 0.4f),
                                                1.00f to Color.Transparent,
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(highResUrl)
                                    .crossfade(true)
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
                                            Color.Black.copy(alpha = 0.45f)
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

    val lyricsOverlayAlpha by animateFloatAsState(
        targetValue = if (showInlineLyrics) 0.50f else 0f,
        animationSpec = tween(500),
        label = "lyricsOverlayAlpha"
    )

    if (lyricsOverlayAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = lyricsOverlayAlpha))
        )
    }
}
