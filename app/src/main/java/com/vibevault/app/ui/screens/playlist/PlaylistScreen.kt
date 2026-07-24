package com.vibevault.app.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (String, List<Track>) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Save Album to Playlist", color = Color.White) },
            text = {
                LazyColumn {
                    items(userPlaylists, key = { it.id }) { p ->
                        Text(
                            text = p.title,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addAlbumToPlaylist(p.id)
                                    showAddToPlaylistDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                    if (userPlaylists.isEmpty()) {
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

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
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
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renamePlaylist(newName)
                    }
                    showRenameDialog = false
                }) {
                    Text("Save", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF282828),
            titleContentColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF282828))
                ) {
                    if (playlistId.startsWith("album:")) {
                        DropdownMenuItem(
                            text = { Text("Save Album to Playlist", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showAddToPlaylistDialog = true
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                viewModel.deletePlaylist(onBackClick)
                            }
                        )
                    }
                }
            }
        }

        if (playlist != null) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    // Header Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        if (playlist!!.coverUrl != null) {
                            AsyncImage(
                                model = playlist!!.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                                contentScale = ContentScale.Crop,
                                alpha = 0.4f
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF282828)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist!!.coverUrl != null) {
                                    AsyncImage(
                                        model = playlist!!.coverUrl,
                                        contentDescription = "Playlist Cover",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = "Playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(20.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (playlistId.startsWith("album:")) "ALBUM" else "PLAYLIST",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = playlist!!.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "V • ${tracks.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    // Controls Block
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (tracks.isNotEmpty()) {
                                        viewModel.recordPlayed()
                                        onTrackClick(tracks.first().id, tracks)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play Playlist",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh, // Placeholder for loop
                            contentDescription = "Loop",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(24.dp))
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp).clickable { showMenu = true }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (tracks.isNotEmpty()) {
                    item {
                        // Track List Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(32.dp))
                            Text("Title", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                        // Divider
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    }

                    itemsIndexed(tracks, key = { _, it -> it.id }) { index, track ->
                        var showTrackMenu by remember { mutableStateOf(false) }
                        
                        val shape = if (index == tracks.lastIndex) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) else RoundedCornerShape(0.dp)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { 
                                    viewModel.recordPlayed()
                                    onTrackClick(track.id, tracks) 
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(32.dp)
                            )
                            AsyncImage(
                                model = track.albumImageUrl,
                                contentDescription = track.album,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box {
                                IconButton(onClick = { showTrackMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "Track Options", tint = Color.White.copy(alpha = 0.5f))
                                }
                                DropdownMenu(
                                    expanded = showTrackMenu,
                                    onDismissRequest = { showTrackMenu = false },
                                    modifier = Modifier.background(Color(0xFF282828))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from playlist", color = Color.White) },
                                        onClick = {
                                            showTrackMenu = false
                                            viewModel.removeTrack(track.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
