package com.vibevault.app.ui.immersive.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevault.app.ui.immersive.Immersive
import com.vibevault.app.ui.immersive.motion.ImmersiveMotion
import com.vibevault.app.ui.theme.rememberReduceMotion
import dev.chrisbanes.haze.HazeState
import kotlin.math.sign

private val FOLD_DISTANCE = 32.dp

@Stable
class ImmersiveChromeState internal constructor(private val foldDistancePx: Float) {
    var minimized by mutableStateOf(false)
        private set

    private var travel = 0f

    fun expand() {
        travel = 0f
        minimized = false
    }

    fun minimize() {
        travel = 0f
        minimized = true
    }

    val scrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy != 0f) {
                if (travel != 0f && sign(travel) != sign(dy)) travel = 0f
                travel += dy
                if (travel <= -foldDistancePx) minimize()
                else if (travel >= foldDistancePx) expand()
            } else if (available.y > 0f && minimized) {
                expand()
            }
            return Offset.Zero
        }
    }
}

val LocalImmersiveChrome = staticCompositionLocalOf<ImmersiveChromeState?> { null }

@Composable
fun rememberImmersiveChromeState(): ImmersiveChromeState {
    val px = with(LocalDensity.current) { FOLD_DISTANCE.toPx() }
    return remember { ImmersiveChromeState(px) }
}

@Composable
fun Modifier.immersiveChromeScroll(state: ImmersiveChromeState? = LocalImmersiveChrome.current): Modifier =
    if (state == null) this else this.nestedScroll(state.scrollConnection)

private val BarControlSize = 44.dp
private val BarControlSizeMinimized = 38.dp
val LocalBarControlSize = compositionLocalOf { BarControlSize }

@Composable
fun rememberBarControlSize(): Dp {
    val minimized = LocalImmersiveChrome.current?.minimized == true
    val size by animateDpAsState(
        targetValue = if (minimized) BarControlSizeMinimized else BarControlSize,
        animationSpec = ImmersiveMotion.chromeSpring(rememberReduceMotion()),
        label = "immersiveBarControlSize",
    )
    return size
}

val ImmersiveHeaderContentHeight = 64.dp
private val HeaderCorner = 28.dp
private val HeaderOverscan = 48.dp
private val HeaderShape = RoundedCornerShape(bottomStart = HeaderCorner, bottomEnd = HeaderCorner)

@Composable
fun immersiveHeaderContentPadding(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + ImmersiveHeaderContentHeight + 12.dp

@Composable
fun rememberTopBarCollapse(state: LazyListState, distance: Dp = 40.dp): () -> Float {
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return remember(state, distancePx) {
        val fraction: () -> Float = {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
        }
        fraction
    }
}

@Composable
internal fun ImmersiveTopGlass(visible: () -> Float, modifier: Modifier, hazeState: HazeState?) {
    val page = LocalImmersiveBackdrop.current
    val reduce = rememberReduceMotion()
    val overscan = with(LocalDensity.current) { HeaderOverscan.roundToPx() }
    val glass = when {
        page != null && hazeState != null && hazeState === LocalImmersiveHaze.current ->
            Modifier.immersiveLiquidGlass(
                shape = HeaderShape,
                page = page,
                tint = Immersive.Page.copy(alpha = 0.28f),
                lens = true,
                rim = true,
            )
        hazeState != null -> Modifier.clip(HeaderShape).immersiveFrost(hazeState, tint = Immersive.Page.copy(alpha = 0.38f))
        else -> Modifier.background(Immersive.Page.copy(alpha = 0.85f), HeaderShape)
    }
    Box(
        modifier
            .layout { measurable, c ->
                val p = measurable.measure(Constraints.fixed(c.maxWidth, c.maxHeight + overscan))
                layout(c.maxWidth, c.maxHeight) { p.place(0, -overscan) }
            }
            .graphicsLayer {
                val v = visible()
                alpha = if (reduce) (if (v > 0.02f) 1f else 0f) else v
            }
            .then(glass),
    ) {
        // Specular bottom hairline highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.2f to Color.White.copy(alpha = 0.18f),
                        0.8f to Color.White.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    )
                )
        )
        // Smooth gradient fade into Color.Transparent at the bottom edge so it blends seamlessly with zero hard block cutoff
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Immersive.Page.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
}

@Composable
fun ImmersiveFloatingHeader(
    title: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalImmersiveHaze.current,
    collapse: () -> Float = { 1f },
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val controlSize = rememberBarControlSize()
    Box(modifier.fillMaxWidth().holdsTouches()) {
        ImmersiveTopGlass(collapse, Modifier.matchParentSize(), hazeState)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(ImmersiveHeaderContentHeight)
                .padding(start = Immersive.Gutter, end = Immersive.Gutter - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Immersive.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                CompositionLocalProvider(LocalBarControlSize provides controlSize) { trailing() }
            }
        }
    }
}

@Composable
fun ImmersiveBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Immersive.Text,
) {
    val size = LocalBarControlSize.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ImmersiveBarAction(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Immersive.Text,
) {
    val size = LocalBarControlSize.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

private fun Modifier.holdsTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

