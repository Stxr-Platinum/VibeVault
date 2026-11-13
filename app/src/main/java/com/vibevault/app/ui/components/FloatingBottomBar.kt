package com.vibevault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics
import com.vibevault.app.ui.immersive.shell.FlatSelectionPill
import com.vibevault.app.ui.immersive.shell.GlassPillMotion
import com.vibevault.app.ui.immersive.shell.GlassSelectionPill
import com.vibevault.app.ui.immersive.shell.LiftedSelectionPill
import com.vibevault.app.ui.immersive.shell.LocalGlassExport
import com.vibevault.app.ui.immersive.shell.LocalImmersiveBackdrop
import com.vibevault.app.ui.immersive.shell.PILL_GROW_HEIGHT
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.layerBackdrop
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.rememberLayerBackdrop
import com.vibevault.app.ui.immersive.shell.immersiveGlassSurface
import com.vibevault.app.ui.immersive.shell.pillPlacement
import com.vibevault.app.ui.immersive.shell.rememberLiquidGlassActive
import com.vibevault.app.ui.theme.LocalReduceMotion
import com.vibevault.app.utils.rememberPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.roundToInt

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

val FLOATING_BAR_MAX_WIDTH = 440.dp

private val PILL_INSET = 6.dp
private val TAB_VERTICAL_PADDING = 9.dp

