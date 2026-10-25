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
    get() {
        val fromTag = localConfiguration?.tag as? MediaMetadata
        if (fromTag != null) return fromTag
        val rawId = mediaId
        val cleanId = rawId.split("/").lastOrNull() ?: rawId
        if (cleanId.isBlank()) return null
        val md = mediaMetadata
        val durMs = md.extras?.getLong("durationMs", 0L) ?: 0L
        val titleStr = md.title?.toString()?.takeIf { it.isNotBlank() }
            ?: md.displayTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: "Track $cleanId"
        val artistStr = md.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: md.subtitle?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"
        val albumStr = md.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        val thumbStr = md.artworkUri?.toString() ?: md.extras?.getString("artwork_uri")
        return MediaMetadata(
            id = cleanId,
            title = titleStr,
            artists = listOf(MediaMetadata.Artist(id = "", name = artistStr)),
            album = albumStr?.let { MediaMetadata.Album(id = "", title = it) },
            duration = if (durMs > 0) (durMs / 1000).toInt() else 0,
            thumbnailUrl = thumbStr
        )
    }


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
                    putLong("durationMs", (duration ?: 0) * 1000L)
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
                    putLong("durationMs", (duration ?: 0) * 1000L)
                    thumbnailUrl?.takeIf { it.isNotBlank() }?.let { putString("artwork_uri", it) }
                })
                .build()
        )
        .build()
}

fun com.vibevault.app.domain.model.Track.toMediaItem(): MediaItem {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artist.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Artist"
    val safeAlbum = if (isVideo) "" else album.takeIf { it.isNotBlank() && it != "Unknown" && it != "Unknown Album" }.orEmpty()
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

    val metadataAlbum = if (!isVideo && !albumId.isNullOrBlank()) {
        MediaMetadata.Album(albumId, safeAlbum.ifBlank { "Album" })
    } else if (!isVideo && safeAlbum.isNotBlank()) {
        MediaMetadata.Album("", safeAlbum)
    } else {
        null
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setCustomCacheKey(id)
        .setTag(MediaMetadata(
            id = id,
            title = safeTitle,
            artists = listOf(MediaMetadata.Artist(artistId, safeArtist)),
            duration = (durationMs / 1000).toInt(),
            thumbnailUrl = albumImageUrl,
            album = metadataAlbum,
            musicVideoType = if (isVideo) "MUSIC_VIDEO_TYPE_OMV" else null
        ))
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(safeTitle)
                .setSubtitle(safeArtist)
                .setArtist(safeArtist)
                .setArtworkUri(safeArtUri)
                .setAlbumTitle(safeAlbum.takeIf { it.isNotBlank() })
                .setAlbumArtist(safeArtist)
                .setDisplayTitle(safeTitle)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    putLong("durationMs", durationMs)
                    albumImageUrl.takeIf { it.isNotBlank() }?.let { putString("artwork_uri", it) }
                })
                .build()
        )
        .build()
}

fun SongItem.toTrack(): Track {
    val safeTitle = title.takeIf { it.isNotBlank() } ?: "Track $id"
    val safeArtist = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val isVideoItem = isVideoSong
    return Track(
        id = id,
        title = safeTitle,
        artist = safeArtist,
        album = if (isVideoItem) "" else (album?.name ?: ""),
        albumImageUrl = thumbnail,
        durationMs = (duration ?: 0) * 1000L,
        source = "youtube",
        albumId = if (isVideoItem) null else album?.id,
        artistId = artists.firstOrNull()?.id,
        isVideo = isVideoItem
    )
}
