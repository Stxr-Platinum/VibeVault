package com.vibevault.app.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.vibevault.app.domain.repository.MusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun DynamicSearchPlaceholder(
    musicRepository: MusicRepository? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    color: Color = Color.Unspecified
) {
    val defaultPrompt = "Search songs, artists, albums..."
    val placeholders = remember {
        mutableStateListOf(
            defaultPrompt,
            "What's on your mind?",
            "Today's top hits...",
            "Trending music & videos...",
            "What do you want to listen to?",
            "Vibe check..."
        )
    }

    LaunchedEffect(musicRepository) {
        if (musicRepository != null) {
            try {
                val recents = musicRepository.getRecentlyPlayed(10).firstOrNull().orEmpty()
                if (recents.isNotEmpty()) {
                    recents.shuffled().take(3).forEach { track ->
                        if (track.title.isNotBlank() && !track.title.startsWith("Track ")) {
                            placeholders.add("Try \"${track.title}\"")
                        }
                    }
                    recents.map { it.artist }.filter { it.isNotBlank() && it != "Unknown" }.distinct().take(2).forEach { artist ->
                        placeholders.add("Songs by $artist")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(placeholders.size) {
        if (placeholders.isNotEmpty()) {
            while (true) {
                delay(7000L) // Rotate placeholders every 7 seconds
                currentIndex = (currentIndex + 1) % placeholders.size
            }
        }
    }

    val currentText = if (placeholders.isNotEmpty() && currentIndex < placeholders.size) {
        placeholders[currentIndex]
    } else {
        defaultPrompt
    }

    AnimatedContent(
        targetState = currentText,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        label = "SearchPlaceholderAnimation"
    ) { text ->
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
