package com.vibevault.app.ui.screens.settings

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.vibevault.app.R
import com.vibevault.app.constants.DarkModeKey
import com.vibevault.app.constants.DynamicThemeKey
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.constants.PureBlackKey
import com.vibevault.app.constants.PureBlackMiniPlayerKey
import com.vibevault.app.constants.SelectedThemeColorKey
import com.vibevault.app.ui.theme.DefaultThemeColor
import com.vibevault.app.ui.theme.VibeVaultColorScheme
import com.vibevault.app.ui.theme.vivimusicTheme
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference

data class ThemePalette(
    val nameRes: Int,
    val seedColor: Color
)

val PaletteColors = listOf(
    ThemePalette(0, Color.Transparent), // Sentinel for System/Dynamic colors
    ThemePalette(0, Color(0xFFEC5464)), // Slightly shifted from DefaultThemeColor (0xFFED5564) to avoid conflict
    ThemePalette(0, Color(0xFFD81B60)),
    ThemePalette(0, Color(0xFF8E24AA)),
    ThemePalette(0, Color(0xFF000000)),
    ThemePalette(0, Color(0xFF5E35B1)),
    ThemePalette(0, Color(0xFF3949AB)),
    ThemePalette(0, Color(0xFF1E88E5)),
    ThemePalette(0, Color(0xFF039BE5)),
    ThemePalette(0, Color(0xFF00ACC1)),
    ThemePalette(0, Color(0xFF00897B)),
    ThemePalette(0, Color(0xFF7CB342)),
    ThemePalette(0, Color(0xFFC0CA33)),
    ThemePalette(0, Color(0xFFFDD835)),
    ThemePalette(0, Color(0xFFFFB300)),
    ThemePalette(0, Color(0xFFFB8C00)),
    ThemePalette(0, Color(0xFFF4511E)),
    ThemePalette(0, Color(0xFF6D4C41)),
    ThemePalette(0, Color(0xFF757575)),
    ThemePalette(0, Color(0xFF546E7A)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChangeRaw) = rememberPreference(PureBlackKey, defaultValue = false)
    val (dynamicBackground, onDynamicBackgroundChange) = rememberPreference(DynamicBackgroundKey, defaultValue = true)
    val (_, onPureBlackMiniPlayerChange) = rememberPreference(
        PureBlackMiniPlayerKey,
        defaultValue = false
    )

    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        onPureBlackChangeRaw(enabled)
        onPureBlackMiniPlayerChange(enabled)
    }
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        0xFF1E88E5.toInt()
    )
    val (_, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)

    val selectedThemeColor = Color(selectedThemeColorInt)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Helper function to handle color selection with dynamic theme toggle
    val handleColorSelection: (Color) -> Unit = { color ->
        onSelectedThemeColorChange(color.toArgb())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (dynamicBackground) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (dynamicBackground) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (dynamicBackground) Color.White else MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (isLandscape) {
            LandscapeThemeLayout(
                innerPadding = innerPadding,
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
                dynamicBackground = dynamicBackground,
                onDynamicBackgroundChange = onDynamicBackgroundChange,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = handleColorSelection
            )
        } else {
            PortraitThemeLayout(
                innerPadding = innerPadding,
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
                dynamicBackground = dynamicBackground,
                onDynamicBackgroundChange = onDynamicBackgroundChange,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = handleColorSelection
            )
        }
    }
}

