package com.vibevault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.local.dao.PfpDao
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.dao.ProfileDao
import com.vibevault.app.data.local.entity.LikedSongEntity
import com.vibevault.app.data.local.entity.PfpEntity
import com.vibevault.app.data.local.entity.PlaylistEntity
import com.vibevault.app.data.local.entity.PlaylistTrackCrossRef
import com.vibevault.app.data.local.entity.ProfileEntity

/**
 * AppDatabase — Room database definition.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ProfileEntity::class,
        PfpEntity::class,
        LikedSongEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun profileDao(): ProfileDao
    abstract fun pfpDao(): PfpDao
}
