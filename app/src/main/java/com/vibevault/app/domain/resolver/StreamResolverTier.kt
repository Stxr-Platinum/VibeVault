package com.vibevault.app.domain.resolver

import com.vibevault.app.data.youtube.YTPlayerUtils

data class TrackResolutionSpec(
    val title: String,
    val artist: String,
    val durationSeconds: Int? = null,
    val videoId: String? = null
)

interface StreamResolverTier {
    val tierName: String
    val priority: Int
    val isEnabled: Boolean
        get() = true

    suspend fun resolve(spec: TrackResolutionSpec): YTPlayerUtils.PlaybackData?
}
