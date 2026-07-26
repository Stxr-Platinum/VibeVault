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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentContext(
    val id: String,
    val type: String,
    val title: String,
    val coverUrl: String,
    val timestamp: Long = 0L
)

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
        private const val KEY_SPOTIFY_SP_DC = "spotify_sp_dc"
        private const val KEY_SPOTIFY_SP_KEY = "spotify_sp_key"
        private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
        private const val KEY_SPOTIFY_EXPIRY = "spotify_expiry"

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

    private val _recentContextsFlow = MutableStateFlow<List<RecentContext>>(emptyList())
    val recentContextsFlow = _recentContextsFlow.asStateFlow()

    init {
        _userDisplayName.value = prefs.getString(KEY_USER_DISPLAY_NAME, null)
        _userAvatarUrl.value = prefs.getString(KEY_USER_AVATAR_URL, null)
        _isLoggedInFlow.value = prefs.getString(KEY_ACCESS_TOKEN, null) != null
        
        val token = prefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
        val spDc = prefs.getString(KEY_SPOTIFY_SP_DC, null)
        _spotifyAccessTokenFlow.value = token
        _isSpotifyConnected.value = !spDc.isNullOrBlank()
        if (!token.isNullOrBlank() && !isSpotifyExpired) {
            com.music.spotify.Spotify.accessToken = token
        }
        
        _recentContextsFlow.value = getRecentContexts()
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
            displayName?.let { putString(KEY_USER_DISPLAY_NAME, it) }
            avatarUrl?.let { putString(KEY_USER_AVATAR_URL, it) }
            putLong(KEY_SESSION_EXPIRY, expiresAtEpochMs)
            putBoolean(KEY_IS_LOGGED_IN, true)
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
        _userDisplayName.value = displayName
        _userAvatarUrl.value = avatarUrl
    }

    // ── Spotify Session ────────────────────────────────────────

    fun saveSpotifyCookieSession(spDc: String, spKey: String = "", accessToken: String, expiresAtMs: Long) {
        Log.d("SpotifyDebug", "SessionManager: Saving Spotify Cookie Session. AccessToken prefix: ${accessToken.take(10)}...")
        prefs.edit().apply {
            putString(KEY_SPOTIFY_SP_DC, spDc)
            putString(KEY_SPOTIFY_SP_KEY, spKey)
            putString(KEY_SPOTIFY_ACCESS_TOKEN, accessToken)
            putLong(KEY_SPOTIFY_EXPIRY, expiresAtMs)
            apply()
        }
        com.music.spotify.Spotify.accessToken = accessToken
        _spotifyAccessTokenFlow.value = accessToken
        _isSpotifyConnected.value = true
    }

    val spotifySpDc: String? get() = prefs.getString(KEY_SPOTIFY_SP_DC, null)
    val spotifySpKey: String? get() = prefs.getString(KEY_SPOTIFY_SP_KEY, null)
    val spotifyAccessToken: String? get() = prefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
    val isSpotifyExpired: Boolean get() {
        val expiry = prefs.getLong(KEY_SPOTIFY_EXPIRY, 0L)
        val now = System.currentTimeMillis()
        val expired = now > expiry
        Log.d("SpotifyDebug", "SessionManager: isSpotifyExpired check. Now: $now, Expiry: $expiry, Expired: $expired")
        return expired
    }

    // ── Playback State ─────────────────────────────────────────

    var lastPlayedTrackId: String?
        get() = prefs.getString(KEY_LAST_PLAYED_TRACK_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_PLAYED_TRACK_ID, value).apply()
        }

    fun addRecentContext(id: String, type: String, title: String, coverUrl: String) {
        val currentJson = prefs.getString("recent_contexts", "[]") ?: "[]"
        try {
            val format = Json { ignoreUnknownKeys = true }
            val list = try {
                format.decodeFromString<List<RecentContext>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            val newItem = RecentContext(id, type, title, coverUrl, System.currentTimeMillis())
            list.removeAll { it.id == id }
            list.add(0, newItem)
            val updated = list.take(10)
            prefs.edit().putString("recent_contexts", format.encodeToString(updated)).apply()
            _recentContextsFlow.value = updated
        } catch (e: Exception) {
            Log.e(TAG, "Error adding recent context", e)
        }
    }

    fun getRecentContexts(): List<RecentContext> {
        val currentJson = prefs.getString("recent_contexts", "[]") ?: "[]"
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString(currentJson)
        } catch (e: Exception) {
            emptyList()
        }
    }


    val isLoggedIn: Boolean get() = prefs.getString(KEY_ACCESS_TOKEN, null) != null
    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val userEmail: String? get() = prefs.getString(KEY_USER_EMAIL, null)
    val userDisplayName: String? get() = prefs.getString(KEY_USER_DISPLAY_NAME, null)
    val userAvatarUrl: String? get() = prefs.getString(KEY_USER_AVATAR_URL, null)
    val sessionExpiryMs: Long get() = prefs.getLong(KEY_SESSION_EXPIRY, 0L)
    val isSessionExpired: Boolean get() = System.currentTimeMillis() > sessionExpiryMs

    /**
     * Refreshes the Spotify access token using the stored sp_dc cookie.
     */
    suspend fun refreshSpotifyToken(): String? {
        Log.d("SpotifyDebug", "SessionManager: refreshSpotifyToken called using sp_dc cookie")
        val spDc = spotifySpDc ?: return null
        val spKey = spotifySpKey.orEmpty()
        
        return try {
            val result = com.music.spotify.SpotifyAuth.fetchAccessToken(spDc, spKey)
            if (result.isSuccess) {
                val internalToken = result.getOrThrow()
                saveSpotifyCookieSession(
                    spDc = spDc,
                    spKey = spKey,
                    accessToken = internalToken.accessToken,
                    expiresAtMs = internalToken.accessTokenExpirationTimestampMs
                )
                internalToken.accessToken
            } else {
                Log.e("SpotifyDebug", "SessionManager: Refresh failed", result.exceptionOrNull())
                null
            }
        } catch (e: Exception) {
            Log.e("SpotifyDebug", "SessionManager: Exception in refreshSpotifyToken", e)
            null
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        com.music.spotify.Spotify.accessToken = null
        _userDisplayName.value = null
        _userAvatarUrl.value = null
        _isLoggedInFlow.value = false
        _spotifyAccessTokenFlow.value = null
        _isSpotifyConnected.value = false
    }

    /** Clears only Spotify session while keeping the main user logged in. */
    fun clearSpotifySession() {
        prefs.edit().apply {
            remove(KEY_SPOTIFY_SP_DC)
            remove(KEY_SPOTIFY_SP_KEY)
            remove(KEY_SPOTIFY_ACCESS_TOKEN)
            remove(KEY_SPOTIFY_EXPIRY)
            apply()
        }
        com.music.spotify.Spotify.accessToken = null
        _spotifyAccessTokenFlow.value = null
        _isSpotifyConnected.value = false
    }
}
