package com.vibevault.app.ui.screens.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibevault.app.R
import com.vibevault.app.domain.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    track: Track,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onDismiss: () -> Unit,
    onViewArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onShowDetails: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414).copy(alpha = 0.96f),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.25f),
                width = 36.dp,
                height = 4.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // ── 1. Header / Top Section: Mini Track Preview ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album artwork thumbnail
                AsyncImage(
                    model = track.albumImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite Toggle Action
                val heartColor by animateColorAsState(
                    targetValue = if (isLiked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    label = "heartColor"
                )
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Song",
                        tint = heartColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Close Button
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            // ── 2. Menu Actions (Vertical List) ──────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Group 1: Navigation Actions (Artist & Album)
                item {
                    MenuCardGroup {
                        MenuItemRow(
                            drawableRes = R.drawable.person,
                            title = "View Artist",
                            subtitle = track.artist,
                            onClick = {
                                onDismiss()
                                onViewArtist(track.artist)
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MenuItemRow(
                            drawableRes = R.drawable.album,
                            title = "View Album",
                            subtitle = track.album.ifBlank { "View album details" },
                            onClick = {
                                onDismiss()
                                val target = if (track.album.isNotBlank()) "${track.album}::${track.artist}" else track.artist
                                onViewAlbum(target)
                            }
                        )
                    }
                }

                // Group 2: Queue & Playlist Actions
                item {
                    MenuCardGroup {
                        MenuItemRow(
                            drawableRes = R.drawable.playlist_add,
                            title = "Add to Playlist",
                            subtitle = "Save to one of your playlists",
                            onClick = {
                                onDismiss()
                                onAddToPlaylist()
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MenuItemRow(
                            drawableRes = R.drawable.queue_music,
                            title = "Play Next",
                            subtitle = "Play right after the current song",
                            onClick = {
                                onPlayNext(track)
                                Toast.makeText(context, "Playing next in queue", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MenuItemRow(
                            drawableRes = R.drawable.add,
                            title = "Add to Queue",
                            subtitle = "Append track to the end of queue",
                            onClick = {
                                onAddToQueue(track)
                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }
                }

                // Group 3: Equalizer, Share & Song Info / Details
                item {
                    MenuCardGroup {
                        MenuItemRow(
                            drawableRes = R.drawable.viviequlizer,
                            title = "Equalizer",
                            subtitle = "Adjust audio presets & frequency bands",
                            onClick = {
                                onDismiss()
                                onOpenEqualizer()
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MenuItemRow(
                            drawableRes = R.drawable.share,
                            title = "Share",
                            subtitle = "Share song link with friends",
                            onClick = {
                                val shareUrl = "https://music.youtube.com/watch?v=${track.id}"
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                                    putExtra(Intent.EXTRA_TEXT, "Listen to \"${track.title}\" by ${track.artist}: $shareUrl")
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Track")
                                context.startActivity(shareIntent)
                                onDismiss()
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MenuItemRow(
                            drawableRes = R.drawable.info,
                            title = "Song Info / Details",
                            subtitle = "Format, bitrate, release year & file info",
                            onClick = {
                                onDismiss()
                                onShowDetails()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCardGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun MenuItemRow(
    @DrawableRes drawableRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(drawableRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(14.dp)
        )
    }
}
