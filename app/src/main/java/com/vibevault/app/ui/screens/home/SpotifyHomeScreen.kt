package com.vibevault.app.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.music.spotify.Spotify
import com.vibevault.app.R
import com.vibevault.app.constants.HomeSessionOpenCountKey
import com.vibevault.app.domain.model.Track
import com.vibevault.app.utils.rememberPreference
import kotlin.random.Random
import com.vibevault.app.ui.components.ExtensionBlocks
import com.vibevault.app.ui.components.Hook
import com.vibevault.app.ui.components.UserAvatar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.ui.graphics.graphicsLayer
import com.vibevault.app.ui.immersive.Immersive
import com.vibevault.app.ui.immersive.shell.ImmersiveBarAction
import com.vibevault.app.ui.immersive.shell.ImmersiveFloatingHeader
import com.vibevault.app.ui.immersive.shell.ImmersiveHeaderContentHeight
import com.vibevault.app.ui.immersive.shell.LocalBarControlSize
import com.vibevault.app.ui.immersive.shell.immersiveChromeScroll
import com.vibevault.app.ui.immersive.shell.immersiveHazeSource
import com.vibevault.app.ui.immersive.shell.immersiveHeaderContentPadding
import com.vibevault.app.ui.immersive.shell.rememberTopBarCollapse
import com.vibevault.app.ui.viewmodel.AuthViewModel
import com.vibevault.app.ui.viewmodel.HomeViewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vibevault.app.ui.components.Surface as ExtensionSurface

private const val LIKED_SONGS_SHORTCUT_ID = "vibevault:liked"

/** The left half of two pills joined (a picked chip and the one it opened), and the right one. */
private val JoinedStart = RoundedCornerShape(
    topStartPercent = 50,
    bottomStartPercent = 50,
    topEndPercent = 15,
    bottomEndPercent = 15
)
private val JoinedEnd = RoundedCornerShape(
    topStartPercent = 15,
    bottomStartPercent = 15,
    topEndPercent = 50,
    bottomEndPercent = 50
)

