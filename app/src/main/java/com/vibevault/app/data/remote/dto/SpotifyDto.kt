package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracksResponse? = null,
    val artists: SpotifyArtistsResponse? = null,
    val albums: SpotifyAlbumsResponse? = null,
    val playlists: SpotifyPlaylistsResponse? = null
)

@Serializable
data class SpotifyTracksResponse(
    val items: List<SpotifyTrackDto> = emptyList()
)

@Serializable
data class SpotifyBatchTracksResponse(
    val tracks: List<SpotifyTrackDto> = emptyList()
)

@Serializable
data class SpotifyArtistsResponse(
    val items: List<SpotifyArtistDto> = emptyList()
)

@Serializable
data class SpotifyAlbumsResponse(
    val items: List<SpotifyAlbumDto> = emptyList()
)

@Serializable
data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("external_urls") val externalUrls: Map<String, String> = emptyMap()
)

@Serializable
data class SpotifyArtistDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList()
)

@Serializable
data class SpotifyAlbumDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
    val artists: List<SpotifyArtistDto> = emptyList()
)

@Serializable
data class SpotifyImageDto(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class SpotifyUserDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
    val email: String? = null
)

@Serializable
data class ListeningHistoryDto(
    val id: String,
    val user_id: String,
    val song_id: String,
    val song_title: String,
    val artist: String,
    val album: String,
    val cover_url: String,
    val duration_ms: Long,
    val source: String,
    val played_at: String
)

@Serializable
data class TokenExchangeRequest(
    val code: String,
    val verifier: String
)

@Serializable
data class TokenRefreshRequest(
    val refresh_token: String
)

@Serializable
data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyHistoryItemDto> = emptyList()
)

@Serializable
data class SpotifyHistoryItemDto(
    val track: SpotifyTrackDto? = null,
    val item: SpotifyTrackDto? = null,
    @SerialName("played_at") val playedAt: String? = null
)

@Serializable
data class SpotifyFeaturedPlaylistsResponse(
    val playlists: SpotifyPlaylistsResponse
)

@Serializable
data class SpotifyNewReleasesResponse(
    val albums: SpotifyAlbumsResponse
)

@Serializable
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylistDto?> = emptyList()
)

@Serializable
data class SpotifyPlaylistDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
    @SerialName("external_urls") val externalUrls: Map<String, String> = emptyMap(),
    val owner: SpotifyUserDto? = null
)

@Serializable
data class SpotifyCategoriesResponse(
    val categories: SpotifyCategoriesListResponse
)

@Serializable
data class SpotifyCategoriesListResponse(
    val items: List<SpotifyCategoryDto> = emptyList()
)

@Serializable
data class SpotifyCategoryDto(
    val id: String,
    val name: String,
    val icons: List<SpotifyImageDto> = emptyList()
)
