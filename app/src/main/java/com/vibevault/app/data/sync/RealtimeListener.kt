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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * RealtimeListener — Sole owner of the Supabase Realtime websocket.
 *
 * Design rules:
 *  1. ONLY this class subscribes to Supabase Realtime channels.
 *  2. ONLY ProcessLifecycleOwner controls start/stop — no ViewModel.
 *  3. We NEVER call realtime.disconnect() — the SDK manages the underlying
 *     socket. We only unsubscribe/removeChannel for our channel.
 *  4. A Mutex prevents start/stop races from lifecycle transitions.
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

    /** Prevents start/stop from overlapping during rapid lifecycle transitions. */
    private val lifecycleMutex = Mutex()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d(TAG, "ProcessLifecycle → onStart. userId=${sessionManager.userId}")
                if (sessionManager.userId != null) {
                    scope.launch { startListeningSafe() }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                Log.d(TAG, "ProcessLifecycle → onStop")
                scope.launch { stopListeningSafe() }
            }
        })
    }

    // ── Public API (for manual calls from auth flows) ──────────────

    fun startListening() {
        scope.launch { startListeningSafe() }
    }

    fun stopListening() {
        scope.launch { stopListeningSafe() }
    }

    suspend fun stopListeningAndJoin() {
        stopListeningSafe()
    }

    // ── Mutex-guarded entry points ─────────────────────────────────

    private suspend fun startListeningSafe() = lifecycleMutex.withLock {
        startListeningInternal()
    }

    private suspend fun stopListeningSafe() = lifecycleMutex.withLock {
        teardownInternal()
    }

    // ── Core implementation ────────────────────────────────────────

    private suspend fun startListeningInternal() {
        val userId = sessionManager.userId
        if (userId == null) {
            Log.w(TAG, "startListeningInternal: No userId, skipping.")
            return
        }

        // Already running? Don't duplicate.
        if (syncJob?.isActive == true) {
            Log.d(TAG, "startListeningInternal: syncJob already active, skipping.")
            return
        }

        Log.i(TAG, "startListeningInternal: Launching sync for user=$userId")

        syncJob = scope.launch {
            while (isActive) {
                try {
                    // ── 1. Clean up any stale channel ────────────────
                    cleanupChannelQuietly()
                    ensureActive()

                    // ── 2. Create a fresh channel ────────────────────
                    val channel: RealtimeChannel
                    try {
                        channel = realtime.channel(CHANNEL_SYNC)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // SDK NPE/ISE can happen if internal state was invalidated
                        Log.w(TAG, "Channel creation failed (${e.javaClass.simpleName}). Retrying in 5s...", e)
                        delay(5000)
                        continue
                    }
                    syncChannel = channel

                    // ── 3. Register postgres change flows ────────────
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "playlists"
                        filter("user_id", FilterOperator.EQ, userId)
                    }.onEach { handlePlaylistChange(it) }
                     .catch { e -> if (e !is CancellationException) Log.e(TAG, "Playlists sync error", e) else throw e }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "playlist_tracks"
                    }.onEach { handlePlaylistTrackChange(it) }
                     .catch { e -> if (e !is CancellationException) Log.e(TAG, "PlaylistTracks sync error", e) else throw e }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "profiles"
                        filter("id", FilterOperator.EQ, userId)
                    }.onEach { handleProfileChange(it) }
                     .catch { e -> if (e !is CancellationException) Log.e(TAG, "Profiles sync error", e) else throw e }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "liked_songs"
                        filter("user_id", FilterOperator.EQ, userId)
                    }.onEach { handleLikeChange(it) }
                     .catch { e -> if (e !is CancellationException) Log.e(TAG, "Likes sync error", e) else throw e }
                     .retryWhen { cause, _ -> if (cause is CancellationException) throw cause; delay(5000); true }
                     .launchIn(this)

                    ensureActive()

                    // ── 4. Connect + Subscribe ───────────────────────
                    Log.d(TAG, "Realtime status = ${realtime.status.value}")
                    if (realtime.status.value == Realtime.Status.DISCONNECTED) {
                        Log.d(TAG, "Connecting Realtime engine...")
                        realtime.connect()
                    }

                    ensureActive()

                    Log.i(TAG, "Subscribing to channel '$CHANNEL_SYNC'...")
                    channel.subscribe(blockUntilSubscribed = true)
                    Log.i(TAG, "✅ Channel subscribed! Realtime is LIVE.")

                    // ── 5. Hold connection until cancelled ───────────
                    awaitCancellation()

                } catch (e: CancellationException) {
                    Log.d(TAG, "Sync coroutine cancelled. Clean shutdown.")
                    throw e
                } catch (e: java.net.SocketException) {
                    Log.w(TAG, "Network drop. Reconnecting in 5s...", e)
                    cleanupChannelQuietly()
                    delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime error. Reconnecting in 5s...", e)
                    cleanupChannelQuietly()
                    delay(5000)
                }
            }
        }
    }

    /**
     * Tears down the channel only — does NOT call realtime.disconnect().
     * The SDK manages its own socket lifecycle; forcibly disconnecting it
     * causes CancellationException in its internal coroutines, which triggers
     * the 7-second reconnect loop.
     */
    private suspend fun teardownInternal() {
        val channel = syncChannel
        val job = syncJob

        if (job == null && channel == null) return

        Log.d(TAG, "teardownInternal: removing channel...")

        syncChannel = null
        syncJob = null

        // 1. Gracefully unsubscribe + remove the channel
        try {
            channel?.unsubscribe()
            channel?.let { realtime.removeChannel(it) }
            Log.d(TAG, "Channel removed successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Channel cleanup error (non-fatal)", e)
        }

        // 2. NOW cancel the sync coroutine — after the channel is gone
        job?.cancel()
        Log.d(TAG, "syncJob cancelled.")
    }

    private suspend fun cleanupChannelQuietly() {
        try {
            syncChannel?.unsubscribe()
            syncChannel?.let { realtime.removeChannel(it) }
        } catch (ignored: Exception) {}
        syncChannel = null
    }

    // ── Change handlers ────────────────────────────────────────────

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
