package com.vibevault.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.vibevault.app.ui.viewmodel.AuthViewModel
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.sync.RealtimeSyncManager
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.SupabaseClient
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibevault.app.ui.components.MiniPlayer
import com.vibevault.app.ui.components.FloatingBottomBar
import com.vibevault.app.ui.components.BottomTab
import com.vibevault.app.ui.components.FLOATING_BAR_MAX_WIDTH
import com.vibevault.app.ui.icons.BitChordIcons
import com.vibevault.app.ui.navigation.AppNavHost
import com.vibevault.app.ui.navigation.Screen
import com.vibevault.app.ui.navigation.VibeBottomBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.vibevault.app.constants.DynamicBackgroundKey
import com.vibevault.app.constants.DynamicThemeKey
import com.vibevault.app.constants.EnableHighRefreshRateKey
import android.os.Build
import com.vibevault.app.utils.rememberPreference
import com.vibevault.app.ui.theme.VibePrimary
import com.vibevault.app.ui.theme.VibeVaultTheme
import com.vibevault.app.ui.theme.extractThemeColor
import com.vibevault.app.ui.viewmodel.MainViewModel
import com.vibevault.app.ui.viewmodel.PlayerViewModel
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
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

    @Inject
    lateinit var listenTogetherManager: com.vibevault.app.data.listentogether.ListenTogetherManager

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
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
            val (enableHighRefreshRate) = rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
            val (enableDynamicTheme) = rememberPreference(DynamicThemeKey, defaultValue = true)
            var themeColor by remember { androidx.compose.runtime.mutableStateOf<Color?>(null) }
            val context = LocalContext.current

            androidx.compose.runtime.LaunchedEffect(enableHighRefreshRate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val window = this@MainActivity.window
                        val params = window.attributes
                        val displayManager = getSystemService(android.hardware.display.DisplayManager::class.java)
                        @Suppress("DEPRECATION")
                        val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                        val modes = display?.supportedModes
                        val maxMode = modes?.maxByOrNull { it.refreshRate }
                        if (maxMode != null) {
                            params.preferredDisplayModeId = if (enableHighRefreshRate) maxMode.modeId else 0
                            window.attributes = params
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed setting high refresh rate display mode", e)
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(currentTrack, enableDynamicTheme) {
                if (!enableDynamicTheme) {
                    themeColor = null
                    return@LaunchedEffect
                }
                val url = currentTrack?.albumImageUrl
                if (url != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .build()
                            val result = context.imageLoader.execute(request)
                            val bitmap = result.drawable?.toBitmap()
                            if (bitmap != null) {
                                val color = bitmap.extractThemeColor()
                                themeColor = color
                            } else {
                                android.util.Log.d("DynamicTheme", "Failed to extract bitmap")
                                themeColor = null
                            }
                        } catch (e: Exception) {
                            themeColor = null
                        }
                    }
                } else {
                    themeColor = null
                }
            }

            VibeVaultTheme(themeColor = themeColor) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalListenTogetherManager provides listenTogetherManager
                ) {
                    val navController = rememberNavController()
                val startDestination by mainViewModel.startDestination.collectAsStateWithLifecycle()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                val (dynamicBackground) = rememberPreference(DynamicBackgroundKey, defaultValue = true)

                // Handle guest track change from host: pop up PlayerScreen if not open, or update in-place if already open
                val listenTogetherVm: com.vibevault.app.ui.viewmodels.ListenTogetherViewModel = hiltViewModel()
                val guestTrackId by listenTogetherVm.guestTrackChanged.collectAsStateWithLifecycle()
                
                androidx.compose.runtime.LaunchedEffect(guestTrackId) {
                    guestTrackId?.let { trackId ->
                        android.util.Log.d("VibeVault", "Guest received track change: $trackId")
                        val current = navController.currentBackStackEntry?.destination?.route
                        if (current?.startsWith("player") != true) {
                            navController.navigate(Screen.Player.createRoute(trackId))
                        }
                        // Clear the state so it doesn't re-trigger
                        listenTogetherVm.clearGuestTrackChanged()
                    }
                }

                if (startDestination == null) {
                    // ── Stitch Music App Splash Screen ──────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = com.vibevault.app.R.drawable.ic_app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(22.dp))
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
                        
                        val mainHazeState = remember { HazeState() }
                        var isReplayStoryOpen by remember { mutableStateOf(false) }
                        val tabs = remember {
                            listOf(
                                BottomTab("Home", BitChordIcons.Home),
                                BottomTab("Search", BitChordIcons.Search),
                                BottomTab("Explore", BitChordIcons.Explore),
                                BottomTab("Library", BitChordIcons.Library),
                            )
                        }
                        val selectedTab = when {
                            currentRoute == Screen.Home.route -> 0
                            currentRoute == Screen.Search.route || currentRoute?.startsWith("search/") == true -> 1
                            currentRoute == Screen.Explore.route || currentRoute?.startsWith("explore/") == true -> 2
                            currentRoute == Screen.Library.route -> 3
                            else -> 0
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (currentRoute != Screen.Player.route &&
                                    currentRoute != Screen.Login.route &&
                                    !isReplayStoryOpen
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                                                .fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // MiniPlayer sits directly above the bottom nav
                                            if (currentTrack != null) {
                                                MiniPlayer(
                                                    playerViewModel = playerViewModel,
                                                    onExpand = { trackId ->
                                                        navController.navigate(Screen.Player.createRoute(trackId))
                                                    }
                                                )
                                            }

                                            // Floating Bottom Bar with exact BitChord liquid spring physics
                                            FloatingBottomBar(
                                                tabs = tabs,
                                                selectedIndex = selectedTab,
                                                hazeState = mainHazeState,
                                                onTabSelected = { index ->
                                                    val targetRoute = when (index) {
                                                        0 -> Screen.Home.route
                                                        1 -> Screen.Search.route
                                                        2 -> Screen.Explore.route
                                                        3 -> Screen.Library.route
                                                        else -> Screen.Home.route
                                                    }
                                                    if (currentRoute != targetRoute) {
                                                        navController.navigate(targetRoute) {
                                                            popUpTo(Screen.Home.route) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(mainHazeState)
                            ) {
                                AppNavHost(
                                    navController = navController,
                                    innerPadding = innerPadding,
                                    playerViewModel = playerViewModel,
                                    startDestination = startDestination!!,
                                    onReplayStoryOpenChange = { isReplayStoryOpen = it }
                                )
                            }
                        }
                    }
                }

            }
        }
        }
    }
    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var auth: io.github.jan.supabase.auth.Auth

    @Inject
    lateinit var authRepository: com.vibevault.app.domain.repository.AuthRepository

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // CRITICAL: Update the activity's intent to the new one
        setIntent(intent) 
        Log.d("SpotifyDebug", "onNewIntent triggered")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        Log.d("VibeVault", "MainActivity: handleIntent called, URI = $uri")
        if (uri != null && uri.scheme == "vibevault") {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val session = auth.currentSessionOrNull()
                    val user = session?.user
                    if (session != null && user != null) {
                        val metadata = user.userMetadata
                        val fullName = metadata?.get("full_name")?.let { 
                            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                        }
                        val avatar = (metadata?.get("avatar_url") ?: metadata?.get("picture"))?.let {
                            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                        }

                        sessionManager.saveSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            userId = user.id,
                            email = user.email ?: "",
                            displayName = fullName ?: user.email?.substringBefore("@"),
                            avatarUrl = avatar,
                            expiresAtEpochMs = (session.expiresAt?.epochSeconds ?: 0) * 1000
                        )
                        authRepository.refreshProfile()
                    }
                } catch (e: Exception) {
                    Log.e("VibeVault", "Failed handling Auth deep link", e)
                }
            }
        }
    }
}

val LocalListenTogetherManager = androidx.compose.runtime.staticCompositionLocalOf<com.vibevault.app.data.listentogether.ListenTogetherManager?> { null }
