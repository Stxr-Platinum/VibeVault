package com.vibevault.app.domain.model

data class AlbumDetails(
    val id: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val coverUrl: String? = null,
    val albumImageUrl: String? = coverUrl,
    val description: String? = null,
    val durationMs: Long = 0L,
    val tracks: List<Track> = emptyList(),
    val playlistId: String? = null,
    val otherVersions: List<Album> = emptyList()
)
