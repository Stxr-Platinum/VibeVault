package com.vibevault.app.data.remote.api

import android.util.Log
import com.vibevault.app.BuildConfig
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.remote.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpotifyApiService — Handles direct communication with the Spotify Web API.
 */
@Singleton
class SpotifyApiService @Inject constructor(
    private val sessionManager: SessionManager
) {
    private val authClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            url("https://api.spotify.com/v1/")
        }
    }

    /**
     * Search for tracks on Spotify.
     */
    suspend fun searchTracks(query: String): Result<SpotifySearchResponse> {
        Log.d("SpotifyDebug", "SpotifyApiService: searchTracks called with query = $query")
        return try {
            val token = getValidToken() 
            Log.d("SpotifyDebug", "SpotifyApiService: Token for search = ${token?.take(10)}...")
            
            if (token == null) {
                Log.e("SpotifyDebug", "SpotifyApiService: ERROR - No valid token found for search")
                return Result.failure(Exception("Not authenticated with Spotify"))
            }
            
            val url = "search"
            Log.d("SpotifyDebug", "SpotifyApiService: Executing GET $url")
            val response: io.ktor.client.statement.HttpResponse = client.get(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("q", query)
                parameter("type", "track,artist,album,playlist")
                parameter("limit", "10")
            }
            
            val status = response.status
            val bodyText = response.bodyAsText()
            Log.d("SpotifyDebug", "SpotifyApiService: Search Status = $status")
            Log.d("SpotifyDebug", "SpotifyApiService: Search Response Body = $bodyText")
            
            if (status.value in 200..299) {
                val searchResponse = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<SpotifySearchResponse>(bodyText)
                Log.d("SpotifyDebug", "SpotifyApiService: Search Success - Track items = ${searchResponse.tracks?.items?.size}")
                Result.success(searchResponse)
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Search Failed - Status $status")
                Log.e("SpotifyDebug", "SpotifyApiService: Error Body = $bodyText")
                Result.failure(Exception("Spotify search failed: $status. Body: $bodyText"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in searchTracks: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch a specific track by ID.
     */
    suspend fun getTrack(trackId: String): Result<SpotifyTrackDto> {
        Log.d("SpotifyDebug", "SpotifyApiService: getTrack called for $trackId")
        return try {
            val token = getValidToken() 
            if (token == null) return Result.failure(Exception("Not authenticated"))
            
            val response: io.ktor.client.statement.HttpResponse = client.get("tracks/$trackId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            Log.d("SpotifyDebug", "SpotifyApiService: getTrack Status = ${response.status}")
            Log.d("SpotifyDebug", "SpotifyApiService: getTrack Response = ${response.bodyAsText()}")
            
            Result.success(response.body())
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getTrack", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch multiple tracks by their IDs.
     */
    suspend fun getTracks(trackIds: List<String>): Result<List<SpotifyTrackDto>> {
        Log.d("SpotifyDebug", "SpotifyApiService: getTracks called with ${trackIds.size} IDs")
        if (trackIds.isEmpty()) return Result.success(emptyList())
        return try {
            val token = getValidToken() 
            if (token == null) return Result.failure(Exception("Not authenticated"))
            
            val response: io.ktor.client.statement.HttpResponse = client.get("tracks") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("ids", trackIds.joinToString(","))
            }
            
            Log.d("SpotifyDebug", "SpotifyApiService: getTracks Status = ${response.status}")
            val body = response.body<SpotifyBatchTracksResponse>()
            Log.d("SpotifyDebug", "SpotifyApiService: getTracks Success - Fetched ${body.tracks.size} tracks")
            
            Result.success(body.tracks)
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyDebug: Exception in getTracks", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the current user's Spotify profile.
     */
    suspend fun getUserProfile(): Result<SpotifyUserDto> {
        Log.d("SpotifyDebug", "SpotifyApiService: Fetching /me profile")
        return try {
            val token = getValidToken(forceUserAuth = true) 
            Log.d("SpotifyDebug", "SpotifyDebug: TOKEN = ${token?.take(15)}...")
            
            if (token == null) {
                Log.e("SpotifyDebug", "SpotifyApiService: getUserProfile ERROR - No valid user token")
                return Result.failure(Exception("Not authenticated"))
            }
            
            val response: io.ktor.client.statement.HttpResponse = client.get("me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            val status = response.status
            val bodyText = response.bodyAsText()
            Log.d("SpotifyDebug", "SpotifyDebug: Response code = $status")
            
            if (status.value in 200..299) {
                val profile = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyUserDto>(bodyText)
                Result.success(profile)
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Profile fetch failed with status $status. Body: $bodyText")
                Result.failure(Exception("Profile fetch failed: $status"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getUserProfile", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch recommendations from Spotify.
     */
    suspend fun getRecommendations(seedTrackId: String? = null): Result<List<SpotifyTrackDto>> {
        Log.d("SpotifyDebug", "SpotifyApiService: getRecommendations called (using search fallback)")
        return try {
            // Because the recommendations and playlist endpoints are returning 403 in dev mode,
            // we will use the Search endpoint as a fallback to get some tracks for the UI.
            val searchResult = searchTracks("year:2024")
            
            if (searchResult.isSuccess) {
                val tracks = searchResult.getOrNull()?.tracks?.items ?: emptyList()
                val validTracks = tracks.filter { it.previewUrl != null }.take(15)
                Log.d("SpotifyDebug", "SpotifyApiService: Recommendations Success - count = ${validTracks.size}")
                Result.success(validTracks)
            } else {
                val err = searchResult.exceptionOrNull()
                Log.e("SpotifyDebug", "SpotifyApiService: Recommendations fallback failed", err)
                Result.failure(err ?: Exception("Unknown search error"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getRecommendations", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch recently played tracks.
     */
    suspend fun getRecentlyPlayed(): Result<SpotifyRecentlyPlayedResponse> {
        return try {
            val token = getValidToken(forceUserAuth = true) ?: return Result.failure(Exception("Not authenticated"))
            val response: io.ktor.client.statement.HttpResponse = client.get("me/player/recently-played") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("limit", "20")
            }
            if (response.status.value in 200..299) {
                Result.success(response.body())
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Recently played failed: ${response.status}")
                Result.failure(Exception("Failed to fetch recently played: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getRecentlyPlayed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch featured playlists. MOCKED for Dev Mode.
     */
    suspend fun getFeaturedPlaylists(): Result<SpotifyFeaturedPlaylistsResponse> {
        Log.d("SpotifyDebug", "SpotifyApiService: getFeaturedPlaylists called (MOCKED)")
        return Result.success(SpotifyFeaturedPlaylistsResponse(SpotifyPlaylistsResponse(emptyList())))
    }

    /**
     * Fetch new releases.
     */
    suspend fun getNewReleases(): Result<SpotifyNewReleasesResponse> {
        return try {
            val token = getValidToken() ?: return Result.failure(Exception("Not authenticated"))
            val response: io.ktor.client.statement.HttpResponse = client.get("browse/new-releases") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.value in 200..299) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch new releases: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch user's top artists.
     */
    suspend fun getTopArtists(): Result<SpotifyArtistsResponse> {
        return try {
            val token = getValidToken(forceUserAuth = true) ?: return Result.failure(Exception("Not authenticated"))
            val response: io.ktor.client.statement.HttpResponse = client.get("me/top/artists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("limit", "10")
            }
            if (response.status.value in 200..299) {
                Result.success(response.body())
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Top artists failed: ${response.status}")
                Result.failure(Exception("Failed to fetch top artists: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getTopArtists", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch user's saved playlists.
     */
    suspend fun getUserPlaylists(): Result<SpotifyPlaylistsResponse> {
        return try {
            val token = getValidToken(forceUserAuth = true) ?: return Result.failure(Exception("Not authenticated"))
            val response: io.ktor.client.statement.HttpResponse = client.get("me/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.value in 200..299) {
                Result.success(response.body())
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: User playlists failed: ${response.status}")
                Result.failure(Exception("Failed to fetch user playlists: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyApiService: Exception in getUserPlaylists", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch browse categories. MOCKED for Dev Mode.
     */
    suspend fun getBrowseCategories(): Result<SpotifyCategoriesResponse> {
        Log.d("SpotifyDebug", "SpotifyApiService: getBrowseCategories called (MOCKED)")
        return Result.success(SpotifyCategoriesResponse(SpotifyCategoriesListResponse(emptyList())))
    }

    /**
     * Fetch tracks from a specific playlist.
     */
    suspend fun getPlaylistTracks(playlistId: String): Result<SpotifyRecentlyPlayedResponse> {
        Log.d("SpotifyDebug", "SpotifyApiService: getPlaylistTracks called for $playlistId")
        return try {
            val token = getValidToken(forceUserAuth = false) ?: return Result.failure(Exception("Not authenticated"))
            val response: HttpResponse = client.get("playlists/$playlistId/items") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("limit", "20")
            }
            if (response.status.value in 200..299) {
                // Returns same structure as recently played (items: [{track: ...}])
                Result.success(response.body())
            } else if (response.status.value == 403 || response.status.value == 404) {
                Log.w("SpotifyDebug", "SpotifyApiService: API token lacks permissions for public playlist $playlistId (403/404). Returning empty list.")
                Result.success(SpotifyRecentlyPlayedResponse(emptyList()))
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Playlist tracks failed: ${response.status}. PlaylistId: $playlistId")
                Result.failure(Exception("Failed to fetch playlist tracks: ${response.status}"))
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("403") || msg.contains("404") || e.javaClass.simpleName.contains("HttpException") || e.javaClass.simpleName.contains("ClientRequestException")) {
                Log.w("SpotifyDebug", "SpotifyApiService: Caught HTTP Exception (403/404) for $playlistId. API token lacks permissions. Returning empty list.")
                Result.success(SpotifyRecentlyPlayedResponse(emptyList()))
            } else {
                Log.e("SpotifyDebug", "SpotifyApiService: Exception in getPlaylistTracks", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Internal: Ensures we have a fresh token before making a request.
     * Priorities:
     * 1. Existing User Access Token (if not expired)
     * 2. User Refresh Token (via secure Edge Function)
     * 3. FALLBACK: Client Credentials Token (for global data, unless forceUserAuth is true)
     */
    private suspend fun getValidToken(forceUserAuth: Boolean = false): String? {
        Log.d("SpotifyDebug", "SpotifyApiService: getValidToken called (forceUserAuth=$forceUserAuth)")
        val userToken = sessionManager.spotifyAccessToken
        val isExpired = sessionManager.isSpotifyExpired
        
        Log.d("SpotifyDebug", "SpotifyDebug: User Token exists = ${userToken != null}, expired = $isExpired")
        
        if (userToken != null && !isExpired) {
            Log.d("SpotifyDebug", "SpotifyDebug: Using existing user token: ${userToken.take(10)}...")
            return userToken
        }
        
        if (sessionManager.spotifyRefreshToken != null) {
            Log.d("SpotifyDebug", "SpotifyDebug: Attempting user token refresh...")
            val refreshed = sessionManager.refreshSpotifyToken()
            if (refreshed != null) return refreshed
        }
        
        if (forceUserAuth) {
            Log.e("SpotifyDebug", "SpotifyDebug: User session invalid/expired and forceUserAuth is true. Returning null.")
            return null
        }

        Log.d("SpotifyDebug", "SpotifyDebug: User session invalid/expired. Falling back to Client Credentials...")
        return getClientCredentialsToken()
    }

    private suspend fun getClientCredentialsToken(): String? {
        val clientId = com.vibevault.app.core.constants.SpotifyConstants.CLIENT_ID
        val clientSecret = com.vibevault.app.core.constants.SpotifyConstants.CLIENT_SECRET
        
        Log.d("SpotifyDebug", "SpotifyDebug: Fetching Client Credentials Token. ClientID = $clientId")
        if (clientSecret.isBlank()) {
            Log.e("SpotifyDebug", "SpotifyDebug: ERROR - Client Secret is EMPTY. Please add it to SpotifyConstants.kt")
            return null
        }

        return try {
            val credentials = android.util.Base64.encodeToString(
                "$clientId:$clientSecret".toByteArray(),
                android.util.Base64.NO_WRAP
            )

            val response: HttpResponse = authClient.post("https://accounts.spotify.com/api/token") {
                header(HttpHeaders.Authorization, "Basic $credentials")
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")
                }))
            }

            val bodyText = response.bodyAsText()
            Log.d("SpotifyDebug", "SpotifyDebug: Token Response Status = ${response.status}")
            Log.d("SpotifyDebug", "SpotifyDebug: Token Response Body = $bodyText")

            if (response.status == HttpStatusCode.OK) {
                val tokenResponse = Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyTokenResponse>(bodyText)
                // Store in session manager as a temporary guest token
                sessionManager.saveSpotifySession(
                    tokenResponse.accessToken,
                    null,
                    tokenResponse.expiresIn
                )
                Log.d("SpotifyDebug", "SpotifyDebug: Client Credentials Token obtained: ${tokenResponse.accessToken.take(10)}...")
                tokenResponse.accessToken
            } else {
                Log.e("SpotifyDebug", "SpotifyDebug: Token Fetch Failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SpotifyDebug: Exception during token fetch", e)
            null
        }
    }
}
