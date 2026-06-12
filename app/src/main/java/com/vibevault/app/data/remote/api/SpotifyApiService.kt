package com.vibevault.app.data.remote.api

import com.vibevault.app.data.remote.dto.SpotifyPlaylistsResponse
import com.vibevault.app.data.remote.dto.SpotifyPlaylistTracksResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface SpotifyApiService {
    
    @GET("v1/me/playlists")
    suspend fun getMyPlaylists(
        @Header("Authorization") bearerToken: String
    ): SpotifyPlaylistsResponse

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistTracks(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String,
        @retrofit2.http.Query("additional_types") additionalTypes: String = "track"
    ): SpotifyPlaylistTracksResponse

    @GET("v1/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String
    ): com.vibevault.app.data.remote.dto.SpotifyPlaylistDto

    @GET("v1/tracks/{id}")
    suspend fun getTrack(
        @Header("Authorization") bearerToken: String,
        @Path("id") trackId: String
    ): com.vibevault.app.data.remote.dto.SpotifyTrackDto

    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("q") query: String,
        @retrofit2.http.Query("type") type: String = "track",
        @retrofit2.http.Query("limit") limit: Int = 1
    ): com.vibevault.app.data.remote.dto.SpotifySearchResponse

    @GET("v1/recommendations")
    suspend fun getRecommendations(
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("seed_tracks") seedTracks: String,
        @retrofit2.http.Query("limit") limit: Int = 20
    ): com.vibevault.app.data.remote.dto.SpotifyRecommendationsResponse
}
