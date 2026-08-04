package com.vibevault.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FastScrollbar — Interactive fast-scroll thumb for LazyColumns with direct touch mapping
 * and auto-resetting visibility timeout on interaction.
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    headerCount: Int = 0,
    thumbHeight: Dp = 48.dp,
    autoHideTimeoutMs: Long = 2000L,
    getSectionText: ((Int) -> String)? = null
) {
    if (itemCount <= 0) return

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    var touchYPx by remember { mutableFloatStateOf(0f) }
    var currentSectionText by remember { mutableStateOf("") }
    var interactionKey by remember { mutableIntStateOf(0) }

    val firstVisibleIndex = listState.firstVisibleItemIndex
    val firstVisibleOffset = listState.firstVisibleItemScrollOffset
    val totalLayoutItems = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)

    // Reset visibility timeout on any scroll, drag, or touch interaction
    LaunchedEffect(firstVisibleIndex, firstVisibleOffset, isDragging, interactionKey) {
        isVisible = true
        if (!isDragging) {
            delay(autoHideTimeoutMs)
            if (!listState.isScrollInProgress) {
                isVisible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "ScrollbarAlpha"
    )

    if (alpha <= 0.001f && !isDragging) return

    // Calculate position for scroll state when not dragging
    val scrollProgress = (firstVisibleIndex.toFloat() / totalLayoutItems.toFloat()).coerceIn(0f, 1f)
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val maxThumbOffsetPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)

    // Direct touch position mapping while dragging, or progress-based position when idle/scrolling
    val currentThumbOffsetPx = if (isDragging) {
        (touchYPx - (thumbHeightPx / 2f)).coerceIn(0f, maxThumbOffsetPx)
    } else {
        scrollProgress * maxThumbOffsetPx
    }

    val currentThumbOffsetDp = with(density) { currentThumbOffsetPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .alpha(alpha)
            .onGloballyPositioned { coordinates ->
                containerHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(itemCount, totalLayoutItems) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        interactionKey++
                        touchYPx = offset.y
                        
                        val fraction = (offset.y / containerHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * totalLayoutItems).toInt().coerceIn(0, totalLayoutItems - 1)
                        val trackIndex = (targetIndex - headerCount).coerceIn(0, itemCount - 1)
                        currentSectionText = getSectionText?.invoke(trackIndex) ?: "#${trackIndex + 1}"
                        
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDragEnd = { 
                        isDragging = false 
                        interactionKey++
                    },
                    onDragCancel = { 
                        isDragging = false 
                        interactionKey++
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        isDragging = true
                        interactionKey++
                        touchYPx = change.position.y
                        
                        val fraction = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * totalLayoutItems).toInt().coerceIn(0, totalLayoutItems - 1)
                        val trackIndex = (targetIndex - headerCount).coerceIn(0, itemCount - 1)
                        currentSectionText = getSectionText?.invoke(trackIndex) ?: "#${trackIndex + 1}"

                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopEnd
    ) {
        // Floating Section Bubble Indicator (follows touch Y directly during dragging)
        if (isDragging && currentSectionText.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .padding(end = 36.dp)
                    .offset(y = currentThumbOffsetDp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = currentSectionText,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Fast-Scroll Thumb Handle Bar
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .offset(y = currentThumbOffsetDp)
                .width(if (isDragging) 8.dp else 5.dp)
                .height(thumbHeight)
                .clip(CircleShape)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.5f)
                )
        )
    }
}
