package com.vibevault.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vibevault.app.ui.screens.artist.ArtistScreen
import com.vibevault.app.ui.screens.home.HomeScreen
import com.vibevault.app.ui.screens.library.LibraryScreen
import com.vibevault.app.ui.screens.liked.LikedSongsScreen
import com.vibevault.app.ui.screens.login.LoginScreen
import com.vibevault.app.ui.screens.player.PlayerScreen
import com.vibevault.app.ui.screens.search.SearchScreen
import com.vibevault.app.ui.viewmodel.PlayerViewModel

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    playerViewModel: PlayerViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onTrackClick = { track ->
                    // HomeScreen gives a Track, PlayerViewModel.playTrack expects (trackId, context)
                    playerViewModel.playTrack(track.id, listOf(track))
                    navController.navigate(Screen.Player.createRoute(track.id))
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onSwipeToQueue = { track ->
                    playerViewModel.addToQueue(track)
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onListenTogetherClick = {
                    navController.navigate(Screen.ListenTogether.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Profile.route) {
            com.vibevault.app.ui.screens.profile.ProfileScreen(
                onBack = { navController.popBackStack() },
                onPremiumClick = { navController.navigate(Screen.Premium.route) },
                onLogout = { 
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onEditProfileClick = { navController.navigate(Screen.EditProfile.route) }
            )
        }
        composable(Screen.Premium.route) {
            com.vibevault.app.ui.screens.premium.PremiumScreen(
                onBack = { navController.popBackStack() }
            )
        }

        
        // Dummy routes to prevent crashes until they are fully ported
        composable("listen_together/chat") {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.material3.Text("Chat coming soon!", modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center))
                androidx.compose.material3.TextButton(
                    onClick = { navController.popBackStack() },
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart)
                ) {
                    androidx.compose.material3.Text("Back")
                }
            }
        }
        

        composable(Screen.EditProfile.route) {
            com.vibevault.app.ui.screens.profile.EditProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onTrackClick = { trackId, context ->
                    playerViewModel.playTrack(trackId, context)
                    navController.navigate(Screen.Player.createRoute(trackId))
                },
                onNavigateToOnlineSearch = { query ->
                    navController.navigate(Screen.OnlineSearch.createRoute(query))
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.Artist.createRoute(artistName))
                }
            )
        }
        
        composable(
            route = Screen.OnlineSearch.route,
            arguments = listOf(
                androidx.navigation.navArgument("query") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val rawQuery = backStackEntry.arguments?.getString("query") ?: ""
            val query = try {
                java.net.URLDecoder.decode(rawQuery, "UTF-8")
            } catch (e: Exception) {
                rawQuery
            }
            com.vibevault.app.ui.screens.search.OnlineSearchScreen(
                query = query,
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId, tracks ->
                    playerViewModel.playTrack(trackId, tracks)
                    navController.navigate(Screen.Player.createRoute(trackId))
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.Artist.createRoute(artistName))
                }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onLikedSongsClick = {
                    navController.navigate("likedSongs")
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable("likedSongs") {
            LikedSongsScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId, context ->
                    playerViewModel.playTrack(trackId, context)
                    navController.navigate(Screen.Player.createRoute(trackId))
                }
            )
        }
        composable(Screen.PlaylistDetail.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            // Will implement PlaylistScreen here
            com.vibevault.app.ui.screens.playlist.PlaylistScreen(
                playlistId = playlistId,
                onBackClick = { navController.popBackStack() },
                onTrackClick = { trackId, context ->
                    playerViewModel.playTrack(trackId, context)
                    navController.navigate(Screen.Player.createRoute(trackId))
                }
            )
        }
        composable(
            route = Screen.Player.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(400)) },
            exitTransition = { fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(400)) }
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId")
            PlayerScreen(
                trackId = trackId,
                onBackClick = { navController.popBackStack() },
                onListenTogetherClick = { navController.navigate(Screen.ListenTogether.route) },
                viewModel = playerViewModel
            )
        }
        composable(Screen.Artist.route) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            ArtistScreen(
                artistName = artistName,
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId, context ->
                    playerViewModel.playTrack(trackId, context)
                    navController.navigate(Screen.Player.createRoute(trackId))
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }
        composable(Screen.ListenTogether.route) {
            com.vibevault.app.ui.screens.listentogether.ListenTogetherScreen(
                navController = navController,
                showTopBar = true
            )
        }
        
        composable(Screen.Settings.route) {
            com.vibevault.app.ui.screens.settings.SettingsScreen(navController = navController)
        }
        composable(Screen.ContentSettings.route) {
            com.vibevault.app.ui.screens.settings.ContentSettings(
                navController = navController,
                scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
            )
        }
        composable(Screen.AppearanceSettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? android.app.Activity
            val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
            if (activity != null) {
                com.vibevault.app.ui.screens.settings.AppearanceSettings(
                    navController = navController,
                    scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(),
                    activity = activity,
                    snackbarHostState = snackbarHostState
                )
            }
        }
        composable(Screen.PlayerSettings.route) {
            com.vibevault.app.ui.screens.settings.PlayerSettings(
                navController = navController,
                scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
            )
        }
        composable(Screen.ThemeSettings.route) {
            com.vibevault.app.ui.screens.settings.ThemeScreen(
                navController = navController
            )
        }
        composable(Screen.ListenTogetherSettings.route) {
            com.vibevault.app.ui.screens.settings.ListenTogetherSettings(
                navController = navController,
                scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
            )
        }
    }
}

