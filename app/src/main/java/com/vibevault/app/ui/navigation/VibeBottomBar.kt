package com.vibevault.app.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vibevault.app.ui.theme.*
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.utils.rememberPreference

/**
 * VibeBottomBar — Bottom navigation bar.
 *
 * Stitch spec: Dark background, green selected state, 5 tabs
 * (Home, Search, Your Library, Premium, Create).
 * Using glassmorphic aesthetic with subtle transparency.
 */
@Composable
fun VibeBottomBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val (dynamicBackground) = rememberPreference(DynamicBackgroundKey, defaultValue = true)

    NavigationBar(
        modifier = modifier,
        containerColor = if (dynamicBackground) Color.Transparent else Color(0xFF121212),
        contentColor = Color.White
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // Pop up to start destination to avoid stacking
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon!! else screen.unselectedIcon!!,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color(0xFF808080),
                    unselectedTextColor = Color(0xFF808080),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
