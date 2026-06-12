package com.vibevault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.sync.SyncScheduler
import com.vibevault.app.domain.repository.AuthRepository
import com.vibevault.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            // Silently refresh Spotify token on cold start if the user has already
            // connected before. This prevents showing the "Connect Spotify" screen
            // every time the app launches just because the 1-hour access token expired.
            if (sessionManager.isLoggedIn && sessionManager.isSpotifyExpired && sessionManager.spotifyRefreshToken != null) {
                Log.d("VibeVault", "Spotify token expired on startup — refreshing silently...")
                val newToken = sessionManager.refreshSpotifyToken()
                Log.d("VibeVault", "Silent Spotify refresh result: ${if (newToken != null) "SUCCESS" else "FAILED"}")
            }

            // Also try to restore the Supabase session if we have a refresh token
            if (sessionManager.isLoggedIn && sessionManager.isSessionExpired && sessionManager.refreshToken != null) {
                Log.d("VibeVault", "Supabase session expired on startup — refreshing silently...")
                try {
                    authRepository.isLoggedIn() // This internally calls auth.refreshSession()
                } catch (e: Exception) {
                    Log.e("VibeVault", "Supabase session refresh failed", e)
                }
            }

            combine(
                sessionManager.isLoggedInFlow,
                sessionManager.isSpotifyConnected
            ) { loggedIn, spotifyConnected ->
                Log.d("VibeVault", "Session Change: LoggedIn=$loggedIn, Spotify=$spotifyConnected")
                when {
                    !loggedIn -> Screen.Login.route
                    !spotifyConnected -> Screen.SpotifyLogin.route
                    else -> Screen.Home.route
                }
            }.collect { route ->
                Log.d("VibeVault", "Setting startDestination to $route")
                _startDestination.value = route
                
                if (route == Screen.Home.route) {
                    // RealtimeListener manages its own lifecycle via ProcessLifecycleOwner.
                    // Only schedule periodic REST sync here.
                    syncScheduler.schedulePeriodicSync()
                }
            }
        }
    }

    // No onCleared() needed — RealtimeListener lifecycle is managed by ProcessLifecycleOwner,
    // not by this ViewModel. Calling stopListening() here caused premature disconnects on
    // configuration changes and navigation events.
}
