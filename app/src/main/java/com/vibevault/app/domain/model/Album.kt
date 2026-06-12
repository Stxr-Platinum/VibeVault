package com.vibevault.app.domain.model

data class Album(
    val id: String,
    val name: String,
    val artistName: String,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val totalTracks: Int = 0
)
