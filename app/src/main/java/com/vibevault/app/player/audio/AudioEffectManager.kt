package com.vibevault.app.player.audio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.vibevault.app.player.service.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Singleton AudioEffectManager responsible for controlling
 * CustomEqualizerAudioProcessor directly inside ExoPlayer's audio pipeline.
 *
 * Modeled after vivi-music:
 * - All EQ band shaping and dynamic headroom is handled cleanly in PCM by CustomEqualizerAudioProcessor.
 * - Conflicting hardware android.media.audiofx.Equalizer / BassBoost instances are disabled
 *   to avoid double-filtering distortion and clipping.
 * - Full real-time digital Freeverb reverberation (Damp, Filter, Fade, Pre-Delay, Size, Mix).
 */
object AudioEffectManager {

    private const val TAG = "AudioEffectManager"
    private const val PREFS_NAME = "vibevault_audio_effects"

    private var prefs: SharedPreferences? = null

    // ── Direct ExoPlayer AudioProcessor (100% Reliable PCM DSP) ──
    val audioProcessor = CustomEqualizerAudioProcessor()

    // ── Optional Spatial Effects ─────────────────────────────────
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    // ── Observable States ──────────────────────────────────────────
    private val _eqEnabled = MutableStateFlow(true)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _toneEnabled = MutableStateFlow(true)
    val toneEnabled: StateFlow<Boolean> = _toneEnabled.asStateFlow()

    private val _limiterEnabled = MutableStateFlow(true)
    val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

    private val _preampGain = MutableStateFlow(0.0f) // 0 dB neutral
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    // 10 bands: 31, 62, 125, 250, 500, 1K, 2K, 4K, 8K, 16K (dB: -10.0 to +10.0)
    private val _bandGains = MutableStateFlow(
        listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    )
    val bandGains: StateFlow<List<Float>> = _bandGains.asStateFlow()

    private val _currentPreset = MutableStateFlow("Flat")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bassLevel = MutableStateFlow(40f) // 40% = neutral 0 dB
    val bassLevel: StateFlow<Float> = _bassLevel.asStateFlow()

    private val _trebleLevel = MutableStateFlow(0f) // 0 to 100
    val trebleLevel: StateFlow<Float> = _trebleLevel.asStateFlow()

    private val _balance = MutableStateFlow(0.0f) // -1.0 to +1.0
    val balance: StateFlow<Float> = _balance.asStateFlow()

    private val _stereoExpand = MutableStateFlow(0.0f) // 0 to 100
    val stereoExpand: StateFlow<Float> = _stereoExpand.asStateFlow()

    private val _tempo = MutableStateFlow(1.00f) // 0.50 to 2.00
    val tempo: StateFlow<Float> = _tempo.asStateFlow()

    private val _isMono = MutableStateFlow(false)
    val isMono: StateFlow<Boolean> = _isMono.asStateFlow()

    private val _volumeLevel = MutableStateFlow(100f) // 0 to 100
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private val _reverbEnabled = MutableStateFlow(false)
    val reverbEnabled: StateFlow<Boolean> = _reverbEnabled.asStateFlow()

    private val _reverbDamp = MutableStateFlow(0.36f)
    val reverbDamp: StateFlow<Float> = _reverbDamp.asStateFlow()

    private val _reverbFilter = MutableStateFlow(0.12f)
    val reverbFilter: StateFlow<Float> = _reverbFilter.asStateFlow()

    private val _reverbFade = MutableStateFlow(0.27f)
    val reverbFade: StateFlow<Float> = _reverbFade.asStateFlow()

    private val _reverbPreDelay = MutableStateFlow(0.54f)
    val reverbPreDelay: StateFlow<Float> = _reverbPreDelay.asStateFlow()

    private val _reverbPreDelayMix = MutableStateFlow(0.18f)
    val reverbPreDelayMix: StateFlow<Float> = _reverbPreDelayMix.asStateFlow()

