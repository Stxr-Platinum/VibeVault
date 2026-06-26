package com.vibevault.app.di

import android.util.Log
import com.vibevault.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * NetworkModule — Configures the Supabase client with all required plugins.
 *
 * Plugins installed:
 *   - Auth: Handles sign-in, sign-up, OAuth, and session management.
 *   - Postgrest: Typed REST queries against the Supabase database.
 *   - Realtime: WebSocket subscriptions for live data sync.
 *
 * The Ktor OkHttp engine is used because it supports WebSockets,
 * which is required by Supabase Realtime.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        Log.d("SpotifyDebug", "NetworkModule: Providing SupabaseClient...")
        Log.d("SpotifyDebug", "NetworkModule: URL = ${BuildConfig.SUPABASE_URL.take(15)}...")
        Log.d("SpotifyDebug", "NetworkModule: Key Prefix = ${BuildConfig.SUPABASE_ANON_KEY.take(10)}...")
        
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            requestTimeout = 30.seconds
            
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
            httpEngine = OkHttp.create {
                config {
                    pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                    connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        install(Auth) {
            // Deep link scheme for OAuth callback
            scheme = "vibevault"
            host = "auth-callback"
        }
        install(Postgrest)
        install(Realtime) {
            // Prevent fatal socket aborts from taking down the app
            disconnectOnSessionLoss = false
            // Keep the WebSocket alive at the application layer
            heartbeatInterval = 15.seconds
        }
        install(Functions)
    }
    }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideRealtime(client: SupabaseClient): Realtime = client.realtime

    @Provides
    @Singleton
    fun provideFunctions(client: SupabaseClient): Functions = client.functions

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
