package com.vibevault.app.ui.screens.playlist

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.components.ArtworkWash
import com.vibevault.app.ui.components.FastScrollbar
import com.vibevault.app.ui.components.FrostedTopBar
import com.vibevault.app.ui.components.TopFadeBlur
import com.vibevault.app.ui.components.topBarContentPadding
import com.vibevault.app.ui.haptics.Haptic
import com.vibevault.app.ui.haptics.rememberHaptics
import com.vibevault.app.ui.icons.BitChordIcons
import com.vibevault.app.ui.theme.ArtworkPalette
import com.vibevault.app.ui.theme.HEADER_ART_PX
import com.vibevault.app.ui.theme.artworkAt
import com.vibevault.app.ui.theme.rememberArtworkPalette
import com.vibevault.app.utils.rememberPreference
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.jan.supabase.storage.Storage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SLEEVE_RATIO = 0.92f
private val PAGE_GUTTER = 16.dp
private val HEADER_GUTTER = PAGE_GUTTER + 14.dp
private val HEADER_DROP = 44.dp
private val MERGE_BAND = 320.dp
private val MERGE_BLUR = 100.dp
private val DETAIL_TITLE_DROP = 320.dp
private val PILL_SHAPE = RoundedCornerShape(12.dp)
private val ROW_DIVIDER_INSET = PAGE_GUTTER + 52.dp + 14.dp // 82.dp
private const val SEARCH_ITEM_INDEX = 1

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StorageEntryPoint {
    fun storage(): Storage
    fun sessionManager(): SessionManager
}

