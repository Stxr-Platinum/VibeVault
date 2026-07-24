/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.vibevault.app.ui.screens.settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize



import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vibevault.app.BuildConfig
import com.vibevault.app.R
import com.vibevault.app.constants.AudioNormalizationKey
import com.vibevault.app.constants.AudioOffload
import com.vibevault.app.constants.AudioQuality
import com.vibevault.app.constants.AudioQualityKey
import com.vibevault.app.constants.EnableSaavnStreamingKey
import com.vibevault.app.constants.SaavnAudioQuality
import com.vibevault.app.constants.SaavnAudioQualityKey
import com.vibevault.app.constants.AutoDownloadOnLikeKey
import com.vibevault.app.constants.CrossfadeDurationKey
import com.vibevault.app.constants.CrossfadeEnabledKey
import com.vibevault.app.constants.CrossfadeGaplessKey
import com.vibevault.app.constants.AutoLoadMoreKey
import com.vibevault.app.constants.AutoSkipNextOnErrorKey
import com.vibevault.app.constants.DisableLoadMoreWhenRepeatAllKey
import com.vibevault.app.constants.EnableGoogleCastKey
import com.vibevault.app.constants.HistoryDuration
import com.vibevault.app.constants.KeepScreenOn
import com.vibevault.app.constants.PauseOnMute
import com.vibevault.app.constants.PersistentQueueKey
import com.vibevault.app.constants.PersistentShuffleAcrossQueuesKey
import com.vibevault.app.constants.PreventDuplicateTracksInQueueKey
import com.vibevault.app.constants.RememberShuffleAndRepeatKey
import com.vibevault.app.constants.ResumeOnBluetoothConnectKey
import com.vibevault.app.constants.SeekExtraSeconds
import com.vibevault.app.constants.ShufflePlaylistFirstKey
import com.vibevault.app.constants.SimilarContent
import com.vibevault.app.constants.SkipSilenceInstantKey
import com.vibevault.app.constants.SkipSilenceKey
import com.vibevault.app.constants.StopMusicOnTaskClearKey
import com.vibevault.app.ui.components.DefaultDialog
import com.vibevault.app.ui.components.EnumDialog
import com.vibevault.app.ui.components.Material3SettingsGroup
import com.vibevault.app.ui.components.Material3SettingsItem
import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference
import kotlin.math.roundToInt

