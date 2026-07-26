package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.repository.AuthRepository
import com.music.spotify.SpotifyAuth
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

    fun connectWithCookies(spDc: String, spKey: String = "") {
        if (spDc.isBlank()) {
            _authState.value = AuthState.Error("sp_dc cookie cannot be empty")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                val tokenResult = SpotifyAuth.fetchAccessToken(spDc.trim(), spKey.trim())
                tokenResult.fold(
                    onSuccess = { internalToken ->
                        sessionManager.saveSpotifyCookieSession(
                            spDc = spDc.trim(),
                            spKey = spKey.trim(),
                            accessToken = internalToken.accessToken,
                            expiresAtMs = internalToken.accessTokenExpirationTimestampMs
                        )

                        // Trigger sync immediately
                        try {
                            musicRepository.backgroundSyncSpotifyPlaylists()
                        } catch (e: Exception) {
                            Log.e("SpotifyAuth", "Background sync failed after cookie auth", e)
                        }

                        _authState.value = AuthState.SpotifySuccess
                    },
                    onFailure = { error ->
                        Log.e("SpotifyAuth", "Failed to connect to Spotify with cookies", error)
                        _authState.value = AuthState.Error(error.message ?: "Failed to connect to Spotify")
                    }
                )
            } catch (e: Exception) {
                Log.e("SpotifyAuth", "Exception during cookie connect", e)
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
