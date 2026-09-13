package com.vibevault.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vibevault.app.R
import com.vibevault.app.constants.EnableSponsorBlockKey
import com.vibevault.app.constants.SponsorBlockShowToastKey
import com.vibevault.app.constants.SponsorBlockSkipInteractionKey
import com.vibevault.app.constants.SponsorBlockSkipIntroOutroKey
import com.vibevault.app.constants.SponsorBlockSkipNonMusicKey
import com.vibevault.app.constants.SponsorBlockSkipPreviewFillerKey
import com.vibevault.app.constants.SponsorBlockSkipSelfPromoKey
import com.vibevault.app.constants.SponsorBlockSkipSponsorKey
import com.vibevault.app.ui.components.Material3SettingsGroup
import com.vibevault.app.ui.components.Material3SettingsItem
import com.vibevault.app.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val (sponsorBlockEnabled, onSponsorBlockEnabledChange) = rememberPreference(
        EnableSponsorBlockKey,
        defaultValue = true
    )
    val (skipNonMusic, onSkipNonMusicChange) = rememberPreference(
        SponsorBlockSkipNonMusicKey,
        defaultValue = true
    )
    val (skipSponsor, onSkipSponsorChange) = rememberPreference(
        SponsorBlockSkipSponsorKey,
        defaultValue = true
    )
    val (skipSelfPromo, onSkipSelfPromoChange) = rememberPreference(
        SponsorBlockSkipSelfPromoKey,
        defaultValue = true
    )
    val (skipInteraction, onSkipInteractionChange) = rememberPreference(
        SponsorBlockSkipInteractionKey,
        defaultValue = true
    )
    val (skipIntroOutro, onSkipIntroOutroChange) = rememberPreference(
        SponsorBlockSkipIntroOutroKey,
        defaultValue = true
    )
    val (skipPreviewFiller, onSkipPreviewFillerChange) = rememberPreference(
        SponsorBlockSkipPreviewFillerKey,
        defaultValue = false
    )
    val (showToast, onShowToastChange) = rememberPreference(
        SponsorBlockShowToastKey,
        defaultValue = true
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sponsorblock)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.sponsorblock_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
            )

            // Master toggle card
            val containerColor by animateColorAsState(
                targetValue = if (sponsorBlockEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                label = "containerColor"
            )

            val contentColor = if (sponsorBlockEnabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Card(
                onClick = { onSponsorBlockEnabledChange(!sponsorBlockEnabled) },
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.enable_sponsorblock),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Switch(
                        checked = sponsorBlockEnabled,
                        onCheckedChange = onSponsorBlockEnabledChange,
                        thumbContent = {
                            Icon(
                                imageVector = if (sponsorBlockEnabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Categories Group
            Material3SettingsGroup(
                title = stringResource(R.string.sponsorblock_categories),
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_non_music)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_non_music_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipNonMusic,
                                onCheckedChange = onSkipNonMusicChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipNonMusic) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipNonMusicChange(!skipNonMusic) }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_sponsor)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_sponsor_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipSponsor,
                                onCheckedChange = onSkipSponsorChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipSponsor) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipSponsorChange(!skipSponsor) }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_selfpromo)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_selfpromo_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipSelfPromo,
                                onCheckedChange = onSkipSelfPromoChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipSelfPromo) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipSelfPromoChange(!skipSelfPromo) }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_interaction)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_interaction_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipInteraction,
                                onCheckedChange = onSkipInteractionChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipInteraction) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipInteractionChange(!skipInteraction) }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_intro_outro)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_intro_outro_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipIntroOutro,
                                onCheckedChange = onSkipIntroOutroChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipIntroOutro) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipIntroOutroChange(!skipIntroOutro) }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_skip_preview_filler)) },
                        description = { Text(stringResource(R.string.sponsorblock_skip_preview_filler_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipPreviewFiller,
                                onCheckedChange = onSkipPreviewFillerChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (skipPreviewFiller) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onSkipPreviewFillerChange(!skipPreviewFiller) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notifications Group
            Material3SettingsGroup(
                title = stringResource(R.string.general),
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.sponsorblock_show_toast)) },
                        description = { Text(stringResource(R.string.sponsorblock_show_toast_desc)) },
                        trailingContent = {
                            Switch(
                                checked = showToast,
                                onCheckedChange = onShowToastChange,
                                enabled = sponsorBlockEnabled,
                                thumbContent = {
                                    Icon(
                                        imageVector = if (showToast) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        enabled = sponsorBlockEnabled,
                        onClick = { onShowToastChange(!showToast) }
                    )
                )
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
