package com.vibevault.app.ui.screens.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.MediaInfo
import com.vibevault.app.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun SongDetailsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var isLoadingInfo by remember { mutableStateOf(true) }

    LaunchedEffect(track.id) {
        withContext(Dispatchers.IO) {
            runCatching {
                YouTube.getMediaInfo(track.id).getOrNull()
            }.onSuccess { info ->
                withContext(Dispatchers.Main) {
                    mediaInfo = info
                    isLoadingInfo = false
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    isLoadingInfo = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF181818).copy(alpha = 0.98f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Song Info / Details",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Artwork Preview Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.albumImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Details List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        DetailItemRow("Album", track.album.ifBlank { "Single" })
                    }

                    val durationSeconds = track.durationMs / 1000
                    val minutes = durationSeconds / 60
                    val seconds = durationSeconds % 60
                    val formattedDuration = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
                    item {
                        DetailItemRow("Duration", formattedDuration)
                    }

                    // Format & Bitrate
                    item {
                        DetailItemRow(
                            "Audio Format",
                            when (track.source) {
                                "spotify" -> "AAC / Ogg Vorbis"
                                "jiosaavn" -> "MP4 / AAC High-Res"
                                "local" -> "Local Audio File"
                                else -> "WebM / Opus"
                            }
                        )
                    }

                    item {
                        DetailItemRow(
                            "Bitrate",
                            when (track.source) {
                                "jiosaavn" -> "320 Kbps (Very High)"
                                "spotify" -> "256 - 320 Kbps"
                                else -> "160 Kbps (Opus Adaptive)"
                            }
                        )
                    }

                    item {
                        DetailItemRow("Sample Rate", "48,000 Hz / Stereo 2.0")
                    }

                    // Release Year / Upload Date
                    val releaseDate = mediaInfo?.uploadDate ?: "Unknown"
                    item {
                        DetailItemRow("Release / Upload Date", releaseDate)
                    }

                    // Views & Likes
                    if (mediaInfo?.viewCount != null) {
                        item {
                            val formattedViews = formatShortCount(mediaInfo?.viewCount ?: 0)
                            DetailItemRow("Views", "$formattedViews views")
                        }
                    }

                    if (mediaInfo?.like != null) {
                        item {
                            val formattedLikes = formatShortCount(mediaInfo?.like ?: 0)
                            DetailItemRow("Likes", "$formattedLikes likes")
                        }
                    }

                    // Estimated File Size
                    val estimatedSizeMb = ((track.durationMs / 1000f) * (160f / 8f) / 1024f)
                    item {
                        DetailItemRow("Approx. File Size", String.format(Locale.getDefault(), "%.1f MB", estimatedSizeMb))
                    }

                    // Source
                    item {
                        DetailItemRow(
                            "Playback Source",
                            track.source.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        )
                    }

                    // Track / Video ID with copy action
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Track ID", track.id))
                                    Toast.makeText(context, "Copied ID to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Track ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = track.id,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatShortCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000f)
        else -> count.toString()
    }
}
