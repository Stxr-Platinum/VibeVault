package com.vibevault.app.ui.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Exact Immersive tokens ported from `myspoti`'s `ImmersiveTheme.kt`.
 */
object Immersive {
    val Page: Color = Color.Black
    val Raised: Color = Color(0xFF1C1C1E)
    val Separator: Color = Color.White.copy(alpha = 0.10f)
    val Text: Color = Color.White
    val TextSecondary: Color = Color.White.copy(alpha = 0.60f)
    val TextTertiary: Color = Color.White.copy(alpha = 0.38f)
    val Accent: Color = Color(0xFF1ED760)
    val GlassFill: Color = Color(0xFF2C2C34).copy(alpha = 0.88f)
    val GlassEdge: Color = Color.White.copy(alpha = 0.12f)
    val Destructive: Color = Color(0xFFFF453A)

    val CoverSmall: Shape = RoundedCornerShape(6.dp)
    val CoverMedium: Shape = RoundedCornerShape(10.dp)
    val CoverLarge: Shape = RoundedCornerShape(14.dp)
    val Card: Shape = RoundedCornerShape(14.dp)
    val Button: Shape = RoundedCornerShape(14.dp)

    val Gutter = 20.dp
    val RowHeight = 60.dp
    val BottomChrome = 170.dp
}

fun Modifier.glass(shape: Shape = CircleShape): Modifier =
    this.background(Immersive.GlassFill, shape).border(1.dp, Immersive.GlassEdge, shape)

