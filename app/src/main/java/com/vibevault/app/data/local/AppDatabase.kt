package com.vibevault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vibevault.app.data.local.dao.*
import com.vibevault.app.data.local.entity.*

/**
 * AppDatabase — Room database definition.
 * Version 11: Added cached_playlists, cached_playlist_tracks, and search_cache tables via explicit MIGRATION_10_11.
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
        LogEntity::class,
        CachedPlaylistEntity::class,
        CachedPlaylistTrackEntity::class,
        SearchCacheEntity::class
    ],
    version = 11,
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
    abstract fun cachedPlaylistDao(): CachedPlaylistDao
    abstract fun searchCacheDao(): SearchCacheDao

    companion object {
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_playlists` (
                        `playlistId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `ownerName` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `trackCount` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_playlist_tracks` (
                        `playlistId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumImageUrl` TEXT NOT NULL,
                        `audioUrl` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        PRIMARY KEY(`playlistId`, `sortOrder`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_playlist_tracks_playlistId` ON `cached_playlist_tracks` (`playlistId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_playlist_tracks_trackId` ON `cached_playlist_tracks` (`trackId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
