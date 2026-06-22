package com.vibevault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.sync.RealtimeListener
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
    private val realtimeListener: RealtimeListener,
    private val syncScheduler: SyncScheduler,
    private val sessionManager: com.vibevault.app.core.session.SessionManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.isLoggedInFlow,
                sessionManager.isSpotifyConnected
            ) { loggedIn, spotifyConnected ->
                Log.d("VibeVault", "Session Change: LoggedIn=$loggedIn, Spotify=$spotifyConnected")
                when {
                    !loggedIn -> Screen.Login.route
                    else -> Screen.Home.route
                }
            }.collect { route ->
                Log.d("VibeVault", "Setting startDestination to $route")
                _startDestination.value = route
                
                if (route == Screen.Home.route) {
                    realtimeListener.startListening()
                    syncScheduler.schedulePeriodicSync()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Explicitly tear down the Realtime connection before viewModelScope is cancelled.
        // stopListening() uses a detached scope so the SDK can send phx_leave cleanly.
        realtimeListener.stopListening()
    }
}
