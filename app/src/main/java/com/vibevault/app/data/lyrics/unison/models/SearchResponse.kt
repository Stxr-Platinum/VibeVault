package com.vibevault.app.data.lyrics.unison.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val success: Boolean,
    val data: List<LyricsData> = emptyList(),
    val error: String? = null,
)
