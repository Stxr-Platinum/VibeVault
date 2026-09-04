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
        "session", "tour", "live at", "snl", "vma", "grammy", "acapella", "karaoke",
        "tribute", "parody"
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
        expectedDurationSec: Int? = null,
        targetTitle: String? = null,
        targetArtist: String? = null
    ): Int {
        val qNorm = normalizeText(query)
        val tNorm = normalizeText(itemTitle)
        val aNorm = normalizeText(itemAuthor.orEmpty())

        val tTargetNorm = targetTitle?.let { normalizeText(it) }?.takeIf { 
            it.isNotBlank() && !it.startsWith("track ") 
        }
        val aTargetNorm = targetArtist?.let { normalizeText(it) }?.takeIf { 
            it.isNotBlank() && it != "unknown" && it != "unknown artist" 
        }

        var score = 0

        // Bonus for official SongItem over VideoItem
        if (isSongItem) {
            score += 50
        }

        // 1. Target Artist Matching (Strongest signal against covers / wrong artists)
        if (aTargetNorm != null) {
            val targetArtistTokens = aTargetNorm.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
            val matchedArtistTokens = targetArtistTokens.count { aNorm.contains(it) }
            val authorMatches = targetArtistTokens.isNotEmpty() && matchedArtistTokens == targetArtistTokens.size
            val authorPartial = targetArtistTokens.isNotEmpty() && matchedArtistTokens > 0

            if (authorMatches) {
                score += 160 // Candidate author matches target artist
            } else if (authorPartial) {
                score += 80
            } else {
                // If candidate artist doesn't match, check if target artist is in candidate title (e.g. "Kavinsky - Nightcall")
                val titleMatchedArtistTokens = targetArtistTokens.count { tNorm.contains(it) }
                if (targetArtistTokens.isNotEmpty() && titleMatchedArtistTokens == targetArtistTokens.size) {
                    score += 60
                } else if (titleMatchedArtistTokens > 0) {
                    score += 20
                } else {
                    // Heavy penalty for wrong artist / cover band (e.g. London Grammar when target is Kavinsky)
                    score -= 200
                }
            }
        }

        // 2. Target Title Matching
        if (tTargetNorm != null) {
            val cleanCandidateTitle = tNorm
                .replace(Regex("\\(feat\\..*?\\)"), "")
                .replace(Regex("\\[feat\\..*?\\]"), "")
                .replace(Regex("\\(with.*?\\)"), "")
                .replace(Regex("- single version"), "")
                .replace(Regex("- remastered.*?$"), "")
                .replace(Regex("\\(remastered.*?\\)"), "")
                .trim()

            val cleanTargetTitle = tTargetNorm
                .replace(Regex("\\(feat\\..*?\\)"), "")
                .replace(Regex("\\[feat\\..*?\\]"), "")
                .replace(Regex("\\(with.*?\\)"), "")
                .replace(Regex("- single version"), "")
                .replace(Regex("- remastered.*?$"), "")
                .replace(Regex("\\(remastered.*?\\)"), "")
                .trim()

            if (cleanCandidateTitle == cleanTargetTitle || tNorm == tTargetNorm || cleanCandidateTitle == tTargetNorm) {
                score += 100 // Exact title match
            } else if (cleanCandidateTitle.startsWith(cleanTargetTitle) || tNorm.startsWith(cleanTargetTitle)) {
                score += 70
            } else {
                val targetTitleTokens = cleanTargetTitle.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
                val matchedTitleTokens = targetTitleTokens.count { tNorm.contains(it) }
                if (targetTitleTokens.isNotEmpty() && matchedTitleTokens == targetTitleTokens.size) {
                    score += 50
                } else if (matchedTitleTokens > 0) {
                    score += matchedTitleTokens * 10
                }
            }
        }

        // 3. Query Token Matching
        val qTokens = qNorm.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
        for (token in qTokens) {
            if (tNorm.contains(token)) score += 10
            if (aNorm.contains(token)) score += 10
        }

        // 4. Version / Modifier Filtering
        for (kw in VERSION_KEYWORDS) {
            val queryHasKw = qNorm.contains(kw) || (tTargetNorm != null && tTargetNorm.contains(kw))
            if (queryHasKw) {
                if (tNorm.contains(kw)) {
                    score += 60
                } else {
                    score -= 40
                }
            } else {
                // If query does NOT ask for live/remix/cover/concert, but item title HAS it, penalize heavily
                if (tNorm.contains(kw)) {
                    score -= 150
                }
            }
        }

        // 5. Duration matching score
        if (expectedDurationSec != null && expectedDurationSec > 0 && candidateDurationSec != null && candidateDurationSec > 0) {
            val diff = abs(expectedDurationSec - candidateDurationSec)
            when {
                diff <= 2 -> score += 80
                diff <= 5 -> score += 50
                diff <= 10 -> score += 25
                diff <= 20 -> score += 5
                diff <= 35 -> score -= 30
                diff <= 60 -> score -= 80
                else -> score -= 150
            }
        }

        return score
    }

    fun selectBestMatch(
        query: String,
        items: List<YTItem>,
        expectedDurationSec: Int? = null,
        targetTitle: String? = null,
        targetArtist: String? = null
    ): YTItem? {
        if (items.isEmpty()) return null

        val scored = items.map { item ->
            val title = item.title
            val author = when (item) {
                is SongItem -> item.artists.joinToString(" ") { it.name }
                else -> ""
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
                expectedDurationSec = expectedDurationSec,
                targetTitle = targetTitle,
                targetArtist = targetArtist
            )
        }

        return scored.maxByOrNull { it.second }?.first ?: items.first()
    }
}
