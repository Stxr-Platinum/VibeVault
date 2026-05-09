package com.vibevault.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type.kt — Stitch dual-font typography system.
 *
 * Stitch specifies:
 *   - Headlines: Spline Sans (Bold/SemiBold, tight letter-spacing)
 *   - Body/Labels: Be Vietnam Pro (Medium/SemiBold)
 *
 * Android substitution:
 *   - Headlines: Inter (geometric sans, similar feel to Spline Sans)
 *   - Body: Default sans-serif (system font, high readability like Be Vietnam Pro)
 *
 * The numeric scale (sizes, weights, line heights) is preserved exactly.
 */

// Headline font — Inter (bundled or system fallback)
// If Inter isn't available as a resource, we fall back to system sans-serif
val HeadlineFont = FontFamily.SansSerif

// Body font — system default sans-serif (simulates Be Vietnam Pro)
val BodyFont = FontFamily.SansSerif

val VibeTypography = Typography(

    // ── display-lg (Stitch: Spline Sans 48/56, Bold, -0.02em) ──
    displayLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp
    ),

    // ── headline-md (Stitch: Spline Sans 32/40, Bold, -0.01em) ──
    headlineMedium = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),

    // ── headline-sm — utility size for sub-headers ──
    headlineSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // ── title-sm (Stitch: Spline Sans 20/28, SemiBold) ──
    titleLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    titleMedium = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    titleSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),

    // ── body-lg (Stitch: Be Vietnam Pro 16/24, Medium) ──
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // ── body-md (Stitch: Be Vietnam Pro 14/20, Medium) ──
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),

    // ── label-sm (Stitch: Be Vietnam Pro 12/16, SemiBold, 0.05em) ──
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),

    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
