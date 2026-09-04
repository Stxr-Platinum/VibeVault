package com.vibevault.app.data.lyrics

/**
 * Central registry for all lyrics providers.
 */
object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "Musixmatch"      to MusixmatchLyricsProvider,
        "Unison"          to UnisonLyricsProvider,
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxSenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun getDefaultProviderOrder(): List<String> = listOf(
        "Musixmatch",
        "Unison",
        "YouLyPlus",
        "Paxsenix",
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    fun getOrderedProviders(orderString: String = ""): List<LyricsProvider> =
        if (orderString.isBlank()) {
            getDefaultProviderOrder().mapNotNull { getProviderByName(it) }
        } else {
            deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }
        }

    fun getDisplayName(name: String): String = when (name) {
        "Musixmatch"      -> "Musixmatch"
        "Unison"          -> "Unison"
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Better Lyrics"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        else              -> name
    }
}
