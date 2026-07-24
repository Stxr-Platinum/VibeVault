package com.vibevault.app.ui.screens.liked

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.screens.library.LibraryViewModel
import com.vibevault.app.ui.theme.*

/**
 * LikedSongsScreen — Stitch "Liked Songs (Updated)" (50c22500).
 */
@Composable
fun LikedSongsScreen(
    onTrackClick: (String, List<Track>) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val liked by viewModel.likedTracks.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().height(240.dp)
                    .background(Brush.verticalGradient(listOf(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(0.5f), androidx.compose.material3.MaterialTheme.colorScheme.background)))
            ) {
                IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(16.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.align(Alignment.Center).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, "Liked", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Liked Songs", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    Text("${liked.size} songs", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { liked.firstOrNull()?.let { onTrackClick(it.id, liked) } }, Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(androidx.compose.material3.MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Default.PlayArrow, "Play", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Play All")
                }
                OutlinedButton(onClick = { }, Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Shuffle")
                }
            }
        }

        items(liked, key = { it.id }) { track ->
            Row(Modifier.fillMaxWidth().clickable { onTrackClick(track.id, liked) }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(track.albumImageUrl, track.album, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} • ${track.album}", style = MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.Favorite, "Liked", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
