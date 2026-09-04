package com.vibevault.app.ui.screens.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibevault.app.domain.model.Track
import com.vibevault.app.player.audio.AudioEffectManager
import java.util.Locale
import kotlin.math.*

private val NeonGreen = Color(0xFF00E676)
private val DarkBackground = Color(0xFF000000)
private val PillBackground = Color(0xFF1A1A1A)
private val KnobTrackColor = Color(0xFF222222)
private val KnobFaceColor = Color(0xFF1E1E1E)

private data class ReverbPresetItem(
    val name: String,
    val damp: Float,
    val filter: Float,
    val fade: Float,
    val preDelay: Float,
    val preDelayMix: Float,
    val size: Float,
    val mix: Float
)

@Composable
fun EqualizerModalDialog(
    track: Track? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Ensure AudioEffectManager is initialized with application context
    LaunchedEffect(Unit) {
        AudioEffectManager.init(context.applicationContext)
    }

    // ── Live Reactive States from AudioEffectManager ─────────────
    val eqEnabled by AudioEffectManager.eqEnabled.collectAsState()
    val toneEnabled by AudioEffectManager.toneEnabled.collectAsState()
    val limiterEnabled by AudioEffectManager.limiterEnabled.collectAsState()
    val preampGain by AudioEffectManager.preampGain.collectAsState()
    val bandGains by AudioEffectManager.bandGains.collectAsState()
    val bassLevel by AudioEffectManager.bassLevel.collectAsState()
    val trebleLevel by AudioEffectManager.trebleLevel.collectAsState()

    val balance by AudioEffectManager.balance.collectAsState()
    val stereoExpand by AudioEffectManager.stereoExpand.collectAsState()
    val tempo by AudioEffectManager.tempo.collectAsState()
    val isMono by AudioEffectManager.isMono.collectAsState()
    val volumeLevel by AudioEffectManager.volumeLevel.collectAsState()

    val reverbEnabled by AudioEffectManager.reverbEnabled.collectAsState()
    val reverbDamp by AudioEffectManager.reverbDamp.collectAsState()
    val reverbFilter by AudioEffectManager.reverbFilter.collectAsState()
    val reverbFade by AudioEffectManager.reverbFade.collectAsState()
    val reverbPreDelay by AudioEffectManager.reverbPreDelay.collectAsState()
    val reverbPreDelayMix by AudioEffectManager.reverbPreDelayMix.collectAsState()
    val reverbSize by AudioEffectManager.reverbSize.collectAsState()
    val reverbMix by AudioEffectManager.reverbMix.collectAsState()

    val bandFrequencies = remember {
        listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    }

    val currentPreset by AudioEffectManager.currentPreset.collectAsState()
    var showPresetDialog by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to listOf(6.5f, 5.5f, 4.0f, 1.5f, 0f, -0.5f, 0f, 1.5f, 2.5f, 3.5f),
            "Rock" to listOf(4.5f, 3.5f, 2.0f, 0.5f, -1.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.0f),
            "Electronic" to listOf(5.0f, 4.0f, 2.0f, -0.5f, -1.5f, 1.0f, 2.5f, 4.0f, 5.0f, 6.0f),
            "Pop" to listOf(-1.5f, 0f, 1.5f, 2.5f, 3.5f, 3.0f, 2.0f, 1.0f, -0.5f, -1.5f),
            "Vocal Boost" to listOf(-2.5f, -1.5f, 0f, 2.5f, 4.5f, 4.0f, 2.5f, 1.5f, 0f, -1.5f),
            "Pure Clarity" to listOf(1.5f, 1.0f, -1.0f, -2.0f, -0.5f, 2.0f, 3.5f, 3.0f, 2.5f, 2.0f),
            "Acoustic" to listOf(-0.5f, 1.0f, 2.0f, 1.0f, -0.5f, 1.5f, 2.0f, 3.0f, 2.5f, 1.5f),
            "Spatial" to listOf(3.0f, 1.5f, 0f, -2.0f, -2.5f, -1.5f, 1.0f, 2.0f, 4.0f, 5.0f),
            "Jazz" to listOf(2.5f, 1.5f, 1.0f, 2.0f, 2.5f, 2.5f, 1.5f, 2.5f, 3.0f, 2.5f)
        )
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Select Equalizer Preset", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                ) {
                    items(presets) { (name, gains) ->
                        val isSelected = currentPreset == name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    AudioEffectManager.setPreset(name, gains)
                                    showPresetDialog = false
                                    Toast.makeText(context, "Preset applied: $name", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) NeonGreen else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    val currentReverbPreset by AudioEffectManager.reverbPreset.collectAsState()
    var showReverbPresetDialog by remember { mutableStateOf(false) }

    val reverbPresets = remember {
        listOf(
            ReverbPresetItem("Studio Room", damp = 0.30f, filter = 0.10f, fade = 0.20f, preDelay = 0.15f, preDelayMix = 0.20f, size = 0.30f, mix = 0.25f),
            ReverbPresetItem("Living Room", damp = 0.50f, filter = 0.25f, fade = 0.35f, preDelay = 0.25f, preDelayMix = 0.25f, size = 0.45f, mix = 0.35f),
            ReverbPresetItem("Concert Hall", damp = 0.35f, filter = 0.15f, fade = 0.65f, preDelay = 0.45f, preDelayMix = 0.35f, size = 0.75f, mix = 0.45f),
            ReverbPresetItem("Cathedral", damp = 0.20f, filter = 0.08f, fade = 0.85f, preDelay = 0.65f, preDelayMix = 0.50f, size = 0.95f, mix = 0.55f),
            ReverbPresetItem("Plate Reverb", damp = 0.15f, filter = 0.05f, fade = 0.50f, preDelay = 0.10f, preDelayMix = 0.15f, size = 0.65f, mix = 0.40f),
            ReverbPresetItem("Ambient Space", damp = 0.25f, filter = 0.18f, fade = 0.90f, preDelay = 0.55f, preDelayMix = 0.40f, size = 0.88f, mix = 0.60f),
            ReverbPresetItem("Small Club", damp = 0.45f, filter = 0.20f, fade = 0.30f, preDelay = 0.20f, preDelayMix = 0.30f, size = 0.40f, mix = 0.30f),
            ReverbPresetItem("Subtle Presence", damp = 0.35f, filter = 0.15f, fade = 0.20f, preDelay = 0.10f, preDelayMix = 0.15f, size = 0.25f, mix = 0.15f)
        )
    }

    if (showReverbPresetDialog) {
        AlertDialog(
            onDismissRequest = { showReverbPresetDialog = false },
            title = { Text("Select Reverb Preset", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                ) {
                    items(reverbPresets) { p ->
                        val isSelected = currentReverbPreset == p.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    AudioEffectManager.setReverbPreset(
                                        p.name, p.damp, p.filter, p.fade,
                                        p.preDelay, p.preDelayMix, p.size, p.mix
                                    )
                                    showReverbPresetDialog = false
                                    Toast.makeText(context, "Reverb: ${p.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = p.name,
                                color = if (isSelected) NeonGreen else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReverbPresetDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Top Navigation Bar: 3 Tabs & Close ───────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tab 0: Sliders
                            HeaderTabIcon(
                                isSelected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            ) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val col = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.4f)
                                    val w = size.width
                                    val h = size.height
                                    drawLine(col, Offset(w * 0.2f, 0f), Offset(w * 0.2f, h), strokeWidth = 2.dp.toPx())
                                    drawCircle(col, radius = 3.dp.toPx(), center = Offset(w * 0.2f, h * 0.35f))
                                    drawLine(col, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 2.dp.toPx())
                                    drawCircle(col, radius = 3.dp.toPx(), center = Offset(w * 0.5f, h * 0.65f))
                                    drawLine(col, Offset(w * 0.8f, 0f), Offset(w * 0.8f, h), strokeWidth = 2.dp.toPx())
                                    drawCircle(col, radius = 3.dp.toPx(), center = Offset(w * 0.8f, h * 0.25f))
                                }
                            }

                            // Tab 1: Knob
                            HeaderTabIcon(
                                isSelected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            ) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val col = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.4f)
                                    drawCircle(color = col, radius = size.minDimension / 2f, center = center)
                                    drawCircle(
                                        color = Color(0xFF1E1E1E),
                                        radius = 3.dp.toPx(),
                                        center = Offset(center.x + size.width * 0.22f, center.y - size.height * 0.22f)
                                    )
                                }
                            }

                            // Tab 2: Surround [ (o) ]
                            HeaderTabIcon(
                                isSelected = selectedTab == 2,
                                onClick = { selectedTab = 2 }
                            ) {
                                Canvas(modifier = Modifier.size(22.dp)) {
                                    val col = if (selectedTab == 2) Color.White else Color.White.copy(alpha = 0.4f)
                                    val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                                    drawRoundRect(
                                        color = col,
                                        topLeft = Offset(0f, 0f),
                                        size = Size(size.width, size.height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                                        style = stroke
                                    )
                                    drawCircle(col, radius = 3.dp.toPx(), center = center)
                                    drawCircle(col, radius = 6.5.dp.toPx(), center = center, style = Stroke(width = 1.2.dp.toPx()))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1E1E))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Active Tab Display ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> EqualizerSlidersTab(
                            presetName = currentPreset,
                            preampGain = preampGain,
                            onPreampChange = { AudioEffectManager.setPreampGain(it) },
                            bandFrequencies = bandFrequencies,
                            bandGains = bandGains,
                            onBandGainChange = { index, gain ->
                                AudioEffectManager.setBandGain(index, gain)
                            },
                            eqEnabled = eqEnabled,
                            onToggleEq = { AudioEffectManager.setEqEnabled(!eqEnabled) },
                            toneEnabled = toneEnabled,
                            onToggleTone = { AudioEffectManager.setToneEnabled(!toneEnabled) },
                            limiterEnabled = limiterEnabled,
                            onToggleLimiter = { AudioEffectManager.setLimiterEnabled(!limiterEnabled) },
                            onOpenPreset = { showPresetDialog = true },
                            bassLevel = bassLevel,
                            onBassChange = { AudioEffectManager.setBass(it) },
                            trebleLevel = trebleLevel,
                            onTrebleChange = { AudioEffectManager.setTreble(it) },
                            track = track,
                            onOpenSystemEq = {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "System equalizer not available", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        1 -> StereoBalanceTab(
                            balance = balance,
                            onBalanceChange = { AudioEffectManager.setBalance(it) },
                            stereoExpand = stereoExpand,
                            onStereoExpandChange = { AudioEffectManager.setStereoExpand(it) },
                            tempo = tempo,
                            onTempoChange = { AudioEffectManager.setTempo(it) },
                            isMono = isMono,
                            onToggleMono = { AudioEffectManager.setMono(!isMono) },
                            volumeLevel = volumeLevel,
                            onVolumeChange = { AudioEffectManager.setVolume(it) },
                            onReset = {
                                AudioEffectManager.resetAll()
                                Toast.makeText(context, "Reset to defaults", Toast.LENGTH_SHORT).show()
                            }
                        )
                        2 -> ReverbTab(
                            reverbEnabled = reverbEnabled,
                            onToggleReverb = { AudioEffectManager.setReverbEnabled(!reverbEnabled) },
                            presetName = currentReverbPreset,
                            onOpenPreset = { showReverbPresetDialog = true },
                            damp = reverbDamp,
                            onDampChange = { AudioEffectManager.setReverbDamp(it) },
                            filter = reverbFilter,
                            onFilterChange = { AudioEffectManager.setReverbFilter(it) },
                            fade = reverbFade,
                            onFadeChange = { AudioEffectManager.setReverbFade(it) },
                            preDelay = reverbPreDelay,
                            onPreDelayChange = { AudioEffectManager.setReverbPreDelay(it) },
                            preDelayMix = reverbPreDelayMix,
                            onPreDelayMixChange = { AudioEffectManager.setReverbPreDelayMix(it) },
                            size = reverbSize,
                            onSizeChange = { AudioEffectManager.setReverbSize(it) },
                            mix = reverbMix,
                            onMixChange = { AudioEffectManager.setReverbMix(it) },
                            onReset = {
                                AudioEffectManager.setReverbEnabled(false)
                                AudioEffectManager.setReverbSize(0f)
                                AudioEffectManager.setReverbMix(0f)
                                Toast.makeText(context, "Reverb reset", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderTabIcon(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 36.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ── TAB 1: GRAPHIC EQUALIZER SLIDERS & TONE (IMAGE 1) ────────────────────────
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun EqualizerSlidersTab(
    presetName: String,
    preampGain: Float,
    onPreampChange: (Float) -> Unit,
    bandFrequencies: List<String>,
    bandGains: List<Float>,
    onBandGainChange: (Int, Float) -> Unit,
    eqEnabled: Boolean,
    onToggleEq: () -> Unit,
    toneEnabled: Boolean,
    onToggleTone: () -> Unit,
    limiterEnabled: Boolean,
    onToggleLimiter: () -> Unit,
    onOpenPreset: () -> Unit,
    bassLevel: Float,
    onBassChange: (Float) -> Unit,
    trebleLevel: Float,
    onTrebleChange: (Float) -> Unit,
    track: Track?,
    onOpenSystemEq: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Vertical Sliders Array (Preamp + 10 Bands)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalSlider(
                label = "Preamp",
                value = preampGain,
                onValueChange = onPreampChange,
                valueRange = -12f..12f,
                modifier = Modifier.width(46.dp),
                isPreamp = true
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bandFrequencies.forEachIndexed { index, freq ->
                    VerticalSlider(
                        label = freq,
                        value = bandGains.getOrElse(index) { 0f },
                        onValueChange = { onBandGainChange(index, it) },
                        valueRange = -10f..10f,
                        modifier = Modifier.width(36.dp),
                        isPreamp = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Frequency Response Curve Visualizer
        FrequencyResponseCanvas(
            bandGains = bandGains,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414))
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Status Badge Pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "NO DVC EQ 10 TON LMT STX BAL REV",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = Color.White.copy(alpha = 0.75f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Control Buttons Row [ Equ ] [ Preset ] [ ⋮ ] [ Tone ] [ Limit ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                text = "Equ",
                isActive = eqEnabled,
                onClick = onToggleEq,
                modifier = Modifier.width(52.dp)
            )
            PillButton(
                text = presetName.ifBlank { "Preset" },
                isActive = presetName != "Custom",
                onClick = onOpenPreset,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PillBackground)
                    .clickable(onClick = onOpenSystemEq),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            PillButton(
                text = "Tone",
                isActive = toneEnabled,
                onClick = onToggleTone,
                modifier = Modifier.width(54.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            PillButton(
                text = "Limit",
                isActive = limiterEnabled,
                onClick = onToggleLimiter,
                modifier = Modifier.width(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Bass & Treble Knobs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryKnob(
                value = bassLevel,
                onValueChange = onBassChange,
                valueRange = 0f..100f,
                label = "Bass",
                formattedValue = "${bassLevel.toInt()}%",
                arcColor = NeonGreen,
                size = 72.dp
            )

            RotaryKnob(
                value = trebleLevel,
                onValueChange = onTrebleChange,
                valueRange = 0f..100f,
                label = "Treble",
                formattedValue = "${trebleLevel.toInt()}%",
                arcColor = NeonGreen,
                size = 72.dp
            )
        }

        // 6. Currently playing track subtext at bottom
        val songName = track?.title ?: "Doesn't look like anything to me"
        Text(
            text = songName,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ── TAB 2: STEREO / BALANCE / TEMPO / VOLUME (IMAGE 2) ───────────────────────
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StereoBalanceTab(
    balance: Float,
    onBalanceChange: (Float) -> Unit,
    stereoExpand: Float,
    onStereoExpandChange: (Float) -> Unit,
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    isMono: Boolean,
    onToggleMono: () -> Unit,
    volumeLevel: Float,
    onVolumeChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryKnob(
                value = balance,
                onValueChange = onBalanceChange,
                valueRange = -1.00f..1.00f,
                label = "Balance",
                formattedValue = String.format(Locale.getDefault(), "%.2f", balance),
                arcColor = NeonGreen,
                size = 80.dp,
                isBipolar = true
            )

            RotaryKnob(
                value = stereoExpand,
                onValueChange = onStereoExpandChange,
                valueRange = 0f..100f,
                label = "Stereo Expand",
                formattedValue = "${stereoExpand.toInt()}%",
                arcColor = NeonGreen,
                size = 80.dp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillButton(
                text = "Tempo",
                isActive = true,
                onClick = {},
                modifier = Modifier.width(68.dp)
            )

            RotaryKnob(
                value = tempo,
                onValueChange = onTempoChange,
                valueRange = 0.50f..2.00f,
                label = "",
                formattedValue = String.format(Locale.getDefault(), "%.2fx", tempo),
                arcColor = Color.Transparent,
                size = 96.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PillBackground)
                        .clickable { onTempoChange((tempo + 0.05f).coerceAtMost(2.0f)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PillBackground)
                        .clickable { onTempoChange((tempo - 0.05f).coerceAtLeast(0.5f)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillButton(
                text = "Mono",
                isActive = isMono,
                onClick = onToggleMono,
                modifier = Modifier.width(68.dp)
            )

            RotaryKnob(
                value = volumeLevel,
                onValueChange = onVolumeChange,
                valueRange = 0f..100f,
                label = "Volume",
                formattedValue = "${volumeLevel.toInt()}%",
                arcColor = NeonGreen,
                size = 110.dp
            )

            PillButton(
                text = "Reset",
                isActive = false,
                onClick = onReset,
                modifier = Modifier.width(68.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ── TAB 3: REVERB / SPATIAL FX (IMAGE 3) ─────────────────────────────────────
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReverbTab(
    reverbEnabled: Boolean,
    onToggleReverb: () -> Unit,
    presetName: String,
    onOpenPreset: () -> Unit,
    damp: Float,
    onDampChange: (Float) -> Unit,
    filter: Float,
    onFilterChange: (Float) -> Unit,
    fade: Float,
    onFadeChange: (Float) -> Unit,
    preDelay: Float,
    onPreDelayChange: (Float) -> Unit,
    preDelayMix: Float,
    onPreDelayMixChange: (Float) -> Unit,
    size: Float,
    onSizeChange: (Float) -> Unit,
    mix: Float,
    onMixChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val BlueArc = Color(0xFF0091EA)
    val CyanArc = Color(0xFF00E5FF)
    val YellowArc = Color(0xFFFFEA00)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryKnob(
                value = damp,
                onValueChange = onDampChange,
                valueRange = 0f..1f,
                label = "Damp",
                formattedValue = String.format(Locale.getDefault(), "%.2f", damp),
                arcColor = NeonGreen,
                size = 68.dp
            )
            RotaryKnob(
                value = filter,
                onValueChange = onFilterChange,
                valueRange = 0f..1f,
                label = "Filter",
                formattedValue = String.format(Locale.getDefault(), "%.2f", filter),
                arcColor = BlueArc,
                size = 68.dp
            )
            RotaryKnob(
                value = fade,
                onValueChange = onFadeChange,
                valueRange = 0f..1f,
                label = "Fade",
                formattedValue = String.format(Locale.getDefault(), "%.2f", fade),
                arcColor = CyanArc,
                size = 68.dp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryKnob(
                value = preDelay,
                onValueChange = onPreDelayChange,
                valueRange = 0f..1f,
                label = "Pre-Delay",
                formattedValue = String.format(Locale.getDefault(), "%.2f", preDelay),
                arcColor = NeonGreen,
                size = 68.dp
            )
            RotaryKnob(
                value = preDelayMix,
                onValueChange = onPreDelayMixChange,
                valueRange = 0f..1f,
                label = "Pre-Delay Mix",
                formattedValue = String.format(Locale.getDefault(), "%.2f", preDelayMix),
                arcColor = BlueArc,
                size = 68.dp
            )
            RotaryKnob(
                value = size,
                onValueChange = onSizeChange,
                valueRange = 0f..1f,
                label = "Size",
                formattedValue = String.format(Locale.getDefault(), "%.2f", size),
                arcColor = YellowArc,
                size = 68.dp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                text = "Reverb",
                isActive = reverbEnabled,
                onClick = onToggleReverb,
                modifier = Modifier.width(68.dp)
            )
            PillButton(
                text = presetName.ifBlank { "Preset" },
                isActive = presetName != "Custom",
                onClick = onOpenPreset,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            PillButton(
                text = "Reset",
                isActive = false,
                onClick = onReset,
                modifier = Modifier.width(64.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            RotaryKnob(
                value = mix,
                onValueChange = onMixChange,
                valueRange = 0f..1f,
                label = "Mix",
                formattedValue = String.format(Locale.getDefault(), "%.2f", mix),
                arcColor = BlueArc,
                size = 78.dp
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ── CUSTOM COMPONENTS: ROTARY KNOB, VERTICAL SLIDER, CURVE CANVAS ───────────
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Precision Rotary Knob with responsive touch drag and direct tap tracking.
 * Uses rememberUpdatedState to avoid stale closures.
 */
@Composable
private fun RotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    formattedValue: String,
    arcColor: Color,
    size: Dp,
    isBipolar: Boolean = false
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val fraction = ((currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size + 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(valueRange) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        // Vertical drag: dragging up increases value, dragging down decreases
                        val delta = -dragAmount.y * 0.005f * rangeSpan
                        val updated = (currentValue + delta).coerceIn(valueRange.start, valueRange.endInclusive)
                        currentOnValueChange(updated)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()
                val radius = (this.size.minDimension - strokeWidth) / 2f

                // Background track arc (270 degrees from 135 to 45)
                drawArc(
                    color = KnobTrackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active glowing arc
                if (arcColor != Color.Transparent) {
                    if (isBipolar) {
                        val centerFraction = 0.5f
                        val sweep = (fraction - centerFraction) * 270f
                        drawArc(
                            color = arcColor,
                            startAngle = 135f + centerFraction * 270f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 1f, cap = StrokeCap.Round)
                        )
                    } else {
                        drawArc(
                            color = arcColor,
                            startAngle = 135f,
                            sweepAngle = fraction * 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 1f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner knob disc
                val innerRadius = radius - 4.dp.toPx()
                drawCircle(
                    color = KnobFaceColor,
                    radius = innerRadius,
                    center = center
                )

                // Pointer indicator line/dot
                val angleDeg = 135f + fraction * 270f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dotDist = innerRadius * 0.72f
                val dotX = center.x + (dotDist * cos(angleRad)).toFloat()
                val dotY = center.y + (dotDist * sin(angleRad)).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = Color.White,
                maxLines = 1
            )
        }

        Text(
            text = formattedValue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1
        )
    }
}

/**
 * Vertical Equalizer Slider with smooth dragging and direct tap support.
 * Uses rememberUpdatedState to avoid stale closures.
 */
@Composable
private fun VerticalSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    isPreamp: Boolean = false
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val fraction = ((currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val h = size.height.toFloat()
                        val tapFraction = (1f - (offset.y / h)).coerceIn(0f, 1f)
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        val newVal = valueRange.start + tapFraction * rangeSpan
                        currentOnValueChange(newVal)
                    }
                }
                .pointerInput(valueRange) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val heightPx = size.height.toFloat()
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        val delta = (-dragAmount.y / heightPx) * rangeSpan
                        val updated = (currentValue + delta).coerceIn(valueRange.start, valueRange.endInclusive)
                        currentOnValueChange(updated)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val trackX = w / 2f

                // Inactive vertical track line
                drawLine(
                    color = Color(0xFF242424),
                    start = Offset(trackX, 10.dp.toPx()),
                    end = Offset(trackX, h - 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Side gradation ticks
                val tickCount = 7
                for (i in 0 until tickCount) {
                    val y = 10.dp.toPx() + i * (h - 20.dp.toPx()) / (tickCount - 1)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = 1.dp.toPx(),
                        center = Offset(trackX - 8.dp.toPx(), y)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = 1.dp.toPx(),
                        center = Offset(trackX + 8.dp.toPx(), y)
                    )
                }

                // Active green glowing line from bottom up to thumb position
                val thumbY = (h - 10.dp.toPx()) - fraction * (h - 20.dp.toPx())
                drawLine(
                    color = NeonGreen,
                    start = Offset(trackX, h - 10.dp.toPx()),
                    end = Offset(trackX, thumbY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Dark elongated pill thumb
                val thumbWidth = if (isPreamp) 22.dp.toPx() else 18.dp.toPx()
                val thumbHeight = 32.dp.toPx()
                val thumbRect = Rect(
                    left = trackX - thumbWidth / 2f,
                    top = thumbY - thumbHeight / 2f,
                    right = trackX + thumbWidth / 2f,
                    bottom = thumbY + thumbHeight / 2f
                )

                drawRoundRect(
                    color = Color(0xFF333333),
                    topLeft = thumbRect.topLeft,
                    size = thumbRect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = thumbRect.topLeft,
                    size = thumbRect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // White horizontal dash notch in center of thumb
                drawLine(
                    color = Color.White,
                    start = Offset(trackX - 4.dp.toPx(), thumbY),
                    end = Offset(trackX + 4.dp.toPx(), thumbY),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = String.format(Locale.getDefault(), "%+.1f", currentValue),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

/**
 * Smooth Frequency Response Curve Canvas.
 */
@Composable
private fun FrequencyResponseCanvas(
    bandGains: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        if (bandGains.isEmpty()) return@Canvas

        val stepX = w / (bandGains.size - 1).coerceAtLeast(1)
        val points = bandGains.mapIndexed { index, gain ->
            val x = index * stepX
            val y = midY - (gain / 10f) * (midY * 0.8f)
            Offset(x, y.coerceIn(4f, h - 4f))
        }

        val path = Path()
        val fillPath = Path()

        path.moveTo(points.first().x, points.first().y)
        fillPath.moveTo(points.first().x, h)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx1 = (p0.x + p1.x) / 2f
            val cy1 = p0.y
            val cx2 = (p0.x + p1.x) / 2f
            val cy2 = p1.y
            path.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
            fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
        }

        fillPath.lineTo(points.last().x, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(NeonGreen.copy(alpha = 0.2f), Color.Transparent)
            )
        )

        drawPath(
            path = path,
            color = NeonGreen,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Styled rounded pill control button.
 */
@Composable
private fun PillButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (isActive) Color(0xFF262626) else PillBackground)
            .border(
                1.dp,
                if (isActive) NeonGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) NeonGreen else Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            maxLines = 1
        )
    }
}
