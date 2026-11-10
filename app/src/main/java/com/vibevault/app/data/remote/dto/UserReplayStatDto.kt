package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Relational DTO mapping 1:1 to Supabase `public.user_replay_stats` columns.
 */
@Serializable
data class UserReplayStatDto(
    @SerialName("user_id") val userId: String,
    val month: String,
    @SerialName("song_id") val songId: String,
    @SerialName("song_title") val songTitle: String,
    val artist: String,
    val album: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("played_ms") val playedMs: Long = 0L,
    @SerialName("play_count") val playCount: Int = 1,
    @SerialName("last_played_at") val lastPlayedAt: String? = null
)

