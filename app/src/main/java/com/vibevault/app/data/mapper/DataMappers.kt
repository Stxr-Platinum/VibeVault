package com.vibevault.app.data.mapper

import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.data.local.entity.LikedSongEntity
import com.vibevault.app.data.local.entity.ProfileEntity
import com.vibevault.app.data.local.entity.PfpEntity
import com.vibevault.app.data.remote.dto.PlaylistDto
import com.vibevault.app.data.remote.dto.TrackDto
import com.vibevault.app.data.local.entity.PlaylistTrackCrossRef
import com.vibevault.app.data.remote.dto.PlaylistTrackDto
import com.vibevault.app.data.remote.dto.LikeDto
import com.vibevault.app.data.remote.dto.PfpDto
import com.vibevault.app.data.remote.dto.ProfileDto
import com.vibevault.app.domain.model.Playlist
import com.vibevault.app.domain.model.Track

/**
 * DataMappers — Aligned with API-driven and denormalized schema.
 */

// ── Track: DTO → Domain ───────────────────────────────────
fun TrackDto.toDomain(artistName: String = "Unknown"): Track = Track(
    id = id,
    title = title,
    artist = artistName,
    album = albumId ?: "Unknown",
    albumImageUrl = albumArt ?: "",
    audioUrl = url,
    durationMs = duration.toLong() * 1000,
    isLiked = false
)

// ── LikedSongEntity → Domain ──────────────────────────────
fun LikedSongEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumImageUrl = albumImageUrl,
    audioUrl = audioUrl,
    durationMs = durationMs,
    isLiked = true
)

// ── PlaylistTrackCrossRef → Domain ────────────────────────
fun PlaylistTrackCrossRef.toDomain(): Track = Track(
    id = trackId,
    title = title,
    artist = artist,
    album = album,
    albumImageUrl = albumImageUrl,
    audioUrl = audioUrl,
    durationMs = durationMs,
    isLiked = false // Will be updated by repository if needed
)

// ── Track → LikedSongEntity ───────────────────────────────
fun Track.toLikedEntity(): LikedSongEntity = LikedSongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumImageUrl = albumImageUrl,
    audioUrl = audioUrl ?: "",
    durationMs = durationMs
)

// ── Track → PlaylistTrackCrossRef ─────────────────────────
fun Track.toPlaylistCrossRef(playlistId: String, sortOrder: Int): PlaylistTrackCrossRef = PlaylistTrackCrossRef(
    playlistId = playlistId,
    trackId = id,
    title = title,
    artist = artist,
    album = album,
    albumImageUrl = albumImageUrl,
    audioUrl = audioUrl ?: "",
    durationMs = durationMs,
    sortOrder = sortOrder
)

// ── Playlist: DTO → Entity ────────────────────────────────
fun PlaylistDto.toPlaylistEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    trackCount = trackCount ?: 0,
    isSynced = true
)

// ── Playlist: Entity → Domain ─────────────────────────────
fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    trackCount = trackCount
)

// ── PFP: DTO → Entity ─────────────────────────────────────
fun PfpDto.toPfpEntity(): com.vibevault.app.data.local.entity.PfpEntity = com.vibevault.app.data.local.entity.PfpEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    userId = userId,
    url = url,
    isActive = isActive
)

// ── PlaylistTrack DTO → CrossRef ──────────────────────────
fun PlaylistTrackDto.toCrossRef(): PlaylistTrackCrossRef = PlaylistTrackCrossRef(
    playlistId,
    trackId,
    songTitle ?: "Unknown",
    artist ?: "Unknown",
    album ?: "Unknown",
    coverUrl ?: "",
    "",
    (durationMs ?: 0).toLong(),
    sortOrder
)

fun LikeDto.toLikedSongEntity(): LikedSongEntity = LikedSongEntity(
    trackId,
    songTitle ?: "Unknown",
    artist ?: "Unknown",
    album ?: "Unknown",
    coverUrl ?: "",
    "",
    (durationMs ?: 0).toLong()
)

fun ProfileDto.mapToProfileEntity(): ProfileEntity = ProfileEntity(
    id,
    username,
    accountHolderName,
    avatarUrl,
    null, // email
    null, // bio
    System.currentTimeMillis()
)