@Composable
fun SquareCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(imageUri) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(imageUri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            Log.e("CropDialog", "Failed decoding bitmap", e)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop Playlist Cover", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Center 1:1 square preview:", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                if (bitmap != null) {
                    val square = remember(bitmap) {
                        val b = bitmap!!
                        val size = Math.min(b.width, b.height)
                        val x = (b.width - size) / 2
                        val y = (b.height - size) / 2
                        Bitmap.createBitmap(b, x, y, size, size)
                    }
                    Image(
                        bitmap = square.asImageBitmap(),
                        contentDescription = "Cropped Preview",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    bitmap?.let { b ->
                        val size = Math.min(b.width, b.height)
                        val x = (b.width - size) / 2
                        val y = (b.height - size) / 2
                        val cropped = Bitmap.createBitmap(b, x, y, size, size)
                        onCropConfirmed(cropped)
                    }
                },
                enabled = bitmap != null
            ) {
                Text("Crop & Set Cover", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF282828)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (String, List<Track>) -> Unit,
    onSwipeToQueue: ((Track) -> Unit)? = null,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmapToUpload by remember { mutableStateOf<Bitmap?>(null) }

    if (pendingCropUri != null) {
        SquareCropDialog(
            imageUri = pendingCropUri!!,
            onDismiss = { pendingCropUri = null },
            onCropConfirmed = { cropped ->
                pendingCropUri = null
                croppedBitmapToUpload = cropped
            }
        )
    }

    LaunchedEffect(croppedBitmapToUpload) {
        val bitmap = croppedBitmapToUpload ?: return@LaunchedEffect
        isUploadingCover = true
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            var finalUrl = "data:image/jpeg;base64,$base64Str"

            val coversDir = File(context.filesDir, "playlist_covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }
            val localFile = File(coversDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(localFile)
            fos.write(bytes)
            fos.flush()
            fos.close()

            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    StorageEntryPoint::class.java
                )
                val storage = entryPoint.storage()
                val sessionManager = entryPoint.sessionManager()
                val userId = sessionManager.userId

                if (userId != null) {
                    val fileName = "${playlistId}_${System.currentTimeMillis()}.jpg"
                    val bucket = storage.from("covers")
                    bucket.upload(fileName, bytes) {
                        upsert = true
                    }
                    val publicHttpUrl = bucket.publicUrl(fileName)
                    if (publicHttpUrl.isNotBlank()) {
                        finalUrl = publicHttpUrl
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistScreen", "Supabase storage upload failed, using Base64 Data URL", e)
            }

            viewModel.updatePlaylistCover(finalUrl)
        } catch (e: Exception) {
            Log.e("PlaylistScreen", "Failed processing cropped cover image", e)
        } finally {
            isUploadingCover = false
            croppedBitmapToUpload = null
        }
    }

    // Image picker for gallery
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            pendingCropUri = selectedUri
        }
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Save Album to Playlist", color = Color.White) },
            text = {
                LazyColumn {
                    items(userPlaylists, key = { it.id }) { p ->
                        Text(
                            text = p.title,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addAlbumToPlaylist(p.id)
                                    showAddToPlaylistDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                    if (userPlaylists.isEmpty()) {
                        item {
                            Text("No playlists available", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = Color(0xFF282828)
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renamePlaylist(newName)
                    }
                    showRenameDialog = false
                }) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF282828),
            titleContentColor = Color.White
        )
    }

    val listState = rememberLazyListState()
    val thumbnail = playlist?.coverUrl?.ifBlank { null } ?: tracks.firstOrNull()?.albumImageUrl.orEmpty()
    val palette = rememberArtworkPalette(thumbnail, artPx = HEADER_ART_PX)
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    val pageHaze = remember { HazeState() }
    val contentHaze = remember { HazeState() }

    val infiniteTransition = rememberInfiniteTransition(label = "playlistAmbient")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val detailTitleDropPx = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val scrolled by remember(listState, detailTitleDropPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > detailTitleDropPx
        }
    }

    var searching by rememberSaveable(playlistId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(playlistId) { mutableStateOf("") }
    var focusSearch by remember(playlistId) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        searchQuery = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    LaunchedEffect(searching) {
        if (searching) listState.animateScrollToItem(SEARCH_ITEM_INDEX, -searchStop)
    }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks.mapIndexed { idx, tr -> idx to tr }
        } else {
            tracks.mapIndexedNotNull { idx, tr ->
                if (tr.title.contains(searchQuery, ignoreCase = true) ||
                    tr.artist.contains(searchQuery, ignoreCase = true)
                ) {
                    idx to tr
                } else null
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val artHeight = maxWidth / SLEEVE_RATIO

        // Container captured by contentHaze for TopFadeBlur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contentHaze)
        ) {
            // ── Layer 1: Page Background (Dynamic Artwork + Wash) ────────────────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .hazeSource(pageHaze)
            ) {
                // Dynamic full-screen ambient artwork backdrop
                if (thumbnail.isNotEmpty() && !reduceDynamicBlur) {
                    AsyncImage(
                        model = thumbnail.artworkAt(HEADER_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.45f * pulseScale
                                scaleY = 1.45f * pulseScale
                            }
                            .blur(72.dp, BlurredEdgeTreatment.Unbounded)
                            .alpha(pulseAlpha)
                    )
                }

                ArtworkWash(palette = palette, modifier = Modifier.matchParentSize())

                // Dynamic gradient overlay unifying backdrop with playlist palette
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to palette.background.copy(alpha = 0.25f),
                                0.40f to palette.wash.copy(alpha = 0.60f),
                                0.75f to palette.background.copy(alpha = 0.90f),
                                1.00f to palette.background
                            )
                        )
                )

                if (thumbnail.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(artHeight)
                            .offset {
                                IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt())
                            }
                    ) {
                        AsyncImage(
                            model = thumbnail.artworkAt(HEADER_ART_PX),
                            contentDescription = playlist?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .background(palette.elevated)
                        )

                        // Shade under top bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.28f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(palette.background.copy(alpha = 0.55f), Color.Transparent)
                                    )
                                )
                        )

                        // Settles the foot of the picture onto the wash colour
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.55f to Color.Transparent,
                                        1.00f to palette.wash.copy(alpha = 0.88f)
                                    )
                                )
                        )
                    }
                }
            }

            // ── Layer 2: MergeBand (Glass Blur Across Picture Join) ───────────────
            if (!reduceDynamicBlur && thumbnail.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MERGE_BAND)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (
                                    listState.headerTop(artHeight.toPx()) +
                                        artHeight.toPx() - MERGE_BAND.toPx() / 2f
                                    ).roundToInt()
                            )
                        }
                        .hazeEffect(pageHaze) {
                            canDrawArea = { true }
                            blurRadius = MERGE_BLUR
                            noiseFactor = 0f
                            tints = listOf(HazeTint(Color.Transparent))
                            backgroundColor = palette.wash
                            mask = Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.50f to Color.Black,
                                1.00f to Color.Transparent
                            )
                        }
                )
            }

            // ── Layer 3: LazyColumn Content ──────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Header item
                item(key = "playlist_header") {
                    PlaylistReleaseHeader(
                        title = playlist?.title.orEmpty(),
                        owner = playlist?.ownerName?.ifBlank { "Spotify" } ?: "Spotify",
                        trackCount = tracks.size,
                        artHeight = artHeight,
                        palette = palette,
                        onPlay = {
                            if (tracks.isNotEmpty()) {
                                viewModel.recordPlayed()
                                onTrackClick(tracks.first().id, tracks)
                            }
                        },
                        onShuffle = {
                            if (tracks.isNotEmpty()) {
                                viewModel.recordPlayed()
                                val shuffled = tracks.shuffled()
                                onTrackClick(shuffled.first().id, shuffled)
                            }
                        },
                        searching = searching,
                        onSearchToggle = {
                            if (searching) closeSearch()
                            else {
                                searching = true
                                focusSearch = true
                            }
                        },
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshPlaylist() }
                    )
                }

                // In-place Search Field
                if (searching) {
                    item(key = "playlist_search_field") {
                        DetailSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = closeSearch,
                            autoFocus = focusSearch,
                            onFocused = { focusSearch = false },
                            palette = palette
                        )
                    }
                }

                if (tracks.isEmpty()) {
                    item(key = "playlist_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No songs in this playlist yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.onBackgroundVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredTracks, key = { _, pair -> "track_${pair.second.id}_${pair.first}" }) { position, (originalIndex, track) ->
                        PlaylistSongRow(
                            trackNumber = originalIndex + 1,
                            track = track,
                            palette = palette,
                            onClick = {
                                viewModel.recordPlayed()
                                onTrackClick(track.id, tracks)
                            },
                            onSwipeToQueue = onSwipeToQueue?.let { queueAction -> { queueAction(track) } },
                            onRemove = { viewModel.removeTrack(track.id) },
                            onShare = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                            }
                        )
                        if (position < filteredTracks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                thickness = 0.5.dp,
                                color = palette.divider,
                            )
                        }
                    }
                }
            }

            // Interactive FastScrollbar for Playlist
            FastScrollbar(
                listState = listState,
                itemCount = filteredTracks.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = topBarContentPadding(), bottom = 120.dp),
                headerCount = if (searching) 2 else 1,
                getSectionText = { index ->
                    filteredTracks.getOrNull(index)?.second?.title?.firstOrNull()?.uppercase() ?: "#${index + 1}"
                }
            )
        }

        // ── Layer 4: TopFadeBlur ─────────────────────────────────────────────
        TopFadeBlur(
            hazeState = contentHaze,
            pageColor = palette.wash,
            scrimColor = palette.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // ── Layer 5: Frosted Top Bar ─────────────────────────────────────────
        FrostedTopBar(
            title = playlist?.title ?: "Playlist",
            scrolled = scrolled,
            onBack = onBackClick,
            refreshing = isRefreshing,
            actions = {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF282828))
                    ) {
                        if (playlistId.startsWith("album:")) {
                            DropdownMenuItem(
                                text = { Text("Save Album to Playlist", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    showAddToPlaylistDialog = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Rename", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Change Cover",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp).padding(end = 12.dp)
                                        )
                                        Text("Change Cover", color = Color.White)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePlaylist(onBackClick)
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Parallax offset calculation matching BitChord.
 */
private fun LazyListState.headerTop(artHeightPx: Float): Float {
    if (firstVisibleItemIndex > 0) return -artHeightPx
    return -firstVisibleItemScrollOffset.toFloat() * 0.45f
}

/**
 * A playlist release header: title, metadata, and action buttons
 * positioned over the foot of the artwork matching BitChord.
 */
@Composable
private fun PlaylistReleaseHeader(
    title: String,
    owner: String,
    trackCount: Int,
    artHeight: Dp,
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    searching: Boolean,
    onSearchToggle: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
) {
    val refreshTransition = rememberInfiniteTransition(label = "refreshRotation")
    val refreshAngle by if (isRefreshing) {
        refreshTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshAngle"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_GUTTER),
            )

            Spacer(Modifier.height(4.dp))
            val metaText = buildString {
                append("Playlist")
                if (owner.isNotBlank()) {
                    append(" • ").append(owner)
                }
                if (trackCount > 0) {
                    append(" • ").append(trackCount).append(" songs")
                }
            }
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_GUTTER),
            )

            Spacer(Modifier.height(18.dp))

            // Action Pills Row matching BitChord: Shuffle, Play, Search, More
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HEADER_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = BitChordIcons.Shuffle,
                    contentDescription = "Shuffle",
                    palette = palette,
                    onClick = onShuffle,
                    size = 50.dp,
                )

                PlayPill(
                    palette = palette,
                    onClick = onPlay,
                    horizontalPadding = 24.dp,
                )

                CircleIconButton(
                    icon = if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                    contentDescription = if (searching) "Close search" else "Search",
                    palette = palette,
                    onClick = onSearchToggle,
                    size = 50.dp,
                )

                CircleIconButton(
                    icon = BitChordIcons.Refresh,
                    contentDescription = "Refresh",
                    palette = palette,
                    onClick = onRefresh,
                    size = 50.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = refreshAngle },
                )
            }
        }
    }
}

