package com.vibevault.app.player.audio

import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Studio-grade, zero-allocation Freeverb DSP engine.
 *
 * Designed with:
 * - Dedicated High-Pass Filter (Low-Cut at 200 Hz) on the reverb input to eliminate
 *   sub-bass mud, kick drum buildup, DC offset, and low-end cracking.
 * - Calibrated CCRMA fixed-gain scaling so parallel comb filters sum cleanly to unity.
 * - Stable feedback limiting to prevent runaway comb oscillation.
 * - One-pole high-cut damping for smooth, warm, lush room and hall ambience.
 */
class ReverbEffect(private val sampleRate: Int = 44100) {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var size: Float = 0.73f       // Room size (0.0 to 1.0)

    @Volatile
    var damp: Float = 0.36f       // High-frequency damping (0.0 to 1.0)

    @Volatile
    var filter: Float = 0.12f     // Tone cutoff (0.0 to 1.0)

    @Volatile
    var fade: Float = 0.27f       // Decay time factor (0.0 to 1.0)

    @Volatile
    var preDelay: Float = 0.54f   // Pre-delay time (0.0 to 1.0 -> 0 to 80ms)

    @Volatile
    var preDelayMix: Float = 0.18f // Pre-delay blend (0.0 to 1.0)

    @Volatile
    var mix: Float = 0.00f        // Wet mix (0.0 to 1.0)

    // Pre-delay ring buffers (up to 150ms at 48kHz = ~7200 samples)
    private val maxPreDelaySamples = (sampleRate * 0.15).roundToInt()
    private val preDelayBufferL = FloatArray(maxPreDelaySamples)
    private val preDelayBufferR = FloatArray(maxPreDelaySamples)
    private var preDelayIndex = 0

    // High-Pass Filter state (Low-Cut at ~200 Hz)
    private var hpX1L = 0f
    private var hpY1L = 0f
    private var hpX1R = 0f
    private var hpY1R = 0f

    // High-pass filter coefficient for 200 Hz cutoff
    private val hpAlpha: Float = run {
        val dt = 1f / sampleRate.toFloat()
        val rc = 1f / (2f * PI.toFloat() * 200f)
        rc / (rc + dt)
    }

    // Comb filter delays at 44.1kHz (Schroeder-Moorer tuning)
    private val combTunings = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassTunings = intArrayOf(556, 441, 341, 225)
    private val stereoSpread = 23

    private val rateScale = sampleRate.toFloat() / 44100f

    private val combsL = Array(8) { i ->
        CombFilter((combTunings[i] * rateScale).roundToInt())
    }
    private val combsR = Array(8) { i ->
        CombFilter(((combTunings[i] + stereoSpread) * rateScale).roundToInt())
    }

    private val allpassesL = Array(4) { i ->
        AllPassFilter((allpassTunings[i] * rateScale).roundToInt())
    }
    private val allpassesR = Array(4) { i ->
        AllPassFilter(((allpassTunings[i] + stereoSpread) * rateScale).roundToInt())
    }

    // High-cut damping state
    private var filterStoreL = 0f
    private var filterStoreR = 0f

    var lastOutputL = 0.0
        private set
    var lastOutputR = 0.0
        private set

    /**
     * One-pole High-Pass Filter (Low-Cut at 200 Hz).
     * Strips out kick drum and sub-bass energy before feeding comb delay lines,
     * completely eliminating low-frequency cracking, mud, and distortion.
     */
    private fun filterLowCut(input: Float, isLeft: Boolean): Float {
        return if (isLeft) {
            val out = hpAlpha * (hpY1L + input - hpX1L)
            hpX1L = input
            hpY1L = out
            out
        } else {
            val out = hpAlpha * (hpY1R + input - hpX1R)
            hpX1R = input
            hpY1R = out
            out
        }
    }

