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
}
