package com.vibevault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.data.sync.RealtimeListener
import com.vibevault.app.data.sync.SyncScheduler
import com.vibevault.app.domain.repository.AuthRepository
import com.vibevault.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val realtimeListener: RealtimeListener,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                realtimeListener.startListening()
                syncScheduler.schedulePeriodicSync()
                _startDestination.value = Screen.Home.route
            } else {
                _startDestination.value = Screen.Login.route
            }
        }
    }
}
