package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.jan.supabase.functions.Functions
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val functions: Functions,
    private val sessionManager: SessionManager,
    private val realtimeSyncManager: com.vibevault.app.data.sync.RealtimeSyncManager,
    private val musicRepository: com.vibevault.app.domain.repository.MusicRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogleIdToken(idToken)
            result.fold(
                onSuccess = { 
                    _authState.value = AuthState.Success 
                },
                onFailure = { error -> 
                    _authState.value = AuthState.Error(error.message ?: "Google login failed")
                }
            )
        }
    }


    fun getSpotifyAuthUrl(): String {
        val clientId = com.vibevault.app.BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = com.vibevault.app.BuildConfig.SPOTIFY_REDIRECT_URI
        val scopes = "playlist-read-private playlist-read-collaborative user-library-read"
        
        // Generate PKCE code verifier and challenge
        val codeVerifier = generateCodeVerifier()
        sessionManager.spotifyCodeVerifier = codeVerifier
        val codeChallenge = generateCodeChallenge(codeVerifier)
        
        return "https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&code_challenge_method=S256&code_challenge=$codeChallenge&scope=$scopes"
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = java.security.SecureRandom()
        val code = ByteArray(32)
        secureRandom.nextBytes(code)
        return android.util.Base64.encodeToString(code, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes)
        val digest = messageDigest.digest()
        return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    fun handleSpotifyCallback(code: String) {
        Log.d("SpotifyAuth", "AUTH CODE RECEIVED: $code")
        val codeVerifier = sessionManager.spotifyCodeVerifier
        if (codeVerifier == null) {
            _authState.value = AuthState.Error("Authentication state lost. Please try again.")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                val clientId = com.vibevault.app.BuildConfig.SPOTIFY_CLIENT_ID
                val redirectUri = com.vibevault.app.BuildConfig.SPOTIFY_REDIRECT_URI

                val client = okhttp3.OkHttpClient()
                val formBody = okhttp3.FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val tokenResponse = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<com.vibevault.app.data.remote.dto.SpotifyTokenResponse>(responseBody)
                    sessionManager.saveSpotifySession(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken ?: "",
                        expiresInSeconds = tokenResponse.expiresIn
                    )
                    
                    // Clear the code verifier
                    sessionManager.spotifyCodeVerifier = null
                    
                    // Trigger sync immediately
                    try {
                        musicRepository.backgroundSyncSpotifyPlaylists()
                    } catch (e: Exception) {
                        Log.e("SpotifyAuth", "Background sync failed after auth", e)
                    }
                    
                    _authState.value = AuthState.SpotifySuccess
                } else {
                    Log.e("SpotifyAuth", "Token exchange failed: ${response.code} - $responseBody")
                    _authState.value = AuthState.Error("Failed to connect to Spotify: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SpotifyAuth", "Exception during token exchange", e)
                _authState.value = AuthState.Error(e.message ?: "Failed to connect to Spotify")
            }
        }
    }


    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object SpotifySuccess : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
