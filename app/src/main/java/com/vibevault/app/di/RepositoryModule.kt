package com.vibevault.app.di

import com.vibevault.app.data.repository.AuthRepositoryImpl
import com.vibevault.app.data.repository.MusicRepositoryImpl
import com.vibevault.app.domain.repository.AuthRepository
import com.vibevault.app.domain.repository.MusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * RepositoryModule — Binds repository interfaces to their implementations.
 *
 * This ensures the Domain layer depends only on abstractions,
 * while Hilt resolves the concrete Data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository
}
