package com.vibevault.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.remote.dto.LikeDto
import com.vibevault.app.core.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val postgrest: Postgrest,
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
            // Since LikedSongEntity ONLY exists if it is liked,
            // we upsert to Supabase.
            // Note: In this version, unliking deletes the local record immediately.
            // A more robust way would be a 'isDeleted' flag, but for now we'll upsert what's there.
            postgrest.from("liked_songs").upsert(
                LikeDto(userId = userId, trackId = song.id)
            )
        }

        likedSongDao.markSynced(unsyncedLikes.map { it.id })
    }

    private suspend fun syncPlaylists(userId: String) {
        val unsyncedPlaylists = playlistDao.getUnsyncedPlaylists()
        if (unsyncedPlaylists.isEmpty()) return

        for (playlist in unsyncedPlaylists) {
            postgrest.from("playlists").upsert(
                mapOf(
                    "id" to playlist.id,
                    "user_id" to userId,
                    "title" to playlist.title,
                    "description" to (playlist.description ?: ""),
                    "cover_url" to (playlist.coverUrl ?: ""),
                    "track_count" to playlist.trackCount,
                    "updated_at" to java.time.Instant.now().toString()
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
                mapOf(
                    "playlist_id" to ref.playlistId,
                    "track_id" to ref.trackId,
                    "sort_order" to ref.sortOrder,
                    "added_at" to java.time.Instant.ofEpochMilli(ref.addedAt).toString()
                )
            )
            playlistDao.markTrackSynced(ref.playlistId, ref.trackId)
        }
    }
}
