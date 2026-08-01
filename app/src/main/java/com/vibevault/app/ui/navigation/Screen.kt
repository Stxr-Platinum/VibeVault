package com.vibevault.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object Library : Screen("library", "Your Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)

    data object OnlineSearch : Screen("search/{query}", "Online Search") {
        fun createRoute(query: String) = "search/${java.net.URLEncoder.encode(query, "UTF-8")}"
    }

    data object Player : Screen("player/{trackId}", "Now Playing") {
        fun createRoute(trackId: String) = "player/${android.net.Uri.encode(trackId)}"
    }
    data object Artist : Screen("artist/{artistName}", "Artist") {
        fun createRoute(artistName: String) = "artist/${android.net.Uri.encode(artistName)}"
    }
    data object PlaylistDetail : Screen("playlist_detail/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "playlist_detail/${android.net.Uri.encode(playlistId)}"
    }
    data object Album : Screen("album/{albumId}", "Album") {
        fun createRoute(albumId: String) = "album/${android.net.Uri.encode(albumId)}"
    }

    data object Login : Screen("login", "Login")
    data object SpotifyLogin : Screen("spotify_login", "Connect Spotify")
    data object LikedSongs : Screen("liked_songs", "Liked Songs")
    data object Podcast : Screen("podcast", "Podcast")
    data object Premium : Screen("premium", "Premium")
    data object Profile : Screen("profile", "Profile")
    data object EditProfile : Screen("edit_profile", "Edit Profile")
    data object ListenTogether : Screen("listen_together", "Listen Together")

    data object Settings : Screen("settings", "Settings")
    data object AppearanceSettings : Screen("settings/appearance", "Appearance")
    data object PlayerSettings : Screen("settings/player", "Player & Audio")
    data object ContentSettings : Screen("settings/content", "Content")
    data object ThemeSettings : Screen("settings/appearance/theme", "Theme")
    data object ListenTogetherSettings : Screen("settings/listen_together", "Listen Together Settings")

    companion object {
        val bottomNavItems get() = listOf(Home, Search, Library)
    }
}
