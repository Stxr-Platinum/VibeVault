package com.vibevault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.vibevault.app.ui.theme.VibePrimary
import com.vibevault.app.ui.theme.VibeSurfaceHigh

/**
 * UserAvatar — Premium circular avatar with fallback to initial.
 * Matches the design seen in the Library header.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String?,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val initial = displayName?.firstOrNull()?.uppercase() ?: "V"
    val fallbackUrl = "https://ui-avatars.com/api/?name=${displayName ?: "V"}&background=1DB954&color=fff&size=200"
    val model = avatarUrl.takeIf { !it.isNullOrBlank() } ?: fallbackUrl
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VibeSurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = VibePrimary
                    )
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = VibePrimary
                    )
                }
            }
        )
    }
}
