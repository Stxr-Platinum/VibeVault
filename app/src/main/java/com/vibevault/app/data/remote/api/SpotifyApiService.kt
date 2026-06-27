package com.vibevault.app.data.remote.api

import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.domain.model.*
import com.vibevault.app.core.session.SessionManager
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SpotifyApiService @Inject constructor(
    private val client: OkHttpClient,
    private val sessionManager: SessionManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun getAuthHeader(): String? {
        val token = sessionManager.spotifyAccessToken
        if (token.isNullOrEmpty()) return null
        return "Bearer $token"
    }

    suspend fun getUserProfile(): Result<Any> = Result.failure(Exception("Stub"))
    
    suspend fun searchTracks(query: String): Result<SpotifySearchResponse> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("No Spotify token"))
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext Result.success(SpotifySearchResponse(null))
        
        val url = "https://api.spotify.com/v1/search?q=${java.net.URLEncoder.encode(trimmedQuery, "UTF-8")}&type=track&limit=25"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader)
            .build()
            
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (bodyString != null) {
                    val parsed = json.decodeFromString<SpotifySearchResponse>(bodyString)
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("Empty body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getRecommendations(seedId: String?): Result<List<SpotifyTrackDto>> = Result.failure(Exception("Stub"))
    suspend fun getTrack(trackId: String): Result<SpotifyTrackDto> = Result.failure(Exception("Stub"))
    suspend fun getRecentlyPlayed(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getFeaturedPlaylists(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getNewReleases(): Result<Any> = Result.failure(Exception("Stub"))
    suspend fun getTopArtists(): Result<Any> = Result.failure(Exception("Stub"))
    
    suspend fun getUserPlaylists(): Result<List<SpotifyPlaylistDto>> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("No Spotify token"))
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/playlists?limit=50")
            .addHeader("Authorization", authHeader)
            .build()
            
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (bodyString != null) {
                    val parsed = json.decodeFromString<SpotifyPlaylistsResponse>(bodyString)
                    Result.success(parsed.items.filterNotNull())
                } else {
                    Result.failure(Exception("Empty body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBrowseCategories(): Result<Any> = Result.failure(Exception("Stub"))
    
    suspend fun getPlaylistTracks(playlistId: String): Result<List<SpotifyPlaylistItemDto>> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("No Spotify token"))
        
        val allItems = mutableListOf<SpotifyPlaylistItemDto>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=100&additional_types=track"
        
        try {
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .addHeader("Authorization", authHeader)
                    .build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (bodyString != null) {
                        android.util.Log.d("SpotifyDebug", "SpotifyApiService: fetched items body length = ${bodyString.length}, first 100 chars: ${bodyString.take(100)}")
                        val parsed = json.decodeFromString<SpotifyPlaylistItemsResponse>(bodyString)
                        android.util.Log.d("SpotifyDebug", "SpotifyApiService: parsed ${parsed.items.size} items")
                        allItems.addAll(parsed.items)
                        nextUrl = parsed.next
                    } else {
                        nextUrl = null
                    }
                } else {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
            }
            Result.success(allItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
