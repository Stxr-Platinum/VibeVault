package com.vibevault.app.data.repository

import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.remote.dto.*
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.local.dao.ProfileDao
import com.vibevault.app.data.local.entity.*
import com.vibevault.app.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.flow.Flow
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
    private val storage: Storage,
    private val sessionManager: SessionManager,
    private val profileDao: ProfileDao
) : AuthRepository {

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
            auth.signInWith(Google)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<String> {
        return try {
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
                
                // Fetch latest profile state from database
                refreshProfile()
                
                Result.success(user.id)
            } else {
                Result.failure(Exception("Google sign-in succeeded but no user returned"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                // Update Local Table
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

            // 1. Build the map of only the fields that actually changed
            val currentProfile = profileDao.getProfileSync(userId)
            val currentUsername = currentProfile?.username ?: sessionManager.userDisplayName
            val currentAvatar = currentProfile?.avatarUrl ?: sessionManager.userAvatarUrl

            val updateMap = mutableMapOf<String, String>()
            if (username != currentUsername) {
                updateMap["username"] = username
            }
            if (avatarUrl != currentAvatar) {
                updateMap["avatar_url"] = avatarUrl
            }

            // Only make network calls if something actually changed
            if (updateMap.isNotEmpty()) {
                // 2. Update Auth Metadata
                // By only sending changed fields, we prevent Supabase Auth triggers from 
                // accidentally triggering duplicate key constraints on unchanged fields.
                auth.updateUser {
                    data = kotlinx.serialization.json.buildJsonObject {
                        updateMap["username"]?.let { put("username", kotlinx.serialization.json.JsonPrimitive(it)) }
                        updateMap["avatar_url"]?.let { put("avatar_url", kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                }

                // 3. Update profiles table
                updateMap["updated_at"] = java.time.Instant.now().toString()
                postgrest.from("profiles").update(updateMap) {
                    filter { eq("id", userId) }
                }
            }

            // 4. Update Local
            profileDao.insertProfile(
                ProfileEntity(
                    id = userId,
                    username = username,
                    accountHolderName = currentProfile?.accountHolderName,
                    avatarUrl = avatarUrl,
                    email = sessionManager.userEmail
                )
            )

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
            val errorMsg = if (e.message?.contains("profiles_username_key") == true) {
                "Username is already taken."
            } else {
                e.message ?: "Failed to update profile"
            }
            Result.failure(Exception(errorMsg))
        }
    }

    override suspend fun updateAvatarOnly(avatarUrl: String): Result<Unit> {
        return try {
            val userId = sessionManager.userId ?: return Result.failure(Exception("Not logged in"))

            var finalAvatarUrl = avatarUrl

            // If the avatarUrl is a local file path, upload to Supabase Storage
            if (avatarUrl.startsWith("/") || avatarUrl.startsWith("file://")) {
                val cleanPath = avatarUrl.removePrefix("file://")
                val file = java.io.File(cleanPath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val ext = file.extension.ifBlank { "jpg" }
                    val fileName = "${userId}_${System.currentTimeMillis()}.$ext"
                    
                    val bucket = storage.from("avatars")
                    bucket.upload(fileName, bytes) {
                        upsert = true
                    }
                    finalAvatarUrl = bucket.publicUrl(fileName)
                }
            }

            // Update auth metadata (avatar only)
            auth.updateUser {
                data = kotlinx.serialization.json.buildJsonObject {
                    put("avatar_url", kotlinx.serialization.json.JsonPrimitive(finalAvatarUrl))
                }
            }

            // Update profiles table (avatar only)
            postgrest.from("profiles").update(
                mapOf(
                    "avatar_url" to finalAvatarUrl,
                    "updated_at" to java.time.Instant.now().toString()
                )
            ) {
                filter { eq("id", userId) }
            }

            // Update local
            val currentProfile = profileDao.getProfileSync(userId)
            profileDao.insertProfile(
                ProfileEntity(
                    id = userId,
                    username = currentProfile?.username,
                    accountHolderName = currentProfile?.accountHolderName,
                    avatarUrl = finalAvatarUrl,
                    email = sessionManager.userEmail
                )
            )

            // Update session
            sessionManager.updateProfileMetadata(null, finalAvatarUrl)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to update avatar"))
        }
    }

    override suspend fun updateUsernameOnly(username: String): Result<Unit> {
        return try {
            val userId = sessionManager.userId ?: return Result.failure(Exception("Not logged in"))

            // Update auth metadata (username only)
            auth.updateUser {
                data = kotlinx.serialization.json.buildJsonObject {
                    put("username", kotlinx.serialization.json.JsonPrimitive(username))
                }
            }

            // Update profiles table (username only)
            postgrest.from("profiles").update(
                mapOf(
                    "username" to username,
                    "updated_at" to java.time.Instant.now().toString()
                )
            ) {
                filter { eq("id", userId) }
            }

            // Update local
            val currentProfile = profileDao.getProfileSync(userId)
            profileDao.insertProfile(
                ProfileEntity(
                    id = userId,
                    username = username,
                    accountHolderName = currentProfile?.accountHolderName,
                    avatarUrl = currentProfile?.avatarUrl,
                    email = sessionManager.userEmail
                )
            )

            // Update session
            sessionManager.updateProfileMetadata(username, null)

            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = if (e.message?.contains("profiles_username_key") == true) {
                "Username is already taken."
            } else {
                e.message ?: "Failed to update username"
            }
            Result.failure(Exception(errorMsg))
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
