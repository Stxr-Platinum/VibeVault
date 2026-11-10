package com.vibevault.app

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.OptIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Application entry point — Hilt component root.
 */
@HiltAndroidApp
class VibeVaultApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
            
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // Pre-warm preferences cache and local listening stats cache for instant Replay loading
        com.vibevault.app.utils.ViviPrefCache.start(this)
        com.vibevault.app.data.stats.ListeningStats.init(this)

        // Initialize appContext for cipher deobfuscators, N-transform solvers, and bot mitigators
        com.vibevault.app.utils.cipher.CipherDeobfuscator.initialize(this)
        com.vibevault.app.utils.BotDetectionMitigator.initialize(this)
        com.music.innertube.pages.YouTubeExtractor.cacheDir = cacheDir
        
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

            // Preload and cache search screen suggestions so navigation to search is instantaneous
            try {
                com.vibevault.app.ui.screens.search.suggestions.SuggestionsPreloader.preload(this@VibeVaultApp)
            } catch (e: Exception) {
                android.util.Log.e("VibeVaultApp", "Failed to preload suggestions: ${e.message}")
            }
        }
    }
}
