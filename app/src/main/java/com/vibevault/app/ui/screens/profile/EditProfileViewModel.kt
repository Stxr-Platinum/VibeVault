package com.vibevault.app.ui.screens.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class EditProfileUiState(
    val username: String = "",
    val avatarUrl: String = "",
    val isUsernameLoading: Boolean = false,
    val isAvatarLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val application: Application,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { 
            it.copy(
                username = sessionManager.userDisplayName ?: "",
                avatarUrl = sessionManager.userAvatarUrl ?: ""
            )
        }
    }

    fun onUsernameChange(name: String) {
        _uiState.update { it.copy(username = name, error = null) }
    }

    /**
     * Called when the user picks an image from the photo picker.
     * Copies the image from the temporary content:// URI to the app's
     * internal storage so it persists across app restarts.
     */
    fun onImagePicked(contentUri: Uri) {
        viewModelScope.launch {
            val permanentPath = copyImageToInternalStorage(contentUri)
            if (permanentPath != null) {
                _uiState.update { it.copy(avatarUrl = permanentPath, error = null) }
            } else {
                _uiState.update { it.copy(error = "Failed to load selected image") }
            }
        }
    }

    private suspend fun copyImageToInternalStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = application.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            
            val avatarDir = File(application.filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            
            // Use a fixed filename so we overwrite the old avatar
            val avatarFile = File(avatarDir, "user_avatar.jpg")
            avatarFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            avatarFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun saveUsername() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Username cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUsernameLoading = true, error = null) }
            
            val result = authRepository.updateUsernameOnly(
                username = state.username
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isUsernameLoading = false, isSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isUsernameLoading = false, error = e.message ?: "Failed to update username") }
                }
            )
        }
    }

    fun saveAvatar() {
        val state = _uiState.value
        if (state.avatarUrl.isBlank()) {
            _uiState.update { it.copy(error = "Please select an image first") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAvatarLoading = true, error = null) }
            
            val result = authRepository.updateAvatarOnly(
                avatarUrl = state.avatarUrl
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isAvatarLoading = false, isSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isAvatarLoading = false, error = e.message ?: "Failed to update avatar") }
                }
            )
        }
    }
}
