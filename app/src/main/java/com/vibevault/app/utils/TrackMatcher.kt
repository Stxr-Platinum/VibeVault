package com.vibevault.app.utils

import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import java.util.Locale

object TrackMatcher {
    val VERSION_KEYWORDS = setOf(
        "slowed", "reverb", "remix", "edit", "nightcore", "speedup", "speed up",
        "spedup", "sped up", "acoustic", "cover", "live", "bootleg", "extended",
        "instrumental", "bassboosted", "bass boosted", "lofi", "lo-fi", "8d",
        "flip", "rework", "dub", "mashup", "vip", "mix"
    )

    fun normalizeText(text: String): String {
        var lower = text.lowercase(Locale.US)
        if (lower.contains("weekend") && !lower.contains("weeknd")) {
            lower = lower.replace("weekend", "weeknd")
        }
        return lower
    }

    fun hasVersionModifier(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return VERSION_KEYWORDS.any { lower.contains(it) }
    }

    fun scoreMatch(query: String, itemTitle: String, itemAuthor: String?): Int {
        val qNorm = normalizeText(query)
        val tNorm = normalizeText(itemTitle)
        val aNorm = normalizeText(itemAuthor.orEmpty())

        val qTokens = qNorm.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
        if (qTokens.isEmpty()) return 0

        var score = 0

        for (token in qTokens) {
            if (tNorm.contains(token)) score += 10
            if (aNorm.contains(token)) score += 5
        }

        for (kw in VERSION_KEYWORDS) {
            if (qNorm.contains(kw)) {
                if (tNorm.contains(kw)) {
                    score += 50
                } else {
                    score -= 30
                }
            }
        }

        val cleanQuery = qNorm.replace("//", "").replace("by ", "").trim()
        if (tNorm.contains(cleanQuery)) {
            score += 40
        }

        return score
    }

    fun selectBestMatch(query: String, items: List<YTItem>): YTItem? {
        if (items.isEmpty()) return null

        val scored = items.map { item ->
            val title = item.title
            val author = when (item) {
                is SongItem -> item.artists.firstOrNull()?.name
                else -> item.title
            }
            item to scoreMatch(query, title, author)
        }

        return scored.maxByOrNull { it.second }?.first ?: items.first()
    }
}
