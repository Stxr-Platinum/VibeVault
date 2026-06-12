package com.vibevault.app.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.theme.VibeOnSurfaceVariant
import com.vibevault.app.ui.theme.VibePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (String, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImagePicked(it) }
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
                        cursorColor = VibePrimary,
                        focusedBorderColor = VibePrimary,
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
                    Text("Save", color = VibePrimary)
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
            .background(Color(0xFF0A0A0A))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
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

        if (isLoading && playlist == null) {
            // Full-screen loading state while playlist metadata loads
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VibePrimary)
            }
        } else if (playlist != null) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                val customCover = playlist!!.coverUrl
                val customModel = customCover?.takeIf { it.isNotBlank() }

                if (customModel != null) {
                    AsyncImage(
                        model = customModel,
                        contentDescription = "Playlist Cover",
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { launcher.launch("image/*") },
                        contentScale = ContentScale.Crop
                    )
                } else if (tracks.isNotEmpty()) {
                    val coverTracks = tracks.take(4)
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282828))
                            .clickable { launcher.launch("image/*") }
                    ) {
                        if (coverTracks.size >= 4) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.weight(1f)) {
                                    AsyncImage(model = coverTracks[0].albumImageUrl, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                    AsyncImage(model = coverTracks[1].albumImageUrl, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                }
                                Row(Modifier.weight(1f)) {
                                    AsyncImage(model = coverTracks[2].albumImageUrl, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                    AsyncImage(model = coverTracks[3].albumImageUrl, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                }
                            }
                        } else {
                            AsyncImage(model = coverTracks[0].albumImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282828))
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist!!.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Playlist • ${tracks.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VibeOnSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(VibePrimary)
                            .clickable {
                                if (tracks.isNotEmpty()) {
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
                }
            }

            Spacer(Modifier.height(16.dp))

            // Track List
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Error / Loading states for tracks
                if (errorMessage != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = VibeOnSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VibeOnSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = VibePrimary)
                            ) {
                                Text("Retry", color = Color.Black)
                            }
                        }
                    }
                } else if (isLoading && tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VibePrimary)
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    var showTrackMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track.id, tracks) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.albumImageUrl,
                            contentDescription = track.album,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onArtistClick(track.artist) }
                            )
                        }
                        Box {
                            IconButton(onClick = { showTrackMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Track Options", tint = VibeOnSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showTrackMenu,
                                onDismissRequest = { showTrackMenu = false },
                                modifier = Modifier.background(Color(0xFF282828))
                            ) {
                                if (index > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Move Up", color = Color.White) },
                                        onClick = {
                                            showTrackMenu = false
                                            viewModel.moveTrack(index, index - 1)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Top", color = Color.White) },
                                        onClick = {
                                            showTrackMenu = false
                                            viewModel.moveTrack(index, 0)
                                        }
                                    )
                                }
                                if (index < tracks.size - 1) {
                                    DropdownMenuItem(
                                        text = { Text("Move Down", color = Color.White) },
                                        onClick = {
                                            showTrackMenu = false
                                            viewModel.moveTrack(index, index + 1)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Bottom", color = Color.White) },
                                        onClick = {
                                            showTrackMenu = false
                                            viewModel.moveTrack(index, tracks.size - 1)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Remove from playlist", color = Color.White) },
                                    onClick = {
                                        showTrackMenu = false
                                        viewModel.removeTrackFromPlaylist(track.id)
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
