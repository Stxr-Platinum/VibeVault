package com.vibevault.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import androidx.work.Configuration

/**
 * Application entry point — Hilt component root.
 */
@HiltAndroidApp
class VibeVaultApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
