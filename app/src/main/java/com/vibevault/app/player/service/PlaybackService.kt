package com.vibevault.app.player.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.player.media.StreamResolver
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import android.util.Log
import com.vibevault.app.extensions.toMediaItem
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var streamResolver: StreamResolver

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var autoMediaBrowserTree: AutoMediaBrowserTree

    @Inject
    lateinit var queueManager: com.vibevault.app.player.QueueManager

    @Inject
    lateinit var sessionManager: com.vibevault.app.core.session.SessionManager

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    lateinit var sleepTimer: com.vibevault.app.player.SleepTimer
        private set

    companion object {
        var instance: PlaybackService? = null
            private set
    }

    private fun isAutoController(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName.lowercase()
        return pkg.contains("gearhead") || 
               pkg.contains("car") || 
               pkg.contains("projection") ||
               pkg.contains("android.auto")
    }

    private fun triggerAutoPlayOnAndroidAuto(reason: String) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (player.isPlaying) {
                    Log.d("PlaybackService", "Android Auto ($reason): Already playing.")
                    return@launch
                }

                if (player.mediaItemCount > 0) {
                    Log.d("PlaybackService", "Android Auto ($reason): Resuming existing queue.")
                    player.playWhenReady = true
                    player.play()
                    return@launch
                }

                val lastTrack = sessionManager.lastPlayedTrack
                    ?: musicRepository.getRecentlyPlayed(1).firstOrNull()?.firstOrNull()

                if (lastTrack != null && lastTrack.id.isNotBlank()) {
                    Log.d("PlaybackService", "Android Auto ($reason): Auto-playing last track ${lastTrack.title}")
                    val pos = sessionManager.lastPlayedPositionMs.coerceAtLeast(0L)
                    val mediaItem = lastTrack.toMediaItem()
                    
                    player.setMediaItem(mediaItem, pos)
                    player.prepare()
                    player.playWhenReady = true
                    player.play()

                    queueManager.syncExternalTrack(lastTrack)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Android Auto ($reason): Failed to auto-play last track", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.vibevault.app.utils.cipher.CipherDeobfuscator.initialize(this)
        com.vibevault.app.utils.BotDetectionMitigator.initialize(this)
        com.music.innertube.pages.YouTubeExtractor.cacheDir = cacheDir
        Log.d("PlaybackService", "onCreate called — initializing fresh ExoPlayer and MediaLibrarySession")

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackService", "[ExoPlayerError] code=${error.errorCode} (${error.errorCodeName}): ${error.message}", error)
                handlePlaybackError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d("PlaybackService", "[PlaybackState] state=$stateName, playWhenReady=${player.playWhenReady}, item=${player.currentMediaItem?.mediaId}")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PlaybackService", "[IsPlaying] isPlaying=$isPlaying")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
                    player.mediaItemCount - player.currentMediaItemIndex <= 5
                ) {
                    queueManager.fetchNextPage()
                }
            }
        })

        sleepTimer = com.vibevault.app.player.SleepTimer(CoroutineScope(Dispatchers.Main), player)
        player.addListener(sleepTimer)
        
        val callback = object : MediaLibrarySession.Callback {

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (isAutoController(controller)) {
                    triggerAutoPlayOnAndroidAuto("PlaybackResumption")
                }
                return serviceScope.future {
                    val lastTrack = sessionManager.lastPlayedTrack 
                        ?: musicRepository.getRecentlyPlayed(1).firstOrNull()?.firstOrNull()
                    
                    if (lastTrack != null && lastTrack.id.isNotBlank()) {
                        val mediaItem = lastTrack.toMediaItem()
                        val pos = sessionManager.lastPlayedPositionMs.coerceAtLeast(0L)
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(mediaItem),
                            0,
                            pos
                        )
                    } else {
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                    }
                }
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                if (isAutoController(controller)) {
                    triggerAutoPlayOnAndroidAuto("onConnect")
                }
                return super.onConnect(session, controller)
            }

            // 1. Root Request - Return Root MediaItem with isBrowsable = true
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                if (isAutoController(browser)) {
                    triggerAutoPlayOnAndroidAuto("onGetLibraryRoot")
                }

                val rootItem = MediaItem.Builder()
                    .setMediaId(AutoMediaBrowserTree.ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("VibeVault")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(
                    LibraryResult.ofItem(rootItem, params.withContentStyleHints())
                )
            }

            // 2. Children Request - Return browsable folders or playable track items
            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    try {
                        val items = autoMediaBrowserTree.getChildren(parentId)
                        val paginated = items.paginate(page, pageSize)
                        LibraryResult.ofItemList(ImmutableList.copyOf(paginated), params.withContentStyleHints())
                    } catch (e: Exception) {
                        LibraryResult.ofItemList(ImmutableList.of(), params.withContentStyleHints())
                    }
                }
            }

            // 3. Individual Item Request - Handle DHU requests for single items
            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future {
                    try {
                        val item = autoMediaBrowserTree.resolvePlayableItem(mediaId, MediaItem.Builder().setMediaId(mediaId).build())
                        val cleanId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
                        val title = item.mediaMetadata.title?.toString() ?: ""
                        val artist = item.mediaMetadata.artist?.toString() ?: ""
                        
                        val uri = item.requestMetadata.mediaUri
                            ?: item.localConfiguration?.uri
                            ?: android.net.Uri.Builder()
                                .scheme("vibevault")
                                .authority("stream")
                                .appendQueryParameter("id", cleanId)
                                .appendQueryParameter("title", title)
                                .appendQueryParameter("artist", artist)
                                .build()

                        val playableItem = item.buildUpon()
                            .setUri(uri)
                            .build()

                        LibraryResult.ofItem(playableItem, null)
                    } catch (e: Exception) {
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                return serviceScope.future {
                    val results = autoMediaBrowserTree.search(query)
                    session.notifySearchResultChanged(browser, query, results.size, params)
                    LibraryResult.ofVoid()
                }
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    val results = autoMediaBrowserTree.search(query)
                    val paginated = results.paginate(page, pageSize)
                    LibraryResult.ofItemList(ImmutableList.copyOf(paginated), params.withContentStyleHints())
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaItemsWithStartPosition> {
                return serviceScope.future {
                    val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
                    val firstItem = mediaItems.firstOrNull() ?: return@future defaultResult
                    val rawId = firstItem.mediaId
                    val path = rawId.split("/")
                    val category = path.firstOrNull() ?: ""
                    val targetTrackId = path.lastOrNull() ?: rawId

                    val categoryItems = autoMediaBrowserTree.getChildren(category)
                    
                    val playableItems = if (categoryItems.isNotEmpty() && categoryItems.any { it.mediaId.endsWith(targetTrackId) }) {
                        categoryItems.filter { it.mediaMetadata.isPlayable == true }
                    } else {
                        val singleItem = autoMediaBrowserTree.resolvePlayableItem(rawId, firstItem)
                        listOf(singleItem)
                    }

                    val targetIndex = playableItems.indexOfFirst { it.mediaId.endsWith(targetTrackId) || it.mediaId == targetTrackId }
                        .coerceAtLeast(0)

                    val resolvedList = playableItems.map { item ->
                        val cleanId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
                        val title = item.mediaMetadata.title?.toString() ?: ""
                        val artist = item.mediaMetadata.artist?.toString() ?: ""
                        val isLiked = item.mediaMetadata.extras?.getBoolean("isLiked") ?: false
                        
                        if (title.isNotBlank() && artist.isNotBlank()) {
                            streamResolver.preResolve(title, artist)
                        }

                        val streamUri = item.requestMetadata.mediaUri
                            ?: item.localConfiguration?.uri
                            ?: android.net.Uri.Builder()
                                .scheme("vibevault")
                                .authority("stream")
                                .appendQueryParameter("id", cleanId)
                                .appendQueryParameter("title", title)
                                .appendQueryParameter("artist", artist)
                                .build()

                        item.buildUpon()
                            .setUri(streamUri)
                            .setMediaMetadata(
                                item.mediaMetadata.buildUpon()
                                    .setExtras(Bundle().apply {
                                        putBoolean("isLiked", isLiked)
                                    })
                                    .build()
                            )
                            .build()
                    }
                    MediaItemsWithStartPosition(resolvedList, targetIndex, startPositionMs)
                }
            }

            // 4. Add MediaItems Request - Convert browser MediaItems into fully populated MediaItems with URIs for ExoPlayer
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                return serviceScope.future {
                    mediaItems.map { item ->
                        val rawId = item.mediaId
                        val cleanId = rawId.split("/").lastOrNull() ?: rawId
                        
                        if (rawId.startsWith("http://") || rawId.startsWith("https://")) {
                            return@map item
                        }
                        
                        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Track $cleanId"
                        val artist = item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Artist"
                        val album = item.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                        val artUri = item.mediaMetadata.artworkUri
                        val isLiked = item.mediaMetadata.extras?.getBoolean("isLiked") ?: false
                        
                        if (title.isNotBlank() && artist != "Unknown Artist") {
                            streamResolver.preResolve(title, artist)
                        }
                        
                        val uri = item.requestMetadata.mediaUri
                            ?: item.localConfiguration?.uri
                            ?: android.net.Uri.Builder()
                                .scheme("vibevault")
                                .authority("stream")
                                .appendQueryParameter("id", cleanId)
                                .appendQueryParameter("title", title)
                                .appendQueryParameter("artist", artist)
                                .build()
                            
                        item.buildUpon()
                            .setUri(uri)
                            .setMediaMetadata(
                                item.mediaMetadata.buildUpon()
                                    .setTitle(title)
                                    .setSubtitle(artist)
                                    .setArtist(artist)
                                    .setAlbumTitle(album)
                                    .setAlbumArtist(artist)
                                    .setDisplayTitle(title)
                                    .setArtworkUri(artUri)
                                    .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
                                        putBoolean("isLiked", isLiked)
                                        artUri?.toString()?.let { putString("artwork_uri", it) }
                                    })
                                    .build()
                            )
                            .build()
                    }.toMutableList()
                }
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                when (playerCommand) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        serviceScope.launch(Dispatchers.Main) {
                            queueManager.next()
                        }
                        return 0
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        serviceScope.launch(Dispatchers.Main) {
                            queueManager.previous()
                        }
                        return 0
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customAction: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return when (customAction.customAction) {
                    "com.vibevault.LIKE" -> {
                        val trackId = args.getString("trackId")
                        if (trackId == null) {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        } else {
                            serviceScope.future {
                                musicRepository.toggleLike(trackId)
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            }
                        }
                    }
                    else -> super.onCustomCommand(session, controller, customAction, args)
                }
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val p = mediaLibrarySession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (::sleepTimer.isInitialized) {
            player.removeListener(sleepTimer)
            sleepTimer.clear()
        }
        if (instance == this) {
            instance = null
        }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private fun LibraryParams?.withContentStyleHints(): LibraryParams {
        val extras = Bundle(this?.extras ?: Bundle()).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }

        return LibraryParams.Builder()
            .setOffline(this?.isOffline ?: false)
            .setRecent(this?.isRecent ?: false)
            .setSuggested(this?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }

    private val retryCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException) {
        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId.ifBlank { return }

        val retries = retryCounts.getOrDefault(mediaId, 0)
        if (retries >= 3) {
            Log.w("PlaybackService", "Max retries (3) reached for $mediaId — letting queue proceed.")
            retryCounts.remove(mediaId)
            return
        }

        retryCounts[mediaId] = retries + 1
        Log.d("PlaybackService", "Attempting playback error recovery for $mediaId (retry ${retries + 1}/3)...")

        serviceScope.launch(Dispatchers.Main) {
            val currentPos = player.currentPosition.coerceAtLeast(0L)
            
            streamResolver.invalidateCacheForTrack(mediaId)
            com.vibevault.app.data.youtube.YTPlayerUtils.forceRefreshForVideo(mediaId)

            player.seekTo(currentPos)
            player.prepare()
            player.play()
        }
    }
}