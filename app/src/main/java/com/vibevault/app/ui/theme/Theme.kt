package com.vibevault.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Theme.kt — VibeVault MaterialTheme wrapping the Stitch Design System.
 *
 * Always dark — music streaming apps are dark-mode-first.
 * The color scheme maps every Stitch token to its Material 3 slot.
 */

private val VibeVaultColorScheme = darkColorScheme(
    // Surfaces
    background              = VibeBg,
    surface                 = VibeSurface,
    surfaceVariant          = VibeSurfaceVariant,
    surfaceTint             = VibeSurfaceTint,
    surfaceBright           = VibeSurfaceBright,
    surfaceDim              = VibeSurfaceDim,
    surfaceContainer        = VibeSurface,
    surfaceContainerHigh    = VibeSurfaceHigh,
    surfaceContainerHighest = VibeSurfaceHighest,
    surfaceContainerLow     = VibeSurfaceContainerLow,
    surfaceContainerLowest  = VibeSurfaceContainerLowest,

    // Primary
    primary                 = VibePrimary,
    onPrimary               = VibeOnPrimary,
    primaryContainer        = VibePrimaryContainer,
    onPrimaryContainer      = VibeOnPrimaryContainer,
    inversePrimary          = VibeInversePrimary,

    // Secondary
    secondary               = VibeSecondary,
    onSecondary             = VibeOnSecondary,
    secondaryContainer      = VibeSecondaryContainer,
    onSecondaryContainer    = VibeOnSecondaryContainer,

    // Tertiary
    tertiary                = VibeTertiary,
    onTertiary              = VibeOnTertiary,
    tertiaryContainer       = VibeTertiaryContainer,
    onTertiaryContainer     = VibeOnTertiaryContainer,

    // Error
    error                   = VibeError,
    onError                 = VibeOnError,
    errorContainer          = VibeErrorContainer,
    onErrorContainer        = VibeOnErrorContainer,

    // Content
    onBackground            = VibeOnBackground,
    onSurface               = VibeOnSurface,
    onSurfaceVariant        = VibeOnSurfaceVariant,
    outline                 = VibeOutline,
    outlineVariant          = VibeOutlineVariant,

    // Inverse
    inverseSurface          = VibeInverseSurface,
    inverseOnSurface        = VibeInverseOnSurface,
)

@Composable
fun VibeVaultTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VibeBg.toArgb()
            window.navigationBarColor = VibeBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = VibeVaultColorScheme,
        typography = VibeTypography,
        content = content
    )
}