@Composable
fun PortraitThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    dynamicBackground: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        ThemePreviewCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(160.dp),
            darkMode = darkMode,
            pureBlack = pureBlack,
            themeColor = selectedThemeColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        ThemeControls(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            dynamicBackground = dynamicBackground,
            onDynamicBackgroundChange = onDynamicBackgroundChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = onSelectedThemeColorChange
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LandscapeThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    dynamicBackground: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ThemePreviewCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                darkMode = darkMode,
                pureBlack = pureBlack,
                themeColor = selectedThemeColor
            )
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            ThemeControls(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
                dynamicBackground = dynamicBackground,
                onDynamicBackgroundChange = onDynamicBackgroundChange,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = onSelectedThemeColorChange
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ThemeControls(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    dynamicBackground: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // System mode (AUTO)
                ModeCircle(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    dynamicBackground = dynamicBackground,
                    targetMode = DarkMode.AUTO,
                    targetPureBlack = pureBlack,
                    onClick = {
                        onDarkModeChange(DarkMode.AUTO)
                        onDynamicBackgroundChange(false)
                    },
                    showIcon = true
                )
                
                // Vertical divider to separate System from manual modes
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // 2nd Position: Dynamic Background mode
                DynamicBackgroundModeCircle(
                    isSelected = dynamicBackground,
                    onClick = {
                        onDarkModeChange(DarkMode.ON)
                        onPureBlackChange(true)
                        onDynamicBackgroundChange(true)
                    }
                )

                // Dark mode (Grey dark)
                ModeCircle(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    dynamicBackground = dynamicBackground,
                    targetMode = DarkMode.ON,
                    targetPureBlack = false,
                    onClick = {
                        onDarkModeChange(DarkMode.ON)
                        onPureBlackChange(false)
                        onDynamicBackgroundChange(false)
                    },
                    showIcon = false
                )
                
                // Pure Black mode (Amoled black)
                ModeCircle(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    dynamicBackground = dynamicBackground,
                    targetMode = DarkMode.ON,
                    targetPureBlack = true,
                    onClick = {
                        onDarkModeChange(DarkMode.ON)
                        onPureBlackChange(true)
                        onDynamicBackgroundChange(false)
                    },
                    showIcon = false
                )

                // Last Position: White mode (Light)
                ModeCircle(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    dynamicBackground = dynamicBackground,
                    targetMode = DarkMode.OFF,
                    targetPureBlack = false,
                    onClick = {
                        onDarkModeChange(DarkMode.OFF)
                        onPureBlackChange(false)
                        onDynamicBackgroundChange(false)
                    },
                    showIcon = false
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(PaletteColors) { palette ->
                    val isDynamicPalette = palette.seedColor == Color.Transparent
                    val isSelected = if (isDynamicPalette) {
                        selectedThemeColor == DefaultThemeColor
                    } else {
                        selectedThemeColor == palette.seedColor
                    }
                    
                    PaletteItem(
                        palette = palette,
                        isSelected = isSelected,
                        onClick = { 
                            val colorToSave = if (isDynamicPalette) DefaultThemeColor else palette.seedColor
                            onSelectedThemeColorChange(colorToSave) 
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModeCircle(
    darkMode: DarkMode,
    pureBlack: Boolean,
    dynamicBackground: Boolean,
    targetMode: DarkMode,
    targetPureBlack: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isSelected = darkMode == targetMode && pureBlack == targetPureBlack && !dynamicBackground
    
    val effectiveDark = when (targetMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }
    
    // Use actual system colors for AUTO mode on Android 12+
    val modeColorScheme = if (targetMode == DarkMode.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = DefaultThemeColor,
            isDark = effectiveDark,
            isAmoled = false,
            style = PaletteStyle.TonalSpot
        )
    }
    
    val fillColor = when {
        targetPureBlack -> Color.Black
        effectiveDark -> modeColorScheme.surface
        else -> modeColorScheme.surface
    }
    
    // Scale animation on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    val contentDesc = when {
        targetPureBlack -> ""
        targetMode == DarkMode.OFF -> ""
        targetMode == DarkMode.ON -> ""
        else -> ""
    }
    
    // Outer card container similar to ReadYou's SelectableMiniPalette
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(fillColor)
                .then(
                    if (targetPureBlack) {
                        Modifier.border(
                            width = 1.dp,
                            color = modeColorScheme.outlineVariant,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                showIcon -> {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "System Mode",
                        tint = modeColorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    // Scaled checkmark animation overlaid on color circle (ReadYou style)
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.4f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.4f,
                            animationSpec = tween(150)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicBackgroundModeCircle(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF3949AB),
                            Color(0xFFD81B60),
                            Color(0xFF1ed760)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = fadeOut() + scaleOut(
                    targetScale = 0.4f,
                    animationSpec = tween(150)
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PaletteItem(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    val colorScheme = if (palette.seedColor.toArgb() == 0x00000001) {
        VibeVaultColorScheme
    } else {
        rememberDynamicColorScheme(
            seedColor = palette.seedColor,
            isDark = isSystemDark,
            isAmoled = false,
            style = if (palette.seedColor.toArgb() == 0xFF000000.toInt()) PaletteStyle.Monochrome else PaletteStyle.TonalSpot
        )
    }
    
    // Scale animation on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val paletteName = if (palette.nameRes != 0) stringResource(palette.nameRes) else "Default"
    val contentDesc = "Theme palette $paletteName"
    
    // Outer card container similar to ReadYou's SelectableMiniPalette
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        if (palette.seedColor == Color.Transparent) {
            // Draw Dynamic/System icon using Material Design icon directly in the center
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "System Color Scheme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            // Scaled checkmark animation overlaid on the center
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = fadeOut() + scaleOut(
                    targetScale = 0.4f,
                    animationSpec = tween(150)
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    drawRect(
                        color = colorScheme.primary,
                        topLeft = Offset(0f, 0f),
                        size = Size(width, height / 2)
                    )
                    
                    drawRect(
                        color = colorScheme.secondary,
                        topLeft = Offset(0f, height / 2),
                        size = Size(width / 2, height / 2)
                    )
                    
                    drawRect(
                        color = colorScheme.tertiary,
                        topLeft = Offset(width / 2, height / 2),
                        size = Size(width / 2, height / 2)
                    )
                }
                
                // Scaled checkmark animation overlaid on color circle (ReadYou style)
                AnimatedVisibility(
                    visible = isSelected,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.4f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = fadeOut() + scaleOut(
                        targetScale = 0.4f,
                        animationSpec = tween(150)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    modifier: Modifier = Modifier,
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    vivimusicTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top bar mockup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title bar pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Action dot
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }

                // Color swatch row â€” primary, secondary, tertiary, primaryContainer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary â€” largest swatch
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
    }
}
