package com.vibevault.app.core.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionManager — Secure token persistence using EncryptedSharedPreferences.
 *
 * Uses AES256-GCM for value encryption and AES256-SIV for key encryption,
 * backed by the Android Keystore system via MasterKey.
 *
 * Includes a fallback to regular SharedPreferences for devices where
 * the Keystore is corrupted or unavailable (rare, but prevents crash loops).
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "vv_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "vv_prefs_fallback"

        // Keys
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_USER_AVATAR_URL = "user_avatar_url"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SESSION_EXPIRY = "session_expiry"
    }

    private val prefs: SharedPreferences by lazy { createPreferences() }

    // ── Reactive State ──────────────────────────────────────────
    private val _userDisplayName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val userDisplayNameFlow = _userDisplayName.asStateFlow()

    private val _userAvatarUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val userAvatarUrlFlow = _userAvatarUrl.asStateFlow()

    init {
        // Initialize state flows with current pref values
        _userDisplayName.value = prefs.getString(KEY_USER_DISPLAY_NAME, null)
        _userAvatarUrl.value = prefs.getString(KEY_USER_AVATAR_URL, null)
    }

    /**
     * Attempts to create EncryptedSharedPreferences.
     * Falls back to standard SharedPreferences if the Keystore is
     * unavailable (e.g., corrupted after OS update on certain OEMs).
     */
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

    // ── Session Write ──────────────────────────────────────────

    /**
     * Persists the full session after a successful authentication.
     */
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
            
            if (displayName != null) putString(KEY_USER_DISPLAY_NAME, displayName)
            else remove(KEY_USER_DISPLAY_NAME)
            
            if (avatarUrl != null) putString(KEY_USER_AVATAR_URL, avatarUrl)
            else remove(KEY_USER_AVATAR_URL)
            
            putBoolean(KEY_IS_LOGGED_IN, true)
            putLong(KEY_SESSION_EXPIRY, expiresAtEpochMs)
            apply()
        }
        _userDisplayName.value = displayName
        _userAvatarUrl.value = avatarUrl
    }

    // ── Session Read ───────────────────────────────────────────

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)

    val userDisplayName: String?
        get() = prefs.getString(KEY_USER_DISPLAY_NAME, null)

    val userAvatarUrl: String?
        get() = prefs.getString(KEY_USER_AVATAR_URL, null)

    val sessionExpiryMs: Long
        get() = prefs.getLong(KEY_SESSION_EXPIRY, 0L)

    val isSessionExpired: Boolean
        get() = System.currentTimeMillis() > sessionExpiryMs

    // ── Session Clear ──────────────────────────────────────────

    /**
     * Wipes all session data — called on logout or account deletion.
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        _userDisplayName.value = null
        _userAvatarUrl.value = null
    }
}
