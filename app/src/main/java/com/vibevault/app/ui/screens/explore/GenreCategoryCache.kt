package com.vibevault.app.ui.screens.explore

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.Gson
import com.music.innertube.YouTube
import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight serializable representation of a genre's songs and shelves for fast disk caching.
 */
data class CachedCategoryPayload(
    val title: String,
    val timestamp: Long,
    val firstSongThumbnail: String?,
    val songs: List<CachedSongDto> = emptyList(),
    val featuredPlaylists: List<CachedPlaylistDto> = emptyList(),
    val communityPlaylists: List<CachedPlaylistDto> = emptyList(),
    val albums: List<CachedAlbumDto> = emptyList()
)

data class CachedSongDto(
    val id: String,
    val title: String,
    val artists: List<String>,
    val albumTitle: String?,
    val duration: Int?,
    val thumbnail: String,
    val explicit: Boolean
)

data class CachedPlaylistDto(
    val id: String,
    val title: String,
    val authorName: String?,
    val songCountText: String?,
    val thumbnail: String?
)

data class CachedAlbumDto(
    val browseId: String,
    val playlistId: String,
    val title: String,
    val artists: List<String>,
    val year: Int?,
    val thumbnail: String
)

/**
 * Centralized cache and resolver for genre tiles and inner genre pages.
 * - Dynamically resolves the album artwork of the first song listed on each genre's detail page.
 * - Stores category payloads and thumbnails locally on disk to eliminate network latency when navigating between categories.
 * - Preloads image bitmaps directly into Coil's disk and memory cache.
 */
object GenreCategoryCache {

    private const val PREFS_NAME = "genre_category_thumbnail_cache"
    private const val CACHE_SUBDIR = "genre_categories"
    private const val CACHE_EXPIRY_MS = 12 * 60 * 60 * 1000L // 12 hours TTL

    private val gson by lazy { Gson() }

    // Fast in-memory lookup for instant response
    private val memoryThumbnailCache = ConcurrentHashMap<String, String>()
    private val memoryPayloadCache = ConcurrentHashMap<String, CachedCategoryPayload>()

    /**
     * Map of genre titles to high-accuracy search queries for discovering top/first songs.
     */
    fun getGenreSongQuery(title: String): String {
        return getDynamicGenreQueries(title).first()
    }

    /**
     * Map of genre titles to multiple diverse, rotating search queries emphasizing recent and trending music.
     */
    fun getDynamicGenreQueries(title: String): List<String> {
        val t = title.lowercase().trim()
        val specific = when (t) {
            "chill" -> listOf("chill lofi relaxing beats 2024", "chill late night aesthetic trending songs", "relaxing acoustic coffee shop latest", "mellow aesthetic chill tracks")
            "commute" -> listOf("driving commute road trip trending songs", "road trip upbeat driving hits 2024", "chill scenic drive playlist latest")
            "energize" -> listOf("energetic workout pump up trending songs", "high energy motivation hits 2024", "adrenaline running tracks latest", "powerful hype workout music")
            "feel good" -> listOf("feel good happy upbeat pop trending songs", "positive vibes sunshine playlist 2024", "upbeat joyful pop anthems new")
            "focus" -> listOf("deep focus study instrumental latest songs", "study beats concentration music 2024", "ambient focus flow work tracks")
            "gaming" -> listOf("gaming electronic bass trending songs", "synthwave gaming tracks 2024", "epic gaming electronic bangers latest")
            "party" -> listOf("party dance club hits 2024", "club dance floor trending bangers", "celebration dance pop anthems new", "weekend party hits 2024")
            "romance" -> listOf("romantic love songs 2024", "late night romantic acoustic latest", "sweet love ballads new melodies", "heartfelt romantic trending tracks")
            "sad" -> listOf("sad emotional acoustic heartbreak trending songs", "sad crying lonely melodies 2024", "melancholy acoustic new songs")
            "sleep" -> listOf("sleep relaxing ambient soft songs", "deep sleep calm piano music latest", "peaceful sleep meditation sounds 2024")
            "workout" -> listOf("workout gym fitness motivation trending songs", "beast mode gym motivation bangers 2024", "heavy bass workout hits latest")
            "dance & electronic" -> listOf("dance electronic edm latest hits", "deep house trending tracks 2024", "festival edm dance new songs")
            "hip-hop" -> listOf("hip hop rap latest trending songs", "hard hip hop beats bangers 2024", "modern trap rap tracks 2024 2025")
            "desi hip-hop" -> listOf("desi hip hop rap latest songs", "indian rap trending bangers 2024", "hindi hip hop new top tracks")
            "hindi" -> listOf("latest hindi bollywood trending songs", "new hindi romantic songs 2024 2025", "hindi latest chartbusters hits", "trending bollywood music 2024")
            "punjabi" -> listOf("latest punjabi trending songs", "new punjabi viral tracks 2024 2025", "punjabi pop bangers 2024", "punjabi bhangra party songs latest")
            "indie pop", "indian indie" -> listOf("indian indie latest trending songs", "acoustic indie pop new gems", "indie folk alternative trending 2024", "late night indie new discoveries")
            "rock" -> listOf("modern rock alternative trending tracks", "latest rock songs 2024", "new rock anthems hits", "guitar rock modern essentials")
            "metal" -> listOf("modern metalcore heavy metal latest", "new heavy metal rock tracks 2024", "modern metal anthems")
            "pop" -> listOf("pop music latest trending hits", "global pop viral chartbusters 2024 2025", "catchy modern pop anthems new", "acoustic pop covers trending")
            "r&b & soul" -> listOf("r&b soul latest trending songs", "smooth neo soul modern grooves", "contemporary r&b vibes 2024")
            "j-pop" -> listOf("j-pop latest trending songs", "anime j-pop viral tracks 2024", "japanese modern pop rock new")
            "k-pop" -> listOf("k-pop latest trending hits", "k-pop new releases 2024 2025", "trending k-pop group hits")
            "decades" -> listOf("retro classic 80s 90s greatest hits", "70s 80s classic rock pop hits", "90s nostalgia chartbusters")
            "classical", "carnatic classical", "hindustani classical" -> listOf("classical vocal instrumental masterpiece", "classical music best recordings")
            else -> listOf("$title latest trending songs", "$title new hits 2024 2025", "$title popular music tracks")
        }
        return specific + listOf("$title latest trending songs", "$title hits 2024")
    }

