package com.vibevault.app.data.lyrics

/**
 * Central registry for all lyrics providers.
 * Simplified from vivi-music to remove DataStore dependency.
 */
object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxSenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "Musixmatch"      to MusixmatchLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getDefaultProviderOrder(): List<String> = listOf(
        "Musixmatch",
        "YouLyPlus",
        "Paxsenix",
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    fun getOrderedProviders(): List<LyricsProvider> =
        getDefaultProviderOrder().mapNotNull { getProviderByName(it) }
}