    fun processStereo(inL: Double, inR: Double) {
        if (!enabled || mix <= 0.001f) {
            lastOutputL = inL
            lastOutputR = inR
            return
        }

        val inputL = inL.toFloat()
        val inputR = inR.toFloat()

        // 1. High-Pass Filter input so sub-bass kick/basslines stay clean & tight
        val cutInputL = filterLowCut(inputL, true)
        val cutInputR = filterLowCut(inputR, false)

        // 2. Pre-Delay processing on the low-cut signal
        val currentPreDelaySamples = ((preDelay * 0.08f * sampleRate).roundToInt()).coerceIn(0, maxPreDelaySamples - 1)
        preDelayBufferL[preDelayIndex] = cutInputL
        preDelayBufferR[preDelayIndex] = cutInputR

        var readIdx = preDelayIndex - currentPreDelaySamples
        if (readIdx < 0) readIdx += maxPreDelaySamples

        val delayedL = preDelayBufferL[readIdx]
        val delayedR = preDelayBufferR[readIdx]

        preDelayIndex = (preDelayIndex + 1) % maxPreDelaySamples

        val pMix = preDelayMix.coerceIn(0f, 1f)
        val rawFeedL = cutInputL * (1f - pMix) + delayedL * pMix
        val rawFeedR = cutInputR * (1f - pMix) + delayedR * pMix

        // 3. Calibrated fixed-gain input scaling (Freeverb standard ~0.02f)
        // Prevents parallel comb filters from amplifying signal by 8x
        val fixedGain = 0.022f
        val feedL = rawFeedL * fixedGain
        val feedR = rawFeedR * fixedGain

        // 4. Parallel Comb Filters with stable feedback ceiling (prevents runaway resonance)
        val feedback = (0.68f + size * 0.22f + fade * 0.04f).coerceIn(0.60f, 0.93f)
        val damping = (damp * 0.45f).coerceIn(0.05f, 0.70f)

        var combOutL = 0f
        var combOutR = 0f

        for (i in 0 until 8) {
            combOutL += combsL[i].process(feedL, feedback, damping)
            combOutR += combsR[i].process(feedR, feedback, damping)
        }

        // 5. Series All-Pass Diffusers (spatial echo dispersion)
        var apOutL = combOutL
        var apOutR = combOutR

        for (i in 0 until 4) {
            apOutL = allpassesL[i].process(apOutL)
            apOutR = allpassesR[i].process(apOutR)
        }

        // 6. Tone filter (high-cut damping to remove digital sizzle)
        val filt = (0.15f + filter * 0.50f + damp * 0.20f).coerceIn(0.10f, 0.85f)
        filterStoreL = apOutL * (1f - filt) + filterStoreL * filt
        filterStoreR = apOutR * (1f - filt) + filterStoreR * filt

        // 7. Clean Wet / Dry mixing without volume jump
        val wetMixAmount = mix.coerceIn(0f, 1f)
        val wetGain = wetMixAmount * 1.8f
        val dryGain = (1f - wetMixAmount * 0.25f).coerceIn(0.75f, 1.0f)

        lastOutputL = (inputL * dryGain + filterStoreL * wetGain).toDouble()
        lastOutputR = (inputR * dryGain + filterStoreR * wetGain).toDouble()
    }

    fun reset() {
        preDelayBufferL.fill(0f)
        preDelayBufferR.fill(0f)
        preDelayIndex = 0
        hpX1L = 0f
        hpY1L = 0f
        hpX1R = 0f
        hpY1R = 0f
        combsL.forEach { it.mute() }
        combsR.forEach { it.mute() }
        allpassesL.forEach { it.mute() }
        allpassesR.forEach { it.mute() }
        filterStoreL = 0f
        filterStoreR = 0f
        lastOutputL = 0.0
        lastOutputR = 0.0
    }

    private class CombFilter(val size: Int) {
        private val buffer = FloatArray(size)
        private var bufferIndex = 0
        private var filterStore = 0f

        fun process(input: Float, feedback: Float, damp: Float): Float {
            val output = buffer[bufferIndex]
            filterStore = output * (1f - damp) + filterStore * damp
            buffer[bufferIndex] = input + filterStore * feedback
            bufferIndex = (bufferIndex + 1) % size
            return output
        }

        fun mute() {
            buffer.fill(0f)
            filterStore = 0f
            bufferIndex = 0
        }
    }

    private class AllPassFilter(val size: Int) {
        private val buffer = FloatArray(size)
        private var bufferIndex = 0
        private val feedback = 0.5f

        fun process(input: Float): Float {
            val bufOut = buffer[bufferIndex]
            val output = -input + bufOut
            buffer[bufferIndex] = input + bufOut * feedback
            bufferIndex = (bufferIndex + 1) % size
            return output
        }

        fun mute() {
            buffer.fill(0f)
            bufferIndex = 0
        }
    }
}
