package com.vibevault.app.ui.screens.search

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics
import com.vibevault.app.ui.screens.search.suggestions.OnlineSearchSuggestionViewModel
import com.vibevault.app.ui.screens.search.suggestions.SuggestionsTabContent
import com.vibevault.app.ui.screens.search.suggestions.SuggestionsViewModel
import com.vibevault.app.ui.components.listItemShape
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTrackClick: (String, List<Track>) -> Unit,
    onNavigateToOnlineSearch: (String) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSwipeToQueue: ((Track) -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    suggestionsViewModel: SuggestionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val searchHistoryManager = remember { SearchHistoryManager(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val suggestionViewState by suggestionViewModel.viewState.collectAsStateWithLifecycle()
    val searchHistory by searchHistoryManager.history.collectAsStateWithLifecycle()
    val jumpBackInTracks by suggestionsViewModel.jumpBackInTracks.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        suggestionsViewModel.onScreenOpened()
    }

    val searchBarHorizontalPadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SearchBarHorizontalPadding"
    )
    val searchBarVerticalPadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SearchBarVerticalPadding"
    )

    fun performSearch(targetQuery: String) {
        val trimmed = targetQuery.trim()
        if (trimmed.isNotBlank()) {
            searchHistoryManager.addQuery(trimmed)
            searchActive = false
            onNavigateToOnlineSearch(trimmed)
        }
    }

    Scaffold(
        topBar = {
            SearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    viewModel.onQueryChange(it)
                    suggestionViewModel.query.value = it
                },
                onSearch = {
                    performSearch(query)
                },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = {
                    DynamicSearchPlaceholder(
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            query = ""
                            viewModel.onQueryChange("")
                            suggestionViewModel.query.value = ""
                        } else {
                            searchActive = true
                        }
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Search,
                            contentDescription = if (searchActive) "Back" else "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            viewModel.onQueryChange("")
                            suggestionViewModel.query.value = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = searchBarHorizontalPadding)
                    .padding(vertical = searchBarVerticalPadding)
            ) {
                // ── Search Active Dropdown Overlay ──────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 1. "Jump Back In" Section (Listening History)
                    if (query.isEmpty() && jumpBackInTracks.isNotEmpty()) {
                        item(key = "jump_back_in_header") {
                            Text(
                                text = "Jump Back In",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        item(key = "jump_back_in_row") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(jumpBackInTracks, key = { "jbi_${it.id}" }) { track ->
                                    val isDark = isSystemInDarkTheme()
                                    val shape = RoundedCornerShape(12.dp)
                                    val cardFill = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.10f)
                                    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

                                    Box(
                                        modifier = Modifier
                                            .width(104.dp)
                                            .clip(shape)
                                            .background(cardFill)
                                            .border(0.75.dp, cardBorder, shape)
                                            .clickable {
                                                searchActive = false
                                                onTrackClick(track.id, listOf(track))
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .blur(8.dp)
                                                .background(cardFill.copy(alpha = 0.05f))
                                        )
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            AsyncImage(
                                                model = track.albumImageUrl,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(92.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. "Search History" Dropdown
                    if (query.isEmpty() && searchHistory.isNotEmpty()) {
                        item(key = "search_history_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Search History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { searchHistoryManager.clearHistory() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        itemsIndexed(searchHistory, key = { _, h -> "sh_$h" }) { _, historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { performSearch(historyItem) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = historyItem,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    query = historyItem
                                    viewModel.onQueryChange(historyItem)
                                    suggestionViewModel.query.value = historyItem
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Fill query",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(45f).size(18.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    searchHistoryManager.removeQuery(historyItem)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Autocomplete Suggestions (while typing query)
                    if (query.isNotEmpty() && suggestionViewState.suggestions.isNotEmpty()) {
                        item(key = "suggestions_dropdown_header") {
                            Text(
                                text = "Suggestions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(suggestionViewState.suggestions) { suggestion ->
                            val decodedSuggestion = suggestion.replace("+", " ")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { performSearch(decodedSuggestion) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = decodedSuggestion,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    query = decodedSuggestion
                                    viewModel.onQueryChange(decodedSuggestion)
                                    suggestionViewModel.query.value = decodedSuggestion
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Fill query",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(45f).size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Instant Top Results (Songs, Albums, Artists)
                    if (query.isNotEmpty() && suggestionViewState.items.isNotEmpty()) {
                        item(key = "top_results_header") {
                            Text(
                                text = "Top Results",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        itemsIndexed(suggestionViewState.items) { index, item ->
                            val shape = listItemShape(index, suggestionViewState.items.size, radius = 16.dp)
                            when (item) {
                                is com.music.innertube.models.SongItem -> {
                                    val track = Track(
                                        id = item.id,
                                        title = item.title,
                                        artist = item.artists.joinToString(", ") { it.name },
                                        album = item.album?.name ?: "",
                                        durationMs = (item.duration ?: 0) * 1000L,
                                        albumImageUrl = item.thumbnail
                                    )
                                    val subtitleText = if (item.isVideoSong) "Video" else "Song"
                                    SwipeToQueueTrackRow(
                                        onSwipeToQueue = onSwipeToQueue?.let { queueAction -> { queueAction(track) } }
                                    ) {
                                        SearchResultRow(
                                            title = item.title,
                                            subtitle = "$subtitleText • ${item.artists.joinToString(", ") { it.name }}",
                                            imageUrl = item.thumbnail,
                                            shape = shape,
                                            onClick = {
                                                searchHistoryManager.addQuery(query)
                                                searchActive = false
                                                onTrackClick(track.id, listOf(track))
                                            }
                                        )
                                    }
                                }
                                is com.music.innertube.models.AlbumItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Album • ${item.artists?.joinToString(", ") { it.name } ?: "Unknown"}",
                                        imageUrl = item.thumbnail,
                                        shape = shape,
                                        onClick = {
                                            searchHistoryManager.addQuery(query)
                                            searchActive = false
                                            onPlaylistClick("album:${item.id}")
                                        }
                                    )
                                }
                                is com.music.innertube.models.ArtistItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Artist",
                                        imageUrl = item.thumbnail,
                                        isCircular = true,
                                        shape = shape,
                                        onClick = {
                                            searchHistoryManager.addQuery(query)
                                            searchActive = false
                                            onArtistClick(item.title)
                                        }
                                    )
                                }
                                is com.music.innertube.models.PlaylistItem -> {
                                    SearchResultRow(
                                        title = item.title,
                                        subtitle = "Playlist • ${item.author?.name ?: "Unknown"}",
                                        imageUrl = item.thumbnail,
                                        shape = shape,
                                        onClick = {
                                            searchHistoryManager.addQuery(query)
                                            searchActive = false
                                            onPlaylistClick("playlist:${item.id}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        // ── Main Inactive Content: Replicated Vivi-Music Suggestions Tab ──────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            SuggestionsTabContent(
                viewModel = suggestionsViewModel,
                onTrackPlay = { track ->
                    onTrackClick(track.id, listOf(track))
                },
                onAlbumClick = { albumId ->
                    onPlaylistClick("album:$albumId")
                },
                onArtistClick = { artistName ->
                    onArtistClick(artistName)
                },
                contentPadding = PaddingValues(bottom = 120.dp)
            )
        }
    }
}

@Composable
fun SearchResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isCircular: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit,
    onActionClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val cardFill = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.10f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(cardFill)
            .border(0.75.dp, cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(8.dp)
                .background(cardFill.copy(alpha = 0.05f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (isCircular) RoundedCornerShape(24.dp) else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
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
            if (onActionClick != null) {
                IconButton(onClick = onActionClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToQueueTrackRow(
    onSwipeToQueue: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    if (onSwipeToQueue == null) {
        content()
        return
    }

    val haptics = rememberHaptics()
    val context = LocalContext.current
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (_: Exception) { 0f }
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    onSwipeToQueue()
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    LaunchedEffect(swipeState, boxWidth) {
        if (boxWidth <= 0f) return@LaunchedEffect
        val armAt = boxWidth * 0.45f
        var armed = false
        snapshotFlow { try { swipeState.requireOffset() } catch (_: Exception) { 0f } }
            .collect { offset ->
                val travelled = abs(offset)
                when {
                    !armed && travelled >= armAt -> {
                        armed = true
                        haptics.play(Haptic.Tick)
                    }
                    armed && travelled < armAt * 0.8f -> armed = false
                }
            }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.onSizeChanged { boxWidth = it.width.toFloat() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val offset = try { swipeState.requireOffset() } catch (_: Exception) { 0f }
                        if (offset > 0f) {
                            clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        } else if (offset < 0f) {
                            clipRect(left = size.width + offset, top = 0f, right = size.width, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    ) {
        content()
    }
}
