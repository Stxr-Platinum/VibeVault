package com.vibevault.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val username: String = "",
    val avatarUrl: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { 
            it.copy(
                username = sessionManager.userDisplayName ?: "", // Use sessionManager's displayName as current username
                avatarUrl = sessionManager.userAvatarUrl ?: ""
            )
        }
    }

    fun onUsernameChange(name: String) {
        _uiState.update { it.copy(username = name, error = null) }
    }

    fun onAvatarUrlChange(url: String) {
        if (url.startsWith("content://")) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = Uri.parse(url)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (bitmap != null) {
                        val outputStream = ByteArrayOutputStream()
                        // Compress to a small JPEG to save DB space
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                        val byteArray = outputStream.toByteArray()
                        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                        val dataUrl = "data:image/jpeg;base64,$base64"
                        
                        _uiState.update { it.copy(avatarUrl = dataUrl, error = null) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            _uiState.update { it.copy(avatarUrl = url, error = null) }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Username cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = authRepository.updateProfile(
                username = state.username,
                avatarUrl = state.avatarUrl
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to update profile") }
                }
            )
        }
    }
}
