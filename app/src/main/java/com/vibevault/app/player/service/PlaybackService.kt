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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
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

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    lateinit var sleepTimer: com.vibevault.app.player.SleepTimer
        private set

    companion object {
        var instance: PlaybackService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        sleepTimer = com.vibevault.app.player.SleepTimer(CoroutineScope(Dispatchers.Main), player)
        player.addListener(sleepTimer)
        
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                    reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    val currentIdx = player.currentMediaItemIndex
                    if (currentIdx >= 0) {
                        queueManager.onMediaItemTransition(currentIdx)
                    }
                }
            }
        })
        
        val callback = object : MediaLibrarySession.Callback {

            // 1. Root Request - Return Root MediaItem with isBrowsable = true
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
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
                        
                        val title = item.mediaMetadata.title?.toString() ?: ""
                        val artist = item.mediaMetadata.artist?.toString() ?: ""
                        val isLiked = item.mediaMetadata.extras?.getBoolean("isLiked") ?: false
                        
                        if (title.isNotBlank() && artist.isNotBlank()) {
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
                                    .setExtras(Bundle().apply {
                                        putBoolean("isLiked", isLiked)
                                    })
                                    .build()
                            )
                            .build()
                    }.toMutableList()
                }
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
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
        }

        return LibraryParams.Builder()
            .setOffline(this?.isOffline ?: false)
            .setRecent(this?.isRecent ?: false)
            .setSuggested(this?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }
}