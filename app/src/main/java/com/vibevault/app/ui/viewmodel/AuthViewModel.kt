package com.vibevault.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.repository.AuthRepository
import com.music.spotify.SpotifyAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import io.github.jan.supabase.functions.Functions
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val functions: Functions,
    private val sessionManager: SessionManager,
    private val realtimeSyncManager: com.vibevault.app.data.sync.RealtimeSyncManager,
    private val musicRepository: com.vibevault.app.domain.repository.MusicRepository,
    private val spotifyApi: com.vibevault.app.data.remote.api.SpotifyApiService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _authEvents = Channel<AuthEvent>(Channel.BUFFERED)
    val authEvents = _authEvents.receiveAsFlow()

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogleIdToken(idToken)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Success
                },
                onFailure = { error ->
                    val message = error.message ?: "Google login failed"
                    _authState.value = AuthState.Error(message)
                    _authEvents.send(AuthEvent.Error(message))
                }
            )
        }
    }

    fun connectWithCookies(spDc: String, spKey: String = "") {
        if (spDc.isBlank()) {
            val message = "sp_dc cookie cannot be empty"
            _authState.value = AuthState.Error(message)
            _authEvents.trySend(AuthEvent.Error(message))
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

                        // Identify the newly linked Spotify account and overwrite cache with its playlists
                        val accountId = spotifyApi.getUserProfile().getOrNull()?.id
                        sessionManager.setActiveSpotifyAccountId(accountId)

                        // Trigger sync immediately for this newly linked Spotify account
                        try {
                            musicRepository.onSpotifyAccountLinked(accountId)
                        } catch (e: Exception) {
                            Log.e("SpotifyAuth", "Background sync failed after cookie auth", e)
                        }

                        _authState.value = AuthState.Idle
                        _authEvents.send(AuthEvent.SpotifyConnected)
                    },
                    onFailure = { error ->
                        Log.e("SpotifyAuth", "Failed to connect to Spotify with cookies", error)
                        val message = error.message ?: "Failed to connect to Spotify"
                        _authState.value = AuthState.Error(message)
                        _authEvents.send(AuthEvent.Error(message))
                    }
                )
            } catch (e: Exception) {
                Log.e("SpotifyAuth", "Exception during cookie connect", e)
                val message = e.message ?: "Failed to connect to Spotify"
                _authState.value = AuthState.Error(message)
                _authEvents.send(AuthEvent.Error(message))
            }
        }
    }

    sealed interface AuthEvent {
        data object SpotifyConnected : AuthEvent
        data class Error(val message: String) : AuthEvent
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object SpotifySuccess : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
