package com.vibevault.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.ui.theme.VibeBg
import com.vibevault.app.ui.theme.VibeOnSurfaceVariant
import com.vibevault.app.ui.theme.VibePrimary
import com.vibevault.app.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    trackId: String?,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

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
                    Text("Close", color = VibePrimary)
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
        Box(modifier = Modifier.fillMaxSize().background(VibeBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VibePrimary)
        }
        return
    }

    val track = currentTrack!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        // Top Bar
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
                IconButton(onClick = { /* TODO: Launch Listen Together UI */ }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Share, "Listen Together", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                }
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Album Art
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.weight(0.5f))

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
                    color = VibeOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { viewModel.toggleLike() }) {
                Icon(
                    if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (track.isLiked) VibePrimary else Color.White
                )
            }
            var isAddAnimating by remember { mutableStateOf(false) }
            val addScale by androidx.compose.animation.core.animateFloatAsState(
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
        }

        Spacer(Modifier.weight(0.5f))

        // Progress Bar
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableStateOf(0f) }
        
        // Use duration from viewModel or fallback to track.durationMs
        val safeDuration = (if (duration > 0) duration else track.durationMs).toFloat().coerceAtLeast(1f)
        val displayPosition = if (isDragging) dragPosition else position.toFloat()

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
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(displayPosition.toLong()), style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant)
            Text(formatTime(safeDuration.toLong()), style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant)
        }

        Spacer(Modifier.weight(0.5f))

        // Playback Controls
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
                    .background(VibePrimary)
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

        Spacer(Modifier.weight(0.5f))

        // Volume Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, "Volume Down", tint = VibeOnSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = volume,
                onValueChange = { viewModel.setVolume(it) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume Up", tint = VibeOnSurfaceVariant)
        }
        
        Spacer(Modifier.weight(1f))
        
        // Bottom Actions (Devices, Queue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showQueueSheet = true }) {
                Icon(Icons.Default.QueueMusic, "Queue", tint = VibeOnSurfaceVariant)
            }
        }
    }

    if (showQueueSheet) {
        val queueList by viewModel.queue.collectAsStateWithLifecycle()
        val queueIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
        
        QueueBottomSheet(
            queue = queueList,
            currentIndex = queueIndex,
            onDismiss = { showQueueSheet = false },
            onTrackClick = { viewModel.playTrack(it.id) },
            onRemoveTrack = { viewModel.removeTrackAt(it) }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