import androidx.compose.material3.Scaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    

    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(
        SkipSilenceInstantKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )

    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        key = AudioOffload,
        defaultValue = false
    )

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (disableLoadMoreWhenRepeatAll, onDisableLoadMoreWhenRepeatAllChange) = rememberPreference(
        DisableLoadMoreWhenRepeatAllKey,
        defaultValue = false
    )
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange) = rememberPreference(
        PersistentShuffleAcrossQueuesKey,
        defaultValue = false
    )
    val (rememberShuffleAndRepeat, onRememberShuffleAndRepeatChange) = rememberPreference(
        RememberShuffleAndRepeatKey,
        defaultValue = true
    )
    val (shufflePlaylistFirst, onShufflePlaylistFirstChange) = rememberPreference(
        ShufflePlaylistFirstKey,
        defaultValue = false
    )
    val (preventDuplicateTracksInQueue, onPreventDuplicateTracksInQueueChange) = rememberPreference(
        PreventDuplicateTracksInQueueKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = false
    )
    val (resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange) = rememberPreference(
        ResumeOnBluetoothConnectKey,
        defaultValue = false
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )
    val (saavnEnabled, _) = rememberPreference(
        EnableSaavnStreamingKey,
        defaultValue = false
    )
    val (saavnQuality, _) = rememberEnumPreference(
        SaavnAudioQualityKey,
        defaultValue = SaavnAudioQuality.QUALITY_320
    )

    var showAudioQualityDialog by remember {
        mutableStateOf(false)
    }

    if (showAudioQualityDialog) {
        EnumDialog(
            onDismiss = { showAudioQualityDialog = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog = false
            },
            title = "",
            current = audioQuality,
            values = AudioQuality.values().toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> ""
                    AudioQuality.HIGH -> ""
                    AudioQuality.LOW -> ""
                }
            }
        )
    }



    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(android.R.drawable.ic_menu_gallery), contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
        var showCrossfadeBetaDialog by remember { mutableStateOf(false) }

        if (showCrossfadeBetaDialog) {
            DefaultDialog(
                onDismiss = { showCrossfadeBetaDialog = false },
                title = { Text("") },
                buttons = {
                    TextButton(onClick = { showCrossfadeBetaDialog = false }) {
                        Text("")
                    }
                    TextButton(onClick = {
                        showCrossfadeBetaDialog = false
                        onCrossfadeEnabledChange(true)
                    }) {
                        Text("")
                    }
                }
            ) {
                Text("")
            }
        }

        Spacer(
            Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = "",
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = {
                        Text(
                            when (audioQuality) {
                                AudioQuality.AUTO -> ""
                                AudioQuality.HIGH -> ""
                                AudioQuality.LOW -> ""
                            }
                        )
                    },
                    onClick = { showAudioQualityDialog = true },
                    isExpressive = true
                ))
                // JioSaavn settings navigation
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = {
                        Text(
                            if (saavnEnabled) {
                                saavnQuality.toLabel()
                            } else {
                                ""
                            }
                        )
                    },
                    onClick = { navController.navigate("settings/player/jio") },
                    isExpressive = true
                ))
                // YouTube Extractor Settings
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    onClick = { navController.navigate("settings/player/cipher") },
                    isExpressive = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    showBadge = true,
                    trailingContent = {
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = {
                                if (!crossfadeEnabled) {
                                    showCrossfadeBetaDialog = true
                                } else {
                                    onCrossfadeEnabledChange(false)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (crossfadeEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        if (!crossfadeEnabled) {
                            showCrossfadeBetaDialog = true
                        } else {
                            onCrossfadeEnabledChange(false)
                        }
                    },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                if (crossfadeEnabled) {
                    add(Material3SettingsItem(
                        icon = painterResource(android.R.drawable.ic_menu_gallery),
                        title = { Text("") },
                        description = {
                            Column {
                                Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                Slider(
                                    value = crossfadeDuration,
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 1f..15f,
                                    steps = 14
                                )
                            }
                        },
                        isExpressive = true,
                        descriptionBelow = true
                    ))
                    add(Material3SettingsItem(
                        icon = painterResource(android.R.drawable.ic_menu_gallery),
                        title = { Text("") },
                        description = { Text("") },
                        trailingContent = {
                            Switch(
                                checked = crossfadeGapless,
                                onCheckedChange = onCrossfadeGaplessChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (crossfadeGapless) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onCrossfadeGaplessChange(!crossfadeGapless) },
                        isExpressive = true,
                        descriptionBelow = true
                    ))
                }
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = {
                        Column {
                            Text(historyDuration.roundToInt().toString())
                            Slider(
                                value = historyDuration,
                                onValueChange = onHistoryDurationChange,
                                valueRange = 1f..100f
                            )
                        }
                    },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = onSkipSilenceChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (skipSilence) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSkipSilenceChange(!skipSilence) },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = skipSilenceInstant,
                            onCheckedChange = { onSkipSilenceInstantChange(it) },
                            enabled = skipSilence,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (skipSilenceInstant) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (skipSilence) onSkipSilenceInstantChange(!skipSilenceInstant) },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = audioNormalization,
                            onCheckedChange = onAudioNormalizationChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (audioNormalization) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAudioNormalizationChange(!audioNormalization) },
                    isExpressive = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = {
                        Text(
                            if (crossfadeEnabled) ""
                            else ""
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = if (crossfadeEnabled) false else audioOffload,
                            onCheckedChange = onAudioOffloadChange,
                            enabled = !crossfadeEnabled,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (!crossfadeEnabled && audioOffload) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (!crossfadeEnabled) onAudioOffloadChange(!audioOffload) },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                // Only show Cast setting in GMS builds (not in F-Droid/FOSS)
                if (false) {
                    add(Material3SettingsItem(
                        icon = painterResource(android.R.drawable.ic_menu_gallery),
                        title = { Text("") },
                        description = { Text("") },
                        trailingContent = {
                            Switch(
                                checked = enableGoogleCast,
                                onCheckedChange = onEnableGoogleCastChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (enableGoogleCast) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onEnableGoogleCastChange(!enableGoogleCast) },
                        isExpressive = true,
                        descriptionBelow = true
                    ))
                }
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = seekExtraSeconds,
                            onCheckedChange = onSeekExtraSeconds,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (seekExtraSeconds) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSeekExtraSeconds(!seekExtraSeconds) },
                    isExpressive = true,
                    descriptionBelow = true
                ))
                add(Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    onClick = { navController.navigate("settings/equalizer") },
                    isExpressive = true,
                    descriptionBelow = true
                ))
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = "",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = persistentQueue,
                            onCheckedChange = onPersistentQueueChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentQueue) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentQueueChange(!persistentQueue) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = autoLoadMore,
                            onCheckedChange = onAutoLoadMoreChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoLoadMore) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoLoadMoreChange(!autoLoadMore) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = disableLoadMoreWhenRepeatAll,
                            onCheckedChange = onDisableLoadMoreWhenRepeatAllChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (disableLoadMoreWhenRepeatAll) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onDisableLoadMoreWhenRepeatAllChange(!disableLoadMoreWhenRepeatAll) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = autoDownloadOnLike,
                            onCheckedChange = onAutoDownloadOnLikeChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoDownloadOnLike) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoDownloadOnLikeChange(!autoDownloadOnLike) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = similarContentEnabled,
                            onCheckedChange = similarContentEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (similarContentEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { similarContentEnabledChange(!similarContentEnabled) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = persistentShuffleAcrossQueues,
                            onCheckedChange = onPersistentShuffleAcrossQueuesChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentShuffleAcrossQueues) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentShuffleAcrossQueuesChange(!persistentShuffleAcrossQueues) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = rememberShuffleAndRepeat,
                            onCheckedChange = onRememberShuffleAndRepeatChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (rememberShuffleAndRepeat) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onRememberShuffleAndRepeatChange(!rememberShuffleAndRepeat) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = shufflePlaylistFirst,
                            onCheckedChange = onShufflePlaylistFirstChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (shufflePlaylistFirst) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShufflePlaylistFirstChange(!shufflePlaylistFirst) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = preventDuplicateTracksInQueue,
                            onCheckedChange = onPreventDuplicateTracksInQueueChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (preventDuplicateTracksInQueue) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPreventDuplicateTracksInQueueChange(!preventDuplicateTracksInQueue) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    description = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = autoSkipNextOnError,
                            onCheckedChange = onAutoSkipNextOnErrorChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoSkipNextOnError) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoSkipNextOnErrorChange(!autoSkipNextOnError) },
                    isExpressive = true,
                    descriptionBelow = true
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = "",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = stopMusicOnTaskClear,
                            onCheckedChange = onStopMusicOnTaskClearChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (stopMusicOnTaskClear) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onStopMusicOnTaskClearChange(!stopMusicOnTaskClear) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = pauseOnMute,
                            onCheckedChange = onPauseOnMuteChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (pauseOnMute) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPauseOnMuteChange(!pauseOnMute) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = resumeOnBluetoothConnect,
                            onCheckedChange = onResumeOnBluetoothConnectChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (resumeOnBluetoothConnect) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onResumeOnBluetoothConnectChange(!resumeOnBluetoothConnect) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(android.R.drawable.ic_menu_gallery),
                    title = { Text("") },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (keepScreenOn) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onKeepScreenOnChange(!keepScreenOn) },
                    isExpressive = true
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
