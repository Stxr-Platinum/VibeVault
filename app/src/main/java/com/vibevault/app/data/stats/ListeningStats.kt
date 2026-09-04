package com.vibevault.app.data.stats

import android.content.Context
import android.util.Log
import com.vibevault.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Local aggregated listening statistics engine for Your Replay.
 */
object ListeningStats {

    private lateinit var directory: File

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var open: OpenBucket? = null
    private val lock = Any()
    private var dirty = false

    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    @Volatile
    private var version = 0L

    private var cached: Cached? = null

    private class Cached(
        val period: ReplayPeriod,
        val version: Long,
        val facts: Int,
        val day: LocalDate,
        val summary: ReplaySummary,
    )

    fun init(context: Context) {
        directory = File(context.filesDir, DIRECTORY)
        ArtistFacts.init(context)
        writer.launch { synchronized(lock) { bucketFor(YearMonth.now()) } }
    }

    private val ready: Boolean get() = this::directory.isInitialized

    fun record(track: Track, playedMs: Long, countsAsPlay: Boolean) {
        if (!ready) return
        if (playedMs <= 0 && !countsAsPlay) return
        if (track.title.isBlank() && track.id.isBlank()) return
        val now = System.currentTimeMillis()
        val at = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        synchronized(lock) {
            val bucket = bucketFor(YearMonth.from(at))
            val key = songKey(track.title, track.artist).ifBlank { track.id }
            val entry = bucket.tracks.getOrPut(key) {
                TrackEntry(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    art = track.albumImageUrl,
                )
            }
            entry.ms += playedMs
            entry.last = now
            if (entry.id.isBlank() && track.id.isNotBlank()) entry.id = track.id
            if (entry.album == null && track.album.isNotBlank()) entry.album = track.album
            if (entry.art == null && track.albumImageUrl.isNotBlank()) entry.art = track.albumImageUrl
            if (countsAsPlay) entry.plays++

            primaryArtist(track.artist)?.let { name ->
                ArtistFacts.noticed(name)
                val artist = bucket.artists.getOrPut(name.lowercase(Locale.ROOT)) {
                    NameEntry(name = name, art = track.albumImageUrl)
                }
                artist.ms += playedMs
                if (countsAsPlay) artist.plays++
            }

            track.album.trim().takeIf { it.isNotEmpty() && it != "Unknown" }?.let { name ->
                val album = bucket.albums.getOrPut(albumKey(name, track.artist)) {
                    NameEntry(name = name, sub = track.artist, art = track.albumImageUrl)
                }
                album.ms += playedMs
                if (countsAsPlay) album.plays++
            }

            bucket.hours[at.hour] += playedMs
            val day = at.dayOfMonth
            bucket.days[day] = (bucket.days[day] ?: 0L) + playedMs
            dirty = true
        }
    }

    fun flush() {
        pending()?.let { (key, snapshot) -> writer.launch { write(key, snapshot) } }
    }

    private suspend fun flushAndAwait() {
        pending()?.let { (key, snapshot) -> writer.launch { write(key, snapshot) }.join() }
    }

    private fun pending(): Pair<String, StoredBucket>? {
        if (!ready) return null
        return synchronized(lock) {
            if (!dirty) return null
            val bucket = open ?: return null
            dirty = false
            bucket.key to bucket.snapshot()
        }
    }

    private suspend fun write(key: String, snapshot: StoredBucket) = writeLock.withLock {
        runCatching {
            directory.mkdirs()
            val file = File(directory, "$key.json")
            val temporary = File(directory, "$key.json.tmp")
            temporary.writeText(json.encodeToString(StoredBucket.serializer(), snapshot))
            if (!temporary.renameTo(file)) {
                if (file.delete() && temporary.renameTo(file)) Unit else temporary.delete()
            }
            version++
        }.onFailure { Log.w(TAG, "Could not write listening bucket $key", it) }
    }

    private fun bucketFor(month: YearMonth): OpenBucket {
        val key = month.toString()
        open?.takeIf { it.key == key }?.let { return it }
        open?.let { previous ->
            val stale = previous.snapshot()
            writer.launch { write(previous.key, stale) }
        }
        val loaded = read(key) ?: StoredBucket(month = key)
        return OpenBucket.of(key, loaded).also {
            open = it
            prune()
        }
    }