/**
 * Pixel-for-pixel replication of `myspoti`'s Classic Spotify HomeScreen:
 * - Sticky top header with profile avatar, What's New bell, and filter pills ("All", "Music",
 *   "Podcasts", "Audiobooks") with sub-chip expansion logic
 * - Responsive Speed Dial / Shortcut grid (48dp rounded-4dp tiles, Liked Songs first, max 8)
 * - Dynamic feed sections & 150dp x 150dp carousels (albums, playlists, circular artists, shows)
 * - ExtensionBlocks(Hook.HOME, Surface.HOME, "{}")
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotifyHomeScreen(
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onLikedSongsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val liveFeed by viewModel.spotifyHomeFeed.collectAsStateWithLifecycle()
    val facet by viewModel.spotifyHomeFacet.collectAsStateWithLifecycle()
    val subChipsByParent by viewModel.spotifyHomeSubChips.collectAsStateWithLifecycle()

    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val speedDialPicks by viewModel.speedDialPicks.collectAsStateWithLifecycle()
    val topArtists by viewModel.spotifyArtists.collectAsStateWithLifecycle()

    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val isSpotifyConnected by viewModel.isSpotifyConnected.collectAsStateWithLifecycle()

    val (sessionOpenCount, onSessionOpenCountChange) = rememberPreference(HomeSessionOpenCountKey, defaultValue = 0)
    LaunchedEffect(Unit) {
        onSessionOpenCountChange(sessionOpenCount + 1)
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showSpotifyConnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected) {
            viewModel.refreshSpotifyHome(force = false)
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.authEvents.collect { event ->
            when (event) {
                is AuthViewModel.AuthEvent.SpotifyConnected -> {
                    Toast.makeText(context, "Spotify Connected Successfully!", Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                    viewModel.refreshSpotifyHome(force = true)
                }
                is AuthViewModel.AuthEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showSpotifyConnectDialog) {
        SpotifyConnectDialog(
            onDismiss = { showSpotifyConnectDialog = false },
            onConnect = { spDc, spKey ->
                authViewModel.connectWithCookies(spDc, spKey)
                showSpotifyConnectDialog = false
            }
        )
    }

    // Build fallback sections when live Spotify `home` feed is not yet available or filtered locally
    val fallbackSections = remember(
        spotifyPlaylists,
        localPlaylists,
        featuredPlaylists,
        recentlyPlayed,
        quickPicks,
        similarRecommendations,
        trendingTracks,
        speedDialPicks,
        topArtists,
        facet
    ) {
        val sections = mutableListOf<Spotify.HomeSection>()
        val shortcutCards = mutableListOf<Spotify.HomeCard>()
        val seenShortcutNames = HashSet<String>()

        spotifyPlaylists.take(6).forEach { pl ->
            if (seenShortcutNames.add(pl.title.lowercase())) {
                shortcutCards.add(
                    Spotify.HomeCard.Playlist(
                        id = pl.id,
                        uri = "spotify:playlist:${pl.id}",
                        name = pl.title,
                        imageUrl = pl.coverUrl.orEmpty(),
                        subtitle = pl.ownerName.orEmpty()
                    )
                )
            }
        }
        localPlaylists.take(6).forEach { pl ->
            if (shortcutCards.size < 7 && seenShortcutNames.add(pl.title.lowercase())) {
                shortcutCards.add(
                    Spotify.HomeCard.Playlist(
                        id = pl.id,
                        uri = "spotify:playlist:${pl.id}",
                        name = pl.title,
                        imageUrl = pl.coverUrl.orEmpty(),
                        subtitle = pl.ownerName.orEmpty()
                    )
                )
            }
        }
        speedDialPicks.forEach { pick ->
            if (shortcutCards.size < 7 && seenShortcutNames.add(pick.title.lowercase())) {
                shortcutCards.add(
                    Spotify.HomeCard.Playlist(
                        id = pick.id,
                        uri = pick.id,
                        name = pick.title,
                        imageUrl = pick.coverUrl,
                        subtitle = ""
                    )
                )
            }
        }
        if (shortcutCards.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(Spotify.HomeSection(uri = "", title = "", items = shortcutCards))
        }

        if (recentlyPlayed.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(
                Spotify.HomeSection(
                    uri = "jump_back_in",
                    title = "Jump back in",
                    items = recentlyPlayed.distinctBy { "${it.album}::${it.artist}" }.map { t ->
                        Spotify.HomeCard.Album(
                            id = t.id,
                            uri = "spotify:album:${t.id}",
                            name = t.album.ifBlank { t.title },
                            imageUrl = t.albumImageUrl,
                            subtitle = t.artist,
                            artists = t.artist
                        )
                    }
                )
            )
        }

        if (spotifyPlaylists.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(
                Spotify.HomeSection(
                    uri = "your_playlists",
                    title = "Your top mixes",
                    items = spotifyPlaylists.map { pl ->
                        Spotify.HomeCard.Playlist(
                            id = pl.id,
                            uri = "spotify:playlist:${pl.id}",
                            name = pl.title,
                            imageUrl = pl.coverUrl.orEmpty(),
                            subtitle = pl.ownerName?.takeIf { it.isNotBlank() } ?: "Playlist"
                        )
                    }
                )
            )
        }

        if (topArtists.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(
                Spotify.HomeSection(
                    uri = "top_artists",
                    title = "Your favorite artists",
                    items = topArtists.map { a ->
                        Spotify.HomeCard.Artist(
                            id = a.id,
                            uri = "spotify:artist:${a.id}",
                            name = a.name,
                            imageUrl = a.imageUrl.orEmpty()
                        )
                    }
                )
            )
        }

        similarRecommendations.forEach { rec ->
            if (rec.items.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
                sections.add(
                    Spotify.HomeSection(
                        uri = "for_fans_${rec.title}",
                        title = "For fans of ${rec.title}",
                        items = rec.items.map { t ->
                            Spotify.HomeCard.Album(
                                id = t.id,
                                uri = "spotify:album:${t.id}",
                                name = t.title,
                                imageUrl = t.albumImageUrl,
                                subtitle = t.artist,
                                artists = t.artist
                            )
                        }
                    )
                )
            }
        }

        if (featuredPlaylists.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(
                Spotify.HomeSection(
                    uri = "featured",
                    title = "Made For ${userDisplayName?.ifBlank { "You" } ?: "You"}",
                    items = featuredPlaylists.map { pl ->
                        Spotify.HomeCard.Playlist(
                            id = pl.id,
                            uri = "spotify:playlist:${pl.id}",
                            name = pl.title,
                            imageUrl = pl.coverUrl.orEmpty(),
                            subtitle = pl.description?.ifBlank { null } ?: "Playlist"
                        )
                    }
                )
            )
        }

        if (trendingTracks.isNotEmpty() && (facet.isEmpty() || facet == "music-chip")) {
            sections.add(
                Spotify.HomeSection(
                    uri = "trending",
                    title = "Best of today",
                    items = trendingTracks.map { t ->
                        Spotify.HomeCard.Album(
                            id = t.id,
                            uri = "spotify:album:${t.id}",
                            name = t.title,
                            imageUrl = t.albumImageUrl,
                            subtitle = t.artist,
                            artists = t.artist
                        )
                    }
                )
            )
        }

        sections
    }

    val sections = remember(liveFeed, fallbackSections, sessionOpenCount) {
        val rawSections = liveFeed?.sections.takeIf { !it.isNullOrEmpty() } ?: fallbackSections
        // Filter out any "Episodes you might like" or podcast show shelves
        val filtered = rawSections.map { s ->
            s.copy(items = s.items.filterNot { item ->
                item is Spotify.HomeCard.Show ||
                s.title.contains("episode", ignoreCase = true) ||
                s.title.contains("podcast", ignoreCase = true)
            })
        }.filter { it.items.isNotEmpty() }

        val grid = filtered.firstOrNull()?.takeIf { it.title.isBlank() }
        val shelves = if (grid != null) filtered.drop(1) else filtered
        // Deterministic session bucket: reorder every 5 app opens
        val seed = (sessionOpenCount / 5).toLong()
        val reorderedShelves = shelves.shuffled(Random(seed))
        if (grid != null) listOf(grid) + reorderedShelves else reorderedShelves
    }

    val gridSection = remember(sections) { sections.firstOrNull()?.takeIf { it.title.isBlank() } }
    val carousels = remember(sections, gridSection) { if (gridSection != null) sections.drop(1) else sections }
    val carouselKeys = remember(carousels) { uniqueKeys(carousels.map { it.title }) }
    val shortcuts = remember(gridSection) {
        gridSection?.items?.let { withLikedFirst(it) }
    }

    val trackById = remember(recentlyPlayed, quickPicks, trendingTracks, similarRecommendations) {
        (recentlyPlayed + quickPicks + trendingTracks + similarRecommendations.flatMap { it.items })
            .associateBy { it.id }
    }

    val onCardClick: (Spotify.HomeCard) -> Unit = { item ->
        when (item) {
            is Spotify.HomeCard.Playlist -> {
                if (item.id == LIKED_SONGS_SHORTCUT_ID) {
                    onLikedSongsClick()
                } else if (item.id.isNotBlank()) {
                    onPlaylistClick(item.id)
                }
            }
            is Spotify.HomeCard.Album -> {
                val localTrack = trackById[item.id]
                if (localTrack != null && item.uri.startsWith("spotify:album:${localTrack.id}")) {
                    onTrackClick(localTrack)
                } else if (item.name.isNotBlank()) {
                    val target = if (item.artists.isNotBlank()) {
                        "album:${item.name}::${item.artists}"
                    } else {
                        "album:${item.id}"
                    }
                    onPlaylistClick(target)
                }
            }
            is Spotify.HomeCard.Artist -> {
                if (item.name.isNotBlank()) {
                    onArtistClick(item.name)
                }
            }
            is Spotify.HomeCard.Show -> {
                if (item.id.isNotBlank()) {
                    onPlaylistClick(item.id)
                }
            }
        }
    }

    val bgColor = Immersive.Page

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        color = bgColor
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refresh(force = true)
                    viewModel.refreshSpotifyHome(force = true)
                    delay(800)
                    isRefreshing = false
                }
            }
        ) {
            val greeting = remember(liveFeed?.greeting) {
                liveFeed?.greeting?.takeIf { it.isNotBlank() } ?: run {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    when (hour) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..21 -> "Good evening"
                        else -> "Good night"
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = immersiveHeaderContentPadding(), bottom = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .immersiveHazeSource()
                    .immersiveChromeScroll()
            ) {
                item(key = "spotify_home_facets") {
                    ImmersiveHomeFacets(
                        facet = facet,
                        subChipsByParent = subChipsByParent,
                        onFacet = { viewModel.selectSpotifyHomeFacet(it) }
                    )
                }

                if (!isSpotifyConnected && liveFeed == null) {
                    item(key = "connect_spotify_card") {
                        ConnectSpotifyBanner(
                            onConnectClick = { showSpotifyConnectDialog = true }
                        )
                    }
                }

                shortcuts?.let { tiles ->
                    item(key = "shortcut_grid") {
                        HomeShortcutGrid(
                            items = tiles,
                            onItemClick = onCardClick
                        )
                    }
                }

                items(carousels.size, key = { carouselKeys[it] }) { i ->
                    HomeFeedSection(
                        section = carousels[i],
                        onItemClick = onCardClick
                    )
                }

                item(key = "extension_blocks") {
                    ExtensionBlocks(Hook.HOME, ExtensionSurface.HOME, "{}")
                }
            }

            ImmersiveFloatingHeader(
                title = greeting,
                collapse = rememberTopBarCollapse(listState, 24.dp),
            ) {
                val controlSize = LocalBarControlSize.current
                ImmersiveBarAction(
                    painter = painterResource(R.drawable.group),
                    contentDescription = "Friends",
                    onClick = onSettingsClick,
                )
                ImmersiveBarAction(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    onClick = onSettingsClick,
                )
                Box(
                    modifier = Modifier
                        .height(ImmersiveHeaderContentHeight)
                        .width(controlSize + 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onProfileClick,
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF539DF3)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!userAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Text(
                                text = userDisplayName?.firstOrNull()?.uppercase() ?: "V",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ImmersiveHomeFacets(
    facet: String,
    subChipsByParent: Map<String, List<Pair<String, String>>>,
    onFacet: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        val labels = mapOf(
            "music-chip" to "Music",
            "podcasts-chip" to "Podcasts",
            "audiobooks-chip" to "Audiobooks",
        )
        if (facet.isEmpty()) {
            ImmersiveFacetPill("All", selected = true) {}
            ImmersiveFacetPill(labels.getValue("music-chip")) { onFacet("music-chip") }
            ImmersiveFacetPill(labels.getValue("podcasts-chip")) { onFacet("podcasts-chip") }
            ImmersiveFacetPill(labels.getValue("audiobooks-chip")) { onFacet("audiobooks-chip") }
        } else {
            val parent = if (facet in subChipsByParent) facet
            else subChipsByParent.entries.firstOrNull { e -> e.value.any { it.first == facet } }?.key ?: facet
            val subs = subChipsByParent[parent].orEmpty()

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Immersive.Raised)
                    .clickable { onFacet("") }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "All",
                    tint = Immersive.Text,
                    modifier = Modifier.size(18.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ImmersiveFacetPill(
                    label = labels[parent].orEmpty().ifBlank { parent },
                    selected = true,
                    shape = if (subs.isEmpty()) RoundedCornerShape(50) else JoinedStart,
                ) { onFacet(parent) }

                subs.forEachIndexed { i, (id, label) ->
                    val shown = remember(id) { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = shown,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    ) {
                        ImmersiveFacetPill(
                            label = label,
                            selected = facet == id,
                            shape = if (i == subs.lastIndex) JoinedEnd else RoundedCornerShape(4.dp),
                        ) { onFacet(id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveFacetPill(
    label: String,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(50),
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun withLikedFirst(items: List<Spotify.HomeCard>): List<Spotify.HomeCard> {
    if (items.any { it is Spotify.HomeCard.Playlist && it.id == LIKED_SONGS_SHORTCUT_ID }) {
        return items.take(8)
    }
    val liked = Spotify.HomeCard.Playlist(
        id = LIKED_SONGS_SHORTCUT_ID,
        uri = "spotify:collection:tracks",
        name = "Liked Songs",
        imageUrl = "",
        subtitle = ""
    )
    return (listOf<Spotify.HomeCard>(liked) + items).take(8)
}

/**
 * Spotify-style top row: profile avatar on the left, What's New bell, vertical separator,
 * and horizontal scrollable filter pills ("All", "Music", "Podcasts", "Audiobooks") with sub-chip expansion.
 */
