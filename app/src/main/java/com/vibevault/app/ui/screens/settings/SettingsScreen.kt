package com.vibevault.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vibevault.app.ui.components.Material3SettingsGroup
import com.vibevault.app.ui.components.Material3SettingsItem
import com.vibevault.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // All Settings Items combined
            Material3SettingsGroup(
                items = buildList {
                    add(
                        Material3SettingsItem(
                            icon = rememberVectorPainter(Icons.Filled.Palette),
                            title = { Text("Appearance") },
                            onClick = { navController.navigate(Screen.AppearanceSettings.route) },
                            isExpressive = true
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = rememberVectorPainter(Icons.Filled.PlayArrow),
                            title = { Text("Player and audio") },
                            onClick = { navController.navigate(Screen.PlayerSettings.route) },
                            isExpressive = true
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = rememberVectorPainter(Icons.Filled.Group),
                            title = { Text("Listen Together") },
                            onClick = { navController.navigate(Screen.ListenTogetherSettings.route) },
                            isExpressive = true
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = rememberVectorPainter(Icons.Filled.Language),
                            title = { Text("Content") },
                            onClick = { navController.navigate(Screen.ContentSettings.route) },
                            isExpressive = true
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}
