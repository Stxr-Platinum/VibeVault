package com.vibevault.app.data.mapper

import com.vibevault.app.data.local.entity.*
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.domain.model.Playlist
import com.vibevault.app.domain.model.Track
import java.time.Instant

/**
 * DataMappers — Bridges DTOs, Entities, and Domain models.
 * Updated to support soft-deletes and denormalized metadata sync.
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
    isLiked = false
)

// ── Track → LikedSongEntity ───────────────────────────────
fun Track.toLikedEntity(): LikedSongEntity = LikedSongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumImageUrl = albumImageUrl,
    audioUrl = audioUrl ?: "",
    durationMs = durationMs,
    isSynced = false // Mark as needing sync when created locally
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
    sortOrder = sortOrder,
    isSynced = false
)

// ── Playlist: DTO → Entity ────────────────────────────────
fun PlaylistDto.toPlaylistEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    trackCount = trackCount,
    durationMs = durationMs,
    isPublic = isPublic,
    isSynced = true,
    isDeleted = isDeleted,
    createdAt = createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
)

// ── Playlist: Entity → Domain ─────────────────────────────
fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    trackCount = trackCount
)

// ── Like: DTO → Entity ────────────────────────────────────
fun LikeDto.toLikedSongEntity(): LikedSongEntity = LikedSongEntity(
    id = trackId,
    title = songTitle ?: "Unknown",
    artist = artist ?: "Unknown",
    album = album ?: "Unknown",
    albumImageUrl = coverUrl ?: "",
    audioUrl = "", // Remote doesn't send audioUrl usually
    durationMs = (durationMs ?: 0).toLong(),
    isSynced = true,
    isDeleted = isDeleted,
    createdAt = createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
)

// ── PlaylistTrack: DTO → CrossRef ─────────────────────────
fun PlaylistTrackDto.toCrossRef(): PlaylistTrackCrossRef = PlaylistTrackCrossRef(
    playlistId = playlistId,
    trackId = trackId,
    title = songTitle ?: "Unknown",
    artist = artist ?: "Unknown",
    album = album ?: "Unknown",
    albumImageUrl = coverUrl ?: "",
    audioUrl = "",
    durationMs = (durationMs ?: 0).toLong(),
    sortOrder = sortOrder,
    isSynced = true,
    isDeleted = isDeleted,
    addedAt = createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
)

// ── Profile: DTO → Entity ─────────────────────────────────
fun ProfileDto.mapToProfileEntity(): ProfileEntity = ProfileEntity(
    id = id,
    username = username,
    accountHolderName = accountHolderName,
    avatarUrl = avatarUrl,
    email = null,
    bio = null,
    lastSyncedAt = System.currentTimeMillis()
)

// ── Pfp: DTO → Entity ─────────────────────────────────────
fun PfpDto.toPfpEntity(): PfpEntity = PfpEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    userId = userId,
    url = url,
    isActive = isActive,
    createdAt = createdAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()
)

// ── Spotify: DTO → Domain ────────────────────────────────
fun SpotifyTrackDto.toDomain(likedIds: Set<String> = emptySet()): Track? {
    val nonNullId = id ?: return null
    return Track(
        id = nonNullId,
    title = name,
    artist = artists.firstOrNull()?.name ?: "Unknown",
    album = album?.name ?: "Unknown",
    albumImageUrl = album?.images?.firstOrNull()?.url ?: "",
    audioUrl = previewUrl,
    durationMs = durationMs,
    isLiked = likedIds.contains(id),
    source = "spotify"
)
}

fun SpotifyArtistDto.toDomain(): com.vibevault.app.domain.model.Artist = com.vibevault.app.domain.model.Artist(
    id = id,
    name = name,
    imageUrl = images.firstOrNull()?.url
)

fun SpotifyAlbumDto.toDomain(likedIds: Set<String> = emptySet()): Track = Track(
    id = id,
    title = name,
    artist = artists.firstOrNull()?.name ?: "Unknown",
    album = name,
    albumImageUrl = images.firstOrNull()?.url ?: "",
    durationMs = 0,
    isLiked = likedIds.contains(id),
    source = "spotify"
)

fun SpotifyPlaylistDto.toDomain(): Playlist = Playlist(
    id = id,
    title = name,
    description = description,
    coverUrl = images.firstOrNull()?.url,
    ownerName = owner?.displayName,
    trackCount = 0
)

fun SpotifyCategoryDto.toDomain(): com.vibevault.app.domain.model.Category = com.vibevault.app.domain.model.Category(
    id = id,
    name = name,
    imageUrl = icons.firstOrNull()?.url
)

// ── Helper: Parse Supabase Timestamp ──────────────────────
private fun parseTimestamp(timestamp: String): Long {
    return try {
        Instant.parse(timestamp).toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
