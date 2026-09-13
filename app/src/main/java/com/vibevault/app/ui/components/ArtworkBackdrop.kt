package com.vibevault.app.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.ui.theme.ArtworkPalette
import com.vibevault.app.utils.rememberPreference

/**
 * The colour a surface takes from the artwork it is about: a flat tint
 * everywhere, with the artwork itself blurred across the top and dissolved
 * down into that tint.
 */
@Composable
fun ArtworkBackdrop(
    palette: ArtworkPalette,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    washFraction: Float = 0.72f,
) {
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    val canBlur = !reduceDynamicBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier.background(palette.background)) {
        if (canBlur && imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(washFraction)
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                    .blur(72.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        val isDark = isSystemInDarkTheme()
        val alpha0 = if (isDark) 0.40f else 0.08f
        val alpha1 = if (isDark) 0.55f else 0.25f
        val alpha2 = if (isDark) 0.85f else 0.72f

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = alpha0),
                        0.20f to palette.background.copy(alpha = alpha1),
                        0.55f to palette.background.copy(alpha = alpha2),
                        washFraction to palette.background,
                    ),
                ),
        )
    }
}

/**
 * The colour a detail page is made of below its artwork.
 */
@Composable
fun ArtworkWash(
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    washFraction: Float = 0.62f,
) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                lerp(washFraction, 1f, 0.35f) to lerp(palette.wash, palette.background, 0.12f),
                lerp(washFraction, 1f, 0.70f) to lerp(palette.wash, palette.background, 0.55f),
                1f to palette.background,
            ),
        )
        blob(palette.accent.copy(alpha = 0.13f), Offset(0.12f, washFraction + 0.08f), 0.80f)
        blob(palette.elevated.copy(alpha = 0.30f), Offset(0.96f, washFraction + 0.30f), 0.95f)
    }
}

private fun DrawScope.blob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
