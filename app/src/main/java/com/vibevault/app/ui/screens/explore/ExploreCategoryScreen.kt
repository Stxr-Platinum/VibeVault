package com.vibevault.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.vibevault.app.domain.model.Track
import com.vibevault.app.extensions.toTrack
import com.vibevault.app.ui.components.PAGE_GUTTER
import com.vibevault.app.ui.components.SectionHeaderSkeleton
import com.vibevault.app.ui.components.ShelfSkeleton
import com.vibevault.app.ui.components.SongRowSkeleton
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreCategoryScreen(
    title: String,
    browseId: String,
    params: String?,
    onBack: () -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    modifier: Modifier = Modifier,
    onTrackClick: (track: Track, context: List<Track>) -> Unit = { _, _ -> },
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val haptics = rememberHaptics()

    LaunchedEffect(title, browseId, params) {
        viewModel.loadCategory(title, browseId, params)
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            // Top Bar
            item(key = "top_bar") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptics.play(Haptic.Select)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Loading Skeleton Screen
            if (uiState.isLoading) {
                // Skeleton for Songs Section (Horizontal scroll layout)
                item(key = "skeleton_songs_header") {
                    SectionHeaderSkeleton(index = 0)
                }
                item(key = "skeleton_songs_horizontal") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(2, key = { "skel_col_$it" }) { colIndex ->
                            Column(
                                modifier = Modifier.width(320.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(4) { rowIndex ->
                                    SongRowSkeleton(index = colIndex * 4 + rowIndex)
                                }
                            }
                        }
                    }
                }

                // Skeleton for Playlists & Albums Shelves
                items(3, key = { "skeleton_shelf_$it" }) { index ->
                    ShelfSkeleton(index = index + 1)
                }
            } else {
                // 1. Songs Section (Horizontal scroll layout, swiping left-to-right)
                if (uiState.songs.isNotEmpty()) {
                    item(key = "section_header_songs") {
                        SectionTitle(
                            title = "Songs",
                            subtitle = "Swipe to explore $title songs"
                        )
                    }

                    item(key = "shelf_songs_horizontal") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Chunk songs into columns of 4 so users swipe left-to-right across columns
                            items(uiState.songs.chunked(4), key = { chunk -> "song_col_${chunk.first().id}" }) { columnSongs ->
                                Column(
                                    modifier = Modifier.width(320.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    columnSongs.forEach { song ->
                                        CategorySongRow(
                                            song = song,
                                            onClick = {
                                                haptics.play(Haptic.Select)
                                                val allTracks = uiState.songs.map { it.toTrack() }
                                                onTrackClick(song.toTrack(), allTracks)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Featured Playlists Section
                if (uiState.featuredPlaylists.isNotEmpty()) {
                    item(key = "section_header_featured_playlists") {
                        SectionTitle(
                            title = "Featured Playlists",
                            subtitle = "Popular & curated $title mixes"
                        )
                    }

                    item(key = "shelf_featured_playlists") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.featuredPlaylists, key = { "feat_pl_${it.id}" }) { playlist ->
                                CategoryCardItem(
                                    title = playlist.title,
                                    subtitle = playlist.author?.name ?: playlist.songCountText ?: "Playlist",
                                    thumbnailUrl = playlist.thumbnail.orEmpty(),
                                    onClick = {
                                        haptics.play(Haptic.Select)
                                        onPlaylistClick(playlist.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Community Playlists Section
                if (uiState.communityPlaylists.isNotEmpty()) {
                    item(key = "section_header_community_playlists") {
                        SectionTitle(
                            title = "Community Playlists",
                            subtitle = "Curated by listeners and creators"
                        )
                    }

                    item(key = "shelf_community_playlists") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.communityPlaylists, key = { "comm_pl_${it.id}" }) { playlist ->
                                CategoryCardItem(
                                    title = playlist.title,
                                    subtitle = playlist.author?.name ?: playlist.songCountText ?: "Playlist",
                                    thumbnailUrl = playlist.thumbnail.orEmpty(),
                                    onClick = {
                                        haptics.play(Haptic.Select)
                                        onPlaylistClick(playlist.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Albums Section
                if (uiState.albums.isNotEmpty()) {
                    item(key = "section_header_albums") {
                        SectionTitle(
                            title = "Albums",
                            subtitle = "Top releases & studio albums"
                        )
                    }

                    item(key = "shelf_albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.albums, key = { "alb_${it.id}" }) { album ->
                                val artistText = album.artists?.firstOrNull()?.name ?: ""
                                val yearText = album.year?.toString().orEmpty()
                                val subtitle = listOf(artistText, yearText).filter { it.isNotBlank() }.joinToString(" • ")

                                CategoryCardItem(
                                    title = album.title,
                                    subtitle = subtitle.ifBlank { "Album" },
                                    thumbnailUrl = album.thumbnail,
                                    onClick = {
                                        haptics.play(Haptic.Select)
                                        onAlbumClick(album.browseId)
                                    }
                                )
                            }
                        }
                    }
                }

                // Extra Browse Shelves (if any)
                uiState.shelves.forEachIndexed { index, shelf ->
                    val shelfTitle = shelf.title ?: "More in $title"
                    item(key = "extra_shelf_header_${index}_$shelfTitle") {
                        SectionTitle(title = shelfTitle)
                    }

                    item(key = "extra_shelf_row_${index}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(shelf.items, key = { it.id }) { item ->
                                CategoryCardItem(
                                    title = item.title,
                                    subtitle = when (item) {
                                        is PlaylistItem -> item.author?.name ?: item.songCountText ?: "Playlist"
                                        is AlbumItem -> item.artists?.firstOrNull()?.name ?: "Album"
                                        else -> ""
                                    },
                                    thumbnailUrl = item.thumbnail.orEmpty(),
                                    onClick = {
                                        haptics.play(Haptic.Select)
                                        when (item) {
                                            is PlaylistItem -> onPlaylistClick(item.id)
                                            is AlbumItem -> onAlbumClick(item.browseId)
                                            else -> {
                                                if (item.id.startsWith("MPREb_") || item.id.startsWith("OLAK5uy_")) {
                                                    onAlbumClick(item.id)
                                                } else {
                                                    onPlaylistClick(item.id)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Error / Empty State
                if (uiState.errorMessage != null && uiState.songs.isEmpty() && uiState.featuredPlaylists.isEmpty() && uiState.communityPlaylists.isEmpty() && uiState.albums.isEmpty()) {
                    item(key = "error") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp, start = 24.dp, end = 24.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "Something went wrong",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(
            start = PAGE_GUTTER + 6.dp,
            end = PAGE_GUTTER + 6.dp,
            top = 22.dp,
            bottom = 8.dp
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CategorySongRow(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistName = song.artists.joinToString(", ") { it.name }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val context = LocalContext.current
            val imageRequest = remember(song.thumbnail) {
                ImageRequest.Builder(context)
                    .data(song.thumbnail)
                    .memoryCacheKey(song.thumbnail)
                    .diskCacheKey(song.thumbnail)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = artistName.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CategoryCardItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(145.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val context = LocalContext.current
            val imageRequest = remember(thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .memoryCacheKey(thumbnailUrl)
                    .diskCacheKey(thumbnailUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play overlay icon pill in bottom right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

