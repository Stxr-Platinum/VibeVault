package com.vibevault.app.data.stats

import com.vibevault.app.data.remote.dto.UserReplayStatDto
import com.vibevault.app.domain.model.Track
import com.vibevault.app.ui.replay.formatListening
import com.vibevault.app.ui.replay.formatMinutes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.YearMonth

class ListeningRecorderTest {

    private lateinit var tempDir: File
    private var fakeClockMs: Long = 1_000_000L

    private val trackA = Track(
        id = "track_a",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up, We're Dreaming",
        albumImageUrl = "https://example.com/a.jpg",
        durationMs = 243_000L
    )

    private val trackB = Track(
        id = "track_b",
        title = "Oblivion",
        artist = "M83",
        album = "Oblivion",
        albumImageUrl = "https://example.com/b.jpg",
        durationMs = 260_000L
    )

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("listening_test").toFile()
        ListeningStats.initForDirectory(tempDir)
        ListeningRecorder.resetForTest()
        fakeClockMs = 1_000_000L
        ListeningRecorder.monotonicClockMs = { fakeClockMs }
    }

    @After
    fun tearDown() {
        ListeningRecorder.resetForTest()
        runBlocking { ListeningStats.clearAll() }
        tempDir.deleteRecursively()
    }

    private fun advanceSamples(track: Track, stepMs: Long, count: Int) {
        ListeningRecorder.onSample(track, track.durationMs)
        repeat(count) {
            fakeClockMs += stepMs
            ListeningRecorder.onSample(track, track.durationMs)
        }
    }

    @Test
    fun partialListensBetween50And60SecondsAreStrictlyDiscarded() = runBlocking {
        // 50s -> 55s -> 59.5s partial listen must NOT record anything
        advanceSamples(trackA, stepMs = 5_000L, count = 11) // 55,000 ms
        fakeClockMs += 4_500L
        ListeningRecorder.onSample(trackA, trackA.durationMs) // 59,500 ms
        ListeningRecorder.onSample(trackB, trackB.durationMs) // Switch tracks before 60,000 ms

        // 58-second listen on trackB then stop
        repeat(11) {
            fakeClockMs += 5_000L
            ListeningRecorder.onSample(trackB, trackB.durationMs)
        } // 55,000 ms
        fakeClockMs += 3_000L
        ListeningRecorder.onSample(trackB, trackB.durationMs) // 58,000 ms
        ListeningRecorder.onStopped()

        val summary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(0, summary.totalPlays)
        assertEquals(0L, summary.totalMs)
        assertTrue(summary.songs.isEmpty())
        assertEquals("0 min", formatListening(55_000L))
        assertEquals("0", formatMinutes(59_000L))
    }

    @Test
    fun reaching60SecondsAddsExactOneMinuteIncrementsWithoutEarlyAggregation() = runBlocking {
        // 12 samples * 5,000ms = 60,000ms active playback -> exact 1st minute
        advanceSamples(trackA, stepMs = 5_000L, count = 12)
        // Extra 55 seconds (11 * 5s) -> total 115,000ms (< 2 full minutes) must NOT aggregate early
        repeat(11) {
            fakeClockMs += 5_000L
            ListeningRecorder.onSample(trackA, trackA.durationMs)
        }

        val midSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(1, midSummary.totalPlays)
        assertEquals(60_000L, midSummary.totalMs)
        assertEquals(1, midSummary.songs.size)
        assertEquals(60_000L, midSummary.songs.first().ms)
        assertEquals(1, midSummary.songs.first().plays)
        assertEquals("1 min", formatListening(115_000L))
        assertEquals("1", formatMinutes(115_000L))

        // Complete the 2nd full minute (+5,000ms -> 120,000ms total active)
        fakeClockMs += 5_000L
        ListeningRecorder.onSample(trackA, trackA.durationMs)
        ListeningRecorder.onStopped()

        val finalSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(1, finalSummary.totalPlays)
        assertEquals(120_000L, finalSummary.totalMs)
        assertEquals(120_000L, finalSummary.songs.first().ms)
        assertEquals(1, finalSummary.songs.first().plays)

        val monthFile = File(tempDir, "${YearMonth.now()}.json")
        assertTrue("Expected YYYY-MM.json file to exist", monthFile.exists())
    }

    @Test
    fun pausePreservesProgressWithoutCountingPausedDuration() = runBlocking {
        // Listen for 35 seconds (7 * 5s)
        advanceSamples(trackA, stepMs = 5_000L, count = 7)
        ListeningRecorder.onPaused()

        // Wait 2 minutes while paused — must NOT be counted
        fakeClockMs += 120_000L

        // Resume and listen for 25 more seconds (5 * 5s) -> total 60s active
        ListeningRecorder.onSample(trackA, trackA.durationMs)
        repeat(5) {
            fakeClockMs += 5_000L
            ListeningRecorder.onSample(trackA, trackA.durationMs)
        }
        ListeningRecorder.onStopped()

        val summary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(1, summary.totalPlays)
        assertEquals(60_000L, summary.totalMs)
    }

    @Test
    fun mergeRemoteRowsAndClearAllWorkAccuratelyWithoutZeroMinutesBug() = runBlocking {
        val currentMonth = YearMonth.now().toString()
        ListeningStats.mergeRemoteRows(
            listOf(
                UserReplayStatDto(
                    userId = "user-1",
                    month = currentMonth,
                    songId = "remote_1",
                    songTitle = "Starlight",
                    artist = "Muse",
                    album = "Black Holes",
                    coverUrl = "https://example.com/muse.jpg",
                    durationMs = 240_000L,
                    playedMs = 125_000L,
                    playCount = 2
                )
            )
        )

        val summary = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
        assertEquals(2, summary.totalPlays)
        assertEquals(125_000L, summary.totalMs)
        assertEquals("2 min", formatListening(summary.totalMs))
        assertEquals("2", formatMinutes(summary.totalMs))
        assertEquals("1 min", formatListening(60_000L))
        assertEquals("25 hr 0 min".replace(" 0 min", ""), formatListening(25 * 60 * 60_000L))

        ListeningStats.clearAll()
        val cleared = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(0, cleared.totalPlays)
        assertEquals(0L, cleared.totalMs)
        assertTrue(cleared.songs.isEmpty())
    }

    @Test
    fun replaceWithRemoteRowsSeamlesslyReplacesStaleLocalCache() = runBlocking {
        val currentMonth = YearMonth.now().toString()
        // Seed stale local track
        ListeningStats.record(trackA, playedMs = 90_000L, countsAsPlay = true)
        ListeningStats.flushAndAwait()

        // Remote authoritative state replaces trackA with trackB
        ListeningStats.replaceWithRemoteRows(
            listOf(
                UserReplayStatDto(
                    userId = "user-1",
                    month = currentMonth,
                    songId = trackB.id,
                    songTitle = trackB.title,
                    artist = trackB.artist,
                    album = trackB.album,
                    coverUrl = trackB.albumImageUrl,
                    durationMs = trackB.durationMs,
                    playedMs = 180_000L,
                    playCount = 3
                )
            )
        )

        val summary = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
        assertEquals(1, summary.songs.size)
        assertEquals(trackB.id, summary.songs.first().song.id)
        assertEquals(180_000L, summary.totalMs)
        assertEquals(3, summary.totalPlays)
    }
}

