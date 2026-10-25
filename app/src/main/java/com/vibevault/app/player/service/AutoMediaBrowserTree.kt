package com.vibevault.app.player.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.vibevault.app.R
import com.vibevault.app.core.session.SessionManager
import com.vibevault.app.domain.model.Track
import com.vibevault.app.domain.repository.MusicRepository
import com.vibevault.app.ui.screens.explore.GenreCategoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoMediaBrowserTree @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context? = null,
    private val sessionManager: SessionManager? = null
) {
    companion object {
        const val ROOT_ID = "root"
        const val ROOT_ALIAS_ID = "vibevault_root"
        const val HOME_ID = "home"
        const val RECENTS_ID = "recents"
        const val BROWSE_ID = "browse"
        const val LIBRARY_ID = "library"

        const val PLAYLISTS_ID = "playlists"
        const val LIKED_SONGS_ID = "liked_songs"
        const val SECTION_MADE_FOR_YOU = "section_made_for_you"
        const val SECTION_RECENT = "section_recent"
        const val SECTION_PLAYLISTS = "section_playlists"

        const val EXPLORE_MOODS_ID = "explore_moods"
        const val EXPLORE_GENRES_ID = "explore_genres"
        const val EXPLORE_NEW_RELEASES_ID = "explore_new_releases"
        const val EXPLORE_CHARTS_ID = "explore_charts"

        const val PLAYLIST_PREFIX = "playlist_"
        const val ALBUM_PREFIX = "album_"
        const val EXPLORE_CATEGORY_PREFIX = "explore_cat_"
        const val DEFAULT_ARTWORK_URI = "android.resource://com.vibevault.app/drawable/ic_app_icon_hi_fi"

        const val TAB_HOME_URI = "android.resource://com.vibevault.app/drawable/ic_tab_home_png"
        const val TAB_LIBRARY_URI = "android.resource://com.vibevault.app/drawable/ic_tab_library_png"
        const val TAB_RECENTS_URI = "android.resource://com.vibevault.app/drawable/ic_tab_recents_png"
        const val TAB_SEARCH_URI = "android.resource://com.vibevault.app/drawable/ic_tab_search_png"

        val VALID_PARENT_IDS = setOf(
            ROOT_ID, ROOT_ALIAS_ID, "",
            HOME_ID,
            RECENTS_ID, SECTION_RECENT,
            BROWSE_ID,
            LIBRARY_ID,
            PLAYLISTS_ID, SECTION_PLAYLISTS,
            LIKED_SONGS_ID, SECTION_MADE_FOR_YOU,
            EXPLORE_MOODS_ID, EXPLORE_GENRES_ID,
            EXPLORE_NEW_RELEASES_ID, EXPLORE_CHARTS_ID
        )

        fun isValidParentId(parentId: String?): Boolean {
            if (parentId == null) return false
            if (parentId in VALID_PARENT_IDS) return true
            if (parentId.startsWith(PLAYLIST_PREFIX) && parentId.length > PLAYLIST_PREFIX.length) {
                val id = parentId.removePrefix(PLAYLIST_PREFIX).trim()
                return id.isNotEmpty()
            }
            if (parentId.startsWith(ALBUM_PREFIX) && parentId.length > ALBUM_PREFIX.length) {
                val id = parentId.removePrefix(ALBUM_PREFIX).trim()
                return id.isNotEmpty()
            }
            if (parentId.startsWith(EXPLORE_CATEGORY_PREFIX) && parentId.length > EXPLORE_CATEGORY_PREFIX.length) {
                val id = parentId.removePrefix(EXPLORE_CATEGORY_PREFIX).trim()
                return id.isNotEmpty()
            }
            return false
        }

        fun safeArtworkUriString(url: String?): String {
            if (url.isNullOrBlank()) {
                return DEFAULT_ARTWORK_URI
            }
            val trimmed = url.trim()
            val scheme = trimmed.substringBefore("://", "")
            return if (scheme.equals("http", ignoreCase = true) ||
                scheme.equals("https", ignoreCase = true) ||
                scheme.equals("content", ignoreCase = true) ||
                scheme.equals("android.resource", ignoreCase = true) ||
                scheme.equals("file", ignoreCase = true)
            ) {
                trimmed
            } else {
                DEFAULT_ARTWORK_URI
            }
        }

        fun safeArtworkUri(url: String?): Uri? {
            val validUriString = safeArtworkUriString(url)
            return try {
                Uri.parse(validUriString)
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Get children for a specified parentId.
     * Guaranteed to never return null and never throw.
     */
    suspend fun getChildren(parentId: String): List<MediaItem> {
        if (!isValidParentId(parentId)) {
            return emptyList()
        }
        return try {
            when (parentId) {
                ROOT_ID, ROOT_ALIAS_ID, "" -> getRootCategories()
                HOME_ID -> getHomeItems()
                RECENTS_ID, SECTION_RECENT -> getRecentlyPlayedItems()
                BROWSE_ID -> getBrowseItems()
                LIBRARY_ID -> getLibraryCategories()
                PLAYLISTS_ID, SECTION_PLAYLISTS -> getPlaylistItems()
                LIKED_SONGS_ID -> getLikedSongItems()
                SECTION_MADE_FOR_YOU -> getMadeForYouItems()
                EXPLORE_MOODS_ID -> getExploreMoodsItems()
                EXPLORE_GENRES_ID -> getExploreGenresItems()
                EXPLORE_NEW_RELEASES_ID -> getExploreNewReleasesItems()
                EXPLORE_CHARTS_ID -> getExploreChartsItems()
                else -> {
                    when {
                        parentId.startsWith(PLAYLIST_PREFIX) -> {
                            val playlistId = parentId.removePrefix(PLAYLIST_PREFIX).trim()
                            if (playlistId.isNotBlank()) {
                                getPlaylistTrackItems(playlistId)
                            } else {
                                emptyList()
                            }
                        }
                        parentId.startsWith(ALBUM_PREFIX) -> {
                            val albumRaw = parentId.removePrefix(ALBUM_PREFIX).trim()
                            if (albumRaw.isNotBlank()) {
                                getAlbumTrackItems(albumRaw)
                            } else {
                                emptyList()
                            }
                        }
                        parentId.startsWith(EXPLORE_CATEGORY_PREFIX) -> {
                            val categoryName = parentId.removePrefix(EXPLORE_CATEGORY_PREFIX).trim()
                            if (categoryName.isNotBlank()) {
                                getExploreCategoryTrackItems(categoryName)
                            } else {
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                }
            }
        } catch (e: Throwable) {
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
                musicRepository.searchTracks(query).firstOrNull()?.toList() ?: emptyList()
            }
            results.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = "search/${query.trim()}") }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Resolve a single playable MediaItem when a direct mediaId is requested.
     */
    suspend fun resolvePlayableItem(rawId: String, fallbackItem: MediaItem): MediaItem {
        return try {
            val cleanId = rawId.split("/").lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: rawId
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

            val tracks = musicRepository.searchOnline(cleanId).getOrElse {
                musicRepository.searchTracks(cleanId).firstOrNull()?.toList() ?: emptyList()
            }
            val matchedTrack = tracks.firstOrNull { it.id == cleanId } ?: tracks.firstOrNull()
            matchedTrack?.toMediaItem(path = "track") ?: fallbackItem
        } catch (e: Throwable) {
            fallbackItem
        }
    }

    private fun getRootCategories(): List<MediaItem> {
        return listOf(
            createCategoryItem(
                id = HOME_ID,
                title = "Home",
                subtitle = "Music & Mixes",
                artworkUrl = TAB_HOME_URI,
                iconResId = R.drawable.ic_auto_tab_home,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                childrenIsGrid = true
            ),
            createCategoryItem(
                id = RECENTS_ID,
                title = "Recents",
                subtitle = "Jump Back In",
                artworkUrl = TAB_RECENTS_URI,
                iconResId = R.drawable.ic_auto_tab_recents,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                childrenIsGrid = false
            ),
            createCategoryItem(
                id = BROWSE_ID,
                title = "Browse",
                subtitle = "Explore & Charts",
                artworkUrl = TAB_SEARCH_URI,
                iconResId = R.drawable.ic_auto_tab_search,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                childrenIsGrid = true
            ),
            createCategoryItem(
                id = LIBRARY_ID,
                title = "Library",
                subtitle = "Playlists & Liked Songs",
                artworkUrl = TAB_LIBRARY_URI,
                iconResId = R.drawable.ic_auto_tab_library,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                isGrid = true,
                childrenIsGrid = true
            )
        )
    }

    /**
     * Home tab children:
     * Exclusively displays recently played playlists and albums as square grid tiles.
     * Standalone songs or single-track cards are strictly excluded per user requirement.
     */
    private suspend fun getHomeItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val seenKeys = mutableSetOf<String>()

        try {
            // 1. Liked Songs playlist
            items.add(
                createCategoryItem(
                    id = LIKED_SONGS_ID,
                    title = "Liked Songs",
                    subtitle = "Favorite Tracks",
                    artworkUrl = DEFAULT_ARTWORK_URI,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                    isGrid = true,
                    isPlayable = true
                )
            )
            seenKeys.add(LIKED_SONGS_ID)
        } catch (e: Throwable) {
            // Degrade gracefully
        }

        try {
            // 2. Recent contexts from SessionManager (strictly playlists & albums from app sessions)
            sessionManager?.recentContextsFlow?.value?.forEach { ctx ->
                if (ctx.title.isNotBlank() && ctx.id.isNotBlank() && seenKeys.add(ctx.id)) {
                    val isAlbum = ctx.type.equals("album", ignoreCase = true)
                    val id = if (isAlbum) "$ALBUM_PREFIX${ctx.id}" else "$PLAYLIST_PREFIX${ctx.id}"
                    items.add(
                        createCategoryItem(
                            id = id,
                            title = ctx.title,
                            subtitle = if (isAlbum) "Recent Album" else "Recent Playlist",
                            artworkUrl = ctx.coverUrl,
                            mediaType = if (isAlbum) MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS else MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                            isGrid = true,
                            isPlayable = true
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            // Degrade gracefully
        }

        try {
            // 4. User's local and Spotify playlists
            val localPlaylists = musicRepository.getPlaylists().firstOrNull()?.toList() ?: emptyList()
            localPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { entity ->
                val pId = "$PLAYLIST_PREFIX${entity.id}"
                if (seenKeys.add(pId)) {
                    items.add(
                        createCategoryItem(
                            id = pId,
                            title = entity.title,
                            subtitle = "Playlist",
                            artworkUrl = entity.coverUrl,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                            isGrid = true,
                            isPlayable = true
                        )
                    )
                }
            }

            val userPlaylists = musicRepository.getUserSpotifyPlaylists().firstOrNull()?.toList() ?: emptyList()
            userPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.forEach { playlist ->
                val pId = "$PLAYLIST_PREFIX${playlist.id}"
                if (seenKeys.add(pId)) {
                    items.add(
                        createCategoryItem(
                            id = pId,
                            title = playlist.title,
                            subtitle = playlist.ownerName ?: "Playlist",
                            artworkUrl = playlist.coverUrl,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                            isGrid = true,
                            isPlayable = true
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            // Degrade gracefully
        }

        try {
            // 5. Featured playlists fallback if list is sparse
            if (items.size <= 2) {
                val featuredPlaylists = musicRepository.getFeaturedPlaylists().firstOrNull()?.toList() ?: emptyList()
                featuredPlaylists.filter { it.title.isNotBlank() && it.id.isNotBlank() }.take(6).forEach { playlist ->
                    val pId = "$PLAYLIST_PREFIX${playlist.id}"
                    if (seenKeys.add(pId)) {
                        items.add(
                            createCategoryItem(
                                id = pId,
                                title = playlist.title,
                                subtitle = playlist.ownerName ?: "Featured",
                                artworkUrl = playlist.coverUrl,
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                                isGrid = true,
                                isPlayable = true
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            // Degrade gracefully
        }

        return items.toList()
    }

    private suspend fun getRecentlyPlayedItems(): List<MediaItem> {
        return try {
            val tracks = musicRepository.getRecentlyPlayed(30).firstOrNull()?.toList() ?: emptyList()
            val spotifyTracks = if (tracks.isEmpty()) {
                musicRepository.getSpotifyRecentlyPlayed().firstOrNull()?.toList() ?: emptyList()
            } else emptyList()

            val resultTracks = when {
                tracks.isNotEmpty() -> tracks
                spotifyTracks.isNotEmpty() -> spotifyTracks
                else -> musicRepository.getDiscoveryTracks().firstOrNull()?.toList() ?: emptyList()
            }

            resultTracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = RECENTS_ID, isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Browse tab children:
     * Exactly matches the explore experience in the mobile app (Moods & moments, Genres, New Releases, Top Charts).
     */
    private suspend fun getBrowseItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        // 1. Primary Explore Shelves
        items.add(
            createCategoryItem(
                id = EXPLORE_NEW_RELEASES_ID,
                title = "New Releases",
                subtitle = "The latest albums & singles",
                artworkUrl = DEFAULT_ARTWORK_URI,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                isGrid = true,
                isPlayable = true
            )
        )

        items.add(
            createCategoryItem(
                id = EXPLORE_CHARTS_ID,
                title = "Top Charts",
                subtitle = "Global Top 50 & Trending",
                artworkUrl = DEFAULT_ARTWORK_URI,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                isGrid = true,
                isPlayable = true
            )
        )

        items.add(
            createCategoryItem(
                id = EXPLORE_MOODS_ID,
                title = "Moods & Moments",
                subtitle = "Chill, Focus, Workout & more",
                artworkUrl = DEFAULT_ARTWORK_URI,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                isPlayable = false
            )
        )

        items.add(
            createCategoryItem(
                id = EXPLORE_GENRES_ID,
                title = "Genres",
                subtitle = "Pop, Rock, Hip-Hop, Indie & more",
                artworkUrl = DEFAULT_ARTWORK_URI,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                isPlayable = false
            )
        )

        // 2. Top Explore category shortcuts directly accessible on Browse root (mirroring mobile app explore)
        val featuredCategories = listOf(
            "Chill", "Workout", "Focus", "Party", "Feel good",
            "Pop", "Hip-Hop", "Rock", "Dance & electronic", "Indie & alternative",
            "R&B & soul", "K-Pop", "Hindi", "Punjabi"
        )

        featuredCategories.forEach { categoryTitle ->
            val thumbnail = context?.let { GenreCategoryCache.getFirstSongThumbnail(it, categoryTitle) }
            items.add(
                createCategoryItem(
                    id = "$EXPLORE_CATEGORY_PREFIX$categoryTitle",
                    title = categoryTitle,
                    subtitle = "Explore Category",
                    artworkUrl = thumbnail,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    isGrid = true,
                    isPlayable = true
                )
            )
        }

        return items.toList()
    }

    private fun getExploreMoodsItems(): List<MediaItem> {
        val moods = listOf(
            "Chill", "Commute", "Energize", "Feel good", "Focus",
            "Gaming", "Party", "Romance", "Sad", "Sleep", "Workout"
        )
        return moods.map { mood ->
            val thumbnail = context?.let { GenreCategoryCache.getFirstSongThumbnail(it, mood) }
            createCategoryItem(
                id = "$EXPLORE_CATEGORY_PREFIX$mood",
                title = mood,
                subtitle = "Mood",
                artworkUrl = thumbnail,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                isPlayable = true
            )
        }
    }

    private fun getExploreGenresItems(): List<MediaItem> {
        val genres = listOf(
            "Pop", "Hip-Hop", "Rock", "Dance & electronic", "Indie & alternative",
            "R&B & soul", "K-Pop", "Country & Americana", "Latin", "Classical",
            "Jazz", "Metal", "Hindi", "Punjabi", "Desi hip-hop", "Indian Indie",
            "Devotional", "Folk & acoustic", "Ghazal/Sufi", "Gujarati", "Bengali",
            "Kannada", "Malayalam", "Marathi", "Tamil", "Telugu", "J-Pop", "African"
        )
        return genres.map { genre ->
            val thumbnail = context?.let { GenreCategoryCache.getFirstSongThumbnail(it, genre) }
            createCategoryItem(
                id = "$EXPLORE_CATEGORY_PREFIX$genre",
                title = genre,
                subtitle = "Genre",
                artworkUrl = thumbnail,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isGrid = true,
                isPlayable = true
            )
        }
    }

    private suspend fun getExploreNewReleasesItems(): List<MediaItem> {
        return try {
            val tracks = musicRepository.getNewReleases().firstOrNull()?.toList() ?: emptyList()
            if (tracks.isNotEmpty()) {
                tracks.map { it.toMediaItem(path = EXPLORE_NEW_RELEASES_ID, isGrid = false) }
            } else {
                val top = musicRepository.getGlobalTop50().firstOrNull()?.toList() ?: emptyList()
                top.map { it.toMediaItem(path = EXPLORE_NEW_RELEASES_ID, isGrid = false) }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getExploreChartsItems(): List<MediaItem> {
        return try {
            val top = musicRepository.getGlobalTop50().firstOrNull()?.toList() ?: emptyList()
            top.map { it.toMediaItem(path = EXPLORE_CHARTS_ID, isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getExploreCategoryTrackItems(categoryName: String): List<MediaItem> {
        return try {
            // 1. Try disk-cached category payload from GenreCategoryCache
            val cached = context?.let { GenreCategoryCache.getCachedCategory(it, categoryName) }
            if (cached != null && cached.songs.isNotEmpty()) {
                return cached.songs.map { songDto ->
                    Track(
                        id = songDto.id,
                        title = songDto.title,
                        artist = songDto.artists.joinToString(", ").takeIf { it.isNotBlank() } ?: "Various Artists",
                        album = songDto.albumTitle ?: categoryName,
                        albumImageUrl = songDto.thumbnail,
                        durationMs = (songDto.duration ?: 0) * 1000L
                    ).toMediaItem(path = "$EXPLORE_CATEGORY_PREFIX$categoryName", isGrid = false)
                }
            }

            // 2. Fallback to online/local search
            val tracks = musicRepository.searchOnline(categoryName).getOrElse {
                musicRepository.searchTracks(categoryName).firstOrNull()?.toList() ?: emptyList()
            }
            tracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = "$EXPLORE_CATEGORY_PREFIX$categoryName", isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getAlbumTrackItems(albumRaw: String): List<MediaItem> {
        return try {
            val parts = albumRaw.split("::")
            val albumTitle = parts.firstOrNull().orEmpty().trim()
            val artistName = parts.getOrNull(1).orEmpty().trim()

            // 1. Try finding songs from recent history matching this album
            val recent = musicRepository.getRecentlyPlayed(50).firstOrNull()?.toList() ?: emptyList()
            val matched = recent.filter { it.album.equals(albumTitle, ignoreCase = true) }
            if (matched.isNotEmpty()) {
                return matched.map { it.toMediaItem(path = "$ALBUM_PREFIX$albumRaw", isGrid = false) }
            }

            // 2. Query repository search
            val query = if (artistName.isNotBlank()) "$albumTitle $artistName" else albumTitle
            val searchResults = musicRepository.searchOnline(query).getOrElse {
                musicRepository.searchTracks(albumTitle).firstOrNull()?.toList() ?: emptyList()
            }
            searchResults.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = "$ALBUM_PREFIX$albumRaw", isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
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
        try {
            items.addAll(getPlaylistItems())
        } catch (e: Throwable) {
            // Degrade gracefully
        }
        return items.toList()
    }

    private suspend fun getPlaylistItems(): List<MediaItem> {
        return try {
            val localPlaylists = musicRepository.getPlaylists().firstOrNull()?.toList() ?: emptyList()
            val spotifyPlaylists = musicRepository.getUserSpotifyPlaylists().firstOrNull()?.toList() ?: emptyList()

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
                val featured = musicRepository.getFeaturedPlaylists().firstOrNull()?.toList() ?: emptyList()
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

            items.toList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getPlaylistTrackItems(playlistId: String): List<MediaItem> {
        return try {
            val onlineTracks = try {
                musicRepository.getSpotifyPlaylistTracks(playlistId).toList()
            } catch (e: Throwable) {
                emptyList()
            }
            val localTracks = if (onlineTracks.isEmpty()) {
                musicRepository.getPlaylistTracks(playlistId).firstOrNull()?.toList() ?: emptyList()
            } else emptyList()

            val tracks = when {
                onlineTracks.isNotEmpty() -> onlineTracks
                localTracks.isNotEmpty() -> localTracks
                else -> musicRepository.getDiscoveryTracks().firstOrNull()?.toList() ?: emptyList()
            }
            tracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = "$PLAYLIST_PREFIX$playlistId", isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getLikedSongItems(): List<MediaItem> {
        return try {
            val tracks = musicRepository.getLikedTracks().firstOrNull()?.toList() ?: emptyList()
            val resultTracks = if (tracks.isNotEmpty()) tracks else {
                musicRepository.getDiscoveryTracks().firstOrNull()?.toList() ?: emptyList()
            }
            resultTracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = LIKED_SONGS_ID, isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun getMadeForYouItems(): List<MediaItem> {
        return try {
            val tracks = musicRepository.getDiscoveryTracks().firstOrNull()?.toList() ?: emptyList()
            val resultTracks = if (tracks.isNotEmpty()) tracks else {
                musicRepository.getGlobalTop50().firstOrNull()?.toList() ?: emptyList()
            }
            resultTracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
                .map { it.toMediaItem(path = SECTION_MADE_FOR_YOU, isGrid = false) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun createCategoryItem(
        id: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
        artworkUrl: String? = null,
        iconResId: Int? = null,
        isGrid: Boolean = true,
        childrenIsGrid: Boolean = isGrid,
        isPlayable: Boolean = false,
        isBrowsable: Boolean = true
    ): MediaItem {
        val safeId = id.trim().takeIf { it.isNotEmpty() } ?: "category_${title.hashCode()}"
        val style = if (isGrid) 2 else 1 // 2 = GRID, 1 = LIST
        val childrenStyle = if (childrenIsGrid) 2 else 1
        val safeArt = artworkUrl ?: DEFAULT_ARTWORK_URI
        val extras = Bundle().apply {
            putInt("androidx.media.utils.CONTENT_STYLE_SINGLE_ITEM", style)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, style)
            putInt("androidx.media.utils.CONTENT_STYLE_BROWSABLE_HINT", childrenStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, childrenStyle)
            putInt("androidx.media.utils.CONTENT_STYLE_PLAYABLE_HINT", childrenStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, childrenStyle)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            if (iconResId != null) {
                putInt("android.support.v4.media.description.ICON_RESOURCE", iconResId)
            }
            putString("android.support.v4.media.description.ICON_URI", safeArt)
        }
        return MediaItem.Builder()
            .setMediaId(safeId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifBlank { "Untitled" })
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(safeArtworkUri(safeArt))
                    .setIsBrowsable(isBrowsable)
                    .setIsPlayable(isPlayable)
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
    val cleanId = id.split("/").lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "track_${title.hashCode()}"
    val validPath = path.trim().takeIf { it.isNotEmpty() } ?: "track"
    val safeTitle = title.ifBlank { "Track $cleanId" }
    val safeArtist = artist.ifBlank { "Unknown Artist" }
    val durationSec = if (durationMs > 0) durationMs / 1000L else 0L
    val durationParam = if (durationSec > 0) "&duration=$durationSec" else ""
    val streamUri = audioUrl ?: "vibevault://stream?id=$cleanId&title=${Uri.encode(safeTitle)}&artist=${Uri.encode(safeArtist)}$durationParam"
    val style = if (isGrid) 2 else 1
    val extras = Bundle().apply {
        putInt("androidx.media.utils.CONTENT_STYLE_SINGLE_ITEM", style)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, style)
        putInt("androidx.media.utils.CONTENT_STYLE_PLAYABLE_HINT", style)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
        putBoolean("isLiked", isLiked)
        if (durationMs > 0) putLong("durationMs", durationMs)
        if (albumImageUrl.isNotBlank()) putString("artwork_uri", albumImageUrl)
    }
    return MediaItem.Builder()
        .setMediaId("$validPath/$cleanId")
        .setUri(streamUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(safeTitle)
                .setSubtitle(safeArtist)
                .setArtist(safeArtist)
                .setAlbumTitle(album)
                .setAlbumArtist(safeArtist)
                .setDisplayTitle(safeTitle)
                .setArtworkUri(AutoMediaBrowserTree.safeArtworkUri(albumImageUrl))
                .setDurationMs(if (durationMs > 0) durationMs else 0L)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)
                .build()
        )
        .build()
}

/**
 * Extension function to convert a Media3 MediaItem back to a domain Track.
 */
fun MediaItem.toDomainTrack(): Track {
    val cleanId = mediaId.split("/").lastOrNull() ?: mediaId
    val md = mediaMetadata
    val dur = md.durationMs ?: (md.extras?.getLong("durationMs") ?: 0L)
    val isLiked = md.extras?.getBoolean("isLiked") ?: false
    val artUri = md.artworkUri?.toString() ?: md.extras?.getString("artwork_uri").orEmpty()
    return Track(
        id = cleanId,
        title = md.title?.toString()?.takeIf { it.isNotBlank() } ?: "Track $cleanId",
        artist = md.artist?.toString()?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown Artist",
        album = md.albumTitle?.toString().orEmpty(),
        albumImageUrl = artUri,
        durationMs = dur,
        isLiked = isLiked
    )
}

fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize < 1 || isEmpty()) return emptyList()
    if (pageSize == Int.MAX_VALUE) return this
    val fromIndex = page.toLong() * pageSize
    if (fromIndex >= size) return emptyList()
    return subList(fromIndex.toInt(), minOf(fromIndex.toInt() + pageSize, size))
}