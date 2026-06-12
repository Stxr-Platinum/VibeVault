package com.vibevault.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.local.dao.LogDao
import com.vibevault.app.data.remote.dto.LikeDto
import com.vibevault.app.data.remote.dto.PlaylistDto
import com.vibevault.app.data.remote.dto.PlaylistTrackDto
import com.vibevault.app.core.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import java.time.Instant

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val logDao: LogDao,
    private val postgrest: Postgrest,
    private val storage: Storage,
    private val sessionManager: SessionManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "vv_sync_worker"
    }

    override suspend fun doWork(): Result {
        val userId = sessionManager.userId ?: return Result.success()

        return try {
            syncLikes(userId)
            syncPlaylists(userId)
            syncPlaylistTracks()
            syncLogs(userId)
            Log.i(TAG, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    private suspend fun syncLikes(userId: String) {
        val unsyncedLikes = likedSongDao.getUnsyncedLikes()
        if (unsyncedLikes.isEmpty()) return

        Log.d(TAG, "Syncing ${unsyncedLikes.size} liked song mutations")

        for (song in unsyncedLikes) {
            postgrest.from("liked_songs").upsert(
                listOf(
                    LikeDto(
                        userId = userId,
                        trackId = song.id,
                        songTitle = song.title,
                        artist = song.artist,
                        album = song.album,
                        coverUrl = song.albumImageUrl,
                        durationMs = song.durationMs.toInt(),
                        isDeleted = song.isDeleted,
                        clientTimestamp = Instant.ofEpochMilli(song.clientTimestamp).toString()
                    )
                )
            ) {
                onConflict = "user_id, track_id"
            }
        }

        likedSongDao.markSynced(unsyncedLikes.map { it.id })
    }

    private suspend fun syncPlaylists(userId: String) {
        val unsyncedPlaylists = playlistDao.getUnsyncedPlaylists()
        if (unsyncedPlaylists.isEmpty()) return

        for (playlist in unsyncedPlaylists) {
            var finalCoverUrl = playlist.coverUrl
            
            // Upload to Supabase Storage if it's a local file
            if (finalCoverUrl != null && (finalCoverUrl.startsWith("/") || finalCoverUrl.startsWith("file://"))) {
                try {
                    val cleanPath = finalCoverUrl.removePrefix("file://")
                    val file = java.io.File(cleanPath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val ext = file.extension.ifBlank { "jpg" }
                        val fileName = "${userId}_${playlist.id}_${System.currentTimeMillis()}.$ext"
                        
                        val bucket = storage.from("covers")
                        bucket.upload(fileName, bytes) {
                            upsert = true
                        }
                        finalCoverUrl = bucket.publicUrl(fileName)
                        
                        // Update local DB so we don't try to upload it again next time
                        playlistDao.updatePlaylist(playlist.copy(coverUrl = finalCoverUrl))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload playlist cover", e)
                }
            }

            postgrest.from("playlists").upsert(
                PlaylistDto(
                    id = playlist.id,
                    userId = userId,
                    title = playlist.title,
                    description = playlist.description,
                    coverUrl = finalCoverUrl,
                    trackCount = playlist.trackCount,
                    durationMs = playlist.durationMs,
                    isPublic = playlist.isPublic,
                    isDeleted = playlist.isDeleted,
                    clientTimestamp = Instant.ofEpochMilli(playlist.clientTimestamp).toString()
                )
            )
        }
        playlistDao.markSynced(unsyncedPlaylists.map { it.id })
    }

    private suspend fun syncPlaylistTracks() {
        val unsyncedTracks = playlistDao.getUnsyncedPlaylistTracks()
        if (unsyncedTracks.isEmpty()) return

        for (ref in unsyncedTracks) {
            postgrest.from("playlist_tracks").upsert(
                listOf(
                    PlaylistTrackDto(
                        playlistId = ref.playlistId,
                        trackId = ref.trackId,
                        sortOrder = ref.sortOrder,
                        songTitle = ref.title,
                        artist = ref.artist,
                        album = ref.album,
                        coverUrl = ref.albumImageUrl,
                        durationMs = ref.durationMs.toInt(),
                        isDeleted = ref.isDeleted,
                        clientTimestamp = Instant.ofEpochMilli(ref.clientTimestamp).toString()
                    )
                )
            ) {
                onConflict = "playlist_id, track_id"
            }
            playlistDao.markTrackSynced(ref.playlistId, ref.trackId)
        }
    }

    private suspend fun syncLogs(userId: String) {
        val unsyncedLogs = logDao.getUnsyncedLogs()
        if (unsyncedLogs.isEmpty()) return

        Log.d(TAG, "Syncing ${unsyncedLogs.size} logs to remote")

        for (log in unsyncedLogs) {
            try {
                postgrest.from("logs").insert(
                    mapOf(
                        "user_id" to userId,
                        "action" to log.tag,
                        "event_type" to "debug",
                        "severity" to log.level.lowercase(),
                        "metadata" to mapOf("message" to log.message),
                        "client_timestamp" to java.time.Instant.ofEpochMilli(log.timestamp).toString()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync individual log", e)
            }
        }
        logDao.markSynced(unsyncedLogs.map { it.id })
    }
}
