package com.vibevault.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(isSignUp = !it.isSignUp, error = null) }
    }

    fun onError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = if (state.isSignUp) {
                authRepository.signUpWithEmail(state.email, state.password)
            } else {
                authRepository.signInWithEmail(state.email, state.password)
            }

            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Authentication failed") }
                }
            )
        }
    }

    fun onGoogleIdTokenReceived(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithGoogleIdToken(idToken).fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Google sign-in failed") }
                }
            )
        }
    }
}
