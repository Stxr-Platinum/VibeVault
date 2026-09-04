package com.vibevault.app.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoMediaBrowserTree @Inject constructor(
    private val musicRepository: MusicRepository
) {
    companion object {
        const val ROOT_ID = "vibevault_root"
        const val HOME_ID = "home"
        const val RECENTS_ID = "recents"
        const val BROWSE_ID = "browse"
        const val LIBRARY_ID = "library"

        const val PLAYLISTS_ID = "playlists"
        const val LIKED_SONGS_ID = "liked_songs"

        private const val PLAYLIST_PREFIX = "playlist_"
    }

    /**
     * Get children for a specified parentId.
     */
    suspend fun getChildren(parentId: String): List<MediaItem> {
        return try {
            when (parentId) {
                ROOT_ID, "root", "" -> getRootCategories()
                HOME_ID -> getHomeItems()
                RECENTS_ID -> getRecentlyPlayedItems()
                BROWSE_ID -> getBrowseItems()
                LIBRARY_ID -> getLibraryCategories()
                PLAYLISTS_ID -> getPlaylistItems()
                LIKED_SONGS_ID -> getLikedSongItems()
                else -> {
                    if (parentId.startsWith(PLAYLIST_PREFIX)) {
                        val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                        getPlaylistTrackItems(playlistId)
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for tracks by query for Android Auto voice/search.
     */
    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return try {
            val results = musicRepository.searchOnline(query).getOrElse {
                musicRepository.searchTracks(query).firstOrNull() ?: emptyList()
            }
            results.map { it.toMediaItem(path = "search/$query") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Resolve a single playable MediaItem when a direct mediaId is requested.
     */
    suspend fun resolvePlayableItem(rawId: String, fallbackItem: MediaItem): MediaItem {
        val cleanId = rawId.split("/").lastOrNull() ?: rawId
        val title = fallbackItem.mediaMetadata.title?.toString() ?: ""
        val artist = fallbackItem.mediaMetadata.artist?.toString() ?: ""

        if (title.isNotBlank() && artist.isNotBlank()) {
            return Track(
                id = cleanId,
                title = title,
                artist = artist,
                album = fallbackItem.mediaMetadata.albumTitle?.toString() ?: "VibeVault",
                albumImageUrl = fallbackItem.mediaMetadata.artworkUri?.toString() ?: "",
                durationMs = fallbackItem.mediaMetadata.durationMs ?: 0L
            ).toMediaItem(path = "track")
        }

        return try {
            val tracks = musicRepository.searchOnline(cleanId).getOrElse {
                musicRepository.searchTracks(cleanId).firstOrNull() ?: emptyList()
            }
            val matchedTrack = tracks.firstOrNull { it.id == cleanId } ?: tracks.firstOrNull()
            matchedTrack?.toMediaItem(path = "track") ?: fallbackItem
        } catch (e: Exception) {
            fallbackItem
        }
    }

    private fun getRootCategories(): List<MediaItem> {
        return listOf(
            createCategoryItem(
                id = HOME_ID,
                title = "Home",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true
            ),
            createCategoryItem(
                id = RECENTS_ID,
                title = "Recents",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true
            ),
            createCategoryItem(
                id = BROWSE_ID,
                title = "Browse",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true
            ),
            createCategoryItem(
                id = LIBRARY_ID,
                title = "Library",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                isGrid = true
            )
        )
    }

    private suspend fun getHomeItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        // 1. Recently Played Songs from user listening history
        val recentlyPlayed = musicRepository.getRecentlyPlayed(8).firstOrNull() ?: emptyList()
        recentlyPlayed.forEach { track ->
            if (track.title.isNotBlank() && track.id.isNotBlank()) {
                items.add(track.toMediaItem(path = HOME_ID, isGrid = true))
            }
        }

        // 2. Personalized User & Spotify Playlists (Filtered for non-blank title and ID)
        val userPlaylists = musicRepository.getUserSpotifyPlaylists().firstOrNull() ?: emptyList()
        val localPlaylists = musicRepository.getPlaylists().firstOrNull() ?: emptyList()

        localPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { entity ->
            items.add(
                createCategoryItem(
                    id = "$PLAYLIST_PREFIX${entity.id}",
                    title = entity.title,
                    subtitle = "Playlist",
                    artworkUrl = entity.coverUrl,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true
                )
            )
        }

        userPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { playlist ->
            items.add(
                createCategoryItem(
                    id = "$PLAYLIST_PREFIX${playlist.id}",
                    title = playlist.title,
                    subtitle = playlist.ownerName ?: "Playlist",
                    artworkUrl = playlist.coverUrl,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true
                )
            )
        }

        // 3. Recommended / Discovery Tracks based on listening history
        val discoveryTracks = musicRepository.getDiscoveryTracks().firstOrNull() ?: emptyList()
        discoveryTracks.take(10).forEach { track ->
            if (track.title.isNotBlank() && track.id.isNotBlank()) {
                items.add(track.toMediaItem(path = HOME_ID, isGrid = true))
            }
        }

        return items
    }

    private suspend fun getRecentlyPlayedItems(): List<MediaItem> {
        val tracks = musicRepository.getRecentlyPlayed(30).firstOrNull() ?: emptyList()
        val spotifyTracks = if (tracks.isEmpty()) {
            musicRepository.getSpotifyRecentlyPlayed().firstOrNull() ?: emptyList()
        } else emptyList()

        val resultTracks = when {
            tracks.isNotEmpty() -> tracks
            spotifyTracks.isNotEmpty() -> spotifyTracks
            else -> musicRepository.getDiscoveryTracks().firstOrNull() ?: emptyList()
        }

        return resultTracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
            .map { it.toMediaItem(path = RECENTS_ID, isGrid = true) }
    }

    private suspend fun getBrowseItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        // 1. Catalog of Available Playlists
        val featuredPlaylists = musicRepository.getFeaturedPlaylists().firstOrNull() ?: emptyList()
        featuredPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { playlist ->
            items.add(
                createCategoryItem(
                    id = "$PLAYLIST_PREFIX${playlist.id}",
                    title = playlist.title,
                    subtitle = playlist.description ?: playlist.ownerName ?: "Featured Catalog",
                    artworkUrl = playlist.coverUrl,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true
                )
            )
        }

        // 2. Global Top Charts & New Releases Tracks
        val topCharts = musicRepository.getGlobalTop50().firstOrNull() ?: emptyList()
        val newReleases = musicRepository.getNewReleases().firstOrNull() ?: emptyList()
        val catalogTracks = if (topCharts.isNotEmpty()) topCharts else newReleases

        catalogTracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
            .mapTo(items) { it.toMediaItem(path = BROWSE_ID, isGrid = true) }

        return items
    }

    private suspend fun getLibraryCategories(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        items.add(
            createCategoryItem(
                id = LIKED_SONGS_ID,
                title = "Liked Songs",
                subtitle = "Favorite Tracks",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                isGrid = true,
                childrenIsGrid = false
            )
        )
        items.addAll(getPlaylistItems())
        return items
    }

    private suspend fun getPlaylistItems(): List<MediaItem> {
        val localPlaylists = musicRepository.getPlaylists().firstOrNull() ?: emptyList()
        val spotifyPlaylists = musicRepository.getUserSpotifyPlaylists().firstOrNull() ?: emptyList()

        val items = mutableListOf<MediaItem>()

        localPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { entity ->
            items.add(
                createCategoryItem(
                    id = "$PLAYLIST_PREFIX${entity.id}",
                    title = entity.title,
                    subtitle = "VibeVault",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true,
                    childrenIsGrid = false
                )
            )
        }

        spotifyPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { playlist ->
            items.add(
                createCategoryItem(
                    id = "$PLAYLIST_PREFIX${playlist.id}",
                    title = playlist.title,
                    subtitle = playlist.ownerName ?: "VibeVault",
                    artworkUrl = playlist.coverUrl,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true,
                    childrenIsGrid = false
                )
            )
        }

        if (items.isEmpty()) {
            val featured = musicRepository.getFeaturedPlaylists().firstOrNull() ?: emptyList()
            featured.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { playlist ->
                items.add(
                    createCategoryItem(
                        id = "$PLAYLIST_PREFIX${playlist.id}",
                        title = playlist.title,
                        subtitle = playlist.ownerName ?: "Featured",
                        artworkUrl = playlist.coverUrl,
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                        isGrid = true,
                        childrenIsGrid = false
                    )
                )
            }
        }

        return items
    }

    private suspend fun getPlaylistTrackItems(playlistId: String): List<MediaItem> {
        val onlineTracks = try {
            musicRepository.getSpotifyPlaylistTracks(playlistId)
        } catch (e: Exception) {
            emptyList()
        }
        val localTracks = if (onlineTracks.isEmpty()) {
            musicRepository.getPlaylistTracks(playlistId).firstOrNull() ?: emptyList()
        } else emptyList()

        val tracks = when {
            onlineTracks.isNotEmpty() -> onlineTracks
            localTracks.isNotEmpty() -> localTracks
            else -> musicRepository.getDiscoveryTracks().firstOrNull() ?: emptyList()
        }
        return tracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
            .map { it.toMediaItem(path = "$PLAYLIST_PREFIX$playlistId", isGrid = false) }
    }

    private suspend fun getLikedSongItems(): List<MediaItem> {
        val tracks = musicRepository.getLikedTracks().firstOrNull() ?: emptyList()
        val resultTracks = if (tracks.isNotEmpty()) tracks else {
            musicRepository.getDiscoveryTracks().firstOrNull() ?: emptyList()
        }
        return resultTracks.map { it.toMediaItem(path = LIKED_SONGS_ID, isGrid = false) }
    }

    private fun createCategoryItem(
        id: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
        artworkUrl: String? = null,
        isGrid: Boolean = true,
        childrenIsGrid: Boolean = isGrid
    ): MediaItem {
        val style = if (isGrid) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        val childrenStyle = if (childrenIsGrid) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, childrenStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, childrenStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, style)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}

/**
 * Extension function to convert a domain Track to a Media3 MediaItem with Android Auto metadata.
 */
fun Track.toMediaItem(path: String = "track", isGrid: Boolean = true): MediaItem {
    val cleanId = id.split("/").lastOrNull() ?: id
    val durationSec = if (durationMs > 0) durationMs / 1000L else 0L
    val durationParam = if (durationSec > 0) "&duration=$durationSec" else ""
    val streamUri = audioUrl ?: "vibevault://stream?id=$cleanId&title=${Uri.encode(title)}&artist=${Uri.encode(artist)}$durationParam"
    val style = if (isGrid) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    val extras = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, style)
        putBoolean("isLiked", isLiked)
        if (durationMs > 0) putLong("durationMs", durationMs)
    }
    return MediaItem.Builder()
        .setMediaId("$path/$cleanId")
        .setUri(streamUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(artist)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(if (albumImageUrl.isNotBlank()) Uri.parse(albumImageUrl) else null)
                .setDurationMs(if (durationMs > 0) durationMs else 0L)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)
                .build()
        )
        .build()
}

fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize < 1 || isEmpty()) return emptyList()
    if (pageSize == Int.MAX_VALUE) return this
    val fromIndex = page.toLong() * pageSize
    if (fromIndex >= size) return emptyList()
    return subList(fromIndex.toInt(), minOf(fromIndex.toInt() + pageSize, size))
}