package com.vibevault.app.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevault.app.constants.*
import com.vibevault.app.domain.model.Track
import com.vibevault.app.data.lyrics.LyricsEntry
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference
import com.vibevault.app.ui.screens.player.components.MetroLyricsLine
import com.vibevault.app.ui.screens.player.components.ShimmerLyricsLoading
import com.vibevault.app.ui.screens.player.components.LyricsOffsetDialog
import kotlinx.coroutines.launch

@Composable
fun InlineLyricsView(
    track: Track?,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    lyricsEntries: List<LyricsEntry> = emptyList(),
    isLoading: Boolean = false,
    lyricsOffset: Long = 0L,
    onOffsetChange: (Long) -> Unit = {},
    activeLyricColor: Color = Color(0xFFFFB4A2),
    inactiveLyricColor: Color = Color.White.copy(alpha = 0.4f),
    modifier: Modifier = Modifier
) {
    val (lyricsTextPosition) = rememberEnumPreference(LyricsTextPositionKey, defaultValue = LyricsPosition.LEFT)
    val (lyricsAnimationStyle) = rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.VIVIMUSIC_1)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 24f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsGlowEffect) = rememberPreference(LyricsGlowEffectKey, defaultValue = false)
    val (lyricsStandardBlur) = rememberPreference(LyricsStandardBlurKey, defaultValue = true)

    var showOffsetDialog by remember { mutableStateOf(false) }
    val effectivePosition = positionMs + lyricsOffset

    val textAlign = remember(lyricsTextPosition) {
        when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Start
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.End
        }
    }

    if (isLoading || (lyricsEntries.isEmpty() && track != null)) {
        ShimmerLyricsLoading(lyricsTextPosition = lyricsTextPosition, modifier = modifier)
        return
    }

    if (lyricsEntries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No lyrics available",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val activeIndex = remember(effectivePosition, lyricsEntries) {
        val index = lyricsEntries.indexOfLast { it.time <= effectivePosition }
        if (index >= 0) index else 0
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsEntries.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = (activeIndex - 1).coerceAtLeast(0),
                    scrollOffset = 0
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy((18f * lyricsLineSpacing).dp),
            contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp)
        ) {
            itemsIndexed(lyricsEntries) { index, entry ->
                val isActive = index == activeIndex
                val hasWordTimings = entry.words?.isNotEmpty() == true

                // Delegate to MetroLyricsLine for advanced fluidity styles (METRO_LYRICS, VIVIMUSIC_1, APPLE, APPLE_V2, LYRICS_V2)
                if (hasWordTimings || 
                    lyricsAnimationStyle == LyricsAnimationStyle.METRO_LYRICS || 
                    lyricsAnimationStyle == LyricsAnimationStyle.VIVIMUSIC_1 || 
                    lyricsAnimationStyle == LyricsAnimationStyle.APPLE || 
                    lyricsAnimationStyle == LyricsAnimationStyle.APPLE_V2 || 
                    lyricsAnimationStyle == LyricsAnimationStyle.LYRICS_V2
                ) {
                    val blurModifier = if (!isActive && lyricsStandardBlur) Modifier.blur(2.dp) else Modifier
                    MetroLyricsLine(
                        entry = entry,
                        nextEntryTime = lyricsEntries.getOrNull(index + 1)?.time,
                        effectivePlaybackPosition = effectivePosition,
                        getCurrentPosition = { effectivePosition },
                        lyricsOffset = lyricsOffset,
                        isSynced = true,
                        isActive = isActive,
                        distanceFromCurrent = kotlin.math.abs(index - activeIndex),
                        lyricsTextPosition = lyricsTextPosition.name,
                        textColor = if (isActive) activeLyricColor else inactiveLyricColor,
                        expressiveAccent = activeLyricColor,
                        showRomanized = false,
                        showTranslated = false,
                        onClick = { onSeekTo((entry.time - lyricsOffset).coerceAtLeast(0L)) },
                        onLongClick = {},
                        isSelected = false,
                        isSelectionModeActive = false,
                        isAutoScrollActive = true,
                        modifier = blurModifier
                    )
                } else {
                    // Custom Fluidity Animation Styles (SLIDE, GLOW, FADE, KARAOKE, NONE)
                    val targetScale = when (lyricsAnimationStyle) {
                        LyricsAnimationStyle.SLIDE -> if (isActive) 1.06f else 0.94f
                        LyricsAnimationStyle.GLOW -> if (isActive) 1.05f else 1.0f
                        LyricsAnimationStyle.FADE -> if (isActive) 1.04f else 0.96f
                        LyricsAnimationStyle.KARAOKE -> if (isActive) 1.05f else 1.0f
                        else -> 1.0f
                    }

                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = if (lyricsAnimationStyle == LyricsAnimationStyle.SLIDE || lyricsAnimationStyle == LyricsAnimationStyle.KARAOKE) {
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        } else {
                            tween(350)
                        },
                        label = "scale"
                    )

                    val targetAlpha = if (isActive) 1f else 0.35f
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(350),
                        label = "alpha"
                    )

                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isActive) activeLyricColor else inactiveLyricColor,
                        animationSpec = tween(350),
                        label = "animatedTextColor"
                    )

                    val fontSize = if (isActive) lyricsTextSize.sp else (lyricsTextSize - 3f).coerceAtLeast(14f).sp
                    val activeShadow = if (isActive && (lyricsGlowEffect || lyricsAnimationStyle == LyricsAnimationStyle.GLOW)) {
                        Shadow(color = activeLyricColor.copy(alpha = 0.8f), blurRadius = 18f, offset = Offset.Zero)
                    } else null

                    val itemModifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .alpha(alpha)
                        .then(
                            if (!isActive && lyricsStandardBlur) Modifier.blur(2.dp) else Modifier
                        )
                        .clickable {
                            onSeekTo((entry.time - lyricsOffset).coerceAtLeast(0L))
                        }
                        .padding(vertical = 4.dp)

                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = fontSize,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            lineHeight = (fontSize.value * 1.35f).sp,
                            shadow = activeShadow
                        ),
                        color = animatedTextColor,
                        textAlign = textAlign,
                        modifier = itemModifier
                    )
                }
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


