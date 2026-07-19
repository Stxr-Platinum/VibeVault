package com.vibevault.app.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

fun listItemShape(index: Int, count: Int, radius: Dp = 24.dp): androidx.compose.ui.graphics.Shape {
    return when {
        count == 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(6.dp)
    }
}
