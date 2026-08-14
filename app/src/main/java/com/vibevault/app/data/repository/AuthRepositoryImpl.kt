package com.vibevault.app.data.repository

import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.local.dao.PfpDao
import com.vibevault.app.data.local.dao.ProfileDao
import com.vibevault.app.data.local.entity.*
import com.vibevault.app.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepositoryImpl — Concrete implementation of AuthRepository.
 * Bridges the domain layer to Supabase Auth and the encrypted SessionManager.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val sessionManager: SessionManager,
    private val profileDao: ProfileDao,
    private val pfpDao: PfpDao
) : AuthRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            auth.sessionStatus.collect { status ->
                if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) {
                    val session = status.session
                    val user = session.user
                    if (user != null) {
                        val metadata = user.userMetadata
                        val fullName = metadata?.get("full_name")?.let { 
                            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                        }
                        val avatar = (metadata?.get("avatar_url") ?: metadata?.get("picture"))?.let {
                            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                        }

                        sessionManager.saveSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            userId = user.id,
                            email = user.email ?: "",
                            displayName = fullName ?: user.email?.substringBefore("@"),
                            avatarUrl = avatar,
                            expiresAtEpochMs = (session.expiresAt?.epochSeconds ?: 0) * 1000
                        )
                        refreshProfile()
                    }
                }
            }
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        if (sessionManager.isLoggedIn && !sessionManager.isSessionExpired) {
            // Restore Supabase session if needed
            try {
                val refreshToken = sessionManager.refreshToken
                if (refreshToken != null) {
                    auth.refreshSession(refreshToken)
                }
            } catch (e: Exception) {
                return false
            }
            return true
        }
        return false
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val session = auth.currentSessionOrNull()
            val user = session?.user
            if (user != null) {
                val metadata = user.userMetadata
                val fullName = metadata?.get("full_name")?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                }
                val avatar = (metadata?.get("avatar_url") ?: metadata?.get("picture"))?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                }

                sessionManager.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    userId = user.id,
                    email = user.email ?: email,
                    displayName = fullName ?: user.email?.substringBefore("@"),
                    avatarUrl = avatar,
                    expiresAtEpochMs = (session.expiresAt?.epochSeconds ?: 0) * 1000
                )
                
                // Immediately try to fetch from profiles table to get latest info
                refreshProfile()
                
                Result.success(user.id)
            } else {
                Result.failure(Exception("Sign-in succeeded but no user returned"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull()
            if (user != null) {
                Result.success(user.id)
            } else {
                Result.success("check_email")  // Email confirmation required
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            auth.signInWith(Google, redirectUrl = "vibevault://auth-callback")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<String> {
        // Attempt 1: Try native Supabase Kotlin client IDToken provider
        try {
            auth.signInWith(io.github.jan.supabase.auth.providers.builtin.IDToken) {
                this.idToken = idToken
                this.provider = Google
            }
            val session = auth.currentSessionOrNull()
            val user = session?.user
            if (user != null) {
                val metadata = user.userMetadata
                val fullName = metadata?.get("full_name")?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                }
                val avatar = (metadata?.get("avatar_url") ?: metadata?.get("picture"))?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().replace("\"", "")
                }

                sessionManager.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    userId = user.id,
                    email = user.email ?: "",
                    displayName = fullName ?: user.email?.substringBefore("@"),
                    avatarUrl = avatar,
                    expiresAtEpochMs = (session.expiresAt?.epochSeconds ?: 0) * 1000
                )
                refreshProfile()
                return Result.success(user.id)
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepositoryImpl", "Supabase SDK IDToken signin failed, trying direct REST endpoint", e)
        }

        // Fallback: Direct REST call to Supabase auth API without redirect_to query parameter
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val jsonBody = org.json.JSONObject().apply {
                    put("id_token", idToken)
                    put("provider", "google")
                }.toString()

                val mediaType = "application/json".toMediaType()
                val requestBody = okhttp3.RequestBody.create(mediaType, jsonBody)

                val request = okhttp3.Request.Builder()
                    .url("${com.vibevault.app.BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=id_token")
                    .addHeader("apikey", com.vibevault.app.BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = org.json.JSONObject(responseBody)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id") ?: ""
                    val email = userObj?.optString("email") ?: ""
                    val userMetadata = userObj?.optJSONObject("user_metadata")
                    val fullName = userMetadata?.optString("full_name") ?: userMetadata?.optString("name")
                    val avatarUrl = userMetadata?.optString("avatar_url") ?: userMetadata?.optString("picture")

                    sessionManager.saveSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        userId = userId,
                        email = email,
                        displayName = if (!fullName.isNullOrBlank()) fullName else email.substringBefore("@"),
                        avatarUrl = avatarUrl,
                        expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000)
                    )
                    refreshProfile()
                    Result.success(userId)
                } else {
                    val errorMsg = if (!responseBody.isNullOrBlank()) {
                        try {
                            org.json.JSONObject(responseBody).optString("error_description", responseBody)
                        } catch (_: Exception) { responseBody }
                    } else "HTTP ${response.code}"
                    Result.failure(Exception("Google Sign-In Error: $errorMsg"))
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepositoryImpl", "Direct REST signin failed", e)
                Result.failure(Exception("Authentication failed: ${e.message ?: "Network error"}"))
            }
        }
    }

    override suspend fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {
            // Best-effort sign out
        }
        sessionManager.clearSession()
    }

    override fun getCurrentUserId(): String? = sessionManager.userId

    override suspend fun refreshProfile(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        return@withContext try {
            val userId = sessionManager.userId ?: return@withContext Result.failure(Exception("Not logged in"))
            
            // 1. Fetch Profile
            val profile = postgrest.from("profiles")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<ProfileDto>()

            if (profile != null) {
                // Update Local Table (Reorganization)
                profileDao.insertProfile(
                    ProfileEntity(
                        id = userId,
                        username = profile.username,
                        accountHolderName = profile.accountHolderName,
                        avatarUrl = profile.avatarUrl,
                        email = sessionManager.userEmail,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )

                // Update Session (Legacy/Compatibility)
                sessionManager.saveSession(
                    accessToken = sessionManager.accessToken ?: "",
                    refreshToken = sessionManager.refreshToken ?: "",
                    userId = userId,
                    email = sessionManager.userEmail ?: "",
                    displayName = profile.username ?: profile.accountHolderName ?: sessionManager.userDisplayName,
                    avatarUrl = profile.avatarUrl ?: sessionManager.userAvatarUrl,
                    expiresAtEpochMs = sessionManager.sessionExpiryMs
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(username: String, avatarUrl: String): Result<Unit> {
        return try {
            val userId = sessionManager.userId ?: return Result.failure(Exception("Not logged in"))

            // 1. Update Auth Metadata
            auth.updateUser {
                data = kotlinx.serialization.json.buildJsonObject {
                    put("username", kotlinx.serialization.json.JsonPrimitive(username))
                    put("avatar_url", kotlinx.serialization.json.JsonPrimitive(avatarUrl))
                }
            }

            // 2. Update profiles table - Only update username and avatar_url
            postgrest.from("profiles").upsert(
                mapOf(
                    "id" to userId,
                    "username" to username,
                    "avatar_url" to avatarUrl,
                    "updated_at" to java.time.Instant.now().toString()
                )
            )

            // 3. Update Local
            val currentProfile = profileDao.getProfileSync(userId)
            profileDao.insertProfile(
                ProfileEntity(
                    id = userId,
                    username = username,
                    accountHolderName = currentProfile?.accountHolderName,
                    avatarUrl = avatarUrl,
                    email = sessionManager.userEmail
                )
            )
            // 4. Removed pfpDao references
            // 5. Update Session
            val session = auth.currentSessionOrNull()
            if (session != null) {
                sessionManager.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    userId = userId,
                    email = sessionManager.userEmail ?: "",
                    displayName = username, // App displays username as main name
                    avatarUrl = avatarUrl,
                    expiresAtEpochMs = (session.expiresAt?.epochSeconds ?: 0) * 1000
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncSpotifyProfile(spotifyUserId: String, displayName: String?, avatarUrl: String?): Result<Unit> {
        return try {
            val userId = sessionManager.userId ?: return Result.failure(Exception("Not logged in"))
            
            // Update Supabase profile
            postgrest.from("profiles").update(
                mapOf(
                    "username" to (displayName ?: ""),
                    "avatar_url" to (avatarUrl ?: ""),
                    "updated_at" to java.time.Instant.now().toString()
                )
            ) {
                filter { eq("id", userId) }
            }

            // Update local profile
            val currentProfile = profileDao.getProfileSync(userId)
            profileDao.insertProfile(
                ProfileEntity(
                    id = userId,
                    username = displayName ?: currentProfile?.username,
                    accountHolderName = currentProfile?.accountHolderName,
                    avatarUrl = avatarUrl ?: currentProfile?.avatarUrl,
                    email = sessionManager.userEmail
                )
            )

            // Update session manager
            sessionManager.updateProfileMetadata(displayName, avatarUrl)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getProfileFlow(userId: String): Flow<ProfileEntity?> = profileDao.getProfile(userId)


    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            // Sign out from Supabase and clear all local session data
            auth.signOut()
            sessionManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            // Even on error, clear local session to prevent stale state
            sessionManager.clearSession()
            Result.failure(e)
        }
    }
}
