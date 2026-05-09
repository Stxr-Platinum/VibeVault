package com.vibevault.app.data.sync

import android.util.Log
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.data.local.dao.LikedSongDao
import com.vibevault.app.data.local.dao.PlaylistDao
import com.vibevault.app.data.local.dao.ProfileDao
import com.vibevault.app.data.mapper.*
import com.vibevault.app.data.local.entity.*
import com.vibevault.app.data.remote.dto.*
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RealtimeListener — Subscribes to Supabase Realtime channels.
 *
 * Listens to INSERT, UPDATE, DELETE events on core tables (playlists, likes, profiles)
 * and immediately reflects changes in the local Room cache.
 */
@Singleton
class RealtimeListener @Inject constructor(
    private val realtime: Realtime,
    private val likedSongDao: LikedSongDao,
    private val playlistDao: PlaylistDao,
    private val profileDao: ProfileDao,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "RealtimeListener"
        private const val CHANNEL_SYNC = "sync-realtime"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Start listening to core tables for realtime updates.
     */
    fun startListening() {
        val userId = sessionManager.userId ?: return
        
        scope.launch {
            try {
                val channel = realtime.channel(CHANNEL_SYNC)

                // 1. Listen to Playlists (only for current user)
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "playlists"
                    filter("user_id", FilterOperator.EQ, userId)
                }.onEach { handlePlaylistChange(it) }
                 .catch { Log.e(TAG, "Playlists flow error", it) }
                 .launchIn(scope)

                // 2. Listen to Playlist Tracks
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "playlist_tracks"
                }.onEach { handlePlaylistTrackChange(it) }
                 .catch { Log.e(TAG, "PlaylistTracks flow error", it) }
                 .launchIn(scope)

                // 3. Listen to Profile (only for current user)
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "profiles"
                    filter("id", FilterOperator.EQ, userId)
                }.onEach { handleProfileChange(it) }
                 .catch { Log.e(TAG, "Profiles flow error", it) }
                 .launchIn(scope)

                // 4. Listen to Likes (only for current user)
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "liked_songs"
                    filter("user_id", FilterOperator.EQ, userId)
                }.onEach { handleLikeChange(it) }
                 .catch { Log.e(TAG, "Likes flow error", it) }
                 .launchIn(scope)

                channel.subscribe()
                Log.i(TAG, "Realtime subscription active for all core data")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to realtime", e)
            }
        }
    }

    private suspend fun handlePlaylistChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> {
                val record = if (action is PostgresAction.Insert) action.record else (action as PostgresAction.Update).record
                try {
                    val dto = json.decodeFromString<PlaylistDto>(record.toString())
                    playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity().copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync playlist", e)
                }
            }
            is PostgresAction.Delete -> {
                val id = action.oldRecord["id"]?.toString()?.trim('"')
                if (id != null) {
                    playlistDao.deletePlaylist(id)
                }
            }
            else -> {}
        }
    }

    private suspend fun handlePlaylistTrackChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                try {
                    val dto = json.decodeFromString<PlaylistTrackDto>(action.record.toString())
                    playlistDao.addTrackToPlaylistFromRemote(dto.toCrossRef().copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync playlist track insert", e)
                }
            }
            is PostgresAction.Delete -> {
                val playlistId = action.oldRecord["playlist_id"]?.toString()?.trim('"')
                val trackId = action.oldRecord["track_id"]?.toString()?.trim('"')
                if (playlistId != null && trackId != null) {
                    playlistDao.removeTrackFromPlaylist(playlistId, trackId)
                }
            }
            else -> {}
        }
    }

    private suspend fun handleProfileChange(action: PostgresAction) {
        if (action is PostgresAction.Update) {
            try {
                val dto = json.decodeFromString<ProfileDto>(action.record.toString())
                
                // 1. Update Profile Table
                profileDao.insertProfile(dto.mapToProfileEntity())

                // 2. Update Session (Sync local session metadata with realtime changes)
                sessionManager.saveSession(
                    accessToken = sessionManager.accessToken ?: "",
                    refreshToken = sessionManager.refreshToken ?: "",
                    userId = dto.id,
                    email = sessionManager.userEmail ?: "",
                    displayName = dto.username ?: dto.accountHolderName ?: sessionManager.userDisplayName,
                    avatarUrl = dto.avatarUrl ?: sessionManager.userAvatarUrl,
                    expiresAtEpochMs = sessionManager.sessionExpiryMs
                )
                Log.d(TAG, "Profile updated via realtime: ${dto.username}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync profile update", e)
            }
        }
    }

    private suspend fun handleLikeChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                try {
                    val dto = json.decodeFromString<LikeDto>(action.record.toString())
                    likedSongDao.insertLikedSong(dto.toLikedSongEntity().copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync like insert", e)
                }
            }
            is PostgresAction.Delete -> {
                val trackId = action.oldRecord["track_id"]?.toString()?.trim('"')
                if (trackId != null) {
                    likedSongDao.deleteLikedSong(trackId)
                }
            }
            else -> {}
        }
    }
}
