/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.vibevault.app.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.music.innertube.models.SongItem
import com.vibevault.app.domain.model.Track
import com.vibevault.app.models.MediaMetadata
import com.vibevault.app.models.toMediaMetadata


val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata


fun SongItem.toMediaItem(): MediaItem {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val safeAlbum = album?.name?.takeIf { it.isNotBlank() } ?: "Unknown Album"
    val safeArtUri = thumbnail.takeIf { it.isNotBlank() }?.toUri()

    val streamUri = if (id.startsWith("http://") || id.startsWith("https://")) {
        id.toUri()
    } else {
        android.net.Uri.Builder()
            .scheme("vibevault")
            .authority("stream")
            .appendQueryParameter("id", id.split("/").lastOrNull() ?: id)
            .appendQueryParameter("title", safeTitle)
            .appendQueryParameter("artist", safeArtist)
            .appendQueryParameter("duration", (duration ?: 0).toString())
            .build()
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setCustomCacheKey(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(safeTitle)
                .setSubtitle(safeArtist)
                .setArtist(safeArtist)
                .setArtworkUri(safeArtUri)
                .setAlbumTitle(safeAlbum)
                .setAlbumArtist(artists.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: safeArtist)
                .setDisplayTitle(safeTitle)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    thumbnail.takeIf { it.isNotBlank() }?.let { putString("artwork_uri", it) }
                })
                .build()
        )
        .build()
}

fun MediaMetadata.toMediaItem(): MediaItem {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val safeAlbum = album?.title?.takeIf { it.isNotBlank() } ?: "Unknown Album"
    val safeArtUri = thumbnailUrl?.takeIf { it.isNotBlank() }?.toUri()

    val streamUri = if (id.startsWith("http://") || id.startsWith("https://")) {
        id.toUri()
    } else {
        android.net.Uri.Builder()
            .scheme("vibevault")
            .authority("stream")
            .appendQueryParameter("id", id.split("/").lastOrNull() ?: id)
            .appendQueryParameter("title", safeTitle)
            .appendQueryParameter("artist", safeArtist)
            .appendQueryParameter("duration", (duration ?: 0).toString())
            .build()
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setCustomCacheKey(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(safeTitle)
                .setSubtitle(safeArtist)
                .setArtist(safeArtist)
                .setArtworkUri(safeArtUri)
                .setAlbumTitle(safeAlbum)
                .setAlbumArtist(artists.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: safeArtist)
                .setDisplayTitle(safeTitle)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    thumbnailUrl?.takeIf { it.isNotBlank() }?.let { putString("artwork_uri", it) }
                })
                .build()
        )
        .build()
}

fun com.vibevault.app.domain.model.Track.toMediaItem(): MediaItem {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artist.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Artist"
    val safeAlbum = album.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Album"
    val safeArtUri = albumImageUrl.takeIf { it.isNotBlank() }?.toUri()

    val streamUri = if (id.startsWith("http://") || id.startsWith("https://")) {
        id.toUri()
    } else {
        android.net.Uri.Builder()
            .scheme("vibevault")
            .authority("stream")
            .appendQueryParameter("id", id.split("/").lastOrNull() ?: id)
            .appendQueryParameter("title", safeTitle)
            .appendQueryParameter("artist", safeArtist)
            .appendQueryParameter("duration", (durationMs / 1000).toString())
            .build()
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setCustomCacheKey(id)
        .setTag(MediaMetadata(
            id = id,
            title = safeTitle,
            artists = listOf(MediaMetadata.Artist(null, safeArtist)),
            duration = (durationMs / 1000).toInt(),
            thumbnailUrl = albumImageUrl,
            album = MediaMetadata.Album("", safeAlbum)
        ))
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(safeTitle)
                .setSubtitle(safeArtist)
                .setArtist(safeArtist)
                .setArtworkUri(safeArtUri)
                .setAlbumTitle(safeAlbum)
                .setAlbumArtist(safeArtist)
                .setDisplayTitle(safeTitle)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    albumImageUrl.takeIf { it.isNotBlank() }?.let { putString("artwork_uri", it) }
                })
                .build()
        )
        .build()
}

fun SongItem.toTrack(): Track {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    return Track(
        id = id,
        title = safeTitle,
        artist = safeArtist,
        album = album?.name ?: "Unknown Album",
        albumImageUrl = thumbnail,
        durationMs = (duration ?: 0) * 1000L,
        source = "youtube"
    )
}
