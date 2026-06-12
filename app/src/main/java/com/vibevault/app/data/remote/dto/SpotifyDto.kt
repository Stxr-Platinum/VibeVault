package com.vibevault.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

data class SpotifySearchResponse(
    val tracks: SpotifyTracksResponse? = null,
    val artists: SpotifyArtistsResponse? = null,
    val albums: SpotifyAlbumsResponse? = null,
    val playlists: SpotifyPlaylistsResponse? = null
)

data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrackDto> = emptyList()
)

data class SpotifyTracksResponse(
    val items: List<SpotifyTrackDto> = emptyList()
)

data class SpotifyBatchTracksResponse(
    val tracks: List<SpotifyTrackDto> = emptyList()
)

data class SpotifyArtistsResponse(
    val items: List<SpotifyArtistDto> = emptyList()
)

data class SpotifyAlbumsResponse(
    val items: List<SpotifyAlbumDto> = emptyList()
)

data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("external_urls") val externalUrls: Map<String, String> = emptyMap(),
    @SerializedName("external_ids") val externalIds: SpotifyExternalIdsDto? = null
)

data class SpotifyExternalIdsDto(
    val isrc: String? = null
)

data class SpotifyArtistDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList()
)

data class SpotifyAlbumDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
    val artists: List<SpotifyArtistDto> = emptyList()
)

data class SpotifyImageDto(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class SpotifyTokenResponse(
    @SerializedName("access_token") @SerialName("access_token") val accessToken: String,
    @SerializedName("token_type") @SerialName("token_type") val tokenType: String,
    @SerializedName("expires_in") @SerialName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") @SerialName("refresh_token") val refreshToken: String? = null,
    @SerializedName("scope") @SerialName("scope") val scope: String? = null
)

data class SpotifyUserDto(
    val id: String,
    @SerializedName("display_name") val displayName: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
    val email: String? = null
)

@Serializable
data class ListeningHistoryDto(
    val id: String,
    @SerializedName("user_id") @SerialName("user_id") val user_id: String,
    @SerializedName("song_id") @SerialName("song_id") val song_id: String,
    @SerializedName("song_title") @SerialName("song_title") val song_title: String,
    val artist: String,
    val album: String,
    @SerializedName("cover_url") @SerialName("cover_url") val cover_url: String,
    @SerializedName("duration_ms") @SerialName("duration_ms") val duration_ms: Long,
    val source: String,
    @SerializedName("played_at") @SerialName("played_at") val played_at: String
)

@Serializable
data class TokenExchangeRequest(
    val code: String,
    val verifier: String
)

@Serializable
data class TokenRefreshRequest(
    @SerializedName("refresh_token") val refresh_token: String
)

data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyHistoryItemDto> = emptyList()
)

data class SpotifyHistoryItemDto(
    val track: SpotifyTrackDto? = null,
    val item: SpotifyTrackDto? = null,
    @SerializedName("played_at") val playedAt: String? = null
)

data class SpotifyFeaturedPlaylistsResponse(
    val playlists: SpotifyPlaylistsResponse
)

data class SpotifyNewReleasesResponse(
    val albums: SpotifyAlbumsResponse
)

data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylistDto?> = emptyList()
)

data class SpotifyPlaylistDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
    @SerializedName("external_urls") val externalUrls: Map<String, String> = emptyMap(),
    val owner: SpotifyUserDto? = null
)

data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistItemDto> = emptyList()
)

data class SpotifyPlaylistItemDto(
    val track: SpotifyTrackDto? = null
)

data class SpotifyCategoriesResponse(
    val categories: SpotifyCategoriesListResponse
)

data class SpotifyCategoriesListResponse(
    val items: List<SpotifyCategoryDto> = emptyList()
)

data class SpotifyCategoryDto(
    val id: String,
    val name: String,
    val icons: List<SpotifyImageDto> = emptyList()
)

// ── Saved Albums (GET /me/albums) ────────────────────────
data class SpotifySavedAlbumsResponse(
    val items: List<SpotifySavedAlbumItemDto> = emptyList()
)

data class SpotifySavedAlbumItemDto(
    val album: SpotifyAlbumDto
)

// ── Artist Top Tracks (GET /artists/{id}/top-tracks) ─────
data class SpotifyArtistTopTracksResponse(
    val tracks: List<SpotifyTrackDto> = emptyList()
)

// ── Artist Albums (GET /artists/{id}/albums) ─────────────
data class SpotifyArtistAlbumsResponse(
    val items: List<SpotifyAlbumDto> = emptyList()
)

// ── Extended SpotifyAlbumDto with extra fields ───────────
data class SpotifyAlbumFullDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
    val artists: List<SpotifyArtistDto> = emptyList(),
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("total_tracks") val totalTracks: Int = 0,
    @SerializedName("album_type") val albumType: String? = null
)
