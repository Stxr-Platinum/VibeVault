package com.vibevault.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*

/**
 * LibraryScreen — Matches Stitch "Your Library".
 *
 * Stitch layout:
 *   - Profile avatar + "Your Library" header
 *   - Filter chips: Playlists, Artists, Albums
 *   - Liked Songs card with gradient
 *   - Recent playlists / track list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onLikedSongsClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("Playlists") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Give your playlist a name") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
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
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName)
                        }
                        showCreateDialog = false
                    }
                ) {
                    Text("Create", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF282828),
            titleContentColor = Color.White
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
        // ── Header ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarUrl = userAvatarUrl,
                    displayName = userDisplayName,
                    size = 36.dp
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            }
            Row {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Settings", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, "Search", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                }
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, "Add", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create playlist", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary) },
                            onClick = { 
                                showAddMenu = false
                                showCreateDialog = true
                            }
                        )
                    }
                }
            }
        }

        // ── Filter Chips ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Playlists", "Artists", "Albums").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            filter,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        selectedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        labelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = CircleShape,
                    border = null
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Sort / Layout Controls ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { /* Show Sort Menu */ }
            ) {
                Icon(Icons.Default.Sort, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Recents",
                    style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.GridView, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── Liked Songs Card ────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLikedSongsClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF450AF5),
                                        Color(0xFFC4EFD9)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Liked",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Liked Songs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp).padding(end = 4.dp)
                            )
                            Text(
                                "Playlist • ${likedTracks.size} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Other Dynamic Items (Episodes, etc.) ─────────
            item {
                LibraryListItem(
                    title = "New Episodes",
                    subtitle = "Updated yesterday",
                    icon = Icons.Default.NotificationAdd,
                    iconTint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    onClick = {}
                )
            }

            // ── Custom Playlists ────────────────────────────
            items(playlists, key = { "pl_${it.id}" }) { playlist ->
                LibraryListItem(
                    title = playlist.title,
                    subtitle = "Playlist • ${playlist.trackCount} songs",
                    imageUri = null, // Future: fetch playlist cover
                    placeholderIcon = Icons.Default.QueueMusic,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }

            // ── Spotify Playlists ────────────────────────────
            items(spotifyPlaylists, key = { "spotify_${it.id}" }) { playlist ->
                LibraryListItem(
                    title = playlist.title,
                    subtitle = "Spotify Playlist • ${playlist.ownerName ?: "Spotify"}",
                    imageUri = playlist.coverUrl,
                    placeholderIcon = Icons.Default.QueueMusic,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }

            if (likedTracks.isEmpty() && playlists.isEmpty() && spotifyPlaylists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                "Empty Library",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Your library is empty",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Like some songs to see them here",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun LibraryListItem(
    title: String,
    subtitle: String,
    imageUri: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MusicNote,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    placeholderIcon,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
