package com.vibevault.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier

enum class Hook(val id: String) {
    HOME("home")
}

enum class Surface(val id: String) {
    HOME("home")
}

data class ExtensionBlockEntry(
    val id: String,
    val hook: Hook,
    val surface: Surface,
    val content: @Composable (contextJson: String, modifier: Modifier) -> Unit
)

object ExtensionBlockRegistry {
    val blocks = mutableStateListOf<ExtensionBlockEntry>()
}

@Composable
fun ExtensionBlocks(
    hook: Hook,
    surface: Surface,
    contextJson: String,
    modifier: Modifier = Modifier
) {
    ExtensionBlockRegistry.blocks
        .filter { it.hook == hook && it.surface == surface }
        .forEach { entry ->
            entry.content(contextJson, modifier)
        }
}