/**
 * Prominent pill-shaped Play button matching BitChord.
 */
@Composable
private fun PlayPill(
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Play",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Small circular icon button matching BitChord.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: Haptic? = Haptic.Tap,
    size: Dp = 50.dp,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptic?.let { haptics.play(it) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * In-place live search input field matching BitChord.
 */
@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(PILL_SHAPE)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), PILL_SHAPE)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search this playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = if (query.isEmpty()) "Close search" else "Clear search",
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Clean playlist track row with swipe-to-queue and track options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSongRow(
    trackNumber: Int,
    track: Track,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onSwipeToQueue: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && onSwipeToQueue != null) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (e: Exception) { 0f }
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    onSwipeToQueue()
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    if (onSwipeToQueue != null) {
        LaunchedEffect(swipeState, boxWidth) {
            if (boxWidth <= 0f) return@LaunchedEffect
            val armAt = boxWidth * 0.45f
            var armed = false
            snapshotFlow { try { swipeState.requireOffset() } catch (e: Exception) { 0f } }
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
                            val offset = try { swipeState.requireOffset() } catch (e: Exception) { 0f }
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
                        .padding(horizontal = PAGE_GUTTER + 6.dp),
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
            SongRowContent(
                trackNumber = trackNumber,
                track = track,
                palette = palette,
                onClick = onClick,
                onRemove = onRemove,
                onShare = onShare,
            )
        }
    } else {
        SongRowContent(
            trackNumber = trackNumber,
            track = track,
            palette = palette,
            onClick = onClick,
            onRemove = onRemove,
            onShare = onShare,
        )
    }
}

@Composable
private fun SongRowContent(
    trackNumber: Int,
    track: Track,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!track.albumImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.albumImageUrl,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.onBackgroundVariant,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600),
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge(palette)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onBackgroundVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Box {
            var showTrackMenu by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showTrackMenu = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Options",
                    tint = palette.onBackgroundVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = { showTrackMenu = false },
                modifier = Modifier.background(Color(0xFF282828))
            ) {
                DropdownMenuItem(
                    text = { Text("Remove from playlist", color = Color.White) },
                    onClick = {
                        showTrackMenu = false
                        onRemove()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = Color.White) },
                    onClick = {
                        showTrackMenu = false
                        onShare()
                    }
                )
            }
        }
    }
}

@Composable
private fun ExplicitBadge(palette: ArtworkPalette) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(palette.onBackgroundVariant.copy(alpha = 0.25f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = palette.onBackground,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
