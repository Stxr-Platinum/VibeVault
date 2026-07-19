package com.vibevault.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.vibevault.app.ui.viewmodel.AuthViewModel
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.sync.RealtimeSyncManager
import javax.inject.Inject
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.utils.rememberPreference
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
@androidx.compose.material3.ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("VibeVault", "MainActivity: onCreate started")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Start PlaybackService so it can intercept app termination (swipe from recents)
        // and pause Spotify App Remote properly.
        startService(Intent(this, com.vibevault.app.player.service.PlaybackService::class.java))
        
        Log.d("VibeVault", "MainActivity: super.onCreate finished")
        
        // Note: RealtimeListener handles real-time sync via ProcessLifecycleOwner.
        // Do NOT also start RealtimeSyncManager here — both use the same Realtime
        // instance and will conflict, causing 7s reconnect loops.
        
        val mainViewModel: MainViewModel by viewModels()
        val authViewModel: AuthViewModel by viewModels()
        Log.d("VibeVault", "MainActivity: ViewModels initialized")
        
        // Handle deep links in onCreate (First launch)
        handleIntent(intent)
        
        // Safer splash condition - don't hang forever
        val startTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            // Hide splash if we have a destination OR if 2 seconds have passed (failsafe)
            mainViewModel.startDestination.value == null && elapsed < 2000
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
                
                val (dynamicBackground) = rememberPreference(DynamicBackgroundKey, defaultValue = true)

                // Auto-navigate to Now Playing screen when guest receives a track change
                val listenTogetherVm: com.vibevault.app.ui.viewmodels.ListenTogetherViewModel = hiltViewModel()
                val guestTrackId by listenTogetherVm.guestTrackChanged.collectAsStateWithLifecycle()
                
                androidx.compose.runtime.LaunchedEffect(guestTrackId) {
                    guestTrackId?.let { trackId ->
                        android.util.Log.d("VibeVault", "Guest received track change: $trackId, auto-navigating")
                        val current = navController.currentBackStackEntry?.destination?.route
                        // Only navigate if not already on the Player screen
                        if (current?.startsWith("player_screen") != true) {
                            navController.navigate(Screen.Player.createRoute(trackId))
                        }
                        // Clear the state so it doesn't re-trigger on configuration change
                        listenTogetherVm.clearGuestTrackChanged()
                    }
                }

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
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
                        if (dynamicBackground) {
                            if (currentTrack != null) {
                                coil.compose.AsyncImage(
                                    model = currentTrack!!.albumImageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(1.2f)
                                        .alpha(0.85f)
                                        .blur(22.dp)
                                )
                            }
                            // Deep dark overlay to keep text readable
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                        }
                        
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (currentRoute != Screen.Player.route && currentRoute != Screen.Login.route) {
                                    Column(modifier = Modifier) {
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // CRITICAL: Update the activity's intent to the new one
        setIntent(intent) 
        Log.d("SpotifyDebug", "onNewIntent triggered")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val authViewModel: AuthViewModel by viewModels()
        Log.d("SpotifyDebug", "MainActivity: handleIntent called")
        val uri = intent?.data
        Log.d("SpotifyDebug", "URI = $uri")

        if (uri != null && uri.toString().startsWith("vibevault://spotify-auth-callback")) {
            Log.d("SpotifyDebug", "Spotify callback received")
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            Log.d("SpotifyDebug", "AUTH CODE = $code")
            if (error != null) Log.e("SpotifyDebug", "MainActivity: Auth Error = $error")

            code?.let {
                Log.d("SpotifyDebug", "MainActivity: Triggering callback handling in ViewModel")
                authViewModel.handleSpotifyCallback(it)
            }
        } else {
            Log.d("SpotifyDebug", "MainActivity: URI null or mismatch: $uri")
        }
    }
}
