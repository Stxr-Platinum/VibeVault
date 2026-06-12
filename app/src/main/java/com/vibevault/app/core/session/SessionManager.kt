package com.vibevault.app.core.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.jan.supabase.functions.Functions
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import com.vibevault.app.data.remote.dto.SpotifyTokenResponse
import com.vibevault.app.data.remote.dto.TokenRefreshRequest
import kotlinx.serialization.json.Json

/**
 * SessionManager — Secure token persistence.
 * Extended to support Spotify OAuth tokens.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val functions: Functions
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "vv_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "vv_prefs_fallback"

        // Supabase Keys
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_USER_AVATAR_URL = "user_avatar_url"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SESSION_EXPIRY = "session_expiry"

        // Spotify Keys
        private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
        private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
        private const val KEY_SPOTIFY_EXPIRY = "spotify_expiry"
        private const val KEY_SPOTIFY_CODE_VERIFIER = "spotify_code_verifier"

        // Playback Keys
        private const val KEY_LAST_PLAYED_TRACK_ID = "last_played_track_id"
    }

    private val prefs: SharedPreferences by lazy { createPreferences() }

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayNameFlow = _userDisplayName.asStateFlow()

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

    private val _userAvatarUrl = MutableStateFlow<String?>(null)
    val userAvatarUrlFlow = _userAvatarUrl.asStateFlow()

    private val _isSpotifyConnected = MutableStateFlow(false)
    val isSpotifyConnected = _isSpotifyConnected.asStateFlow()

    private val _spotifyAccessTokenFlow = MutableStateFlow<String?>(null)
    val spotifyAccessTokenFlow = _spotifyAccessTokenFlow.asStateFlow()

    init {
        _userDisplayName.value = prefs.getString(KEY_USER_DISPLAY_NAME, null)
        _userAvatarUrl.value = prefs.getString(KEY_USER_AVATAR_URL, null)
        _isLoggedInFlow.value = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        _spotifyAccessTokenFlow.value = prefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
        
        // The user is "connected" to Spotify if they have a refresh token stored.
        // Even if the access token has expired, MainViewModel will silently refresh it
        // on startup, so we should NOT send the user back to the Connect screen.
        val hasRefreshToken = prefs.getString(KEY_SPOTIFY_REFRESH_TOKEN, null) != null
        val hasAccessToken = _spotifyAccessTokenFlow.value != null
        _isSpotifyConnected.value = hasRefreshToken || (hasAccessToken && !isSpotifyExpired)
    }

    private fun createPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, using fallback", e)
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // ── Supabase Session ───────────────────────────────────────

    fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String,
        displayName: String?,
        avatarUrl: String?,
        expiresAtEpochMs: Long
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putBoolean(KEY_IS_LOGGED_IN, true)
            displayName?.let { putString(KEY_USER_DISPLAY_NAME, it) }
            avatarUrl?.let { putString(KEY_USER_AVATAR_URL, it) }
            putLong(KEY_SESSION_EXPIRY, expiresAtEpochMs)
            apply()
        }
        _isLoggedInFlow.value = true
        _userDisplayName.value = displayName
        _userAvatarUrl.value = avatarUrl
    }

    fun updateProfileMetadata(displayName: String?, avatarUrl: String?) {
        prefs.edit().apply {
            displayName?.let { putString(KEY_USER_DISPLAY_NAME, it) }
            avatarUrl?.let { putString(KEY_USER_AVATAR_URL, it) }
            apply()
        }
        displayName?.let { _userDisplayName.value = it }
        avatarUrl?.let { _userAvatarUrl.value = it }
    }

    // ── Spotify Session ────────────────────────────────────────

    fun saveSpotifySession(accessToken: String, refreshToken: String?, expiresInSeconds: Int) {
        val expiry = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        Log.d("SpotifyDebug", "SessionManager: Saving Spotify Session. AccessToken prefix: ${accessToken.take(10)}..., Expires in $expiresInSeconds s (at $expiry)")
        prefs.edit().apply {
            putString(KEY_SPOTIFY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { 
                Log.d("SpotifyDebug", "SessionManager: Saving RefreshToken prefix: ${it.take(10)}...")
                putString(KEY_SPOTIFY_REFRESH_TOKEN, it) 
            }
            putLong(KEY_SPOTIFY_EXPIRY, expiry)
            apply()
        }
        _spotifyAccessTokenFlow.value = accessToken
        _isSpotifyConnected.value = true
    }

    val spotifyAccessToken: String? get() = prefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
    val spotifyRefreshToken: String? get() = prefs.getString(KEY_SPOTIFY_REFRESH_TOKEN, null)
    val isSpotifyExpired: Boolean get() {
        val expiry = prefs.getLong(KEY_SPOTIFY_EXPIRY, 0L)
        val now = System.currentTimeMillis()
        val expired = now > expiry
        Log.d("SpotifyDebug", "SessionManager: isSpotifyExpired check. Now: $now, Expiry: $expiry, Expired: $expired")
        return expired
    }
    
    var spotifyCodeVerifier: String?
        get() = prefs.getString(KEY_SPOTIFY_CODE_VERIFIER, null)
        set(value) {
            Log.d("SpotifyDebug", "SessionManager: Setting spotifyCodeVerifier = $value")
            prefs.edit().putString(KEY_SPOTIFY_CODE_VERIFIER, value).apply()
        }

    // ── Playback State ─────────────────────────────────────────

    var lastPlayedTrackId: String?
        get() = prefs.getString(KEY_LAST_PLAYED_TRACK_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_PLAYED_TRACK_ID, value).apply()
        }

    // ── Read Accessors ─────────────────────────────────────────

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val userEmail: String? get() = prefs.getString(KEY_USER_EMAIL, null)
    val userDisplayName: String? get() = prefs.getString(KEY_USER_DISPLAY_NAME, null)
    val userAvatarUrl: String? get() = prefs.getString(KEY_USER_AVATAR_URL, null)
    val sessionExpiryMs: Long get() = prefs.getLong(KEY_SESSION_EXPIRY, 0L)
    val isSessionExpired: Boolean get() = System.currentTimeMillis() > sessionExpiryMs

    /**
     * Refreshes the Spotify access token using the stored refresh token.
     * Calls the secure Supabase Edge Function to protect the Client Secret.
     */
    suspend fun refreshSpotifyToken(): String? {
        Log.d("SpotifyDebug", "SessionManager: refreshSpotifyToken called")
        val currentRefresh = spotifyRefreshToken
        Log.d("SpotifyDebug", "SessionManager: Current RefreshToken prefix: ${currentRefresh?.take(10)}...")
        
        if (currentRefresh == null) {
            Log.e("SpotifyDebug", "SessionManager: ERROR - No refresh token available")
            return null
        }
        
        return try {
            Log.d("SpotifyDebug", "SessionManager: Invoking Edge Function 'spotify-token-refresh'...")
            val response = functions.invoke(
                function = "spotify-token-refresh",
                body = TokenRefreshRequest(refresh_token = currentRefresh)
            )
            
            Log.d("SpotifyDebug", "SessionManager: Refresh Status = ${response.status}")
            val bodyText = response.bodyAsText()
            Log.d("SpotifyDebug", "SessionManager: Refresh Response Body = $bodyText")
            
            if (response.status.value in 200..299) {
                val tokenData = Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyTokenResponse>(bodyText)
                Log.d("SpotifyDebug", "SessionManager: Refresh Success. New AccessToken prefix: ${tokenData.accessToken.take(10)}...")
                saveSpotifySession(
                    accessToken = tokenData.accessToken,
                    refreshToken = tokenData.refreshToken ?: currentRefresh,
                    expiresInSeconds = tokenData.expiresIn
                )
                tokenData.accessToken
            } else {
                Log.e("SpotifyDebug", "SessionManager: Refresh failed. Status: ${response.status}, Body: $bodyText")
                null
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SessionManager: Exception in refreshSpotifyToken", e)
            null
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _userDisplayName.value = null
        _userAvatarUrl.value = null
        _isLoggedInFlow.value = false
        _spotifyAccessTokenFlow.value = null
        _isSpotifyConnected.value = false
    }

    /** Clears only Spotify session while keeping the main user logged in. */
    fun clearSpotifySession() {
        prefs.edit().apply {
            remove(KEY_SPOTIFY_ACCESS_TOKEN)
            remove(KEY_SPOTIFY_REFRESH_TOKEN)
            remove(KEY_SPOTIFY_EXPIRY)
            apply()
        }
        _spotifyAccessTokenFlow.value = null
        _isSpotifyConnected.value = false
    }
}
