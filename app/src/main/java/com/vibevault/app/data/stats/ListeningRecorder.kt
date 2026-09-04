package com.vibevault.app.data.stats

import com.vibevault.app.domain.model.Track
import kotlin.math.min

/**
 * Turns player ticking into listening statistics kept by [ListeningStats].
 */
object ListeningRecorder {

    private var currentId: String? = null
    private var lastSampleAt: Long = 0L
    private var playedThisTrack: Long = 0L
    private var playCounted = false
    private var samplesSinceFlush = 0

    @Synchronized
    fun onSample(track: Track, durationMs: Long) {
        val now = System.currentTimeMillis()
        if (track.id != currentId) {
            currentId = track.id
            lastSampleAt = now
            playedThisTrack = 0L
            playCounted = false
            return
        }
        val step = (now - lastSampleAt).coerceIn(0L, MAX_STEP_MS)
        lastSampleAt = now
        if (step <= 0L) return
        playedThisTrack += step

        val length = durationMs.takeIf { it > 0 } ?: track.durationMs
        val threshold = if (length > 0) {
            min(length / 2, PLAY_CEILING_MS).coerceAtLeast(PLAY_FLOOR_MS)
        } else {
            PLAY_FLOOR_MS
        }
        val counts = !playCounted && playedThisTrack >= threshold
        if (counts) playCounted = true

        ListeningStats.record(track, step, counts)

        if (++samplesSinceFlush >= FLUSH_EVERY) {
            samplesSinceFlush = 0
            ListeningStats.flush()
        }
    }

    @Synchronized
    fun onStopped() {
        currentId = null
        playedThisTrack = 0L
        playCounted = false
        samplesSinceFlush = 0
        ListeningStats.flush()
    }

    private const val MAX_STEP_MS = 8_000L
    private const val PLAY_FLOOR_MS = 30_000L
    private const val PLAY_CEILING_MS = 4 * 60 * 1000L
    private const val FLUSH_EVERY = 6
}
