package com.vibevault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel — Drives the Home Dashboard with reactive track streams.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val allTracks: StateFlow<List<Track>> = musicRepository.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentTracks: StateFlow<List<Track>> = musicRepository.getRecentlyPlayed(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Initializes data by syncing with Supabase.
     * Fallbacks to mock data only if sync fails and local DB is empty.
     */
    fun initializeData() {
        viewModelScope.launch {
            try {
                // Always try to sync real data from Supabase first
                musicRepository.syncFromRemote()
            } catch (e: Exception) {
                // If sync fails (e.g. offline) and we have nothing, seed mock data
                if (allTracks.value.isEmpty()) {
                    musicRepository.seedMockData()
                }
            }
        }
    }
}
