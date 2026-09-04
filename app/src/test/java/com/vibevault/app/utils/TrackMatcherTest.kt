package com.vibevault.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun testVersionModifierDetection() {
        assertTrue(TrackMatcher.hasVersionModifier("camila cabello - havana // slowed + reverb mix"))
        assertTrue(TrackMatcher.hasVersionModifier("trust issues (remix)"))
        assertTrue(TrackMatcher.hasVersionModifier("song title nightcore"))
        assertTrue(!TrackMatcher.hasVersionModifier("havana by camila cabello"))
    }

    @Test
    fun testSlowedReverbMatchScoring() {
        val query = "camila cabello - havana // slowed + reverb mix"

        val originalScore = TrackMatcher.scoreMatch(query, "Camila Cabello - Havana (Official Video)", "Camila Cabello")
        val slowedReverbScore = TrackMatcher.scoreMatch(query, "Camila Cabello - Havana (Slowed + Reverb)", "Camila Cabello")

        assertTrue(slowedReverbScore > originalScore)
    }

    @Test
    fun testArtistKeywordMatchScoring() {
        val query = "trust issues by weekend"

        val drakeScore = TrackMatcher.scoreMatch(query, "Drake - Trust Issues", "Drake")
        val weekndScore = TrackMatcher.scoreMatch(query, "The Weeknd - Trust Issues", "The Weeknd")

        assertTrue(weekndScore > drakeScore)
    }

    @Test
    fun testNightcallCombinedSelection() = kotlinx.coroutines.runBlocking {
        val query = "Kavinsky Nightcall"
        val summary = com.music.innertube.YouTube.searchSummary(query).getOrNull()
        val topResultItems = summary?.summaries?.firstOrNull { it.title.equals("Top result", ignoreCase = true) }?.items.orEmpty()
        val otherSummaryItems = summary?.summaries?.filterNot { it.title.equals("Top result", ignoreCase = true) }?.flatMap { it.items }.orEmpty()
        val songItems = com.music.innertube.YouTube.search(query, com.music.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()

        val allCandidates = (topResultItems + songItems + otherSummaryItems).distinctBy { it.id }
        val topResultIds = topResultItems.map { it.id }.toSet()

        val best = TrackMatcher.selectBestMatch(
            query = query,
            items = allCandidates,
            expectedDurationSec = 259,
            targetTitle = "Nightcall",
            targetArtist = "Kavinsky",
            topResultIds = topResultIds
        )

        assertEquals("MV_3Dpw-BRY", best?.id)
    }
}
