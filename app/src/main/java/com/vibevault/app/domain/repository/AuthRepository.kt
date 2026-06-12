package com.vibevault.app.domain.repository

/**
 * AuthRepository — Domain-layer interface for authentication operations.
 * The data layer provides the concrete implementation backed by Supabase Auth.
 */
interface AuthRepository {

    /** Returns true if a valid, non-expired session exists. */
    suspend fun isLoggedIn(): Boolean

    /** Email + password sign-in. Returns Result with user ID on success. */
    suspend fun signInWithEmail(email: String, password: String): Result<String>

    /** Email + password sign-up. Returns Result with user ID on success. */
    suspend fun signUpWithEmail(email: String, password: String): Result<String>

    /** Initiates Google OAuth flow via Supabase Auth. */
    suspend fun signInWithGoogle(): Result<Unit>

    /** Authenticates with Supabase using a Google ID Token. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<String>

    /** Signs out, clears local session. */
    suspend fun signOut()

    /** Returns the current user's ID, or null. */
    fun getCurrentUserId(): String?

    /** Fetch the user's latest profile data from Supabase and update local session. */
    suspend fun refreshProfile(): Result<Unit>

    /** Updates the user's profile metadata. */
    suspend fun updateProfile(username: String, avatarUrl: String): Result<Unit>

    /** Updates only the user's avatar URL. */
    suspend fun updateAvatarOnly(avatarUrl: String): Result<Unit>

    /** Updates only the user's username. */
    suspend fun updateUsernameOnly(username: String): Result<Unit>

    /** Syncs Spotify profile data to Supabase. */
    suspend fun syncSpotifyProfile(spotifyUserId: String, displayName: String?, avatarUrl: String?): Result<Unit>

    /** Deletes the current user's account. */
    suspend fun deleteAccount(): Result<Unit>

}
