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
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference
import kotlinx.coroutines.launch

data class LyricsLine(
    val timeMs: Long,
    val text: String
)

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
    val (lyricsAnimationStyle) = rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.VIVIMUSIC_1)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 24f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsGlowEffect) = rememberPreference(LyricsGlowEffectKey, defaultValue = false)
    val (lyricsStandardBlur) = rememberPreference(LyricsStandardBlurKey, defaultValue = false)

    val textAlign = remember(lyricsTextPosition) {
        when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Start
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.End
        }
    }

    val lyricsLines = remember(track?.id) {
        listOf(
            LyricsLine(0L, "You've been runnin' 'round, runnin' 'round, runnin' 'round throwin' that dirt all on my name"),
            LyricsLine(5000L, "'Cause you knew that I'd, knew that I'd, knew that I'd call you up"),
            LyricsLine(10000L, "You've been goin' 'round, goin' 'round, goin' 'round every party in LA"),
            LyricsLine(15000L, "'Cause you knew that I'd, knew that I'd, knew that I'd be at one, oh (oh)"),
            LyricsLine(20000L, "I know that dress is karma, perfume regret"),
            LyricsLine(26000L, "You got me thinkin' 'bout when you were mine, oh"),
            LyricsLine(32000L, "And now I'm all up on ya, what'd you expect?"),
            LyricsLine(38000L, "But you're not comin' home with me tonight"),
            LyricsLine(44000L, "You just want attention, you don't want my heart"),
            LyricsLine(50000L, "Maybe you just hate the thought of me with someone new"),
            LyricsLine(56000L, "Yeah, you just want attention, I knew from the start"),
            LyricsLine(62000L, "You're just makin' sure I'm never gettin' over you, oh")
        )
    }

    val activeIndex = remember(positionMs, lyricsLines) {
        val index = lyricsLines.indexOfLast { it.timeMs <= positionMs }
        if (index >= 0) index else 0
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsLines.indices) {
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
            itemsIndexed(lyricsLines) { index, line ->
                val isActive = index == activeIndex

                val targetScale = when (lyricsAnimationStyle) {
                    LyricsAnimationStyle.SLIDE -> if (isActive) 1.05f else 0.95f
                    LyricsAnimationStyle.GLOW -> if (isActive) 1.06f else 1.0f
                    LyricsAnimationStyle.APPLE, LyricsAnimationStyle.APPLE_V2, LyricsAnimationStyle.VIVIMUSIC_1 -> if (isActive) 1.05f else 1.0f
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
                        onSeekTo(line.timeMs)
                    }
                    .padding(vertical = 2.dp)

                Text(
                    text = line.text,
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
