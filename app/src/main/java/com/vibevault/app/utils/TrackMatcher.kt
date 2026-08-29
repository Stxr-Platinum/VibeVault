package com.vibevault.app.utils

import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import java.util.Locale
import kotlin.math.abs

object TrackMatcher {
    val VERSION_KEYWORDS = setOf(
        "slowed", "reverb", "remix", "edit", "nightcore", "speedup", "speed up",
        "spedup", "sped up", "acoustic", "cover", "live", "bootleg", "extended",
        "instrumental", "bassboosted", "bass boosted", "lofi", "lo-fi", "8d",
        "flip", "rework", "dub", "mashup", "vip", "mix", "performance", "concert",
        "session", "tour", "live at", "snl", "vma", "grammy", "acapella"
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

    fun scoreMatch(
        query: String,
        itemTitle: String,
        itemAuthor: String?,
        isSongItem: Boolean = false,
        candidateDurationSec: Int? = null,
        expectedDurationSec: Int? = null
    ): Int {
        val qNorm = normalizeText(query)
        val tNorm = normalizeText(itemTitle)
        val aNorm = normalizeText(itemAuthor.orEmpty())

        val qTokens = qNorm.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
        if (qTokens.isEmpty()) return 0

        var score = 0

        // Bonus for official SongItem over VideoItem
        if (isSongItem) {
            score += 40
        }

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
            } else {
                // If query does NOT ask for live/remix/cover/concert, but item title HAS it, penalize heavily
                if (tNorm.contains(kw)) {
                    score -= 60
                }
            }
        }

        val cleanQuery = qNorm.replace("//", "").replace("by ", "").trim()
        if (tNorm.contains(cleanQuery)) {
            score += 40
        }

        // Duration matching score
        if (expectedDurationSec != null && expectedDurationSec > 0 && candidateDurationSec != null && candidateDurationSec > 0) {
            val diff = abs(expectedDurationSec - candidateDurationSec)
            when {
                diff <= 3 -> score += 35
                diff <= 10 -> score += 15
                diff <= 30 -> score -= 20
                diff <= 60 -> score -= 50
                else -> score -= 100
            }
        }

        return score
    }

    fun selectBestMatch(query: String, items: List<YTItem>, expectedDurationSec: Int? = null): YTItem? {
        if (items.isEmpty()) return null

        val scored = items.map { item ->
            val title = item.title
            val author = when (item) {
                is SongItem -> item.artists.firstOrNull()?.name
                else -> item.title
            }
            val durationSec = when (item) {
                is SongItem -> item.duration
                else -> null
            }
            val isSong = item is SongItem
            item to scoreMatch(
                query = query,
                itemTitle = title,
                itemAuthor = author,
                isSongItem = isSong,
                candidateDurationSec = durationSec,
                expectedDurationSec = expectedDurationSec
            )
        }

        return scored.maxByOrNull { it.second }?.first ?: items.first()
    }
}
