package com.vibevault.app.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevault.app.data.lyrics.LyricsEntry
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyricsList: List<LyricsEntry>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyricsList.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No lyrics found.",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Find the currently active line
    val activeIndex = remember(lyricsList, currentPosition) {
        val index = lyricsList.indexOfLast { it.time <= currentPosition }
        if (index != -1) index else 0
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsList.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -300 // Center roughly in view
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 200.dp)
    ) {
        itemsIndexed(lyricsList) { index, entry ->
            val isActive = index == activeIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.5f,
                animationSpec = tween(300),
                label = "alpha"
            )
            val blurRadius by animateFloatAsState(
                targetValue = if (isActive) 0f else 4f,
                animationSpec = tween(300),
                label = "blur"
            )

            MetroLyricsLine(
                entry = entry,
                nextEntryTime = lyricsList.getOrNull(index + 1)?.time,
                effectivePlaybackPosition = currentPosition,
                getCurrentPosition = { currentPosition }, // Fallback to provided state
                isSynced = true,
                isActive = isActive,
                distanceFromCurrent = index - activeIndex,
                textColor = Color.White,
                onClick = { onSeek(entry.time) },
                isSelected = false,
                isSelectionModeActive = false,
                isAutoScrollActive = true,
                expressiveAccent = MaterialTheme.colorScheme.primary,
                bgVisible = true,
                modifier = Modifier
            )
        }
    }
}
