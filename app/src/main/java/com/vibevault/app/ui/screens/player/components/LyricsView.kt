package com.vibevault.app.ui.screens.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.*
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
import com.vibevault.app.constants.LyricsAnimationStyle
import com.vibevault.app.constants.LyricsAnimationStyleKey
import com.vibevault.app.constants.LyricsPosition
import com.vibevault.app.constants.LyricsTextPositionKey
import com.vibevault.app.data.lyrics.LyricsEntry
import com.vibevault.app.utils.rememberEnumPreference
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyricsList: List<LyricsEntry>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    isLoading: Boolean = false,
    lyricsOffset: Long = 0L,
    onOffsetChange: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (lyricsTextPosition) = rememberEnumPreference(LyricsTextPositionKey, defaultValue = LyricsPosition.CENTER)
    val (lyricsAnimationStyle) = rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.VIVIMUSIC_1)
    var showOffsetDialog by remember { mutableStateOf(false) }

    val effectivePosition = currentPosition + lyricsOffset

    if (isLoading) {
        ShimmerLyricsLoading(lyricsTextPosition = lyricsTextPosition, modifier = modifier)
        return
    }

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
    val activeIndex = remember(lyricsList, effectivePosition) {
        val index = lyricsList.indexOfLast { it.time <= effectivePosition }
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

    if (showOffsetDialog) {
        LyricsOffsetDialog(
            currentOffsetMs = lyricsOffset,
            onOffsetChange = onOffsetChange,
            onDismiss = { showOffsetDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 180.dp)
        ) {
            itemsIndexed(lyricsList) { index, entry ->
                val isActive = index == activeIndex

                MetroLyricsLine(
                    entry = entry,
                    nextEntryTime = lyricsList.getOrNull(index + 1)?.time,
                    effectivePlaybackPosition = effectivePosition,
                    getCurrentPosition = { effectivePosition },
                    lyricsOffset = lyricsOffset,
                    isSynced = true,
                    isActive = isActive,
                    distanceFromCurrent = kotlin.math.abs(index - activeIndex),
                    textColor = Color.White,
                    onClick = { onSeek((entry.time - lyricsOffset).coerceAtLeast(0L)) },
                    isSelected = false,
                    isSelectionModeActive = false,
                    isAutoScrollActive = true,
                    expressiveAccent = MaterialTheme.colorScheme.primary,
                    bgVisible = true,
                    lyricsTextPosition = lyricsTextPosition.name,
                    modifier = Modifier
                )
            }
        }

        // Resync Badge Control Button
        Surface(
            onClick = { showOffsetDialog = true },
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "Resync lyrics",
                    modifier = Modifier.size(14.dp),
                    tint = if (lyricsOffset != 0L) MaterialTheme.colorScheme.primary else Color.White
                )
                Text(
                    text = if (lyricsOffset == 0L) "Resync" else "${if (lyricsOffset > 0) "+" else ""}${lyricsOffset}ms",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = if (lyricsOffset != 0L) MaterialTheme.colorScheme.primary else Color.White
                )
            }
        }
    }
}

