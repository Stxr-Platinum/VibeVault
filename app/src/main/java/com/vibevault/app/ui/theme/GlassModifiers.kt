package com.vibevault.app.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassModifiers.kt — Glassmorphic Compose modifier extensions.
 *
 * Implements the Stitch design system's frosted-glass aesthetic:
 *   - API 31+: Real backdrop blur via RenderEffect
 *   - Below API 31: Graceful degradation to semi-transparent overlay
 *
 * Stitch spec: "The playback bar uses a frosted glass effect
 * (backdrop-filter) to overlay the content scroll."
 */

/**
 * Glassmorphic panel modifier — the primary glass effect.
 *
 * @param cornerRadius Corner radius (Stitch default: 10dp for containers)
 * @param blurRadius Blur intensity in pixels (default: 50f for heavy frost)
 * @param alpha Background overlay alpha (0.05f = 5% white)
 * @param borderAlpha Border glow alpha (0.1f = 10% white)
 */
fun Modifier.glassPanel(
    cornerRadius: Dp = 10.dp,
    blurRadius: Float = 50f,
    alpha: Float = 0.05f,
    borderAlpha: Float = 0.1f
): Modifier = this
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        } else {
            Modifier  // Graceful degradation — no blur on older APIs
        }
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        color = Color.White.copy(alpha = alpha),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 0.5.dp,
        color = Color.White.copy(alpha = borderAlpha),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Glass button modifier — lighter glass for interactive elements.
 * Stitch spec: "Secondary actions use a ghost style with a white border
 * or a subtle charcoal fill."
 */
fun Modifier.glassButton(
    cornerRadius: Dp = 24.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Glass card modifier — for media cards.
 * Stitch spec: "Media cards feature a #282828 background, 10px radius,
 * 16px internal padding."
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 10.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        color = VibeCardBackground,
        shape = RoundedCornerShape(cornerRadius)
    )
