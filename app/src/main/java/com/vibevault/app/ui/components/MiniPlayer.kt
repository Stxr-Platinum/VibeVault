package com.vibevault.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.ui.theme.*
import com.vibevault.app.ui.viewmodel.PlayerViewModel

/**
 * MiniPlayer — Persistent playback bar docked above the bottom nav.
 *
 * Stitch spec: "Now Playing Bar — A persistent footer component
 * featuring a backdrop blur, playback controls centered."
 *
 * Design: Dark surface with green progress bar, album art, track info,
 * like toggle, green circular play button, skip next.
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

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        currentTrack?.let { track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExpand(track.id) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art thumbnail
                    AsyncImage(
                        model = track.albumImageUrl,
                        contentDescription = track.album,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.width(10.dp))

                    // Track info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Like button
                    IconButton(
                        onClick = { playerViewModel.toggleLike() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (track.isLiked) Icons.Default.Favorite
                                          else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (track.isLiked) VibePrimary else VibeOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play/Pause button (green circle)
                    IconButton(
                        onClick = { playerViewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VibePrimaryContainer)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause
                                          else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    // Skip next
                    IconButton(
                        onClick = { playerViewModel.skipNext() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Green progress indicator line at bottom
                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(horizontal = 10.dp),
                        color = VibePrimary,
                        trackColor = Color(0xFF333333),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
