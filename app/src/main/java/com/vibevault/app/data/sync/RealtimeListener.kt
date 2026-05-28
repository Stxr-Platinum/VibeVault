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
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * RealtimeListener — Enhanced bidirectional sync listener.
 * Handles instant updates from Supabase and propagates them to Room.
 *
 * Teardown strategy:
 * The supabase-kt SDK runs internal coroutines for the WebSocket. If the parent
 * CoroutineScope is cancelled first, the SDK catches the CancellationException
 * internally and enters a 7-second reconnect loop. To avoid this, [stopListening]
 * tears down the channel and disconnects the Realtime engine in a fully detached
 * CoroutineScope BEFORE cancelling [syncJob]. This ensures the SDK's phx_leave
 * handshake completes while the parent scope is still active.
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
        private const val CHANNEL_SYNC = "sync-v1"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var syncChannel: RealtimeChannel? = null
    private var syncJob: Job? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (sessionManager.userId != null) {
                    startListening()
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                stopListening()
            }
        })
    }

    /**
     * Start listening to core tables for realtime updates.
     */
    fun startListening() {
        val userId = sessionManager.userId ?: return
        
        stopListening() // Ensure clean state

        syncJob = scope.launch {
            while (isActive) {
                try {
                    // Explicitly cleanup old channel so we don't attach listeners to an already-joined channel
                    try {
                        syncChannel?.unsubscribe()
                        syncChannel?.let { realtime.removeChannel(it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ignored: Exception) {}

                    // Check cancellation before creating a new channel
                    ensureActive()

                    // Channel creation can NPE/ISE if the SDK's internal state was
                    // invalidated after a clean disconnect. Catch and reset.
                    val channel: RealtimeChannel
                    try {
                        channel = realtime.channel(CHANNEL_SYNC)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Covers NullPointerException & IllegalStateException from
                        // RealtimeImpl.channel() when internal maps are invalidated
                        if (e is NullPointerException || e is IllegalStateException) {
                            Log.w(TAG, "Realtime plugin state invalidated (${e.javaClass.simpleName}). Resetting...", e)
                            try { realtime.disconnect() } catch (ignored: Exception) {}
                            delay(3000)
                            continue // Retry from the top of the while-loop
                        }
                        throw e // Re-throw anything else to the outer catch
                    }
                    syncChannel = channel

                    // 1. Listen to Playlists
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "playlists"
                        filter("user_id", FilterOperator.EQ, userId)
                    }.onEach { handlePlaylistChange(it) }
                     .catch { Log.e(TAG, "Playlists sync error", it) }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    // 2. Listen to Playlist Tracks
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "playlist_tracks"
                    }.onEach { handlePlaylistTrackChange(it) }
                     .catch { Log.e(TAG, "PlaylistTracks sync error", it) }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    // 3. Listen to Profiles
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "profiles"
                        filter("id", FilterOperator.EQ, userId)
                    }.onEach { handleProfileChange(it) }
                     .catch { Log.e(TAG, "Profiles sync error", it) }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    // 4. Listen to Liked Songs
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "liked_songs"
                        filter("user_id", FilterOperator.EQ, userId)
                    }.onEach { handleLikeChange(it) }
                     .catch { Log.e(TAG, "Likes sync error", it) }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    // Check cancellation before initiating network connection
                    ensureActive()

                    // Ensure underlying realtime connection is active before subscribing
                    if (realtime.status.value == Realtime.Status.DISCONNECTED) {
                        realtime.connect()
                    }

                    // Check cancellation between connect and subscribe
                    ensureActive()

                    Log.i(TAG, "Realtime engine started for user: $userId")
                    // Block until subscribed and hold the connection. If the socket dies, it throws.
                    channel.subscribe(blockUntilSubscribed = true)
                    
                    // Suspend indefinitely until cancelled or socket aborts
                    awaitCancellation()

                } catch (e: CancellationException) {
                    // stopListening() already performed a clean teardown in a detached scope
                    // BEFORE cancelling this job, so the SDK has already sent phx_leave.
                    // Just log and rethrow — do NOT attempt cleanup here (scope is cancelled).
                    Log.d(TAG, "Realtime listener cancelled, shutting down cleanly.")
                    throw e
                } catch (e: java.net.SocketException) {
                    // Transient network drop — log as warning and reconnect gracefully
                    Log.w(TAG, "Network drop, reconnecting in 5s...", e)
                    cleanupChannelQuietly()
                    delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime Socket aborted or failed. Reconnecting in 5s...", e)
                    cleanupChannelQuietly()
                    delay(5000)
                }
            }
        }
    }

    /**
     * Cleanly tears down the Realtime connection BEFORE cancelling the coroutine job.
     *
     * This is the critical fix: the supabase-kt SDK's internal coroutines are children
     * of the Realtime engine's scope. If we cancel our job first, the SDK catches the
     * CancellationException and enters a 7s reconnect loop. By disconnecting in a
     * detached scope first, the SDK can send phx_leave and shut down gracefully.
     */
    fun stopListening() {
        val channel = syncChannel
        val job = syncJob

        // Nothing to stop
        if (job == null && channel == null) return

        Log.d(TAG, "stopListening: tearing down Realtime in detached scope...")

        // 1. Tear down in a fully detached scope — this scope is NOT a child of `scope`
        //    or `syncJob`, so it cannot be cancelled by their lifecycle.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                channel?.unsubscribe()
                channel?.let { realtime.removeChannel(it) }
                realtime.disconnect()
                Log.d(TAG, "Realtime teardown complete.")
            } catch (e: Exception) {
                Log.w(TAG, "Realtime teardown encountered error (non-fatal)", e)
            }
        }

        // 2. NOW cancel the sync job — the SDK has already been told to disconnect
        job?.cancel()
        syncJob = null
        syncChannel = null
    }

    /**
     * Suspending variant of [stopListening] that waits for the teardown to complete.
     * Use from ViewModel.onCleared() or other lifecycle-aware components.
     */
    suspend fun stopListeningAndJoin() {
        val channel = syncChannel
        val job = syncJob

        if (job == null && channel == null) return

        Log.d(TAG, "stopListeningAndJoin: tearing down Realtime and waiting...")

        val teardownJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                channel?.unsubscribe()
                channel?.let { realtime.removeChannel(it) }
                realtime.disconnect()
                Log.d(TAG, "Realtime teardown complete.")
            } catch (e: Exception) {
                Log.w(TAG, "Realtime teardown encountered error (non-fatal)", e)
            }
        }

        // Wait for the phx_leave to actually transmit before proceeding
        teardownJob.join()

        job?.cancel()
        syncJob = null
        syncChannel = null
    }

    /**
     * Best-effort cleanup of the current channel during error recovery.
     * Called from within the active (non-cancelled) reconnect loop, so the scope
     * is still alive and we can call unsubscribe() normally.
     */
    private suspend fun cleanupChannelQuietly() {
        try {
            syncChannel?.unsubscribe()
            syncChannel?.let { realtime.removeChannel(it) }
        } catch (ignored: Exception) {}
        syncChannel = null
    }

    private suspend fun handlePlaylistChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = json.decodeFromJsonElement<PlaylistDto>(action.record)
                playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity())
            }
            is PostgresAction.Update -> {
                val dto = json.decodeFromJsonElement<PlaylistDto>(action.record)
                if (dto.isDeleted) {
                    playlistDao.deletePlaylist(dto.id)
                } else {
                    playlistDao.insertPlaylistFromRemote(dto.toPlaylistEntity())
                }
            }
            is PostgresAction.Delete -> {
                val id = action.oldRecord["id"]?.toString()?.trim('"')
                if (id != null) playlistDao.deletePlaylist(id)
            }
            else -> {}
        }
    }

    private suspend fun handlePlaylistTrackChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = json.decodeFromJsonElement<PlaylistTrackDto>(action.record)
                playlistDao.addTrackToPlaylistFromRemote(dto.toCrossRef())
            }
            is PostgresAction.Update -> {
                val dto = json.decodeFromJsonElement<PlaylistTrackDto>(action.record)
                if (dto.isDeleted) {
                    playlistDao.removeTrackFromPlaylist(dto.playlistId, dto.trackId)
                } else {
                    playlistDao.addTrackToPlaylistFromRemote(dto.toCrossRef())
                }
            }
            is PostgresAction.Delete -> {
                val pId = action.oldRecord["playlist_id"]?.toString()?.trim('"')
                val tId = action.oldRecord["track_id"]?.toString()?.trim('"')
                if (pId != null && tId != null) playlistDao.removeTrackFromPlaylist(pId, tId)
            }
            else -> {}
        }
    }

    private suspend fun handleProfileChange(action: PostgresAction) {
        if (action is PostgresAction.Update) {
            val dto = json.decodeFromJsonElement<ProfileDto>(action.record)
            profileDao.insertProfile(dto.mapToProfileEntity())
            
            // Sync local session with remote profile change
            sessionManager.updateProfileMetadata(
                displayName = dto.username ?: dto.accountHolderName,
                avatarUrl = dto.avatarUrl
            )
        }
    }

    private suspend fun handleLikeChange(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val dto = json.decodeFromJsonElement<LikeDto>(action.record)
                likedSongDao.insertLikedSong(dto.toLikedSongEntity())
            }
            is PostgresAction.Update -> {
                val dto = json.decodeFromJsonElement<LikeDto>(action.record)
                if (dto.isDeleted) {
                    likedSongDao.deleteLikedSong(dto.trackId)
                } else {
                    likedSongDao.insertLikedSong(dto.toLikedSongEntity())
                }
            }
            is PostgresAction.Delete -> {
                val tId = action.oldRecord["track_id"]?.toString()?.trim('"')
                if (tId != null) likedSongDao.deleteLikedSong(tId)
            }
            else -> {}
        }
    }
}
