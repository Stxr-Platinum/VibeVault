package com.vibevault.app.data.stats

import android.os.SystemClock
import com.vibevault.app.domain.model.Track

/**
 * Turns active player ticking into Replay listening statistics kept by [ListeningStats].
 *
 * Rules enforced:
 * - Play count and listening duration remain 0 until a track reaches 60,000 ms (1 full minute)
 *   of active playback — eliminating premature recording between 50 and 60 seconds.
 * - Quick skips (< 60s) are discarded completely with 0 plays and 0 ms attributed.
 * - Listening time is strictly recorded and flushed in exact 1-minute (60,000 ms) increments
 *   without early aggregation between minute boundaries.
 * - Pauses preserve accumulated progress without counting paused wall-clock time.
 * - Monotonic timing + step clamping prevent seeking or clock jumps from inflating duration.
 */
object ListeningRecorder {

    internal const val QUALIFICATION_THRESHOLD_MS = 60_000L
    private const val MINUTE_INCREMENT_MS = 60_000L
    private const val MAX_STEP_MS = 8_000L

    internal var monotonicClockMs: () -> Long = {
        runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.nanoTime() / 1_000_000L }
    }

    private var currentId: String? = null
    private var currentTrack: Track? = null
    private var lastSampleAt: Long = 0L
    private var accumulatedMs: Long = 0L
    private var playCounted = false

    @Synchronized
    fun onSample(track: Track, @Suppress("UNUSED_PARAMETER") durationMs: Long = 0L) {
        if (track.id.isBlank() ||
            track.title.isBlank() ||
            track.title == "Unknown" ||
            track.title == "Loading..."
        ) {
            return
        }

        val now = monotonicClockMs()
        if (track.id != currentId) {
            // Discard any unqualified partial remainder (< 60s) from the previous track without early aggregation
            currentId = track.id
            currentTrack = track
            lastSampleAt = now
            accumulatedMs = 0L
            playCounted = false
            return
        }

        // Resuming from a pause/buffer: re-anchor monotonic timestamp without crediting paused time
        if (lastSampleAt == 0L) {
            lastSampleAt = now
            return
        }

        val step = (now - lastSampleAt).coerceIn(0L, MAX_STEP_MS)
        lastSampleAt = now
        if (step <= 0L) return

        currentTrack = track
        accumulatedMs += step

        var recordedMinute = false
        while (accumulatedMs >= QUALIFICATION_THRESHOLD_MS) {
            accumulatedMs -= MINUTE_INCREMENT_MS
            val isFirstQualifiedMinute = !playCounted
            playCounted = true
            ListeningStats.record(track, MINUTE_INCREMENT_MS, countsAsPlay = isFirstQualifiedMinute)
            recordedMinute = true
        }
        if (recordedMinute) {
            ListeningStats.flush()
        }
    }

    /**
     * Pauses active timing without discarding the current track's accumulated progress.
     */
    @Synchronized
    fun onPaused() {
        lastSampleAt = 0L
    }

    /**
     * Ends the current listening session (track ended, stopped, or cleared).
     * Any partial listen below a full 60-second increment is discarded cleanly.
     */
    @Synchronized
    fun onStopped() {
        currentId = null
        currentTrack = null
        lastSampleAt = 0L
        accumulatedMs = 0L
        playCounted = false
    }

    @Synchronized
    internal fun resetForTest() {
        currentId = null
        currentTrack = null
        lastSampleAt = 0L
        accumulatedMs = 0L
        playCounted = false
    }
}
