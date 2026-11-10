package com.vibevault.app.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Track>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onTrackIndexClick: ((Int) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val safeIndex = currentIndex.takeIf { it in queue.indices } ?: -1
    val currentTrack = if (safeIndex >= 0) queue[safeIndex] else null
    val previousTracks = if (safeIndex > 0) queue.subList(0, safeIndex) else emptyList()
    val upcomingTracks = if (safeIndex in queue.indices && safeIndex + 1 < queue.size) {
        queue.subList(safeIndex + 1, queue.size)
    } else if (safeIndex == -1) {
        queue
    } else {
        emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // 1. NOW PLAYING
                if (currentTrack != null) {
                    item(key = "header_now_playing") {
                        QueueSectionHeader(title = "NOW PLAYING")
                        Spacer(Modifier.height(10.dp))
                    }
                    item(key = "now_playing_${safeIndex}_${currentTrack.id}") {
                        NowPlayingQueueRow(track = currentTrack)
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // 2. NEXT UP
                item(key = "header_next_up") {
                    QueueSectionHeader(
                        title = "NEXT UP",
                        trailing = "${upcomingTracks.size} tracks"
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (upcomingTracks.isEmpty()) {
                    item(key = "next_up_empty") {
                        Text(
                            text = "No upcoming tracks",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val baseOffset = if (safeIndex >= 0) safeIndex + 1 else 0
                    itemsIndexed(
                        items = upcomingTracks,
                        key = { index, track -> "next_${baseOffset + index}_${track.id}" }
                    ) { index, track ->
                        val actualIndex = baseOffset + index
                        QueueUpcomingTrackRow(
                            track = track,
                            trackNumber = actualIndex + 1,
                            onClick = {
                                if (onTrackIndexClick != null) onTrackIndexClick(actualIndex)
                                else onTrackClick(track)
                            },
                            onSwipeToRemove = { onRemoveTrack(actualIndex) }
                        )
                    }
                }

                // 3. PREVIOUSLY PLAYED
                if (previousTracks.isNotEmpty()) {
                    item(key = "header_previously_played") {
                        Spacer(Modifier.height(24.dp))
                        QueueSectionHeader(
                            title = "PREVIOUSLY PLAYED",
                            trailing = "${previousTracks.size} tracks"
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    itemsIndexed(
                        items = previousTracks,
                        key = { index, track -> "prev_${index}_${track.id}" }
                    ) { index, track ->
                        QueuePreviousTrackRow(
                            track = track,
                            trackNumber = index + 1,
                            onClick = {
                                if (onTrackIndexClick != null) onTrackIndexClick(index)
                                else onTrackClick(track)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = Color.Gray
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun NowPlayingQueueRow(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.albumImageUrl,
                contentDescription = "Now Playing",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(
                    Color.Black.copy(alpha = 0.45f),
                    BlendMode.Darken
                )
            )
            AnimatedEqualizerBars(color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
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
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnimatedEqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "queueEq")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )

    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueUpcomingTrackRow(
    track: Track,
    trackNumber: Int,
    onClick: () -> Unit,
    onSwipeToRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToRemove()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(track) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color(0xFFFF4444) else Color.Transparent,
                label = "swipeBgColor"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove from Queue",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        QueueTrackRowContent(
            track = track,
            trackNumber = trackNumber,
            onClick = onClick
        )
    }
}

@Composable
private fun QueuePreviousTrackRow(
    track: Track,
    trackNumber: Int,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.alpha(0.55f)) {
        QueueTrackRowContent(
            track = track,
            trackNumber = trackNumber,
            onClick = onClick
        )
    }
}

@Composable
private fun QueueTrackRowContent(
    track: Track,
    trackNumber: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = trackNumber.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(12.dp))
        AsyncImage(
            model = track.albumImageUrl,
            contentDescription = track.album,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
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
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