    suspend fun summary(period: ReplayPeriod): ReplaySummary = withContext(Dispatchers.IO) {
        flushAndAwait()
        val today = LocalDate.now()
        val facts = ArtistFacts.revision.value
        cached?.takeIf {
            it.period == period && it.version == version && it.facts == facts && it.day == today
        }?.let { return@withContext it.summary }

        val merged = MergedBucket()
        months().filter { period.covers(it, today) }
            .forEach { month -> read(month.toString())?.let(merged::add) }
        merged.toSummary(period, today).also {
            cached = Cached(period, version, facts, today, it)
        }
    }

    fun months(): List<YearMonth> {
        if (!ready) return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            file.name.removeSuffix(".json").takeIf { it != file.name }
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        }.sorted()
    }

    private fun read(key: String): StoredBucket? {
        val file = File(directory, "$key.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(StoredBucket.serializer(), file.readText()) }
            .onFailure { Log.w(TAG, "Discarding unreadable listening bucket $key", it) }
            .getOrNull()
    }

    private fun prune() {
        val existing = months()
        if (existing.size > KEEP_MONTHS) {
            existing.take(existing.size - KEEP_MONTHS).forEach {
                File(directory, "$it.json").delete()
            }
        }
        val bucket = open ?: return
        bucket.tracks.trimTo(MAX_TRACKS) { it.ms }
        bucket.artists.trimTo(MAX_NAMES) { it.ms }
        bucket.albums.trimTo(MAX_NAMES) { it.ms }
    }

    private inline fun <T : Any> List<T>.mergedBy(
        into: LinkedHashMap<String, T>,
        key: (T) -> String,
    ): LinkedHashMap<String, T> {
        forEach { entry ->
            val k = key(entry)
            val existing = into[k]
            if (existing == null) {
                into[k] = entry
            } else {
                @Suppress("UNCHECKED_CAST")
                (existing as NameEntry).absorb(entry as NameEntry)
            }
        }
        return into
    }

    private fun List<TrackEntry>.mergedTracks(
        into: LinkedHashMap<String, TrackEntry> = LinkedHashMap(),
    ): LinkedHashMap<String, TrackEntry> {
        forEach { entry ->
            val k = songKey(entry.title, entry.artist).ifBlank { entry.id }
            val existing = into[k]
            if (existing == null) {
                into[k] = entry.copy()
            } else {
                existing.absorb(entry)
            }
        }
        return into
    }

    private inline fun <K, V> MutableMap<K, V>.trimTo(limit: Int, crossinline weight: (V) -> Long) {
        if (size <= limit) return
        entries.sortedBy { weight(it.value) }
            .take(size - limit)
            .forEach { remove(it.key) }
    }

    private class OpenBucket(
        val key: String,
        val tracks: LinkedHashMap<String, TrackEntry>,
        val artists: LinkedHashMap<String, NameEntry>,
        val albums: LinkedHashMap<String, NameEntry>,
        val hours: LongArray,
        val days: MutableMap<Int, Long>,
    ) {
        fun snapshot(): StoredBucket = StoredBucket(
            month = key,
            tracks = tracks.values.toList(),
            artists = artists.values.toList(),
            albums = albums.values.toList(),
            hours = hours.toList(),
            days = days.toMap(),
        )

        companion object {
            fun of(key: String, stored: StoredBucket): OpenBucket = OpenBucket(
                key = key,
                tracks = stored.tracks.mergedTracks(LinkedHashMap()),
                artists = stored.artists
                    .map { it.copy(name = primaryArtist(it.name) ?: it.name) }
                    .mergedBy(LinkedHashMap()) { it.name.lowercase(Locale.ROOT) },
                albums = stored.albums.mergedBy(LinkedHashMap()) { albumKey(it.name, it.sub.orEmpty()) },
                hours = LongArray(24) { stored.hours.getOrElse(it) { 0L } },
                days = stored.days.toMutableMap(),
            )
        }
    }

    private class MergedBucket {
        val tracks = HashMap<String, TrackEntry>()
        val artists = HashMap<String, NameEntry>()
        val albums = HashMap<String, NameEntry>()
        val hours = LongArray(24)
        val days = HashMap<String, Long>()
        var earliest: String? = null

        fun add(bucket: StoredBucket) {
            bucket.tracks.forEach { entry ->
                val k = songKey(entry.title, entry.artist).ifBlank { entry.id }
                tracks.merge(k, entry.copy()) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.artists.forEach { entry ->
                val lead = entry.copy(name = primaryArtist(entry.name) ?: entry.name)
                artists.merge(lead.name.lowercase(Locale.ROOT), lead) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.albums.forEach { entry ->
                val key = albumKey(entry.name, entry.sub.orEmpty())
                albums.merge(key, entry.copy()) { a, b -> a.also { it.absorb(b) } }
            }
            repeat(24) { hours[it] += bucket.hours.getOrElse(it) { 0L } }
            bucket.days.forEach { (day, ms) ->
                val date = "${bucket.month}-%02d".format(day)
                days[date] = (days[date] ?: 0L) + ms
            }
            if (earliest == null || bucket.month < earliest!!) earliest = bucket.month
        }

        fun toSummary(period: ReplayPeriod, today: LocalDate): ReplaySummary {
            val mergedSongs = LinkedHashMap<String, TrackEntry>()
            tracks.values.forEach { entry ->
                val k = songKey(entry.title, entry.artist).ifBlank { entry.id }
                val existing = mergedSongs[k]
                if (existing == null) {
                    mergedSongs[k] = entry.copy()
                } else {
                    existing.absorb(entry)
                }
            }

            val rankedSongs = mergedSongs.values
                .sortedWith(compareByDescending<TrackEntry> { it.ms }.thenByDescending { it.plays })
                .map {
                    RankedSong(
                        song = Track(
                            id = it.id,
                            title = it.title,
                            artist = it.artist,
                            album = it.album ?: "",
                            albumImageUrl = it.art ?: "",
                            durationMs = it.ms,
                        ),
                        ms = it.ms,
                        plays = it.plays,
                    )
                }

            val rankedArtists = artists.values
                .sortedWith(compareByDescending<NameEntry> { it.ms }.thenByDescending { it.plays })
                .map {
                    RankedEntry(
                        title = it.name,
                        subtitle = it.sub,
                        artworkUrl = ArtistFacts.imageFor(it.name) ?: it.art,
                        browseId = it.id ?: ArtistFacts.browseIdFor(it.name),
                        ms = it.ms,
                        plays = it.plays,
                    )
                }

            val rankedAlbums = albums.values
                .sortedWith(compareByDescending<NameEntry> { it.ms }.thenByDescending { it.plays })
                .map { RankedEntry(it.name, it.sub, it.art, it.id, it.ms, it.plays) }

            val genreMs = LinkedHashMap<String, Long>()
            val genrePlays = LinkedHashMap<String, Int>()
            val genreArt = LinkedHashMap<String, String?>()
            rankedArtists.forEach { artist ->
                ArtistFacts.genresFor(artist.title).forEach { genre ->
                    genreMs[genre] = (genreMs[genre] ?: 0L) + artist.ms
                    genrePlays[genre] = (genrePlays[genre] ?: 0) + artist.plays
                    if (genreArt[genre] == null) genreArt[genre] = artist.artworkUrl
                }
            }

            rankedArtists.take(ARTISTS_TO_RESOLVE)
                .filter { it.artworkUrl == null || it.browseId == null }
                .forEach { ArtistFacts.noticed(it.title) }

            val rankedGenres = genreMs.entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenByDescending { genrePlays[it.key] ?: 0 })
                .map {
                    RankedEntry(
                        title = it.key,
                        subtitle = null,
                        artworkUrl = genreArt[it.key],
                        browseId = null,
                        ms = it.value,
                        plays = genrePlays[it.key] ?: 0,
                    )
                }

            val busiest = days.maxByOrNull { it.value }

            return ReplaySummary(
                period = period,
                label = period.label(today),
                totalMs = hours.sum(),
                totalPlays = rankedSongs.sumOf { it.plays },
                songs = rankedSongs,
                artists = rankedArtists,
                albums = rankedAlbums,
                genres = rankedGenres,
                hourOfDay = hours.toList(),
                busiestDay = busiest?.key,
                busiestDayMs = busiest?.value ?: 0L,
                distinctSongs = rankedSongs.size,
                distinctArtists = rankedArtists.size,
                distinctAlbums = rankedAlbums.size,
                since = earliest,
            )
        }
    }

    fun songKey(title: String, artist: String): String {
        val t = cleanTitle(title)
        val a = primaryArtist(artist)?.lowercase(Locale.ROOT) ?: artist.trim().lowercase(Locale.ROOT)
        return if (t.isNotBlank()) "$t$TRACK_KEY_SEPARATOR$a" else title.trim().lowercase(Locale.ROOT)
    }

    private fun cleanTitle(title: String): String {
        return title.trim().lowercase(Locale.ROOT)
            .replace(CLEAN_TITLE_REGEX, "")
            .trim()
    }

    private val CLEAN_TITLE_REGEX = Regex(
        """\s*[\(\[](official\s+(audio|video|music\s+video|lyric\s+video|visualizer|remaster(ed)?)|audio|video|lyrics|explicit|clean)[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    private val TRACK_KEY_SEPARATOR = Char(30).toString()

    private fun albumKey(album: String, artist: String): String =
        "${album.trim().lowercase(Locale.ROOT)}$ALBUM_KEY_SEPARATOR${primaryArtist(artist)?.lowercase(Locale.ROOT) ?: ""}"

    private val ALBUM_KEY_SEPARATOR = Char(31).toString()

    fun primaryArtist(credit: String): String? {
        lastCredit?.let { (raw, name) -> if (raw == credit) return name }
        val name = credit.split(CREDIT_SEPARATORS)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        lastCredit = credit to name
        return name
    }

    @Volatile
    private var lastCredit: Pair<String, String?>? = null

    private val CREDIT_SEPARATORS = Regex(
        """\s*,\s*|\s+&\s+|\s+x\s+|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+""",
        RegexOption.IGNORE_CASE,
    )

    private const val TAG = "ListeningStats"
    private const val DIRECTORY = "listening"
    private const val ARTISTS_TO_RESOLVE = 15
    private const val KEEP_MONTHS = 36
    private const val MAX_TRACKS = 600
    private const val MAX_NAMES = 400
}

@Serializable
data class TrackEntry(
    var id: String = "",
    val title: String = "",
    val artist: String = "",
    var album: String? = null,
    var albumId: String? = null,
    var artistId: String? = null,
    var art: String? = null,
    var ms: Long = 0L,
    var plays: Int = 0,
    var last: Long = 0L,
) {
    fun absorb(other: TrackEntry) {
        ms += other.ms
        plays += other.plays
        if (id.isBlank() && other.id.isNotBlank()) id = other.id
        if (other.last > last) {
            last = other.last
            if (other.id.isNotBlank()) id = other.id
        }
        if (album == null) album = other.album
        if (albumId == null) albumId = other.albumId
        if (artistId == null) artistId = other.artistId
        if (art == null) art = other.art
    }
}

@Serializable
data class NameEntry(
    val name: String = "",
    val sub: String? = null,
    var art: String? = null,
    var id: String? = null,
    var ms: Long = 0L,
    var plays: Int = 0,
    val key: String? = null,
) {
    fun absorb(other: NameEntry) {
        ms += other.ms
        plays += other.plays
        if (art == null) art = other.art
        if (id == null) id = other.id
    }
}

@Serializable
data class StoredBucket(
    val version: Int = 1,
    val month: String,
    val tracks: List<TrackEntry> = emptyList(),
    val artists: List<NameEntry> = emptyList(),
    val albums: List<NameEntry> = emptyList(),
    val hours: List<Long> = List(24) { 0L },
    val days: Map<Int, Long> = emptyMap(),
)

data class RankedEntry(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val browseId: String?,
    val ms: Long,
    val plays: Int,
)

data class RankedSong(val song: Track, val ms: Long, val plays: Int)

enum class ReplayPeriod(val chip: String) {
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    ;

    fun covers(month: YearMonth, today: LocalDate): Boolean = when (this) {
        THIS_MONTH -> month == YearMonth.from(today)
        THIS_YEAR -> month.year == today.year
        ALL_TIME -> true
    }

    fun label(today: LocalDate): String = when (this) {
        THIS_MONTH -> YearMonth.from(today).month.name.lowercase(Locale.ROOT)
            .replaceFirstChar { it.uppercase(Locale.ROOT) } + " ${today.year}"
        THIS_YEAR -> today.year.toString()
        ALL_TIME -> "All time"
    }
}

data class ReplaySummary(
    val period: ReplayPeriod,
    val label: String,
    val totalMs: Long,
    val totalPlays: Int,
    val songs: List<RankedSong>,
    val artists: List<RankedEntry>,
    val albums: List<RankedEntry>,
    val genres: List<RankedEntry>,
    val hourOfDay: List<Long>,
    val busiestDay: String?,
    val busiestDayMs: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val distinctAlbums: Int,
    val since: String?,
) {
    val minutes: Long get() = totalMs / 60_000
    val hours: Long get() = totalMs / 3_600_000
    val isEmpty: Boolean get() = songs.isEmpty()
    val peakHour: Int? get() = hourOfDay.withIndex()
        .filter { it.value > 0 }
        .maxByOrNull { it.value }
        ?.index
}
