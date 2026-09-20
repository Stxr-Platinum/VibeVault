package com.vibevault.app.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.utils.rememberPreference
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

private val FADE_RUN = 96.dp
private const val PEAK = 0.75f
private const val SCRIM_PEAK = 0.42f
private const val SCRIM_STOPS = 12

/**
 * The glass behind every top bar: full blur along the top edge, ramping to
 * nothing on the way down, smoothly blending with cards and content below.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
    scrimColor: Color,
) {
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    if (reduceDynamicBlur) return

    val height = topBarHeight() + FADE_RUN

    val scrim = remember(scrimColor) {
        Brush.verticalGradient(
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
            },
        )
    }

    val fadeMask = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black,
                0.35f to Color.Black,
                0.55f to Color.Black.copy(alpha = 0.85f),
                0.72f to Color.Black.copy(alpha = 0.50f),
                0.88f to Color.Black.copy(alpha = 0.18f),
                1.0f to Color.Transparent,
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = fadeMask,
                    blendMode = BlendMode.DstIn,
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.ultraThin(pageColor),
                ) {
                    canDrawArea = { true }
                    progressive = HazeProgressive.verticalGradient(
                        easing = EaseOutCubic,
                        startIntensity = PEAK,
                        endIntensity = 0f,
                    )
                    noiseFactor = 0f
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim),
        )
    }
}