    private val _reverbSize = MutableStateFlow(0.73f)
    val reverbSize: StateFlow<Float> = _reverbSize.asStateFlow()

    private val _reverbMix = MutableStateFlow(0.00f)
    val reverbMix: StateFlow<Float> = _reverbMix.asStateFlow()

    private val _reverbPreset = MutableStateFlow("Studio Room")
    val reverbPreset: StateFlow<String> = _reverbPreset.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadPreferences()
        }

        val activePlayer = PlaybackService.instance?.player as? ExoPlayer
        val sessionId = activePlayer?.audioSessionId ?: 0
        if (sessionId > 0) {
            attachAudioSession(context, sessionId)
        }
    }

    private fun loadPreferences() {
        val p = prefs ?: return
        _eqEnabled.value = p.getBoolean("eq_enabled", true)
        _preampGain.value = p.getFloat("preamp_gain", 0.0f)
        _bassLevel.value = p.getFloat("bass_level", 40f)
        _trebleLevel.value = p.getFloat("treble_level", 0f)
        _balance.value = p.getFloat("balance", 0.0f)
        _stereoExpand.value = p.getFloat("stereo_expand", 0.0f)
        _tempo.value = p.getFloat("tempo", 1.00f)
        _isMono.value = p.getBoolean("is_mono", false)
        _volumeLevel.value = p.getFloat("volume_level", 100f)
        _reverbEnabled.value = p.getBoolean("reverb_enabled", false)
        _reverbDamp.value = p.getFloat("reverb_damp", 0.36f)
        _reverbFilter.value = p.getFloat("reverb_filter", 0.12f)
        _reverbFade.value = p.getFloat("reverb_fade", 0.27f)
        _reverbPreDelay.value = p.getFloat("reverb_predelay", 0.54f)
        _reverbPreDelayMix.value = p.getFloat("reverb_predelay_mix", 0.18f)
        _reverbSize.value = p.getFloat("reverb_size", 0.73f)
        _reverbMix.value = p.getFloat("reverb_mix", 0.00f)
        val loadedPreset = p.getString("preset_name", "Flat") ?: "Flat"
        _currentPreset.value = if (loadedPreset == "Vivi Signature") "Flat" else loadedPreset
        _reverbPreset.value = p.getString("reverb_preset_name", "Studio Room") ?: "Studio Room"

        val gains = mutableListOf<Float>()
        for (i in 0 until 10) {
            gains.add(p.getFloat("band_$i", 0.0f))
        }
        _bandGains.value = gains

        // Synchronize with audio processor immediately
        audioProcessor.setEqEnabled(_eqEnabled.value)
        audioProcessor.setToneEnabled(_toneEnabled.value)
        audioProcessor.setLimiterEnabled(_limiterEnabled.value)
        audioProcessor.setPreamp(_preampGain.value)
        audioProcessor.setBandGains(gains)
        audioProcessor.setBassLevel(_bassLevel.value)
        audioProcessor.setTrebleLevel(_trebleLevel.value)
        audioProcessor.setBalance(_balance.value)
        audioProcessor.setMono(_isMono.value)
        audioProcessor.setVolume(_volumeLevel.value)

        audioProcessor.setReverbEnabled(_reverbEnabled.value)
        audioProcessor.setReverbSize(_reverbSize.value)
        audioProcessor.setReverbDamp(_reverbDamp.value)
        audioProcessor.setReverbFilter(_reverbFilter.value)
        audioProcessor.setReverbFade(_reverbFade.value)
        audioProcessor.setReverbPreDelay(_reverbPreDelay.value)
        audioProcessor.setReverbPreDelayMix(_reverbPreDelayMix.value)
        audioProcessor.setReverbMix(_reverbMix.value)
    }

    @Synchronized
    fun attachAudioSession(context: Context, sessionId: Int) {
        if (sessionId <= 0) return
        if (currentSessionId == sessionId) return

        releaseEffects(context)
        currentSessionId = sessionId
        Log.d(TAG, "Attaching audio session broadcast for audioSessionId: $sessionId")

        try {
            val openIntent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(openIntent)

            if (_stereoExpand.value > 0) {
                virtualizer = Virtualizer(0, sessionId).apply {
                    enabled = true
                    if (strengthSupported) {
                        setStrength((_stereoExpand.value * 10f).roundToInt().coerceIn(0, 1000).toShort())
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Optional audio effect initialization on session $sessionId", e)
        }
    }

    private fun releaseEffects(context: Context) {
        if (currentSessionId > 0) {
            try {
                val closeIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                }
                context.sendBroadcast(closeIntent)
            } catch (_: Exception) {}
        }
        try { virtualizer?.release() } catch (_: Exception) {}
        virtualizer = null
        currentSessionId = 0
    }

    // ── Apply Settings Live ──────────────────────────────────

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        prefs?.edit()?.putBoolean("eq_enabled", enabled)?.apply()
        audioProcessor.setEqEnabled(enabled)
    }

    fun setToneEnabled(enabled: Boolean) {
        _toneEnabled.value = enabled
        prefs?.edit()?.putBoolean("tone_enabled", enabled)?.apply()
        audioProcessor.setToneEnabled(enabled)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        prefs?.edit()?.putBoolean("limiter_enabled", enabled)?.apply()
        audioProcessor.setLimiterEnabled(enabled)
    }

    fun setPreampGain(gain: Float) {
        _preampGain.value = gain
        prefs?.edit()?.putFloat("preamp_gain", gain)?.apply()
        audioProcessor.setPreamp(gain)
    }

    fun setBandGain(index: Int, gain: Float) {
        val list = _bandGains.value.toMutableList()
        if (index in list.indices) {
            list[index] = gain
            _bandGains.value = list
            _currentPreset.value = "Custom"
            prefs?.edit()?.putFloat("band_$index", gain)?.putString("preset_name", "Custom")?.apply()
            audioProcessor.setBandGains(list)
        }
    }

    fun setAllBandGains(gains: List<Float>) {
        _bandGains.value = gains
        val editor = prefs?.edit()
        gains.forEachIndexed { i, g -> editor?.putFloat("band_$i", g) }
        editor?.apply()
        audioProcessor.setBandGains(gains)
    }

    fun setPreset(name: String, gains: List<Float>) {
        Log.d("EQ_DEBUG", "setPreset('$name'): gains=$gains")
        _currentPreset.value = name
        _bandGains.value = gains
        _eqEnabled.value = true
        val editor = prefs?.edit()
        editor?.putString("preset_name", name)?.putBoolean("eq_enabled", true)
        gains.forEachIndexed { i, g -> editor?.putFloat("band_$i", g) }
        editor?.apply()
        audioProcessor.setEqEnabled(true)
        audioProcessor.setBandGains(gains)
        Log.d("EQ_DEBUG", "setPreset done: processor.equalizerEnabled should be true")
    }

    fun setBass(level: Float) {
        _bassLevel.value = level
        prefs?.edit()?.putFloat("bass_level", level)?.apply()
        audioProcessor.setBassLevel(level)
    }

    fun setTreble(level: Float) {
        _trebleLevel.value = level
        prefs?.edit()?.putFloat("treble_level", level)?.apply()
        audioProcessor.setTrebleLevel(level)
    }

    fun setBalance(bal: Float) {
        _balance.value = bal
        prefs?.edit()?.putFloat("balance", bal)?.apply()
        audioProcessor.setBalance(bal)
    }

    fun setStereoExpand(expand: Float) {
        _stereoExpand.value = expand
        prefs?.edit()?.putFloat("stereo_expand", expand)?.apply()
        try {
            virtualizer?.enabled = expand > 0
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength((expand * 10f).roundToInt().coerceIn(0, 1000).toShort())
            }
        } catch (_: Exception) {}
    }

    fun setTempo(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _tempo.value = clamped
        prefs?.edit()?.putFloat("tempo", clamped)?.apply()
        try {
            val player = PlaybackService.instance?.player as? ExoPlayer
            player?.setPlaybackParameters(PlaybackParameters(clamped, 1.0f))
        } catch (_: Exception) {}
    }

    fun setMono(mono: Boolean) {
        _isMono.value = mono
        prefs?.edit()?.putBoolean("is_mono", mono)?.apply()
        audioProcessor.setMono(mono)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 100f)
        _volumeLevel.value = clamped
        prefs?.edit()?.putFloat("volume_level", clamped)?.apply()
        audioProcessor.setVolume(clamped)
    }

    fun setReverbEnabled(enabled: Boolean) {
        _reverbEnabled.value = enabled
        prefs?.edit()?.putBoolean("reverb_enabled", enabled)?.apply()
        audioProcessor.setReverbEnabled(enabled)
    }

    fun setReverbPreset(
        name: String,
        damp: Float,
        filter: Float,
        fade: Float,
        preDelay: Float,
        preDelayMix: Float,
        size: Float,
        mix: Float
    ) {
        _reverbPreset.value = name
        _reverbEnabled.value = true
        _reverbDamp.value = damp
        _reverbFilter.value = filter
        _reverbFade.value = fade
        _reverbPreDelay.value = preDelay
        _reverbPreDelayMix.value = preDelayMix
        _reverbSize.value = size
        _reverbMix.value = mix

        prefs?.edit()
            ?.putString("reverb_preset_name", name)
            ?.putBoolean("reverb_enabled", true)
            ?.putFloat("reverb_damp", damp)
            ?.putFloat("reverb_filter", filter)
            ?.putFloat("reverb_fade", fade)
            ?.putFloat("reverb_predelay", preDelay)
            ?.putFloat("reverb_predelay_mix", preDelayMix)
            ?.putFloat("reverb_size", size)
            ?.putFloat("reverb_mix", mix)
            ?.apply()

        audioProcessor.setReverbEnabled(true)
        audioProcessor.setReverbDamp(damp)
        audioProcessor.setReverbFilter(filter)
        audioProcessor.setReverbFade(fade)
        audioProcessor.setReverbPreDelay(preDelay)
        audioProcessor.setReverbPreDelayMix(preDelayMix)
        audioProcessor.setReverbSize(size)
        audioProcessor.setReverbMix(mix)
    }

    fun setReverbDamp(value: Float) {
        _reverbDamp.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_damp", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbDamp(value)
    }

    fun setReverbFilter(value: Float) {
        _reverbFilter.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_filter", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbFilter(value)
    }

    fun setReverbFade(value: Float) {
        _reverbFade.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_fade", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbFade(value)
    }

    fun setReverbPreDelay(value: Float) {
        _reverbPreDelay.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_predelay", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbPreDelay(value)
    }

    fun setReverbPreDelayMix(value: Float) {
        _reverbPreDelayMix.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_predelay_mix", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbPreDelayMix(value)
    }

    fun setReverbSize(value: Float) {
        _reverbSize.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_size", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbSize(value)
    }

    fun setReverbMix(value: Float) {
        _reverbMix.value = value
        _reverbPreset.value = "Custom"
        prefs?.edit()?.putFloat("reverb_mix", value)?.putString("reverb_preset_name", "Custom")?.apply()
        audioProcessor.setReverbMix(value)
    }

    fun resetAll() {
        setPreampGain(0f)
        setAllBandGains(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        setBass(40f)
        setTreble(0f)
        setBalance(0f)
        setStereoExpand(0f)
        setTempo(1.0f)
        setMono(false)
        setVolume(100f)
        setReverbEnabled(false)
        setReverbSize(0f)
        setReverbMix(0f)
    }
}
