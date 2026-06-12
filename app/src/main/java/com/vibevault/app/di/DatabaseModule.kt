package com.vibevault.app.di

import android.content.Context
import androidx.room.Room
import com.vibevault.app.data.local.AppDatabase
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule — Room database and DAO providers.
 *
 * The database is the single source of truth for the UI layer.
 * All remote data is fetched → mapped → cached into Room, and
 * the UI observes Room via Flow.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "vibevault_db"
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideLikedSongDao(db: AppDatabase): com.vibevault.app.data.local.dao.LikedSongDao = db.likedSongDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()



    @Provides
    fun provideDeviceDao(db: AppDatabase): com.vibevault.app.data.local.dao.DeviceDao = db.deviceDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): com.vibevault.app.data.local.dao.HistoryDao = db.historyDao()

    @Provides
    fun provideLogDao(db: AppDatabase): com.vibevault.app.data.local.dao.LogDao = db.logDao()
}