    /**
     * Checks if a song is an older/retro track that should be excluded from modern genres.
     */
    fun isOldSong(title: String, albumName: String?, genre: String): Boolean {
        val g = genre.lowercase().trim()
        // Never filter out old songs from retro, decades, or classical categories
        if (g.contains("decade") || g.contains("retro") || g.contains("classic") || g.contains("80s") || g.contains("90s") || g.contains("70s")) {
            return false
        }

        val text = "${title.lowercase()} ${albumName.orEmpty().lowercase()}"

        // Exclude songs with explicit years before 2018 (e.g. 1950 - 2017)
        val oldYearRegex = Regex("\\b(19[5-9][0-9]|200[0-9]|201[0-7])\\b")
        if (oldYearRegex.containsMatchIn(text)) {
            return true
        }

        // Exclude retro and compilation markers
        val oldKeywords = listOf(
            "old is gold",
            "golden era",
            "classic hits",
            "purane gaane",
            "purane geet",
            "evergreen hits",
            "nostalgia hits",
            "vintage 70s",
            "vintage 80s",
            "vintage 90s",
            "remastered 199",
            "remastered 200",
            "remastered 2010",
            "remastered 2011",
            "remastered 2012",
            "remastered 2013",
            "remastered 2014",
            "remastered 2015"
        )
        return oldKeywords.any { text.contains(it) }
    }

    /**
     * Strictly verifies whether a song belongs to the specified genre.
     */
    fun matchesGenre(song: SongItem, genre: String): Boolean {
        val g = genre.lowercase().trim()
        val text = "${song.title.lowercase()} ${song.artists.joinToString(" ") { it.name.lowercase() }} ${song.album?.name.orEmpty().lowercase()}"

        // Exclude kids nursery rhymes unless genre is family
        if (text.contains("nursery rhyme") || text.contains("cocomelon") || text.contains("kids song") || text.contains("lullaby for babies")) {
            if (!g.contains("family") && !g.contains("kids")) return false
        }

        // Exclude devotional from secular genres
        if (text.contains("bhajan") || text.contains("aarti") || text.contains("chalisa") || text.contains("mantra")) {
            if (!g.contains("devotional")) return false
        }

        // Exclude extreme metal / hard drops from gentle chill/sleep/focus genres
        if (g == "chill" || g == "sleep" || g == "focus") {
            if (text.contains("death metal") || text.contains("metalcore") || text.contains("heavy bass drop") || text.contains("screamo")) {
                return false
            }
        }

        // Exclude ambient/classical from hip-hop
        if (g == "hip-hop" || g == "desi hip-hop") {
            if (text.contains("classical orchestra") || text.contains("sleep ambient") || text.contains("lofi study instrumental")) {
                return false
            }
        }

        // Exclude soft acoustic pop from heavy metal
        if (g == "metal") {
            if (text.contains("acoustic pop") || text.contains("relaxing piano") || text.contains("bollywood romance")) {
                return false
            }
        }

        return true
    }

