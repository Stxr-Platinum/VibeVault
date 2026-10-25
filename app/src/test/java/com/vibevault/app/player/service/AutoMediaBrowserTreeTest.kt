package com.vibevault.app.player.service

import com.vibevault.app.domain.model.*
import com.vibevault.app.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AutoMediaBrowserTreeTest {

    private lateinit var fakeRepo: FakeMusicRepository
    private lateinit var browserTree: AutoMediaBrowserTree

    @Before
    fun setup() {
        fakeRepo = FakeMusicRepository()
        browserTree = AutoMediaBrowserTree(fakeRepo)
    }

    /**
     * Requirement 7(a): Empty data state must never throw, must return valid lists.
     */
    @Test
    fun testEmptyDataStateNeverThrows() = runBlocking {
        fakeRepo.returnEmpty = true

        val rootChildren = browserTree.getChildren(AutoMediaBrowserTree.ROOT_ID)
        assertNotNull(rootChildren)
        assertTrue("Root categories should have items", rootChildren.isNotEmpty())

        val homeChildren = browserTree.getChildren(AutoMediaBrowserTree.HOME_ID)
        assertNotNull(homeChildren)

        val recentsChildren = browserTree.getChildren(AutoMediaBrowserTree.RECENTS_ID)
        assertNotNull(recentsChildren)

        val browseChildren = browserTree.getChildren(AutoMediaBrowserTree.BROWSE_ID)
        assertNotNull(browseChildren)

        val libraryChildren = browserTree.getChildren(AutoMediaBrowserTree.LIBRARY_ID)
        assertNotNull(libraryChildren)

        val playlistsChildren = browserTree.getChildren(AutoMediaBrowserTree.PLAYLISTS_ID)
        assertNotNull(playlistsChildren)

        val likedChildren = browserTree.getChildren(AutoMediaBrowserTree.LIKED_SONGS_ID)
        assertNotNull(likedChildren)
    }

    /**
     * Requirement 3: Invalid or unknown parentId must return empty list, never throw.
     */
    @Test
    fun testUnknownParentIdReturnsEmptyList() = runBlocking {
        val unknown1 = browserTree.getChildren("unknown_category_123")
        assertNotNull(unknown1)
        assertTrue(unknown1.isEmpty())

        val unknown2 = browserTree.getChildren("playlist_")
        assertNotNull(unknown2)
        assertTrue(unknown2.isEmpty())

        assertFalse(AutoMediaBrowserTree.isValidParentId("invalid_parent_xyz"))
        assertTrue(AutoMediaBrowserTree.isValidParentId(AutoMediaBrowserTree.ROOT_ID))
        assertTrue(AutoMediaBrowserTree.isValidParentId("playlist_valid123"))
    }

    /**
     * Requirement 7(b): Slow network / loading state executes safely without hanging or throwing.
     */
    @Test
    fun testSlowLoadingStateGracefullyCompletes() = runBlocking {
        fakeRepo.delayMs = 50L
        fakeRepo.returnEmpty = false
        fakeRepo.sampleTracks = listOf(
            Track(
                id = "track1",
                title = "Slow Loaded Song",
                artist = "Test Artist",
                album = "Test Album",
                albumImageUrl = "https://example.com/cover.jpg",
                durationMs = 180000L
            )
        )

        val recents = browserTree.getChildren(AutoMediaBrowserTree.RECENTS_ID)
        assertNotNull(recents)
        assertEquals(1, recents.size)
        assertEquals("recents/track1", recents[0].mediaId)
    }

    /**
     * Requirement 4 & 7(c): Malformed or missing artwork URLs safely resolve to default drawable URI.
     */
    @Test
    fun testArtworkSafeFallback() {
        val nullArt = AutoMediaBrowserTree.safeArtworkUriString(null)
        assertEquals(AutoMediaBrowserTree.DEFAULT_ARTWORK_URI, nullArt)

        val blankArt = AutoMediaBrowserTree.safeArtworkUriString("   ")
        assertEquals(AutoMediaBrowserTree.DEFAULT_ARTWORK_URI, blankArt)

        val invalidScheme = AutoMediaBrowserTree.safeArtworkUriString("invalid_scheme://not_an_image")
        assertEquals(AutoMediaBrowserTree.DEFAULT_ARTWORK_URI, invalidScheme)

        val validWebArt = AutoMediaBrowserTree.safeArtworkUriString("https://img.youtube.com/vi/test/hqdefault.jpg")
        assertEquals("https://img.youtube.com/vi/test/hqdefault.jpg", validWebArt)

        val validContentArt = AutoMediaBrowserTree.safeArtworkUriString("content://media/external/audio/albumart/1")
        assertEquals("content://media/external/audio/albumart/1", validContentArt)

        // Ensure safeArtworkUri safely handles all inputs without throwing
        AutoMediaBrowserTree.safeArtworkUri(null)
        AutoMediaBrowserTree.safeArtworkUri("   ")
        AutoMediaBrowserTree.safeArtworkUri("invalid_scheme://not_an_image")
        AutoMediaBrowserTree.safeArtworkUri("https://img.youtube.com/vi/test/hqdefault.jpg")
    }

    /**
     * Requirement 6 & 7(d): Rapid tab switching and concurrent modifications do not throw ConcurrentModificationException.
     */
    @Test
    fun testRapidTabSwitchingAndConcurrentAccess() = runBlocking {
        fakeRepo.sampleTracks = (1..20).map {
            Track(
                id = "track_$it",
                title = "Song $it",
                artist = "Artist $it",
                album = "Album $it",
                albumImageUrl = "https://example.com/art$it.jpg",
                durationMs = 200000L
            )
        }
        fakeRepo.returnEmpty = false

        val tabs = listOf(
            AutoMediaBrowserTree.ROOT_ID,
            AutoMediaBrowserTree.HOME_ID,
            AutoMediaBrowserTree.RECENTS_ID,
            AutoMediaBrowserTree.BROWSE_ID,
            AutoMediaBrowserTree.LIBRARY_ID,
            AutoMediaBrowserTree.PLAYLISTS_ID,
            AutoMediaBrowserTree.LIKED_SONGS_ID
        )

        // Simulate rapid tab switching across multiple coroutines
        val jobs = List(50) { index ->
            launch(Dispatchers.Default) {
                val tab = tabs[index % tabs.size]
                val items = browserTree.getChildren(tab)
                assertNotNull(items)
            }
        }
        jobs.forEach { it.join() }
    }

    /**
     * Requirement: Standalone songs are not shown on top in Home; only recently played playlists & albums are shown.
     */
    @Test
    fun testHomeOnlyReturnsPlaylistsAndAlbums() = runBlocking {
        fakeRepo.returnEmpty = false
        fakeRepo.sampleTracks = listOf(
            Track(
                id = "track_nightcall",
                title = "Nightcall",
                artist = "Kavinsky",
                album = "OutRun",
                albumImageUrl = "https://example.com/outrun.jpg",
                durationMs = 240000L
            )
        )
        fakeRepo.samplePlaylists = listOf(
            Playlist(
                id = "playlist_synth",
                title = "Synthwave Classics",
                ownerName = "VibeVault",
                coverUrl = "https://example.com/synth.jpg",
                trackCount = 20
            )
        )

        val homeItems = browserTree.getChildren(AutoMediaBrowserTree.HOME_ID)
        assertNotNull(homeItems)
        assertTrue(homeItems.isNotEmpty())

        // Ensure NO items are direct single song tracks (path="home/...")
        val hasDirectSongs = homeItems.any { it.mediaId.startsWith("home/") }
        assertFalse("Home screen must not contain standalone direct song items on top", hasDirectSongs)

        // Ensure songs are not converted into pseudo-album items from track history
        val hasSongAsAlbum = homeItems.any { it.mediaMetadata.title == "Nightcall" }
        assertFalse("Individual song titles must not appear as items on the Home screen", hasSongAsAlbum)

        // Ensure playlists are present
        val hasPlaylist = homeItems.any { it.mediaId == AutoMediaBrowserTree.LIKED_SONGS_ID || it.mediaId.startsWith(AutoMediaBrowserTree.PLAYLIST_PREFIX) }
        assertTrue("Home screen should contain playlist", hasPlaylist)
    }

    @Test
    fun testRootCategoriesHaveValidPngDrawables() = runBlocking {
        val rootChildren = browserTree.getChildren(AutoMediaBrowserTree.ROOT_ID)
        assertEquals(4, rootChildren.size)
        val home = rootChildren.first { it.mediaId == AutoMediaBrowserTree.HOME_ID }
        assertEquals("Home", home.mediaMetadata.title.toString())
        val search = rootChildren.first { it.mediaId == AutoMediaBrowserTree.BROWSE_ID }
        assertEquals("Browse", search.mediaMetadata.title.toString())
        assertTrue(AutoMediaBrowserTree.TAB_HOME_URI.startsWith("android.resource://"))
        assertTrue(AutoMediaBrowserTree.TAB_HOME_URI.endsWith("ic_tab_home_png"))
        assertTrue(AutoMediaBrowserTree.TAB_SEARCH_URI.endsWith("ic_tab_search_png"))
        assertTrue(AutoMediaBrowserTree.TAB_LIBRARY_URI.endsWith("ic_tab_library_png"))
        assertTrue(AutoMediaBrowserTree.TAB_RECENTS_URI.endsWith("ic_tab_recents_png"))
    }

    /**
     * Requirement: Browse in Android Auto displays exact same structure that Explore in the mobile app displays.
     */
    @Test
    fun testBrowseMatchesAppExploreStructure() = runBlocking {
        fakeRepo.returnEmpty = false
        val browseItems = browserTree.getChildren(AutoMediaBrowserTree.BROWSE_ID)
        assertNotNull(browseItems)
        assertTrue(browseItems.isNotEmpty())

        val ids = browseItems.map { it.mediaId }
        assertTrue(ids.contains(AutoMediaBrowserTree.EXPLORE_NEW_RELEASES_ID))
        assertTrue(ids.contains(AutoMediaBrowserTree.EXPLORE_CHARTS_ID))
        assertTrue(ids.contains(AutoMediaBrowserTree.EXPLORE_MOODS_ID))
        assertTrue(ids.contains(AutoMediaBrowserTree.EXPLORE_GENRES_ID))
        assertTrue(ids.any { it.startsWith(AutoMediaBrowserTree.EXPLORE_CATEGORY_PREFIX) })

        // Check sub-category drilldown for moods & genres
        val moods = browserTree.getChildren(AutoMediaBrowserTree.EXPLORE_MOODS_ID)
        assertTrue("Moods should contain items", moods.isNotEmpty())
        assertTrue(moods.any { it.mediaMetadata.title == "Chill" })

        val genres = browserTree.getChildren(AutoMediaBrowserTree.EXPLORE_GENRES_ID)
        assertTrue("Genres should contain items", genres.isNotEmpty())
        assertTrue(genres.any { it.mediaMetadata.title == "Rock" })
    }
}

