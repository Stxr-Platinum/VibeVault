package com.vibevault.app.ui.immersive.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vibevault.app.ui.immersive.Immersive
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.LayerBackdrop
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.layerBackdrop
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

val LocalImmersiveHaze = compositionLocalOf<HazeState?> { null }
val LocalImmersiveFrostPage = compositionLocalOf<LayerBackdrop?> { null }

@Composable
fun rememberImmersiveHazeState(): HazeState = remember { HazeState() }

@Composable
fun Modifier.immersiveHazeSource(
    state: HazeState? = LocalImmersiveHaze.current,
    backdrop: LayerBackdrop? = LocalImmersiveBackdrop.current,
    frost: LayerBackdrop? = LocalImmersiveFrostPage.current,
): Modifier {
    if (backdrop == null && frost != null) return this.layerBackdrop(frost)
    val hazed = if (state == null) this else this.hazeSource(state)
    return if (backdrop == null) hazed else hazed.layerBackdrop(backdrop)
}

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.immersiveFrost(
    state: HazeState,
    tint: Color = Immersive.GlassFill.copy(alpha = 0.55f),
    blur: Float = 24f,
): Modifier {
    val page = LocalImmersiveFrostPage.current
    if (page != null && state === LocalImmersiveHaze.current) return this.immersivePageFrost(page, tint, blur)
    return hazeFrost(state, tint, blur)
}

@OptIn(ExperimentalHazeApi::class)
private fun Modifier.hazeFrost(state: HazeState, tint: Color, blur: Float): Modifier = hazeEffect(state) {
    inputScale = HazeInputScale.Fixed(0.33f)
    backgroundColor = Immersive.Page
    tints = listOf(HazeTint(tint))
    blurRadius = blur.dp
    noiseFactor = 0f
}

@Composable
fun Modifier.immersiveGlassSurface(
    shape: Shape,
    state: HazeState? = LocalImmersiveHaze.current,
    liquid: Boolean = true,
    interactionSource: InteractionSource? = null,
): Modifier {
    val page = LocalImmersiveBackdrop.current
    val press = rememberGlassPress(interactionSource)
    if (liquid && page != null && state != null && state === LocalImmersiveHaze.current) {
        return this.immersiveLiquidGlass(shape, page, press = press)
    }
    return this
        .then(
            if (press == null) Modifier else Modifier.graphicsLayer {
                val s = 1f - 0.04f * press()
                scaleX = s
                scaleY = s
            },
        )
        .clip(shape)
        .then(if (state != null) Modifier.immersiveFrost(state) else Modifier.background(Immersive.GlassFill))
        .border(1.dp, Immersive.GlassEdge, shape)
}

