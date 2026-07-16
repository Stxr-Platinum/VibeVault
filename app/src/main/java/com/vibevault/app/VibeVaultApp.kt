package com.vibevault.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import androidx.work.Configuration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application entry point — Hilt component root.
 */
@HiltAndroidApp
class VibeVaultApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        
        // Generate a fresh session every time the app opens to preemptively avoid bot detection
        GlobalScope.launch(Dispatchers.IO) {
            try {
                com.music.innertube.YouTube.refreshVisitorData().onSuccess { newData ->
                    com.music.innertube.YouTube.visitorData = newData
                    android.util.Log.i("VibeVaultApp", "Generated fresh session on startup")
                }
            } catch (e: Exception) {
                android.util.Log.e("VibeVaultApp", "Failed to generate fresh session: ${e.message}")
            }
        }
    }
}
