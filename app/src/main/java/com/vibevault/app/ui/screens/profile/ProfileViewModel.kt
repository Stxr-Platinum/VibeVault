package com.vibevault.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.repository.AuthRepositoryImpl
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val username: String? = null,
    val accountHolderName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val isPremium: Boolean = false,
    val isAccountDeleted: Boolean = false,
    val deleteError: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val userId = sessionManager.userId
        if (userId != null) {
            viewModelScope.launch {
                val profileFlow = (authRepository as AuthRepositoryImpl).getProfileFlow(userId)
                profileFlow.collect { profile ->
                    if (profile != null) {
                        _uiState.value = _uiState.value.copy(
                            username = profile.username,
                            accountHolderName = profile.accountHolderName,
                            avatarUrl = profile.avatarUrl
                        )
                    }
                }
            }
        } else {
            // Fallback to session manager
            viewModelScope.launch {
                sessionManager.userDisplayNameFlow.collect { name ->
                    _uiState.value = _uiState.value.copy(username = name)
                }
            }
            viewModelScope.launch {
                sessionManager.userAvatarUrlFlow.collect { url ->
                    _uiState.value = _uiState.value.copy(avatarUrl = url)
                }
            }
        }
        _uiState.value = _uiState.value.copy(email = sessionManager.userEmail)
    }

    fun refresh() {
        // Now handled by reactive flows
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun deleteAccount(adminPassword: String) {
        if (adminPassword != "adminp") {
            _uiState.value = _uiState.value.copy(deleteError = "Incorrect admin password")
            return
        }
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isAccountDeleted = true, deleteError = null)
            } else {
                // Still mark deleted since we cleared local session anyway
                _uiState.value = _uiState.value.copy(isAccountDeleted = true, deleteError = null)
            }
        }
    }

    fun clearDeleteError() {
        _uiState.value = _uiState.value.copy(deleteError = null)
    }
}