    /**
     * Retrieves the cached first song thumbnail for a genre from memory or disk.
     */
    fun getFirstSongThumbnail(context: Context, title: String): String? {
        val key = title.trim().lowercase()
        memoryThumbnailCache[key]?.let { return it }

        // Check persistent SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persisted = prefs.getString(key, null)
        if (!persisted.isNullOrBlank()) {
            memoryThumbnailCache[key] = persisted
            return persisted
        }

        // Check cached category payload
        getCachedCategory(context, title)?.firstSongThumbnail?.let { thumb ->
            if (thumb.isNotBlank()) {
                setFirstSongThumbnail(context, title, thumb)
                return thumb
            }
        }

        return null
    }

    /**
     * Saves the first song's thumbnail for a genre into persistent cache.
     */
    fun setFirstSongThumbnail(context: Context, title: String, thumbnailUrl: String) {
        if (thumbnailUrl.isBlank()) return
        val key = title.trim().lowercase()
        memoryThumbnailCache[key] = thumbnailUrl
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(key, thumbnailUrl).apply()
        }
        preloadThumbnail(context, thumbnailUrl, "genre_thumb_$key")
    }

    /**
     * Resolves the album artwork of the first song listed on that genre's detail page.
     * Uses the exact query and scoring logic as the genre's detail page.
     */
    suspend fun resolveFirstSongThumbnail(
        context: Context,
        title: String,
        favoriteArtists: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        // 1. Check local cache first
        getFirstSongThumbnail(context, title)?.let { return@withContext it }

        // 2. Fetch the top songs for this genre
        val query = getGenreSongQuery(title)
        val searchResult = runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()
        val songs = searchResult?.items?.filterIsInstance<SongItem>().orEmpty()

        val firstSong = if (favoriteArtists.isNotEmpty() && songs.isNotEmpty()) {
            songs.firstOrNull { song ->
                favoriteArtists.any { fav -> song.artists.any { it.name.contains(fav, ignoreCase = true) } }
            } ?: songs.firstOrNull()
        } else {
            songs.firstOrNull()
        }

        val thumbnail = firstSong?.thumbnail
        if (!thumbnail.isNullOrBlank()) {
            setFirstSongThumbnail(context, title, thumbnail)
            return@withContext thumbnail
        }

        null
    }

    /**
     * Retrieves cached inner genre category data (songs, playlists, albums).
     */
    fun getCachedCategory(context: Context, title: String): CachedCategoryPayload? {
        val key = title.trim().lowercase()
        memoryPayloadCache[key]?.let { return it }

        return runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR)
            val file = File(dir, "${key.replace("[^a-zA-Z0-9]".toRegex(), "_")}.json")
            if (file.exists()) {
                val json = file.readText()
                val payload = gson.fromJson(json, CachedCategoryPayload::class.java)
                if (payload != null) {
                    memoryPayloadCache[key] = payload
                    payload
                } else null
            } else null
        }.getOrNull()
    }

    /**
     * Checks whether cached category data exists and is still fresh.
     */
    fun isCacheFresh(context: Context, title: String): Boolean {
        val cached = getCachedCategory(context, title) ?: return false
        val age = System.currentTimeMillis() - cached.timestamp
        return age < CACHE_EXPIRY_MS && cached.songs.isNotEmpty()
    }

    /**
     * Persists inner genre category data to local disk and preloads all thumbnail images into Coil.
     */
    fun saveCategory(
        context: Context,
        title: String,
        songs: List<SongItem>,
        featuredPlaylists: List<PlaylistItem>,
        communityPlaylists: List<PlaylistItem>,
        albums: List<AlbumItem>
    ) {
        val key = title.trim().lowercase()
        val firstThumbnail = songs.firstOrNull()?.thumbnail

        val songDtos = songs.map { song ->
            CachedSongDto(
                id = song.id,
                title = song.title,
                artists = song.artists.map { it.name },
                albumTitle = song.album?.name,
                duration = song.duration,
                thumbnail = song.thumbnail,
                explicit = song.explicit
            )
        }

        val featuredDtos = featuredPlaylists.map { p ->
            CachedPlaylistDto(
                id = p.id,
                title = p.title,
                authorName = p.author?.name,
                songCountText = p.songCountText,
                thumbnail = p.thumbnail
            )
        }

        val communityDtos = communityPlaylists.map { p ->
            CachedPlaylistDto(
                id = p.id,
                title = p.title,
                authorName = p.author?.name,
                songCountText = p.songCountText,
                thumbnail = p.thumbnail
            )
        }

        val albumDtos = albums.map { a ->
            CachedAlbumDto(
                browseId = a.browseId,
                playlistId = a.playlistId,
                title = a.title,
                artists = a.artists?.map { it.name }.orEmpty(),
                year = a.year,
                thumbnail = a.thumbnail
            )
        }

        val payload = CachedCategoryPayload(
            title = title,
            timestamp = System.currentTimeMillis(),
            firstSongThumbnail = firstThumbnail,
            songs = songDtos,
            featuredPlaylists = featuredDtos,
            communityPlaylists = communityDtos,
            albums = albumDtos
        )

        memoryPayloadCache[key] = payload

        if (!firstThumbnail.isNullOrBlank()) {
            setFirstSongThumbnail(context, title, firstThumbnail)
        }

        // Save to disk asynchronously
        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${key.replace("[^a-zA-Z0-9]".toRegex(), "_")}.json")
            file.writeText(gson.toJson(payload))
        }

        // Preload thumbnail images locally into Coil's disk cache
        val allThumbnails = mutableListOf<String>()
        songs.mapNotNull { it.thumbnail.takeIf { t -> t.isNotBlank() } }.forEach { allThumbnails.add(it) }
        featuredPlaylists.mapNotNull { it.thumbnail?.takeIf { t -> t.isNotBlank() } }.forEach { allThumbnails.add(it) }
        communityPlaylists.mapNotNull { it.thumbnail?.takeIf { t -> t.isNotBlank() } }.forEach { allThumbnails.add(it) }
        albums.mapNotNull { it.thumbnail.takeIf { t -> t.isNotBlank() } }.forEach { allThumbnails.add(it) }

        preloadThumbnails(context, allThumbnails)
    }

    /**
     * Converts CachedSongDto back into Innertube SongItem.
     */
    fun CachedSongDto.toSongItem(): SongItem = SongItem(
        id = id,
        title = title,
        artists = artists.map { Artist(name = it, id = null) },
        album = albumTitle?.let { Album(name = it, id = "") },
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit
    )

    /**
     * Converts CachedPlaylistDto back into Innertube PlaylistItem.
     */
    fun CachedPlaylistDto.toPlaylistItem(): PlaylistItem = PlaylistItem(
        id = id,
        title = title,
        author = authorName?.let { Artist(name = it, id = null) },
        songCountText = songCountText,
        thumbnail = thumbnail,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null
    )

    /**
     * Converts CachedAlbumDto back into Innertube AlbumItem.
     */
    fun CachedAlbumDto.toAlbumItem(): AlbumItem = AlbumItem(
        browseId = browseId,
        playlistId = playlistId,
        title = title,
        artists = artists.map { Artist(name = it, id = null) },
        year = year,
        thumbnail = thumbnail,
        explicit = false
    )

    /**
     * Preloads an individual image directly into Coil memory and disk cache.
     */
    fun preloadThumbnail(context: Context, url: String, key: String? = null) {
        if (url.isBlank()) return
        runCatching {
            val builder = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)

            if (!key.isNullOrBlank()) {
                builder.memoryCacheKey(key)
                builder.diskCacheKey(key)
            } else {
                builder.memoryCacheKey(url)
                builder.diskCacheKey(url)
            }

            context.imageLoader.enqueue(builder.build())
        }
    }

    /**
     * Preloads a batch of image URLs into Coil disk cache so inner pages display immediately.
     */
    fun preloadThumbnails(context: Context, urls: List<String>) {
        urls.distinct().take(30).forEach { url ->
            preloadThumbnail(context, url)
        }
    }
}
