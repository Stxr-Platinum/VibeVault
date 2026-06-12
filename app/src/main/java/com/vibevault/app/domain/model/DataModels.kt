package com.vibevault.app.domain.model

import com.google.gson.annotations.SerializedName

// ── Qobuz API Models ─────────────────────────────────────────────────────────

data class QobuzSearchResponse(
    @SerializedName("data") val data: QobuzSearchData?
)

data class QobuzSearchData(
    @SerializedName("tracks") val tracks: QobuzTrackList?,
    @SerializedName("albums") val albums: QobuzAlbumList?,
    @SerializedName("artists") val artists: QobuzArtistList?
)

data class QobuzTrackList(
    @SerializedName("items") val items: List<QobuzTrackDto>?
)

data class QobuzAlbumList(
    @SerializedName("items") val items: List<QobuzAlbumDto>?
)

data class QobuzArtistList(
    @SerializedName("items") val items: List<QobuzArtistDto>?
)

data class QobuzTrackDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("parental_warning") val parentalWarning: Boolean?,
    @SerializedName("hires_streamable") val hiresStreamable: Boolean?,
    @SerializedName("maximum_bit_depth") val maximumBitDepth: Int?,
    @SerializedName("isrc") val isrc: String?,
    @SerializedName("performer") val performer: QobuzArtistDto?,
    @SerializedName("album") val album: QobuzAlbumDto?
)

data class QobuzAlbumDto(
    @SerializedName("id") val id: String?,
    @SerializedName("qobuz_id") val qobuzId: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("image") val image: QobuzImageDto?,
    @SerializedName("artist") val artist: QobuzArtistDto?
)

data class QobuzArtistDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("picture") val picture: String?,
    @SerializedName("image") val image: QobuzImageDto?
)

data class QobuzImageDto(
    @SerializedName("large") val large: String?,
    @SerializedName("small") val small: String?,
    @SerializedName("thumbnail") val thumbnail: String?
)

data class QobuzStreamResponse(
    @SerializedName("data") val data: QobuzStreamData?
)

data class QobuzStreamData(
    @SerializedName("url") val url: String?
)


// ── Domain Models ────────────────────────────────────────────────────────────

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumImageUrl: String,
    val audioUrl: String? = null,
    val durationMs: Long,
    val isLiked: Boolean = false,
    val localPath: String? = null,
    val source: String = "stream",
    val externalUrl: String? = null,
    
    // Extensions for Qobuz compatibility
    val coverUrl: String = albumImageUrl,
    val duration: Int = (durationMs / 1000).toInt(),
    val isExplicit: Boolean = false,
    val audioQuality: String = "HIGH",
    val isrc: String = ""
)

// ── Mappers ──────────────────────────────────────────────────────────────────

fun QobuzTrackDto.toDomainTrack(): Track {
    val artistName = this.performer?.name ?: this.album?.artist?.name ?: "Unknown Artist"
    val albumTitle = this.album?.title ?: "Unknown Album"
    // The Qobuz API returns a direct HTTP URL for the image
    val image = this.album?.image?.large ?: ""
    val durationSecs = this.duration ?: 0
    
    val quality = when {
        this.hiresStreamable == true -> "HI_RES_LOSSLESS"
        (this.maximumBitDepth ?: 0) >= 24 -> "LOSSLESS"
        else -> "HIGH"
    }

    return Track(
        id = this.id?.toString() ?: "",
        title = this.title ?: "",
        artist = artistName,
        album = albumTitle,
        albumImageUrl = image,
        durationMs = durationSecs * 1000L,
        isExplicit = this.parentalWarning ?: false,
        audioQuality = quality,
        isrc = this.isrc ?: ""
    )
}

fun com.vibevault.app.data.remote.dto.SpotifyTrackDto.toDomainTrack(): Track {
    return Track(
        id = this.id ?: "",
        title = this.name ?: "Unknown Title",
        artist = this.artists?.firstOrNull()?.name ?: "Unknown Artist",
        album = this.album?.name ?: "",
        albumImageUrl = this.album?.images?.firstOrNull()?.url ?: "",
        durationMs = this.durationMs?.toLong() ?: 0L,
        isrc = this.externalIds?.isrc ?: "",
        audioUrl = this.previewUrl,
        externalUrl = this.externalUrls?.get("spotify")
    )
}

fun com.vibevault.app.data.remote.dto.SpotifyPlaylistDto.toPlaylistEntity(): com.vibevault.app.data.local.entity.PlaylistEntity {
    return com.vibevault.app.data.local.entity.PlaylistEntity(
        id = this.id ?: "",
        title = this.name ?: "Unknown Playlist",
        createdAt = System.currentTimeMillis(),
        trackCount = 0, // Fetched separately
        coverUrl = this.images?.firstOrNull()?.url
    )
}
