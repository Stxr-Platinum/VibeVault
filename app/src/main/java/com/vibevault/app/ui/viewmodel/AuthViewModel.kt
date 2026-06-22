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
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val functions: Functions,
    private val sessionManager: SessionManager,
    private val realtimeSyncManager: com.vibevault.app.data.sync.RealtimeSyncManager
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
        Log.d("SpotifyAuth", "LOGIN STARTED (MOCKED)")
    }

    fun handleSpotifyCallback(code: String) {
        Log.d("SpotifyAuth", "AUTH CODE RECEIVED (MOCKED)")
    }


    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object SpotifySuccess : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