@Composable
private fun HomeHeaderRow(
    avatarUrl: String?,
    displayName: String,
    facet: String,
    subChipsByParent: Map<String, List<Pair<String, String>>>,
    onFacet: (String) -> Unit,
    onProfileClick: () -> Unit,
    onWhatsNewClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onProfileClick
                )
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            UserAvatar(
                avatarUrl = avatarUrl,
                displayName = displayName,
                size = 34.dp
            )
        }

        WhatsNewBell(onClick = onWhatsNewClick)

        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 1.dp, height = 22.dp)
                .background(Color(0xFF3E3E3E))
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            val labels = mapOf(
                "music-chip" to "Music",
                "podcasts-chip" to "Podcasts",
                "audiobooks-chip" to "Audiobooks"
            )
            if (facet.isEmpty()) {
                HomePill("All", selected = true) {}
                HomePill(labels.getValue("music-chip")) { onFacet("music-chip") }
                HomePill(labels.getValue("podcasts-chip")) { onFacet("podcasts-chip") }
                HomePill(labels.getValue("audiobooks-chip")) { onFacet("audiobooks-chip") }
            } else {
                val parent = if (facet in subChipsByParent) facet
                else subChipsByParent.entries.firstOrNull { e -> e.value.any { it.first == facet } }?.key ?: facet
                val subs = subChipsByParent[parent].orEmpty()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable { onFacet("") }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "All",
                        tint = Immersive.Text,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HomePill(
                        label = labels[parent].orEmpty().ifBlank { parent },
                        selected = true,
                        shape = if (subs.isEmpty()) RoundedCornerShape(50) else JoinedStart
                    ) { onFacet(parent) }

                    subs.forEachIndexed { i, (id, label) ->
                        val shown = remember(id) { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = shown,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn()
                        ) {
                            HomePill(
                                label = label,
                                selected = facet == id,
                                shape = if (i == subs.lastIndex) JoinedEnd else RoundedCornerShape(4.dp)
                            ) { onFacet(id) }
                        }
                    }
                }
            }
        }

        IconButton(onClick = onWhatsNewClick) {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = "Friends",
                tint = Immersive.Text,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HomePill(
    label: String,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(50),
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF1ED760) else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Immersive.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WhatsNewBell(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp)
            .size(34.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = "What's New",
            tint = Immersive.Text,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HomeShortcutGrid(
    items: List<Spotify.HomeCard>,
    onItemClick: (Spotify.HomeCard) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 280.dp).toInt().coerceAtLeast(2)
        val cardShape = RoundedCornerShape(8.dp)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.chunked(cols).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(cardShape)
                                .background(Color(0xFF2A2A2A))
                                .clickable { onItemClick(item) }
                        ) {
                            val imageShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                            if (item is Spotify.HomeCard.Playlist && item.id == LIKED_SONGS_SHORTCUT_ID) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(imageShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF450AF5),
                                                    Color(0xFFC4EFD9)
                                                )
                                            )
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = item.imageUrl.takeIf { it.isNotBlank() },
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(imageShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Text(
                                text = item.name,
                                color = Immersive.Text,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .weight(1f)
                            )
                        }
                    }
                    repeat(cols - rowItems.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun uniqueKeys(raw: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return raw.map { k ->
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) k else "$k#$n"
    }
}

@Composable
private fun HomeFeedSection(
    section: Spotify.HomeSection,
    onItemClick: (Spotify.HomeCard) -> Unit
) {
    Text(
        text = section.title,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)
    )
    val keys = remember(section) { uniqueKeys(section.items.map { it.name + "|" + it.imageUrl }) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(section.items.size, key = { keys[it] }) { i ->
            HomeFeedCard(section.items[i]) { onItemClick(section.items[i]) }
        }
    }
}

@Composable
private fun HomeFeedCard(
    item: Spotify.HomeCard,
    onClick: () -> Unit
) {
    val isArtist = item is Spotify.HomeCard.Artist
    val subtitle = when (item) {
        is Spotify.HomeCard.Album -> item.subtitle
        is Spotify.HomeCard.Playlist -> item.subtitle
        is Spotify.HomeCard.Artist -> "Artist"
        is Spotify.HomeCard.Show -> item.subtitle
    }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box {
            AsyncImage(
                model = item.imageUrl.takeIf { it.isNotBlank() },
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(150.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (item is Spotify.HomeCard.Show && !item.isEpisode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConnectSpotifyBanner(onConnectClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Connect Spotify",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sign in to load your live Spotify Home feed, mixes & playlists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        }
    }
}
