package com.vibevault.app.domain.model

data class SpotifySearchResult(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Track> = emptyList(), // Representing albums as tracks for now (album tracks) or simplified
    val playlists: List<Playlist> = emptyList()
)