class FakeMusicRepository : MusicRepository {
    var delayMs: Long = 0L
    var returnEmpty: Boolean = true
    var sampleTracks: List<Track> = emptyList()
    var samplePlaylists: List<Playlist> = emptyList()

    override fun getAllTracks(): Flow<List<Track>> = flowOf(sampleTracks)
    override fun getLikedTracks(): Flow<List<Track>> = flowOf(if (returnEmpty) emptyList() else sampleTracks)
    override fun getRecentlyPlayed(limit: Int): Flow<List<Track>> = flow {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        emit(if (returnEmpty) emptyList() else sampleTracks)
    }
    override fun searchTracks(query: String): Flow<List<Track>> = flowOf(sampleTracks)
    override fun getDiscoveryTracks(): Flow<List<Track>> = flowOf(if (returnEmpty) emptyList() else sampleTracks)
    override suspend fun getSimilarTracks(track: Track): Result<List<Track>> = Result.success(emptyList())
    override fun getTracksByArtist(artistName: String): Flow<List<Track>> = flowOf(emptyList())
    override suspend fun getArtistTopSongs(artistName: String): Result<List<Track>> = Result.success(emptyList())
    override suspend fun getArtistLatestAlbums(artistName: String): Result<List<Album>> = Result.success(emptyList())
    override suspend fun searchOnline(query: String): Result<List<Track>> = Result.success(sampleTracks)
    override suspend fun toggleLike(trackId: String) {}
    override suspend fun toggleLikeTrack(track: Track) {}
    override suspend fun recordPlay(track: Track) {}
    override suspend fun purgePlaceholderHistory() {}
    override fun getLogs(): Flow<List<com.vibevault.app.data.local.entity.LogEntity>> = flowOf(emptyList())
    override fun getDevices(): Flow<List<com.vibevault.app.data.local.entity.DeviceEntity>> = flowOf(emptyList())
    override suspend fun clearLocalData() {}
    override suspend fun refreshTracks() {}
    override suspend fun syncFromRemote() {}
    override suspend fun syncRecentlyPlayed() {}
    override suspend fun seedMockData() {}
    override fun getPlaylists(): Flow<List<com.vibevault.app.data.local.entity.PlaylistEntity>> = flowOf(emptyList())
    override fun getPlaylistTracks(playlistId: String): Flow<List<Track>> = flowOf(sampleTracks)
    override suspend fun getPlaylist(playlistId: String): com.vibevault.app.data.local.entity.PlaylistEntity? = null
    override suspend fun createPlaylist(title: String): String = "id"
    override suspend fun renamePlaylist(playlistId: String, newTitle: String) {}
    override suspend fun updatePlaylistCover(playlistId: String, coverUrl: String) {}
    override suspend fun deletePlaylist(playlistId: String) {}
    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {}
    override suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean = false
    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {}
    override fun clearSpotifyCache() {}
    override fun getSpotifyRecentlyPlayed(): Flow<List<Track>> = flowOf(emptyList())
    override fun getFeaturedPlaylists(): Flow<List<Playlist>> = flowOf(if (returnEmpty) emptyList() else samplePlaylists)
    override fun getNewReleases(): Flow<List<Track>> = flowOf(emptyList())
    override fun getTopArtists(): Flow<List<Artist>> = flowOf(emptyList())
    override fun getUserSpotifyPlaylists(): Flow<List<Playlist>> = flowOf(if (returnEmpty) emptyList() else samplePlaylists)
    override suspend fun getSpotifyPlaylistTracks(playlistId: String): List<Track> = sampleTracks
    override suspend fun backgroundSyncSpotifyPlaylists() {}
    override suspend fun backgroundSyncSpotifyPlaylistTracks(playlistId: String): Result<Unit> = Result.success(Unit)
    override suspend fun forceRefreshSpotifyPlaylist(playlistId: String): Result<Pair<Playlist?, List<Track>>> = Result.success(null to emptyList())
    override fun getBrowseCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun searchSpotifyAll(query: String): Result<SpotifySearchResult> = Result.success(SpotifySearchResult(emptyList(), emptyList(), emptyList()))
    override fun getGlobalTop50(): Flow<List<Track>> = flowOf(emptyList())
    override suspend fun getAlbumDetails(albumId: String): Result<AlbumDetails> = Result.failure(Exception())
    override suspend fun cacheSpotifyPlaylist(playlistId: String, title: String, coverUrl: String?, tracks: List<Track>) {}
    override suspend fun resolveTrackLinks(trackId: String): Result<Track> = Result.failure(Exception())
}
