package com.vibevault.app.ui.screens.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ChillScreen(
    onBack: () -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    modifier: Modifier = Modifier,
    onTrackClick: (track: com.vibevault.app.domain.model.Track, context: List<com.vibevault.app.domain.model.Track>) -> Unit = { _, _ -> }
) {
    ExploreCategoryScreen(
        title = "Chill",
        browseId = "FEmusic_moods_and_genres_category",
        params = "ggMPOg1leHBsb3JlX21vb2Rz",
        onBack = onBack,
        onPlaylistClick = onPlaylistClick,
        onAlbumClick = onAlbumClick,
        onTrackClick = onTrackClick,
        modifier = modifier
    )
}
