package com.vibevault.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibevault.app.ui.components.MiniPlayer
import com.vibevault.app.ui.navigation.AppNavHost
import com.vibevault.app.ui.navigation.Screen
import com.vibevault.app.ui.navigation.VibeBottomBar
import com.vibevault.app.ui.theme.VibePrimary
import com.vibevault.app.ui.theme.VibeVaultTheme
import com.vibevault.app.ui.viewmodel.MainViewModel
import com.vibevault.app.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — Single-activity with Scaffold, bottom nav,
 * MiniPlayer bar, and NavHost.
 *
 * The MiniPlayer sits directly above the bottom nav in a Column,
 * ensuring both remain visible when a track is playing.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val mainViewModel: MainViewModel by viewModels()
        
        // Keep splash screen visible until we have a start destination
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.startDestination.value == null
        }
        
        enableEdgeToEdge()
        setContent {
            VibeVaultTheme {
                val navController = rememberNavController()
                val playerViewModel: PlayerViewModel = hiltViewModel()
                val startDestination by mainViewModel.startDestination.collectAsStateWithLifecycle()
                val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                if (startDestination == null) {
                    // ── Spotify-style Splash Screen ──────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = VibePrimary,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "VibeVault",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (currentRoute != Screen.Player.route && currentRoute != Screen.Login.route) {
                                Column(modifier = Modifier.navigationBarsPadding()) {
                                    // MiniPlayer sits above the bottom nav
                                    if (currentTrack != null) {
                                        MiniPlayer(
                                            playerViewModel = playerViewModel,
                                            onExpand = { trackId ->
                                                navController.navigate(Screen.Player.createRoute(trackId))
                                            }
                                        )
                                    }

                                    // Bottom nav always visible below
                                    VibeBottomBar(navController = navController)
                                }
                            }
                        }
                    ) { innerPadding ->
                        AppNavHost(
                            navController = navController,
                            innerPadding = innerPadding,
                            playerViewModel = playerViewModel,
                            startDestination = startDestination!!
                        )
                    }
                }

            }
        }
    }
}
