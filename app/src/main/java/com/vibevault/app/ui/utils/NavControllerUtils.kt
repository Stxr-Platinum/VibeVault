/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.vibevault.app.ui.utils

import androidx.navigation.NavController


fun NavController.backToMain() {
    val mainRoutes = listOf("home", "search", "library")

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

