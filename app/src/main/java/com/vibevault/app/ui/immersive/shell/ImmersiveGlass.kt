package com.vibevault.app.ui.immersive.shell

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vibevault.app.constants.LiquidGlassKey
import com.vibevault.app.ui.immersive.Immersive
import com.vibevault.app.ui.immersive.motion.ImmersiveMotion
import com.vibevault.app.ui.immersive.shell.backdrop.Backdrop
import com.vibevault.app.ui.immersive.shell.backdrop.BackdropEffectScope
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.LayerBackdrop
import com.vibevault.app.ui.immersive.shell.backdrop.backdrops.rememberLayerBackdrop
import com.vibevault.app.ui.immersive.shell.backdrop.drawBackdrop
import com.vibevault.app.ui.immersive.shell.backdrop.effects.blur
import com.vibevault.app.ui.immersive.shell.backdrop.effects.colorControls
import com.vibevault.app.ui.immersive.shell.backdrop.effects.lens
import com.vibevault.app.ui.immersive.shell.backdrop.highlight.Highlight
import com.vibevault.app.ui.immersive.shell.backdrop.shadow.Shadow
import com.vibevault.app.ui.theme.rememberReduceMotion
import com.vibevault.app.utils.rememberPreference

enum class LiquidGlassAvailability {
    AVAILABLE,
    REDUCED,
    UNSUPPORTED,
}

enum class GlassPath {
    LENS,
    LIVE_BLUR,
    FROST,
}

@Composable
fun liquidGlassAvailability(): LiquidGlassAvailability {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LiquidGlassAvailability.UNSUPPORTED
    return if (rememberReduceMotion()) LiquidGlassAvailability.REDUCED else LiquidGlassAvailability.AVAILABLE
}

internal fun glassPathOf(
    switchOn: Boolean,
    availability: LiquidGlassAvailability,
    sdk: Int = Build.VERSION.SDK_INT
): GlassPath = when {
    !switchOn || availability != LiquidGlassAvailability.AVAILABLE -> GlassPath.FROST
    sdk >= Build.VERSION_CODES.TIRAMISU -> GlassPath.LENS
    else -> GlassPath.LIVE_BLUR
}

@Composable
fun rememberLiquidGlassActive(): Boolean {
    val availability = liquidGlassAvailability()
    val (on) = rememberPreference(LiquidGlassKey, defaultValue = true)
    return glassPathOf(on, availability) != GlassPath.FROST
}

val LocalImmersiveBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
fun rememberImmersiveBackdrop(): LayerBackdrop = rememberLayerBackdrop()

internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

private const val GLASS_RESOLUTION_SCALE = 0.33f
private const val THIN_DP = 56f
private const val LARGE_DP = 200f

internal val GlassTint = Color(0xFF1C1C22).copy(alpha = 0.42f)
private const val LARGE_TINT_ALPHA = 0.60f

private val GlassShadow = Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.22f))
private val GlassHighlight = Highlight(width = 0.75f.dp, alpha = 0.9f)
private const val PRESS_BULGE = 0.35f

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
internal fun Modifier.immersiveLiquidGlass(
    shape: Shape,
    page: LayerBackdrop,
    tint: Color = GlassTint,
    lens: Boolean = true,
    rim: Boolean = true,
    press: (() -> Float)? = null,
): Modifier {
    val export = LocalGlassExport.current
    val backdrop = remember(page) { PageBackdrop(page) }
    val bend = lens && shape is CornerBasedShape && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val adaptive = tint == GlassTint
    val effects = remember(bend, press) {
        val block: BackdropEffectScope.() -> Unit = {
            if (size.isSpecified) {
                val k = GLASS_RESOLUTION_SCALE
                val minDp = size.minDimension / k / density
                val depth = ((minDp - THIN_DP) / (LARGE_DP - THIN_DP)).coerceIn(0f, 1f)
                colorControls(brightness = -0.03f, saturation = 1.6f)
                blur(mix(3.5f, 14f, depth) * density * k)
                if (bend) {
                    val bulge = 1f + PRESS_BULGE * (press?.invoke() ?: 0f)
                    val height = mix((minDp * 0.42f).coerceAtMost(22f), 30f, depth)
                    val amount = height * mix(1.7f, 1.2f, depth) * bulge
                    lens(
                        refractionHeight = height * density * k,
                        refractionAmount = amount * density * k,
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            }
        }
        block
    }
    val surface = remember(tint, adaptive) {
        val block: DrawScope.() -> Unit = {
            val depth = ((size.minDimension / density - THIN_DP) / (LARGE_DP - THIN_DP)).coerceIn(0f, 1f)
            drawRect(
                color = if (adaptive) tint.copy(alpha = mix(tint.alpha, LARGE_TINT_ALPHA, depth)) else tint,
                size = size
            )
        }
        block
    }
    val layerBlock = remember(press) {
        press?.let { p ->
            val block: GraphicsLayerScope.() -> Unit = {
                val s = 1f - 0.04f * p()
                scaleX = s
                scaleY = s
            }
            block
        }
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = effects,
        highlight = if (rim) ({ GlassHighlight }) else null,
        shadow = if (rim) ({ GlassShadow }) else null,
        layerBlock = layerBlock,
        onDrawSurface = surface,
        exportedBackdrop = export,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

@Composable
internal fun Modifier.immersivePageFrost(
    page: LayerBackdrop,
    tint: Color,
    blur: Float,
): Modifier {
    val backdrop = remember(page) { PageBackdrop(page) }
    val effects = remember(blur) {
        val block: BackdropEffectScope.() -> Unit = {
            blur(blur * density * GLASS_RESOLUTION_SCALE)
        }
        block
    }
    val surface = remember(tint) {
        val block: DrawScope.() -> Unit = { drawRect(color = tint, size = size) }
        block
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = effects,
        highlight = null,
        shadow = null,
        onDrawSurface = surface,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

@Composable
internal fun rememberGlassPress(source: InteractionSource?): (() -> Float)? {
    if (source == null || rememberReduceMotion()) return null
    val pressed by source.collectIsPressedAsState()
    val progress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) ImmersiveMotion.pressSpring<Float>() else ImmersiveMotion.releaseSpring<Float>(),
        label = "glassPress",
    )
    return remember(progress) { { progress.value } }
}

internal class PageBackdrop(private val page: LayerBackdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val source = page.layerCoordinates
        page.moves
        val attached = coordinates != null && source != null && source.isAttached && coordinates.isAttached &&
            !page.isInsideSource(coordinates)
        val sameWindow = attached && source!!.findRoot() === coordinates!!.findRoot()
        val usable = attached && (sameWindow || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        drawRect(if (usable) Immersive.Page else Immersive.GlassFill, topLeft = FAR_TOP_LEFT, size = FAR_SIZE)
        if (!usable) return
        if (sameWindow) {
            with(page) { drawBackdrop(density, coordinates, layerBlock) }
        } else {
            val surface = coordinates!!.positionOnScreen()
            with(page) { drawSources { src -> surface - src.positionOnScreen() } }
        }
    }

    private companion object {
        val FAR_TOP_LEFT = Offset(-20_000f, -20_000f)
        val FAR_SIZE = Size(40_000f, 40_000f)
    }
}

private fun LayoutCoordinates.findRoot(): LayoutCoordinates {
    var c = this
    while (true) c = c.parentCoordinates ?: return c
}