private val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
private const val STRETCH = 0.16f
private const val SQUASH = 0.5f

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val container = MaterialTheme.colorScheme.surface
    val (dynamicBackground) = rememberPreference(DynamicBackgroundKey, defaultValue = true)
    val glassSpec: AnimationSpec<Float> = GlassSpring
    val liquidGlassActive = rememberLiquidGlassActive()

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { 6.dp.toPx() }
    val n = tabs.size

    val tabWidthPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width - gapPx * (n - 1)) / n
    } else 0f
    val tabStepPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width + gapPx) / n
    } else 0f

    if (liquidGlassActive) {
        val coroutineScope = rememberCoroutineScope()
        val reduceMotion = LocalReduceMotion.current ?: false
        val page = LocalImmersiveBackdrop.current
        val glassPill = page != null
        val barGlass = rememberLayerBackdrop()
        val tabRow = rememberLayerBackdrop()
        val pill = remember { GlassPillMotion(coroutineScope, selectedIndex.coerceAtLeast(0)) }
        pill.reduceMotion = reduceMotion
        pill.stepDp = tabStepPx / density.density

        val padPx = with(density) { PILL_INSET.toPx() }
        val rowHeightPx = rowSize.height.toFloat()
        val restSize = Size(tabWidthPx, rowHeightPx)
        val liftedHeightPx = rowHeightPx + padPx * 2f + with(density) { PILL_GROW_HEIGHT.toPx() }
        val liftedSize = if (rowHeightPx > 0f) {
            Size(liftedHeightPx * tabWidthPx / rowHeightPx, liftedHeightPx)
        } else {
            restSize
        }
        val pillCenterInRow: (Float) -> Offset = { position ->
            val x = position * tabStepPx + tabWidthPx / 2f
            Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
        }
        val showPill = selectedIndex >= 0 && tabWidthPx > 0f
        var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }
        var lastSelectedTabIndex by remember { mutableIntStateOf(selectedIndex) }
        val pillFillColor = Color.White.copy(alpha = 0.12f)

        LaunchedEffect(selectedIndex) {
            if (selectedIndex >= 0) {
                if (lastSelectedTabIndex < 0) {
                    pill.snapTo(selectedIndex)
                } else {
                    pill.animateTo(selectedIndex)
                }
            }
            lastSelectedTabIndex = selectedIndex
            lastHapticTab = selectedIndex
        }

        Box(
            modifier = modifier
                .fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalGlassExport provides if (glassPill) barGlass else null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .immersiveGlassSurface(
                            shape = pillShape,
                            state = hazeState,
                            liquid = true,
                        )
                        .padding(horizontal = PILL_INSET, vertical = PILL_INSET),
                ) {
                    if (showPill && (!glassPill || pill.isFlat)) {
                        FlatSelectionPill(
                            color = pillFillColor,
                            modifier = Modifier
                                .matchParentSize()
                                .pillPlacement(
                                    center = { pillCenterInRow(pill.position) },
                                    size = { pill.liveSize(restSize, liftedSize) },
                                ),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowSize = it }
                            .then(if (glassPill) Modifier.layerBackdrop(tabRow) else Modifier)
                            .pointerInput(tabStepPx, isRtl, selectedIndex >= 0, n) {
                                if (currentSelectedIndex < 0 || tabStepPx <= 0f) return@pointerInput
                                val direction = if (isRtl) -1f else 1f
                                var totalDrag = 0f
                                var dragStartIndex = currentSelectedIndex
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        totalDrag = 0f
                                        dragStartIndex = currentSelectedIndex
                                        pill.startDrag()
                                    },
                                    onDragCancel = { pill.release(currentSelectedIndex) },
                                    onDragEnd = {
                                        val absoluteIndexFloat = (dragStartIndex + totalDrag / tabStepPx)
                                            .coerceIn(0f, tabs.lastIndex.toFloat())
                                        val newIndex = absoluteIndexFloat.roundToInt()
                                        pill.release(newIndex)
                                        if (newIndex != currentSelectedIndex) {
                                            onTabSelected(newIndex)
                                        }
                                    },
                                    onHorizontalDrag = { _, delta ->
                                        totalDrag += delta * direction
                                        val absoluteIndexFloat = (dragStartIndex + totalDrag / tabStepPx)
                                            .coerceIn(0f, tabs.lastIndex.toFloat())
                                        pill.dragTo(absoluteIndexFloat)
                                        val approxTab = absoluteIndexFloat.roundToInt()
                                        if (approxTab != lastHapticTab) {
                                            haptics.play(Haptic.Tick)
                                            lastHapticTab = approxTab
                                        }
                                    },
                                )
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            BottomBarItem(
                                tab = tab,
                                selected = index == selectedIndex,
                                glassSpec = glassSpec,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (showPill && !pill.isFlat) {
                val placement = Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { pillCenterInRow(pill.position) + Offset(padPx, padPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    )
                if (page != null) {
                    GlassSelectionPill(
                        motion = pill,
                        fillColor = pillFillColor,
                        page = page,
                        barGlass = barGlass,
                        tabRow = tabRow,
                        modifier = placement,
                    )
                } else {
                    LiftedSelectionPill(
                        motion = pill,
                        fillColor = pillFillColor,
                        modifier = placement,
                    )
                }
            }
        }
        return
    }

    val pillTargetPx = if (tabStepPx > 0f) {
        selectedIndex * tabStepPx + dragOffset
    } else 0f

    val animatedPillOffset by animateFloatAsState(
        targetValue = pillTargetPx,
        animationSpec = glassSpec,
        label = "pillOffset",
    )

    val lag = if (tabStepPx > 0f) {
        (abs(pillTargetPx - animatedPillOffset) / tabStepPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) { dragOffset = 0f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(Color.Black.copy(alpha = 0.85f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), pillShape)
            .padding(horizontal = PILL_INSET, vertical = PILL_INSET),
    ) {
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(with(density) { rowSize.height.toDp() })
                    .graphicsLayer {
                        translationX = animatedPillOffset
                        scaleX = 1f + lag * STRETCH
                        scaleY = 1f - lag * STRETCH * SQUASH
                    }
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowSize = it }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            if (tabStepPx > 0f) {
                                val ratio = totalDrag / tabStepPx
                                val shift = when {
                                    ratio > 0.35f -> kotlin.math.max(1, ratio.roundToInt())
                                    ratio < -0.35f -> kotlin.math.min(-1, ratio.roundToInt())
                                    else -> 0
                                }
                                val newIndex = (currentSelectedIndex + shift).coerceIn(0, tabs.lastIndex)
                                if (newIndex != currentSelectedIndex) {
                                    onTabSelected(newIndex)
                                }
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            totalDrag += delta
                            val rawPx = when {
                                totalDrag > 0 && currentSelectedIndex == tabs.lastIndex ->
                                    totalDrag * 0.25f
                                totalDrag < 0 && currentSelectedIndex == 0 ->
                                    totalDrag * 0.25f
                                else -> totalDrag
                            }
                            dragOffset = rawPx

                            val approxTab =
                                (currentSelectedIndex + dragOffset / tabStepPx)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                                    .roundToInt()
                            if (approxTab != lastHapticTab) {
                                haptics.play(Haptic.Tick)
                                lastHapticTab = approxTab
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                BottomBarItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    glassSpec = glassSpec,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    glassSpec: AnimationSpec<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = glassSpec,
        label = "tabScale",
    )
    val haptics = rememberHaptics()
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!selected) haptics.play(Haptic.Select)
                onClick()
            }
            .padding(vertical = TAB_VERTICAL_PADDING),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
