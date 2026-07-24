package com.vibevault.app.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibevault.app.ui.theme.*

/**
 * PodcastScreen — Matches Stitch "Podcast / Episode" (a5ff3ee1).
 *
 * Features:
 *   - Episode hero with cover art
 *   - Episode metadata (duration, date)
 *   - Description with "Show more"
 *   - Play / Download / Share action row
 *   - Episode list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, "More", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
        }

        // ── Episode Hero ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = "https://picsum.photos/seed/podcast/300/300",
                contentDescription = "Podcast Cover",
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "The Creative Mind",
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Episode 42: Finding Flow",
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "45 min • May 1, 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Action Row ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, "Play", Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Default.Download, "Download", Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Description ────────────────────────────────────
        Text(
            text = "Episode Description",
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "In this episode, we explore the psychology of creative flow states — how artists, musicians, and developers enter deep focus and produce their best work. We discuss practical techniques for triggering flow, managing distractions, and building environments that support sustained creative output.",
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(24.dp))

        // ── More Episodes ──────────────────────────────────
        Text(
            "More Episodes",
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))

        repeat(5) { index ->
            EpisodeRow(
                title = "Episode ${41 - index}: ${listOf("Deep Work", "Morning Rituals", "Sound Design", "Imposter Syndrome", "The Art of Rest")[index]}",
                duration = "${35 + index * 5} min",
                date = "Apr ${25 - index * 3}, 2026"
            )
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun EpisodeRow(title: String, duration: String, date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$duration • $date", style = MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.PlayArrow, "Play", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
    }
}
