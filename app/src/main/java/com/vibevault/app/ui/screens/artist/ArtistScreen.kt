package com.vibevault.app.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.vibevault.app.R
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.ExpandableText
import com.vibevault.app.ui.components.NavigationTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    onTrackClick: (String, List<Track>) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artistPage by viewModel.artistPage.collectAsStateWithLifecycle()
    val subscriberCountText by viewModel.subscriberCountText.collectAsStateWithLifecycle()
    val monthlyListenerCount by viewModel.monthlyListenerCount.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val latestAlbums by viewModel.latestAlbums.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    val systemBarsTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0
        }
    }

    val displayArtistName = artistPage?.artist?.title ?: artistName
    val thumbnail = artistPage?.artist?.thumbnail ?: topSongs.firstOrNull()?.albumImageUrl ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header Item ─────────────────────────────────────────────
            item(key = "header") {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header Image (Starts directly below status bar, top-aligned so head/face is never cut off)
                    if (thumbnail.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        ) {
                            AsyncImage(
                                model = thumbnail,
                                contentDescription = displayArtistName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Black.copy(alpha = 0.65f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Content Section sitting over bottom of header image
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (thumbnail.isNotEmpty()) 180.dp else systemBarsTopPadding + 16.dp
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Artist Name Title
                            Text(
                                text = displayArtistName,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Statistics Badges Row
                            val subsText = subscriberCountText ?: artistPage?.subscriberCountText
                            val listenersText = monthlyListenerCount ?: artistPage?.monthlyListenerCount

                            if (!subsText.isNullOrBlank() || !listenersText.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    if (!subsText.isNullOrBlank()) {
                                        val parsedSubs = subsText.split(' ').firstOrNull() ?: subsText
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.group),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$parsedSubs ${stringResource(R.string.subscribers)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    if (!listenersText.isNullOrBlank()) {
                                        val parsedListeners = listenersText.split(' ').firstOrNull() ?: listenersText
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.graphic_eq),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$parsedListeners ${stringResource(R.string.monthly_listeners)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // About Artist Bio
                            val bio = description ?: artistPage?.description
                            if (!bio.isNullOrEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.about_artist),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    ExpandableText(
                                        text = bio,
                                        collapsedMaxLines = 3
                                    )
                                }
                            }

                            // Control Buttons Row (Single line formatting without text wrapping)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Subscribe Button
                                Button(
                                    onClick = { viewModel.toggleSubscribe() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isSubscribed) stringResource(R.string.subscribed) else stringResource(R.string.subscribe),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Radio Button
                                OutlinedButton(
                                    onClick = { topSongs.firstOrNull()?.let { onTrackClick(it.id, topSongs) } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.radio),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Shuffle Button
                                OutlinedButton(
                                    onClick = {
                                        if (topSongs.isNotEmpty()) {
                                            val shuffled = topSongs.shuffled()
                                            shuffled.firstOrNull()?.let { onTrackClick(it.id, shuffled) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.shuffle),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Dynamic Sections / Popular Songs ──────────────────────────
            val pageSections = artistPage?.sections
            if (!pageSections.isNullOrEmpty()) {
                pageSections.forEach { section ->
                    if (section.items.isNotEmpty()) {
                        item(key = "section_${section.title}") {
                            NavigationTitle(
                                title = section.title,
                                onClick = { }
                            )
                        }

                        if ((section.items.firstOrNull() as? SongItem) != null) {
                            val songItems = section.items.filterIsInstance<SongItem>()
                            itemsIndexed(
                                items = songItems,
                                key = { index, song -> "section_song_${song.id}_$index" }
                            ) { index, song ->
                                SongRowItem(
                                    title = song.title,
                                    subtitle = song.artists?.joinToString(", ") { it.name } ?: displayArtistName,
                                    thumbnailUrl = song.thumbnail ?: "",
                                    index = index + 1,
                                    onClick = {
                                        val mappedTrack = Track(
                                            id = song.id,
                                            title = song.title,
                                            artist = displayArtistName,
                                            album = song.album?.name ?: "",
                                            albumImageUrl = song.thumbnail ?: "",
                                            durationMs = (song.duration ?: 0) * 1000L
                                        )
                                        val playlist = songItems.map { item ->
                                            Track(
                                                id = item.id,
                                                title = item.title,
                                                artist = displayArtistName,
                                                album = item.album?.name ?: "",
                                                albumImageUrl = item.thumbnail ?: "",
                                                durationMs = (item.duration ?: 0) * 1000L
                                            )
                                        }
                                        onTrackClick(mappedTrack.id, playlist)
                                    }
                                )
                            }
                        } else {
                            item(key = "section_row_${section.title}") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(
                                        items = section.items,
                                        key = { "grid_${it.id}" }
                                    ) { gridItem ->
                                        val itemTitle = when (gridItem) {
                                            is AlbumItem -> gridItem.title
                                            is com.music.innertube.models.PlaylistItem -> gridItem.title
                                            is com.music.innertube.models.ArtistItem -> gridItem.title
                                            else -> ""
                                        }
                                        val itemSubtitle = when (gridItem) {
                                            is AlbumItem -> gridItem.year?.toString() ?: "Album"
                                            is com.music.innertube.models.PlaylistItem -> "Playlist"
                                            is com.music.innertube.models.ArtistItem -> "Artist"
                                            else -> ""
                                        }
                                        val itemThumb = gridItem.thumbnail

                                        Column(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .clickable {
                                                    if (gridItem is AlbumItem) {
                                                        onPlaylistClick(gridItem.id)
                                                    }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = itemThumb,
                                                contentDescription = itemTitle,
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = itemTitle,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = itemSubtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback rendering using topSongs and latestAlbums
                if (topSongs.isNotEmpty()) {
                    item(key = "top_songs_title") {
                        NavigationTitle(
                            title = "Popular",
                            onClick = { }
                        )
                    }

                    itemsIndexed(
                        items = topSongs,
                        key = { index, track -> "top_song_${track.id}_$index" }
                    ) { index, track ->
                        SongRowItem(
                            title = track.title,
                            subtitle = track.album,
                            thumbnailUrl = track.albumImageUrl,
                            index = index + 1,
                            onClick = { onTrackClick(track.id, topSongs) }
                        )
                    }
                }

                if (latestAlbums.isNotEmpty()) {
                    item(key = "albums_title") {
                        NavigationTitle(
                            title = "Releases",
                            onClick = { }
                        )
                    }

                    item(key = "albums_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(latestAlbums, key = { it.id }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable { onPlaylistClick(album.id) }
                                ) {
                                    AsyncImage(
                                        model = album.coverUrl,
                                        contentDescription = album.title,
                                        modifier = Modifier
                                            .size(130.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.releaseDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Action Button (Play All) ──────────────────────────
        AnimatedVisibility(
            visible = topSongs.isNotEmpty(),
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { topSongs.firstOrNull()?.let { onTrackClick(it.id, topSongs) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play All",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // ── Floating Top Bar (Fades background on scroll) ──────────────────────────
        TopAppBar(
            title = {
                if (!transparentAppBar) {
                    Text(
                        text = displayArtistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (transparentAppBar) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val shareLink = "https://music.youtube.com/channel/${viewModel.artistName.value}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Artist Link", shareLink)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Artist link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = if (transparentAppBar) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            } else {
                TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            }
        )
    }
}

@Composable
private fun SongRowItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
