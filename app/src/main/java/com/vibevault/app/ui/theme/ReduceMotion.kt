package com.vibevault.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalReduceMotion = compositionLocalOf<Boolean?> { null }

@Composable
fun rememberReduceMotion(): Boolean =
    LocalReduceMotion.current ?: rememberReduceMotionSetting()

@Composable
private fun rememberReduceMotionSetting(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

