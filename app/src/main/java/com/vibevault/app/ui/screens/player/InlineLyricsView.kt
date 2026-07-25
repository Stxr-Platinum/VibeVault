package com.vibevault.app.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevault.app.constants.*
import com.vibevault.app.domain.model.Track
import com.vibevault.app.data.lyrics.LyricsEntry
import com.vibevault.app.data.lyrics.WordTimestamp
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference
import com.vibevault.app.ui.screens.player.components.MetroLyricsLine
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontStyle

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
fun InlineLyricsView(
    track: Track?,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    lyricsEntries: List<LyricsEntry> = emptyList(),
    isLoadingLyrics: Boolean = false,
    activeLyricColor: Color = Color(0xFFFFB4A2),
    inactiveLyricColor: Color = Color.White.copy(alpha = 0.4f),
    modifier: Modifier = Modifier
) {
    val (lyricsTextPosition) = rememberEnumPreference(LyricsTextPositionKey, defaultValue = LyricsPosition.LEFT)
    val (lyricsAnimationStyle) = rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.METRO_LYRICS)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 24f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsGlowEffect) = rememberPreference(LyricsGlowEffectKey, defaultValue = false)
    val (lyricsStandardBlur) = rememberPreference(LyricsStandardBlurKey, defaultValue = true)

    val textAlign = remember(lyricsTextPosition) {
        when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Start
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.End
        }
    }

    if (isLoadingLyrics) {
        // Loading Shimmer Placeholder Animation
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

    if (lyricsEntries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (track != null) "No lyrics found" else "No track playing",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val activeIndex = remember(positionMs, lyricsEntries) {
        val index = lyricsEntries.indexOfLast { it.time <= positionMs }
        if (index >= 0) index else 0
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

    LaunchedEffect(activeIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && activeIndex in lyricsEntries.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = (activeIndex - 1).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy((20f * lyricsLineSpacing).dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 64.dp)
        ) {
            itemsIndexed(lyricsEntries) { index, entry ->
                val isActive = index == activeIndex
                
                if (lyricsAnimationStyle == LyricsAnimationStyle.METRO_LYRICS || lyricsAnimationStyle == LyricsAnimationStyle.APPLE || lyricsAnimationStyle == LyricsAnimationStyle.APPLE_V2) {
                    val blurModifier = if (!isActive && lyricsStandardBlur) Modifier.blur(3.dp) else Modifier
                    MetroLyricsLine(
                        entry = entry,
                        nextEntryTime = lyricsEntries.getOrNull(index + 1)?.time,
                        effectivePlaybackPosition = positionMs,
                        getCurrentPosition = { positionMs },
                        isSynced = true,
                        isActive = isActive,
                        distanceFromCurrent = kotlin.math.abs(index - activeIndex),
                        lyricsTextPosition = lyricsTextPosition.name,
                        textColor = if (isActive) activeLyricColor else inactiveLyricColor,
                        expressiveAccent = activeLyricColor,
                        showRomanized = false,
                        showTranslated = false,
                        onClick = { 
                            isAutoScrollEnabled = true
                            onSeekTo(entry.time) 
                        },
                        onLongClick = {},
                        isSelected = false,
                        isSelectionModeActive = false,
                        isAutoScrollActive = isAutoScrollEnabled,
                        lyricsTextSize = if (entry.isBackground) lyricsTextSize * 0.7f else lyricsTextSize,
                        lyricsLineSpacing = lyricsLineSpacing,
                        modifier = blurModifier
                    )
                } else {
                    val targetScale = when (lyricsAnimationStyle) {
                        LyricsAnimationStyle.SLIDE -> if (isActive) 1.05f else 0.95f
                        LyricsAnimationStyle.GLOW -> if (isActive) 1.06f else 1.0f
                        else -> 1.0f
                    }

                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = if (lyricsAnimationStyle == LyricsAnimationStyle.SLIDE) {
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        } else {
                            tween(400)
                        },
                        label = "scale"
                    )

                    val targetAlpha = if (isActive) 1f else (if (entry.isBackground) 0.25f else 0.35f)
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(400),
                        label = "alpha"
                    )

                    val targetTextColor = if (isActive) activeLyricColor else inactiveLyricColor
                    val animatedTextColor by animateColorAsState(
                        targetValue = targetTextColor,
                        animationSpec = tween(400),
                        label = "animatedTextColor"
                    )

                    val baseFontSize = if (entry.isBackground) lyricsTextSize * 0.75f else lyricsTextSize
                    val fontSize = if (isActive) baseFontSize.sp else (baseFontSize - 3f).coerceAtLeast(12f).sp
                    val activeShadow = if (isActive && (lyricsGlowEffect || lyricsAnimationStyle == LyricsAnimationStyle.GLOW)) {
                        Shadow(color = activeLyricColor, blurRadius = 16f, offset = Offset.Zero)
                    } else null

                    val itemModifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .alpha(alpha)
                        .then(
                            if (!isActive && lyricsStandardBlur) Modifier.blur(3.dp) else Modifier
                        )
                        .clickable {
                            isAutoScrollEnabled = true
                            onSeekTo(entry.time)
                        }
                        .padding(vertical = if (entry.isBackground) 0.dp else 2.dp)

                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = fontSize,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            fontStyle = if (entry.isBackground) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = (fontSize.value * lyricsLineSpacing).sp,
                            shadow = activeShadow
                        ),
                        color = animatedTextColor,
                        textAlign = if (entry.isBackground) TextAlign.Center else textAlign,
                        modifier = itemModifier
                    )
                }
            }
        }

        // Floating Resync Button when user manually scrolls
        AnimatedVisibility(
            visible = !isAutoScrollEnabled,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            ElevatedButton(
                onClick = {
                    isAutoScrollEnabled = true
                    if (activeIndex in lyricsEntries.indices) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = (activeIndex - 1).coerceAtLeast(0),
                                scrollOffset = 0
                            )
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = activeLyricColor,
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

