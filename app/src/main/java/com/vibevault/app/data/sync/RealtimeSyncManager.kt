package com.vibevault.app.data.sync

import android.util.Log
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.remote.dto.LikeDto
import com.vibevault.app.data.remote.dto.PlaylistDto
import com.vibevault.app.data.remote.dto.PlaylistTrackDto
import com.vibevault.app.data.remote.dto.ListeningHistoryDto
import com.vibevault.app.data.local.dao.HistoryDao
import com.vibevault.app.data.local.entity.HistoryEntity
import com.vibevault.app.data.mapper.toLikedSongEntity
import com.vibevault.app.data.mapper.toPlaylistEntity
import com.vibevault.app.data.mapper.toCrossRef
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.CoroutineScope



import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RealtimeSyncManager — Handles bidirectional "Instant reflection".
 * Listens for remote changes in Supabase and updates local Room DB.
 */
@Singleton
class RealtimeSyncManager @Inject constructor(
    private val realtime: Realtime,
    private val sessionManager: SessionManager,
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var syncJob: Job? = null

    fun startSync() {
        val userId = sessionManager.userId ?: return
        if (syncJob?.isActive == true) return

        Log.i("RealtimeSync", "Starting real-time sync for user: $userId")
        
        syncJob = scope.launch {
            val channel = realtime.channel("db-changes")

            // 1. Listen for Liked Songs
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "liked_songs"
                filter(FilterOperation("user_id", FilterOperator.EQ, userId))
            }.onEach { action ->
                handleLikeAction(action)
            }.launchIn(this)

            // 2. Listen for Playlists
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "playlists"
                filter(FilterOperation("user_id", FilterOperator.EQ, userId))
            }.onEach { action ->
                handlePlaylistAction(action)
            }.launchIn(this)

            // 3. Listen for Playlist Tracks
            // Note: This filter is harder because it depends on playlist_id. 
            // For now, we listen to all and filter in handle if necessary, 
            // but ideally we'd join or use a specific channel.
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "playlist_tracks"
            }.onEach { action ->
                handlePlaylistTrackAction(action)
            }.launchIn(this)
            
            // 4. Listen for Recently Played History
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "listening_history"
                filter(FilterOperation("user_id", FilterOperator.EQ, userId))
            }.onEach { action ->
                handleHistoryAction(action)
            }.launchIn(this)

            channel.subscribe()
        }
    }

    fun stopSync() {
        Log.i("RealtimeSync", "Stopping real-time sync")
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun handleLikeAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = Json.decodeFromJsonElement<LikeDto>(action.record)
                likedSongDao.insertLikedSong(dto.toLikedSongEntity())
                Log.d("RealtimeSync", "Remote Liked Song Inserted: ${dto.trackId}")
            }
            is PostgresAction.Update -> {
                val dto = Json.decodeFromJsonElement<LikeDto>(action.record)
                likedSongDao.insertLikedSong(dto.toLikedSongEntity())
                Log.d("RealtimeSync", "Remote Liked Song Updated: ${dto.trackId}")
            }
            is PostgresAction.Delete -> {
                // Delete is harder because we only get the old record (usually just ID)
                // In our schema, we use soft-delete (is_deleted), so it's mostly handled via Update.
                // If a hard delete happens:
                val id = action.oldRecord["id"]?.toString()
                if (id != null) {
                    // Note: id here is the Long primary key
                }
            }
            else -> Unit
        }
    }

    private suspend fun handlePlaylistAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = Json.decodeFromJsonElement<PlaylistDto>(action.record)
                playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity())
            }
            is PostgresAction.Update -> {
                val dto = Json.decodeFromJsonElement<PlaylistDto>(action.record)
                playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity())
            }
            is PostgresAction.Delete -> {
                val id = action.oldRecord["id"]?.toString()
                if (id != null) playlistDao.deletePlaylist(id)
            }
            else -> Unit
        }
    }

    private suspend fun handlePlaylistTrackAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = Json.decodeFromJsonElement<PlaylistTrackDto>(action.record)
                playlistDao.addTrackToPlaylistFromRemote(dto.toCrossRef())
            }
            is PostgresAction.Update -> {
                val dto = Json.decodeFromJsonElement<PlaylistTrackDto>(action.record)
                playlistDao.addTrackToPlaylistFromRemote(dto.toCrossRef())
            }
            else -> Unit
        }
    }

    private suspend fun handleHistoryAction(action: PostgresAction) {
        if (action is PostgresAction.Insert) {
            try {
                val dto = Json.decodeFromJsonElement<ListeningHistoryDto>(action.record)
                val entity = HistoryEntity(
                    trackId = dto.song_id,
                    title = dto.song_title,
                    artist = dto.artist ?: "Unknown",
                    albumImageUrl = dto.cover_url ?: ""
                )
                historyDao.insertHistory(entity)
                Log.d("RealtimeSync", "Remote History Inserted: ${dto.song_title}")
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Failed to decode remote history action", e)
            }
        }
    }
}
