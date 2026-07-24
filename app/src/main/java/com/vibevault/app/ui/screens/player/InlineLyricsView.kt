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

@Composable
fun InlineLyricsView(
    track: Track?,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
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

    val lyricsEntries = remember(track?.id) {
        listOf(
            LyricsEntry(0L, "You've been runnin' 'round, runnin' 'round, runnin' 'round throwin' that dirt all on my name", words = listOf(
                WordTimestamp("You've ", 0.0, 0.5), WordTimestamp("been ", 0.5, 1.0), WordTimestamp("runnin' ", 1.0, 1.5), WordTimestamp("'round, ", 1.5, 2.0),
                WordTimestamp("runnin' ", 2.0, 2.5), WordTimestamp("'round, ", 2.5, 3.0), WordTimestamp("runnin' ", 3.0, 3.5), WordTimestamp("'round ", 3.5, 4.0),
                WordTimestamp("throwin' ", 4.0, 4.3), WordTimestamp("that ", 4.3, 4.5), WordTimestamp("dirt ", 4.5, 4.7), WordTimestamp("all ", 4.7, 4.8), WordTimestamp("on ", 4.8, 4.9), WordTimestamp("my ", 4.9, 5.0), WordTimestamp("name", 5.0, 5.5)
            )),
            LyricsEntry(6000L, "'Cause you knew that I'd, knew that I'd, knew that I'd call you up", words = listOf(
                WordTimestamp("'Cause ", 6.0, 6.5), WordTimestamp("you ", 6.5, 7.0), WordTimestamp("knew ", 7.0, 7.5), WordTimestamp("that ", 7.5, 8.0), WordTimestamp("I'd, ", 8.0, 8.5),
                WordTimestamp("knew ", 8.5, 9.0), WordTimestamp("that ", 9.0, 9.5), WordTimestamp("I'd, ", 9.5, 10.0), WordTimestamp("knew ", 10.0, 10.5), WordTimestamp("that ", 10.5, 11.0), WordTimestamp("I'd ", 11.0, 11.5),
                WordTimestamp("call ", 11.5, 12.0), WordTimestamp("you ", 12.0, 12.5), WordTimestamp("up", 12.5, 13.0)
            )),
            LyricsEntry(14000L, "You've been goin' 'round, goin' 'round, goin' 'round every party in LA", words = listOf(
                WordTimestamp("You've ", 14.0, 14.5), WordTimestamp("been ", 14.5, 15.0), WordTimestamp("goin' ", 15.0, 15.5), WordTimestamp("'round, ", 15.5, 16.0),
                WordTimestamp("goin' ", 16.0, 16.5), WordTimestamp("'round, ", 16.5, 17.0), WordTimestamp("goin' ", 17.0, 17.5), WordTimestamp("'round ", 17.5, 18.0),
                WordTimestamp("every ", 18.0, 18.5), WordTimestamp("party ", 18.5, 19.0), WordTimestamp("in ", 19.0, 19.5), WordTimestamp("LA", 19.5, 20.0)
            )),
            LyricsEntry(21000L, "'Cause you knew that I'd, knew that I'd, knew that I'd be at one, oh (oh)", words = listOf(
                WordTimestamp("'Cause ", 21.0, 21.5), WordTimestamp("you ", 21.5, 22.0), WordTimestamp("knew ", 22.0, 22.5), WordTimestamp("that ", 22.5, 23.0), WordTimestamp("I'd, ", 23.0, 23.5),
                WordTimestamp("knew ", 23.5, 24.0), WordTimestamp("that ", 24.0, 24.5), WordTimestamp("I'd, ", 24.5, 25.0), WordTimestamp("knew ", 25.0, 25.5), WordTimestamp("that ", 25.5, 26.0), WordTimestamp("I'd ", 26.0, 26.5),
                WordTimestamp("be ", 26.5, 27.0), WordTimestamp("at ", 27.0, 27.5), WordTimestamp("one, ", 27.5, 28.0), WordTimestamp("oh ", 28.0, 28.5), WordTimestamp("(oh)", 28.5, 29.0)
            )),
            LyricsEntry(30000L, "I know that dress is karma, perfume regret"),
            LyricsEntry(36000L, "You got me thinkin' 'bout when you were mine, oh"),
            LyricsEntry(42000L, "And now I'm all up on ya, what'd you expect?"),
            LyricsEntry(48000L, "But you're not comin' home with me tonight"),
            LyricsEntry(54000L, "You just want attention, you don't want my heart"),
            LyricsEntry(60000L, "Maybe you just hate the thought of me with someone new"),
            LyricsEntry(66000L, "Yeah, you just want attention, I knew from the start"),
            LyricsEntry(72000L, "You're just makin' sure I'm never gettin' over you, oh")
        )
    }

    val activeIndex = remember(positionMs, lyricsEntries) {
        val index = lyricsEntries.indexOfLast { it.time <= positionMs }
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy((20f * lyricsLineSpacing).dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
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
                        onClick = { onSeekTo(entry.time) },
                        onLongClick = {},
                        isSelected = false,
                        isSelectionModeActive = false,
                        isAutoScrollActive = true,
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

                    val targetAlpha = if (isActive) 1f else 0.35f
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

                    val fontSize = if (isActive) lyricsTextSize.sp else (lyricsTextSize - 4f).coerceAtLeast(14f).sp
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
                            onSeekTo(entry.time)
                        }
                        .padding(vertical = 2.dp)

                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = fontSize,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
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
    }
}

