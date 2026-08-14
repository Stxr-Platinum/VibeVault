package com.vibevault.app.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import androidx.palette.graphics.Palette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle

import com.materialkolor.rememberDynamicColorScheme
import com.vibevault.app.ui.screens.settings.DarkMode
import com.vibevault.app.constants.DarkModeKey
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.constants.PureBlackKey
import com.vibevault.app.constants.SelectedThemeColorKey
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference

val DefaultThemeColor = Color(0xFFFFFFFF)

val LocalSolidColorScheme = staticCompositionLocalOf { darkColorScheme() }

val VibeVaultColorScheme = darkColorScheme(
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
    primary                 = VibePrimary,
    onPrimary               = VibeOnPrimary,
    primaryContainer        = VibePrimaryContainer,
    onPrimaryContainer      = VibeOnPrimaryContainer,
    inversePrimary          = VibeInversePrimary,
    secondary               = VibeSecondary,
    onSecondary             = VibeOnSecondary,
    secondaryContainer      = VibeSecondaryContainer,
    onSecondaryContainer    = VibeOnSecondaryContainer,
    tertiary                = VibeTertiary,
    onTertiary              = VibeOnTertiary,
    tertiaryContainer       = VibeTertiaryContainer,
    onTertiaryContainer     = VibeOnTertiaryContainer,
    error                   = VibeError,
    onError                 = VibeOnError,
    errorContainer          = VibeErrorContainer,
    onErrorContainer        = VibeOnErrorContainer,
    onBackground            = VibeOnBackground,
    onSurface               = VibeOnSurface,
    onSurfaceVariant        = VibeOnSurfaceVariant,
    outline                 = VibeOutline,
    outlineVariant          = VibeOutlineVariant,
    inverseSurface          = VibeInverseSurface,
    inverseOnSurface        = VibeInverseOnSurface,
)

@Composable
fun VibeVaultTheme(
    themeColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val (darkModePref) = rememberEnumPreference(DarkModeKey, DarkMode.ON)
    val (pureBlackPref) = rememberPreference(PureBlackKey, defaultValue = false)
    val (dynamicBackgroundPref) = rememberPreference(DynamicBackgroundKey, defaultValue = true)
    val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = 0xFF1E88E5.toInt())

    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (darkModePref) {
        DarkMode.ON -> true
        DarkMode.OFF -> false
        DarkMode.AUTO -> isSystemDark
    }
    
    val selectedThemeColor = themeColor ?: Color(selectedThemeColorInt)
    
    val initialColorScheme = if (selectedThemeColorInt == 0x00000001) {
        VibeVaultColorScheme
    } else {
        val useSystemDynamicColor = (selectedThemeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        if (useSystemDynamicColor) {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            rememberDynamicColorScheme(
                seedColor = selectedThemeColor,
                isDark = isDarkTheme,
                isAmoled = false,
                style = if (selectedThemeColor.toArgb() == 0xFF000000.toInt()) PaletteStyle.Monochrome else PaletteStyle.TonalSpot
            )
        }
    }
    
    val colorScheme = remember(initialColorScheme, pureBlackPref, isDarkTheme, dynamicBackgroundPref) {
        val baseScheme = if (isDarkTheme && pureBlackPref) {
            initialColorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black
            )
        } else {
            initialColorScheme
        }
        
        if (dynamicBackgroundPref) {
            baseScheme.copy(
                background = Color.Transparent,
                surface = Color(0x33000000),
                surfaceContainer = Color(0x33000000),
                surfaceContainerLowest = Color(0x33000000),
                surfaceContainerLow = Color(0x33000000),
                surfaceContainerHigh = Color(0x55000000),
                surfaceContainerHighest = Color(0x55000000),
                surfaceVariant = Color(0x44000000)
            )
        } else {
            baseScheme
        }
    }
    
    val solidScheme = remember(initialColorScheme, pureBlackPref, isDarkTheme) {
        if (isDarkTheme && pureBlackPref) {
            initialColorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black
            )
        } else {
            initialColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSolidColorScheme provides solidScheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VibeTypography,
            content = content
        )
    }
}

@Composable
fun vivimusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    VibeVaultTheme(content = content)
}

fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this).maximumColorCount(8).generate()
    val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
    return swatch?.rgb?.let { Color(it) } ?: Color(0xFF1E88E5)
}
