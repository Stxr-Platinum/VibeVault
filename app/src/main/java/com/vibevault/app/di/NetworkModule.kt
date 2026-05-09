package com.vibevault.app.di

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
import javax.inject.Singleton

/**
 * NetworkModule — Configures the Supabase client with all required plugins.
 *
 * Plugins installed:
 *   - Auth: Handles sign-in, sign-up, OAuth, and session management.
 *   - Postgrest: Typed REST queries against the Supabase database.
 *   - Realtime: WebSocket subscriptions for live data sync.
 *
 * The Ktor Android engine is resolved automatically by supabase-kt
 * from the ktor-client-android dependency on the classpath.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            // Deep link scheme for OAuth callback
            scheme = "vibevault"
            host = "auth-callback"
        }
        install(Postgrest)
        install(Realtime)
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
}
