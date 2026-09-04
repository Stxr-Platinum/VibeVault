package com.vibevault.app.data.stats

import android.content.Context
import android.net.Uri
import android.util.Log
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * What the Replay knows about an artist beyond their name: picture, browseId, and genres.
 */
object ArtistFacts {

    private lateinit var file: File

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val httpClient by lazy { OkHttpClient() }

    private val known = ConcurrentHashMap<String, StoredArtist>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val requests = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var dirty = false

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val ready: Boolean get() = this::file.isInitialized

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        load()
        scope.launch { worker() }
        scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                save()
            }
        }
    }

    val genresAvailable: Boolean = true

    fun imageFor(artist: String): String? = known[key(artist)]?.image
    fun browseIdFor(artist: String): String? = known[key(artist)]?.browseId
    fun genresFor(artist: String): List<String> = known[key(artist)]?.genres.orEmpty()

    fun noticed(artist: String) {
        if (!ready) return
        val trimmed = artist.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return
        val k = key(trimmed)
        val entry = known[k]
        if (entry != null && !wantsWork(entry)) return
        if (!queued.add(k)) return
        requests.trySend(trimmed)
    }

    private fun wantsWork(entry: StoredArtist): Boolean {
        val wantsCard = entry.browseId == null &&
            System.currentTimeMillis() - entry.cardAt > TimeUnit.DAYS.toMillis(RETRY_DAYS)
        val wantsGenres = entry.genres.isEmpty() &&
            System.currentTimeMillis() - entry.genresAt > TimeUnit.DAYS.toMillis(RETRY_DAYS)
        return wantsCard || wantsGenres
    }

    private suspend fun worker() {
        for (name in requests) {
            val existing = known[key(name)]
            if (existing?.browseId == null) {
                runCatching { fetchCard(name) }
                    .onFailure { Log.w(TAG, "Artist lookup failed for $name", it) }
            }
            if (existing?.genres.isNullOrEmpty()) {
                runCatching { fetchGenres(name) }
                    .onFailure { Log.w(TAG, "Genre lookup failed for $name", it) }
            }
            _revision.value++
            delay(REQUEST_SPACING_MS)
        }
    }

    private suspend fun fetchCard(name: String) {
        val hit = runCatching {
            YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                ?.items
                ?.filterIsInstance<ArtistItem>()
                ?.firstOrNull()
        }.getOrNull()

        val entry = known[key(name)] ?: StoredArtist(key = key(name))
        known[key(name)] = entry.copy(
            image = hit?.thumbnail?.takeIf { hit.title.equals(name, ignoreCase = true) } ?: entry.image,
            browseId = hit?.id?.takeIf { hit.title.equals(name, ignoreCase = true) } ?: entry.browseId,
            cardAt = System.currentTimeMillis(),
        )
        dirty = true
    }

    private fun fetchGenres(name: String) {
        // Last.fm open API fallback key
        val apiKey = "b25b959554ed76058ac220b7b2e0a026"
        val url = "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptags" +
            "&artist=${Uri.encode(name)}" +
            "&api_key=$apiKey" +
            "&autocorrect=1&format=json"
        val request = Request.Builder().url(url).header("User-Agent", "VibeVault/1.0").build()
        val body = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string()
            }
        }.getOrNull() ?: return

        val tags = runCatching {
            Json.parseToJsonElement(body).jsonObject["toptags"]
                ?.jsonObject?.get("tag")?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrNull().orEmpty()

        val genres = tags.asSequence()
            .mapNotNull { canonical(it) }
            .distinct()
            .take(MAX_GENRES_PER_ARTIST)
            .toList()

        val entry = known[key(name)] ?: StoredArtist(key = key(name))
        known[key(name)] = entry.copy(genres = genres, genresAt = System.currentTimeMillis())
        dirty = true
    }

    private fun canonical(tag: String): String? {
        val cleaned = tag.trim().lowercase(Locale.ROOT)
            .replace('-', ' ')
            .replace("&", "and")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return null
        return VOCABULARY[cleaned] ?: VOCABULARY[ALIASES[cleaned] ?: return null]
    }

    private fun key(artist: String): String = artist.trim().lowercase(Locale.ROOT)

    private fun load() {
        if (!ready || !file.exists()) return
        runCatching {
            json.decodeFromString(Stored.serializer(), file.readText())
        }.onSuccess { stored ->
            stored.artists.forEach { known[it.key] = it }
        }.onFailure {
            Log.w(TAG, "Discarding unreadable artist cache", it)
            file.delete()
        }
    }

    private fun save() {
        if (!ready || !dirty) return
        dirty = false
        runCatching {
            val stored = Stored(
                artists = known.values
                    .sortedByDescending { maxOf(it.cardAt, it.genresAt) }
                    .take(MAX_ARTISTS),
            )
            file.writeText(json.encodeToString(Stored.serializer(), stored))
        }.onFailure { Log.w(TAG, "Could not write artist cache", it) }
    }

    @Serializable
    private data class Stored(val version: Int = 2, val artists: List<StoredArtist> = emptyList())

    @Serializable
    data class StoredArtist(
        val key: String,
        val genres: List<String> = emptyList(),
        val genresAt: Long = 0L,
        val image: String? = null,
        val browseId: String? = null,
        val cardAt: Long = 0L,
    )

    private const val TAG = "ArtistFacts"
    private const val FILE_NAME = "artist_facts.json"
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_GENRES_PER_ARTIST = 2
    private const val MAX_ARTISTS = 4_000
    private const val REQUEST_SPACING_MS = 1_500L
    private const val SAVE_INTERVAL_MS = 5_000L
    private const val RETRY_DAYS = 14L

    private val SPELLINGS = mapOf(
        "randb" to "R&B",
        "edm" to "EDM",
        "lo fi" to "Lo-Fi",
        "hip hop" to "Hip-Hop",
        "k pop" to "K-Pop",
        "j pop" to "J-Pop",
        "j rock" to "J-Rock",
        "c pop" to "C-Pop",
        "drum and bass" to "Drum & Bass",
        "singer songwriter" to "Singer-Songwriter",
        "post punk" to "Post-Punk",
        "post rock" to "Post-Rock",
        "bossa nova" to "Bossa Nova",
    )

    private val VOCABULARY: Map<String, String> = listOf(
        "pop", "rock", "hip hop", "rap", "randb", "soul", "funk", "jazz", "blues",
        "country", "folk", "indie", "indie pop", "indie rock", "alternative",
        "alternative rock", "metal", "heavy metal", "punk", "punk rock", "hardcore",
        "electronic", "house", "deep house", "techno", "trance", "dubstep",
        "drum and bass", "edm", "ambient", "lo fi", "synthpop", "disco",
        "classical", "opera", "soundtrack", "instrumental", "acoustic",
        "reggae", "reggaeton", "dancehall", "ska", "latin", "salsa", "bossa nova",
        "afrobeats", "afrobeat", "k pop", "j pop", "j rock", "c pop",
        "bollywood", "punjabi", "desi", "bhangra", "hindi", "sufi", "ghazal",
        "singer songwriter", "emo", "grunge", "shoegaze", "psychedelic",
        "progressive rock", "hard rock", "garage rock", "post punk", "new wave",
        "gospel", "christian", "world", "experimental", "trap", "drill", "grime",
        "phonk", "hyperpop", "chillout", "downtempo", "jungle", "garage",
        "bluegrass", "americana", "swing", "big band", "motown", "britpop",
        "dream pop", "art pop", "noise", "industrial", "gothic", "doom metal",
        "black metal", "death metal", "thrash metal", "metalcore", "post rock",
        "math rock", "jam band", "surf rock", "rockabilly", "boom bap",
        "cloud rap", "conscious hip hop", "west coast rap", "east coast rap",
    ).associateWith { normalised ->
        SPELLINGS[normalised] ?: normalised.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

    private val ALIASES = mapOf(
        "rnb" to "randb",
        "r and b" to "randb",
        "rhythm and blues" to "randb",
        "contemporary randb" to "randb",
        "hiphop" to "hip hop",
        "hip hop rap" to "hip hop",
        "lofi" to "lo fi",
        "lo fi hip hop" to "lo fi",
        "chillhop" to "lo fi",
        "kpop" to "k pop",
        "jpop" to "j pop",
        "jrock" to "j rock",
        "cpop" to "c pop",
        "korean" to "k pop",
        "dnb" to "drum and bass",
        "drum n bass" to "drum and bass",
        "drumandbass" to "drum and bass",
        "electronica" to "electronic",
        "electro" to "electronic",
        "dance" to "electronic",
        "electropop" to "synthpop",
        "synth pop" to "synthpop",
        "indierock" to "indie rock",
        "indiepop" to "indie pop",
        "alt rock" to "alternative rock",
        "altrock" to "alternative rock",
        "singersongwriter" to "singer songwriter",
        "female vocalists" to "pop",
        "hindi pop" to "bollywood",
        "indian" to "desi",
        "filmi" to "bollywood",
        "afro beats" to "afrobeats",
        "afropop" to "afrobeats",
        "amapiano" to "afrobeats",
        "regueton" to "reggaeton",
        "latin pop" to "latin",
        "trip hop" to "downtempo",
        "nu metal" to "metal",
        "classic rock" to "rock",
        "soft rock" to "rock",
        "pop rock" to "rock",
        "pop punk" to "punk",
        "hardcore punk" to "hardcore",
        "orchestral" to "classical",
        "film score" to "soundtrack",
        "score" to "soundtrack",
        "ost" to "soundtrack",
        "chill" to "chillout",
        "chillwave" to "chillout",
        "worship" to "christian",
        "rap rock" to "rap",
        "gangsta rap" to "rap",
        "underground hip hop" to "hip hop",
    )
}
