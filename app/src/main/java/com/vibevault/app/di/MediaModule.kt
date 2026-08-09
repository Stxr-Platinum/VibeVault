package com.vibevault.app.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.session.MediaSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * MediaModule — ExoPlayer, AudioAttributes, LoadControl, and MediaSession.
 *
 * ExoPlayer is configured for streaming with:
 *   - Optimized buffer sizes (2s min, 30s max) for low-latency start.
 *   - USAGE_MEDIA + CONTENT_TYPE_MUSIC audio attributes.
 *   - Audio focus handling delegated to ExoPlayer's built-in manager.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideLoadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15000, // minBufferMs
            10 * 60 * 1000, // maxBufferMs (10 mins, enough to pre-fetch next 2 songs)
            150, // bufferForPlaybackMs (Ultra-fast initial playback start <200ms!)
            1000  // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideCache(
        @ApplicationContext context: Context
    ): androidx.media3.datasource.cache.Cache {
        val cacheDir = java.io.File(context.cacheDir, "media_cache")
        // 500 MB cache size
        val cacheEvictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024)
        val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
        return androidx.media3.datasource.cache.SimpleCache(cacheDir, cacheEvictor, databaseProvider)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMediaSourceFactory(
        @ApplicationContext context: Context,
        cache: androidx.media3.datasource.cache.Cache,
        streamResolver: com.vibevault.app.player.media.StreamResolver
    ): androidx.media3.exoplayer.source.MediaSource.Factory {
        val upstreamFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            
        val resolvingFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            upstreamFactory,
            streamResolver
        )

        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
    }

    @OptIn(UnstableApi::class)
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        loadControl: LoadControl,
        mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(mediaSourceFactory)
        .setHandleAudioBecomingNoisy(true)   // Pause on headphone disconnect
        .build()

}
