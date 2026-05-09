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
        .build()

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        loadControl: LoadControl
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setLoadControl(loadControl)
        .setHandleAudioBecomingNoisy(true)   // Pause on headphone disconnect
        .build()

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): MediaSession = MediaSession.Builder(context, player)
        .build()
}
