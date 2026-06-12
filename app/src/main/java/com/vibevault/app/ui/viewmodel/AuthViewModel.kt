package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.remote.auth.SpotifyAuthManager
import com.vibevault.app.data.remote.dto.SpotifyTokenResponse
import com.vibevault.app.data.remote.dto.TokenExchangeRequest
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.jan.supabase.functions.Functions
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val functions: Functions,
    private val sessionManager: SessionManager
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


    fun startSpotifyAuth() {
        Log.d("SpotifyAuth", "LOGIN STARTED")
        // Only clear Spotify session to force fresh consent if needed, 
        // DO NOT clear the main session as it would redirect to Login screen.
        sessionManager.clearSpotifySession() 
        spotifyAuthManager.startAuthFlow()
    }

    fun handleSpotifyCallback(code: String) {
        Log.d("SpotifyAuth", "AUTH CODE RECEIVED")
        Log.d("SpotifyDebug", "AuthViewModel: handleSpotifyCallback called with code = $code")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            try {
                val verifier = sessionManager.spotifyCodeVerifier 
                Log.d("SpotifyDebug", "AuthViewModel: Retreived Verifier = $verifier")
                
                if (verifier == null) {
                    Log.e("SpotifyDebug", "AuthViewModel: ERROR - No code verifier found in SessionManager")
                    throw Exception("No code verifier found")
                }
                
                // Secure Token Exchange via Edge Function
                Log.d("SpotifyDebug", "AuthViewModel: Invoking Edge Function 'spotify-token-exchange'...")
                val response = functions.invoke(
                    function = "spotify-token-exchange",
                    body = TokenExchangeRequest(code = code, verifier = verifier)
                )
                
                Log.d("SpotifyDebug", "AuthViewModel: Edge Function Status = ${response.status}")
                val bodyText = response.bodyAsText()
                Log.d("SpotifyDebug", "AuthViewModel: Edge Function Response Body = $bodyText")
                
                if (response.status.value in 200..299) {
                    val tokenData = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyTokenResponse>(bodyText)
                    Log.d("SpotifyDebug", "AuthViewModel: Parsed Token Data - AccessToken prefix: ${tokenData.accessToken.take(10)}...")
                    
                    sessionManager.saveSpotifySession(
                        accessToken = tokenData.accessToken,
                        refreshToken = tokenData.refreshToken,
                        expiresInSeconds = tokenData.expiresIn
                    )
                    Log.d("SpotifyAuth", "TOKEN SAVED")
                    Log.d("SpotifyDebug", "AuthViewModel: Session saved to SessionManager")
                    
                    Log.d("SpotifyDebug", "AuthVM: Token obtained successfully")
                
                // Fetch user profile through musicRepository or directly
                try {
                    // For now just mark as authenticated since we purged Spotify API
                    _authState.value = AuthState.SpotifySuccess
                } catch (e: Exception) {
                    Log.e("SpotifyDebug", "AuthVM: Profile fetch failed", e)
                    _authState.value = AuthState.Error("Failed to fetch user profile")
                }
                } else {
                    Log.e("SpotifyDebug", "AuthViewModel: Exchange failed. Body: $bodyText")
                    _authState.value = AuthState.Error("Exchange failed: ${response.status}")
                }
            } catch (e: Exception) {
                Log.e("SpotifyDebug", "AuthViewModel: Exception in handleSpotifyCallback", e)
                _authState.value = AuthState.Error(e.message ?: "Spotify auth failed")
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
