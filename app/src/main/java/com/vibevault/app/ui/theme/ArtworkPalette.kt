package com.vibevault.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Artwork size hints matching BitChord's cache strategies */
const val ROW_ART_PX = 160
const val CARD_ART_PX = 480
const val HEADER_ART_PX = 720
const val NOTIFICATION_ART_PX = 544

private val SIZE_HINT = Regex("""w\d+-h\d+""")

/**
 * Replaces size hints in thumbnail URLs (e.g. YouTube Music innerTube URLs)
 * to request high-res assets matching BitChord's exact caching logic.
 */
fun String?.artworkAt(px: Int): String? = this?.replace(SIZE_HINT, "w$px-h$px")

/**
 * The colours an album, playlist or artist page paints itself in.
 *
 * Apple Music's release pages are not one design tinted five ways — the whole
 * page is derived from the sleeve, down to which grey the metadata line is.
 * Finished set: page tint, accent legible *on that tint*, text colours, and hairline.
 */
@Immutable
data class ArtworkPalette(
    /** The page's background wash. */
    val background: Color,
    /**
     * The colour the artwork's own bottom edge blurs down to.
     */
    val wash: Color,
    /** Fill for the glass buttons and chips that sit on [background]. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected — titles, icons, Play. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl].
 *
 * Replicated exactly from BitChord: theme-aware, seed cache, 128px quantisation on background
 * thread, bottom-edge 18% sampling, and animated transitions.
 */
@Composable
fun rememberArtworkPalette(
    imageUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    artPx: Int = CARD_ART_PX,
): ArtworkPalette {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val (reduceAnimation) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)

    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }
    val knownUpFront = remember(imageUrl) { seed != null }

    LaunchedEffect(imageUrl, artPx) {
        if (imageUrl.isNullOrBlank() || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(artPx))
            .size(PALETTE_PX)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { seedOf(bitmap) } ?: return@LaunchedEffect
        seedCache[imageUrl] = found
        seed = found
    }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outline,
    )

    val spec: AnimationSpec<Color> = if (reduceAnimation || knownUpFront) {
        snap()
    } else {
        tween(TINT_FADE_MS)
    }

    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(target.onBackgroundVariant, spec, label = "tintOnVariant").value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > SEED_CACHE_ENTRIES
}

private const val SEED_CACHE_ENTRIES = 128
private const val PALETTE_PX = 128
private const val TINT_FADE_MS = 260

private data class Seed(val dominant: Color, val vibrant: Color, val edge: Color)

private fun seedOf(bitmap: Bitmap): Seed? {
    fun swatches(builder: Palette.Builder) =
        builder.maximumColorCount(SWATCH_COUNT).generate().swatches

    val found = swatches(Palette.from(bitmap)).ifEmpty {
        swatches(Palette.from(bitmap).clearFilters())
    }
    if (found.isEmpty()) return null

    val dominant = found.maxByOrNull { it.population } ?: return null
    val vibrant = found.maxByOrNull { swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    } ?: dominant

    return Seed(Color(dominant.rgb), Color(vibrant.rgb), bitmap.bottomEdgeColor())
}

private const val SWATCH_COUNT = 24

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)

    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

private const val EDGE_BAND = 0.18f

private fun Seed.toPalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        background = dominant.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.13f }),
        wash = edge.withHsl(
            saturation = { it.coerceIn(0.18f, 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.22f }),
        accent = vibrant.withHsl(
            saturation = { it.coerceAtLeast(0.55f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        onBackgroundVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.91f }),
        wash = edge.withHsl(
            saturation = { it.coerceIn(0.12f, 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.83f }),
        accent = vibrant.withHsl(
            saturation = { it.coerceAtLeast(0.55f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()
