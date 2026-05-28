package com.vibevault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibevault.app.data.local.dao.*
import com.vibevault.app.data.local.entity.*

/**
 * AppDatabase — Room database definition.
 * Version 8: Resetting schema to ensure clean slate and fix launch crashes.
 * Using destructive migration to guarantee parity between Entities and DB.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ProfileEntity::class,
        PfpEntity::class,
        LikedSongEntity::class,
        DeviceEntity::class,
        HistoryEntity::class,
        LogEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun profileDao(): ProfileDao
    abstract fun pfpDao(): PfpDao
    abstract fun deviceDao(): DeviceDao
    abstract fun historyDao(): HistoryDao
    abstract fun logDao(): LogDao
}
