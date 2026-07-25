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

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
fun LyricsView(
    lyricsList: List<LyricsEntry>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    isLoadingLyrics: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isLoadingLyrics) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shimmerAlpha by animateFloatAsState(
                targetValue = 0.6f,
                animationSpec = tween(durationMillis = 800),
                label = "shimmerAlpha"
            )
            repeat(5) { index ->
                val lineAlpha = (0.2f + (index % 3) * 0.2f) * shimmerAlpha
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.85f else 0.65f)
                        .height(24.dp)
                        .alpha(lineAlpha)
                        .background(Color.White, RoundedCornerShape(12.dp))
                )
            }
        }
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
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    isAutoScrollEnabled = false
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }
    
    // Find the currently active line
    val activeIndex = remember(lyricsList, currentPosition) {
        val index = lyricsList.indexOfLast { it.time <= currentPosition }
        if (index != -1) index else 0
    }

    LaunchedEffect(activeIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && activeIndex in lyricsList.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -300
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = 200.dp, bottom = 260.dp)
        ) {
            itemsIndexed(lyricsList) { index, entry ->
                val isActive = index == activeIndex

                MetroLyricsLine(
                    entry = entry,
                    nextEntryTime = lyricsList.getOrNull(index + 1)?.time,
                    effectivePlaybackPosition = currentPosition,
                    getCurrentPosition = { currentPosition },
                    isSynced = true,
                    isActive = isActive,
                    distanceFromCurrent = kotlin.math.abs(index - activeIndex),
                    textColor = Color.White,
                    onClick = {
                        isAutoScrollEnabled = true
                        onSeek(entry.time)
                    },
                    isSelected = false,
                    isSelectionModeActive = false,
                    isAutoScrollActive = isAutoScrollEnabled,
                    expressiveAccent = MaterialTheme.colorScheme.primary,
                    bgVisible = true,
                    lyricsTextSize = if (entry.isBackground) 20f else 28f,
                    lyricsLineSpacing = 1.3f
                )
            }
        }

        // Resync Button when scrolling manually
        AnimatedVisibility(
            visible = !isAutoScrollEnabled,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            ElevatedButton(
                onClick = {
                    isAutoScrollEnabled = true
                    if (activeIndex in lyricsList.indices) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = activeIndex,
                                scrollOffset = -300
                            )
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Resync lyrics", modifier = Modifier.size(18.dp))
                    Text(text = "Resync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
