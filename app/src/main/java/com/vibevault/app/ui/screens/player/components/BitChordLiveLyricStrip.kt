package com.vibevault.app.ui.screens.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vibevault.app.data.lyrics.LyricsEntry
import com.vibevault.app.ui.icons.BitChordIcons
import kotlinx.coroutines.delay

private const val LYRIC_FADE_FRACTION = 0.28f
private const val LYRIC_FADE_MIN_MS = 160f
private const val LYRIC_FADE_MAX_MS = 700f
private const val UNSUNG_ALPHA_STRIP = 0.55f
private const val INSTRUMENTAL_MARK = "Instrumental"

private val INTRO_LINES = listOf(
    "Beat's landing",
    "Song's starting",
    "Intro's cooking",
    "Warming up",
    "Here we go",
    "Setting the mood",
    "Drums are in",
    "Bass first, words later",
    "Turn it up",
    "Vibe check",
    "Wait for it",
    "Feel that build",
    "Let it ride",
    "Speakers breathing",
    "Hook's on the way",
    "Eyes closed",
    "Almost words",
    "Tuning in",
    "Buckle up",
    "Let it breathe",
    "Lyrics loading",
    "Cue the vocals",
    "Slow burn",
    "Nod along",
    "Groove's on deck",
    "Ease into it",
    "Big things coming",
    "Stage is set",
    "Sit with it",
    "Any second now",
    "Locked in",
    "Deep breath"
)

private val LYRICS_LOADING_LINES = listOf(
    "Getting lyrics",
    "Chasing the words",
    "Digging up the lyrics",
    "Words incoming",
    "On the hunt for lyrics",
    "Fetching the verses",
    "Tracking down the words",
    "Lyrics loading",
    "Scanning for lyrics",
    "Words on the way",
    "Looking this one up"
)

/**
 * Ticks song position every frame so the sweep remains silky smooth between player updates.
 */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { owner, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(positionMs, isPlaying, foreground) {
        clock.longValue = positionMs
        if (!isPlaying || !foreground) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue += frame - previousFrame
                previousFrame = frame
            }
        }
    }
    return clock
}

/**
 * Calculates fractional character index into line text based on word timestamps.
 */
private fun calculateRevealedChars(entry: LyricsEntry, positionMs: Long): Float {
    val text = entry.text
    val words = entry.words
    if (words.isNullOrEmpty()) {
        return if (positionMs >= entry.time) text.length.toFloat() else 0f
    }
    var offset = 0
    words.forEachIndexed { index, word ->
        val startMs = (word.startTime * 1000).toLong()
        val endMs = (word.endTime * 1000).toLong()
        val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
        val end = start + word.text.length
        if (positionMs < startMs) return start.toFloat()
        if (positionMs < endMs) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            val through = (positionMs - startMs).toFloat() / span
            return start + through * word.text.length
        }
        val next = words.getOrNull(index + 1)
        if (next != null) {
            val nextStartMs = (next.startTime * 1000).toLong()
            if (positionMs < nextStartMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (nextStartMs - endMs).coerceAtLeast(1L)
                val through = (positionMs - endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
        }
        offset = end
    }
    return text.length.toFloat()
}

private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    lineStart: Int,
    lineEnd: Int,
): Float {
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val next = layout.getHorizontalPosition(
        (index + 1).coerceAtMost(lineEnd),
        usePrimaryDirection = true,
    )
    return here + (next - here) * (chars - index)
}

private fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealedChars: Float) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val right = if (revealedChars >= end) {
            layout.getLineRight(visualLine)
        } else {
            horizontalAt(layout, revealedChars, start, end)
        }
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = layout.getLineTop(visualLine),
            right = right,
            bottom = layout.getLineBottom(visualLine),
        ) {
            this@sweepTo.drawContent()
        }
    }
}

/**
 * SweptLyricLine renders dim text layered with bright swept text revealed character-by-character.
 */
@Composable
private fun SweptLyricLine(
    entry: LyricsEntry,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    var layout by remember(entry.text) { mutableStateOf<TextLayoutResult?>(null) }

    val sweep = Modifier.drawWithContent {
        val position = clock.longValue
        val lastWordEnd = entry.words?.lastOrNull()?.let { (it.endTime * 1000).toLong() }
        val endMs = lastWordEnd ?: (entry.time + 4000L)
        when {
            position >= endMs -> drawContent()
            position <= entry.time -> Unit
            else -> layout?.let { sweepTo(it, calculateRevealedChars(entry, position)) }
        }
    }

    Box(modifier) {
        Text(
            text = entry.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
        )
        Text(
            text = entry.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            modifier = sweep,
        )
    }
}

/**
 * Interactive 1-line BitChord live lyric strip that tracks playback word-by-word.
 */
@Composable
fun BitChordLiveLyricStrip(
    lyricsList: List<LyricsEntry>,
    isLoadingLyrics: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    trackKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyricsList.isEmpty()) {
        if (isLoadingLyrics) {
            val text = remember(trackKey) { LYRICS_LOADING_LINES.random() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            var visible by remember(trackKey) { mutableStateOf(true) }
            LaunchedEffect(trackKey) {
                delay(4000L)
                visible = false
            }
            val alpha by animateFloatAsState(
                targetValue = if (visible) 0.55f else 0.25f,
                animationSpec = tween(durationMillis = 600),
                label = "lyricsUnavailableAlpha",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp)
                    .graphicsLayer { this.alpha = alpha },
            ) {
                Text(
                    text = "Lyrics not available",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        return
    }

    val clock = rememberLyricClock(positionMs, isPlaying)

    val index by remember(lyricsList) {
        derivedStateOf { lyricsList.indexOfLast { it.time <= clock.longValue } }
    }
    val current = lyricsList.getOrNull(index)
    val instrumental = current == null || current.text.isBlank()
    val firstSung = remember(lyricsList) { lyricsList.indexOfFirst { it.text.isNotBlank() } }
    val intro = instrumental && firstSung >= 0 && index < firstSung
    val introLine = remember(trackKey) { INTRO_LINES.random() }

    val displayText = when {
        intro -> introLine
        instrumental -> INSTRUMENTAL_MARK
        else -> current!!.text
    }

    val isWordSynced = current?.words?.isNotEmpty() == true

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                if (instrumental) {
                    alpha = 0.5f
                    return@graphicsLayer
                }
                val start = lyricsList.getOrNull(index)?.time ?: 0L
                val end = lyricsList.getOrNull(index + 1)?.time
                    ?: durationMs.takeIf { it > start }
                    ?: (start + 4000L)
                val fade = ((end - start) * LYRIC_FADE_FRACTION)
                    .coerceIn(LYRIC_FADE_MIN_MS, LYRIC_FADE_MAX_MS)
                val remaining = (end - clock.longValue).toFloat()
                alpha = 0.85f * (remaining / fade).coerceIn(0.3f, 1f)
            },
    ) {
        if (instrumental) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }

        if (!instrumental && isWordSynced && current != null) {
            SweptLyricLine(
                entry = current,
                clock = clock,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                dimAlpha = UNSUNG_ALPHA_STRIP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Spacer(Modifier.width(6.dp))

        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = "Open full lyrics",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}
