package com.vibevault.app.data.remote.api

import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.domain.model.*
import javax.inject.Inject

class SpotifyApiService @Inject constructor() {
    suspend fun getUserProfile(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun searchTracks(query: String): Result<SpotifySearchResponse> = Result.failure(Exception("Stub"))
    suspend fun getRecommendations(seedId: String?): Result<List<SpotifyTrackDto>> = Result.failure(Exception("Stub"))
    suspend fun getTrack(trackId: String): Result<SpotifyTrackDto> = Result.failure(Exception("Stub"))
    suspend fun getRecentlyPlayed(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getFeaturedPlaylists(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getNewReleases(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getTopArtists(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getUserPlaylists(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getBrowseCategories(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getPlaylistTracks(playlistId: String): Result<Any> = Result.failure(Exception("Stub"))
}
