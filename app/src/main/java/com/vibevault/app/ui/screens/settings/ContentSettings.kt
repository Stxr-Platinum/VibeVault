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

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size



import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
//
import com.vibevault.app.R
import com.vibevault.app.constants.AppLanguageKey
import com.vibevault.app.constants.ContentCountryKey
import com.vibevault.app.constants.ContentLanguageKey
import com.vibevault.app.constants.SuggestionRegionKey
import com.vibevault.app.constants.SuggestionRegionSlugToName

import com.vibevault.app.constants.CountryCodeToName
import com.vibevault.app.constants.EnableBetterLyricsKey
import com.vibevault.app.constants.EnableMusixmatchKey
import com.vibevault.app.constants.EnableKugouKey
import com.vibevault.app.constants.EnableLrcLibKey
import com.vibevault.app.constants.EnableSimpMusicKey
import com.vibevault.app.constants.EnableYouLyPlusKey
import com.vibevault.app.constants.EnablePaxsenixKey
import com.vibevault.app.constants.HideExplicitKey
import com.vibevault.app.constants.HideVideoSongsKey
import com.vibevault.app.constants.HideYoutubeShortsKey
import com.vibevault.app.constants.AlbumCanvasEnabledKey
import com.vibevault.app.constants.LanguageCodeToName
import com.vibevault.app.constants.LyricsProviderOrderKey
import com.vibevault.app.constants.ProxyEnabledKey
import com.vibevault.app.constants.ProxyPasswordKey
import com.vibevault.app.constants.ProxyTypeKey
import com.vibevault.app.constants.ProxyUrlKey
import com.vibevault.app.constants.ProxyUsernameKey
import com.vibevault.app.constants.QuickPicks
import com.vibevault.app.constants.QuickPicksKey
import com.vibevault.app.constants.RandomizeHomeOrderKey
import com.vibevault.app.constants.SYSTEM_DEFAULT
import com.vibevault.app.constants.ShowArtistDescriptionKey
import com.vibevault.app.constants.ShowArtistSubscriberCountKey
import com.vibevault.app.constants.ShowMonthlyListenersKey
import com.vibevault.app.constants.ShowArtistVideoKey
import com.vibevault.app.constants.ShowArtistBackgroundVideoKey
import com.vibevault.app.constants.ShowWrappedCardKey
import com.vibevault.app.constants.TopSize
import com.vibevault.app.ui.components.EnumDialog
import androidx.compose.material3.IconButton
import com.vibevault.app.ui.components.Material3SettingsGroup
import com.vibevault.app.ui.components.Material3SettingsItem

import com.vibevault.app.utils.rememberEnumPreference
import com.vibevault.app.utils.rememberPreference
import androidx.compose.ui.text.font.FontWeight
//compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.music.innertube.models.IpVersion
import com.vibevault.app.constants.IpVersionKey

//
//
//
import androidx.compose.runtime.mutableStateListOf
import com.vibevault.app.utils.PlaybackLogManager
import com.vibevault.app.ui.components.PlaybackLogsDialog
import androidx.compose.runtime.collectAsState
import java.net.Proxy

import androidx.compose.material3.Scaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (suggestionRegion, onSuggestionRegionChange) = rememberPreference(key = SuggestionRegionKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideoSongs, onHideVideoSongsChange) = rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val (hideYoutubeShorts, onHideYoutubeShortsChange) = rememberPreference(key = HideYoutubeShortsKey, defaultValue = false)
    val (showArtistDescription, onShowArtistDescriptionChange) = rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val (showArtistSubscriberCount, onShowArtistSubscriberCountChange) = rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val (showMonthlyListeners, onShowMonthlyListenersChange) = rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)
    val (showArtistVideo, onShowArtistVideoChange) = rememberPreference(key = ShowArtistVideoKey, defaultValue = true)
    val (showArtistBackgroundVideo, onShowArtistBackgroundVideoChange) = rememberPreference(key = ShowArtistBackgroundVideoKey, defaultValue = true)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "username")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "password")
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableMusixmatch, onEnableMusixmatchChange) = rememberPreference(key = EnableMusixmatchKey, defaultValue = true)
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(key = EnableSimpMusicKey, defaultValue = true)
    val (enableYouLyPlus, onEnableYouLyPlusChange) = rememberPreference(key = EnableYouLyPlusKey, defaultValue = true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(key = EnablePaxsenixKey, defaultValue = true)
    val (lyricsRomanization, onLyricsRomanizationChange) = rememberPreference(key = com.vibevault.app.constants.LyricsRomanizationKey, defaultValue = true)
    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = "",
    )
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (showWrappedCard, onShowWrappedCardChange) = rememberPreference(key = ShowWrappedCardKey, defaultValue = false)
    val (randomizeHomeOrder, onRandomizeHomeOrderChange) = rememberPreference(
        RandomizeHomeOrderKey,
        defaultValue = true
    )
    val (ipVersion, onIpVersionChange) = rememberEnumPreference(
        IpVersionKey,
        defaultValue = IpVersion.AUTO
    )
    val (albumCanvasEnabled, onAlbumCanvasEnabledChange) = rememberPreference(key = AlbumCanvasEnabledKey, defaultValue = false)

    var showPlaybackLogsDialog by rememberSaveable { mutableStateOf(false) }
    var showSuggestionSheet by rememberSaveable { mutableStateOf(false) }
    val playbackLogs by PlaybackLogManager.logs.collectAsState()

    var showProxyConfigurationDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProxyConfigurationDialog) {
        var expandedDropdown by remember { mutableStateOf(false) }

        var tempProxyUrl by rememberSaveable { mutableStateOf(proxyUrl) }
        var tempProxyUsername by rememberSaveable { mutableStateOf(proxyUsername) }
        var tempProxyPassword by rememberSaveable { mutableStateOf(proxyPassword) }
        var authEnabled by rememberSaveable { mutableStateOf(proxyUsername.isNotBlank() || proxyPassword.isNotBlank()) }

        AlertDialog(
            onDismissRequest = { showProxyConfigurationDialog = false },
            title = {
                Text(stringResource(R.string.config_proxy))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = proxyType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.proxy_type)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        onProxyTypeChange(type)
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempProxyUrl,
                        onValueChange = { tempProxyUrl = it },
                        label = { Text(stringResource(R.string.proxy_url)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.enable_authentication))
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = {
                                authEnabled = it
                                if (!it) {
                                    tempProxyUsername = ""
                                    tempProxyPassword = ""
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = authEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempProxyUsername,
                                onValueChange = { tempProxyUsername = it },
                                label = { Text(stringResource(R.string.proxy_username)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tempProxyPassword,
                                onValueChange = { tempProxyPassword = it },
                                label = { Text(stringResource(R.string.proxy_password)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProxyUrlChange(tempProxyUrl)
                        onProxyUsernameChange(if (authEnabled) tempProxyUsername else "")
                        onProxyPasswordChange(if (authEnabled) tempProxyPassword else "")
                        showProxyConfigurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProxyConfigurationDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    var showContentLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentLanguageDialog) {
        EnumDialog(
            onDismiss = { showContentLanguageDialog = false },
            onSelect = {
                onContentLanguageChange(it)
                showContentLanguageDialog = false
            },
            title = stringResource(R.string.content_language),
            current = contentLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showContentCountryDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentCountryDialog) {
        EnumDialog(
            onDismiss = { showContentCountryDialog = false },
            onSelect = {
                onContentCountryChange(it)
                showContentCountryDialog = false
            },
            title = stringResource(R.string.content_country),
            current = contentCountry,
            values = (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showAppLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAppLanguageDialog) {
        EnumDialog(
            onDismiss = { showAppLanguageDialog = false },
            onSelect = {
                onAppLanguageChange(it)
                showAppLanguageDialog = false
            },
            title = stringResource(R.string.app_language),
            current = appLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showProviderPriorityDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderPriorityDialog) {
        // Stubbed out Lyrics Provider Priority dialog
    }

    var showQuickPicksDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showQuickPicksDialog) {
        EnumDialog(
            onDismiss = { showQuickPicksDialog = false },
            onSelect = {
                onQuickPicksChange(it)
                showQuickPicksDialog = false
            },
            title = stringResource(R.string.set_quick_picks),
            current = quickPicks,
            values = QuickPicks.values().toList(),
            valueText = {
                when (it) {
                    QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                    QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                }
            }
        )
    }

    var showTopLengthDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showTopLengthDialog) {
        var tempLength by rememberSaveable { mutableFloatStateOf(lengthTop.toFloat()) }

        AlertDialog(
            onDismissRequest = { showTopLengthDialog = false },
            title = { Text(stringResource(R.string.top_length)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tempLength.toInt().toString())
                    Slider(
                        value = tempLength,
                        onValueChange = { tempLength = it },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLengthTopChange(tempLength.toInt().toString())
                        showTopLengthDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    var showIpVersionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showIpVersionDialog) {
        EnumDialog(
            onDismiss = { showIpVersionDialog = false },
            onSelect = {
                onIpVersionChange(it)
                showIpVersionDialog = false
            },
            title = stringResource(R.string.network_ip_version),
            current = ipVersion,
            values = IpVersion.entries,
            valueText = {
                when (it) {
                    IpVersion.AUTO -> stringResource(R.string.ip_version_auto)
                    IpVersion.IPV4 -> stringResource(R.string.ip_version_ipv4)
                    IpVersion.IPV6 -> stringResource(R.string.ip_version_ipv6)
                }
            }
        )
    }

    if (showPlaybackLogsDialog) {
        PlaybackLogsDialog(
            logs = playbackLogs,
            onClear = { PlaybackLogManager.clearLogs() },
            onDismiss = { showPlaybackLogsDialog = false }
        )
    }

    if (showSuggestionSheet) {
        // SuggestionRegionSheet stubbed
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.content)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
        Material3SettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content_language)) },
                    description = {
                        Text(
                            LanguageCodeToName.getOrElse(contentLanguage) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentLanguageDialog = true },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.content_country)) },
                    description = {
                        Text(
                            CountryCodeToName.getOrElse(contentCountry) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentCountryDialog = true },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text("Suggestions Region") },
                    description = {
                        Text(
                            SuggestionRegionSlugToName.getOrElse(suggestionRegion) { "Global Charts" }
                        )
                    },
                    onClick = { showSuggestionSheet = true },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    trailingContent = {
                        Switch(
                            checked = hideExplicit,
                            onCheckedChange = onHideExplicitChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideExplicit) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideExplicitChange(!hideExplicit) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.hide_video_songs)) },
                    trailingContent = {
                        Switch(
                            checked = hideVideoSongs,
                            onCheckedChange = onHideVideoSongsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideVideoSongs) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideVideoSongsChange(!hideVideoSongs) },
                    isExpressive = true
                ),

                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.hide_youtube_shorts)) },
                    trailingContent = {
                        Switch(
                            checked = hideYoutubeShorts,
                            onCheckedChange = onHideYoutubeShortsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideYoutubeShorts) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideYoutubeShortsChange(!hideYoutubeShorts) },
                    isExpressive = true
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.artist_page_settings),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.show_artist_description)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistDescription,
                            onCheckedChange = onShowArtistDescriptionChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistDescription) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistDescriptionChange(!showArtistDescription) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.person),
                    title = { Text(stringResource(R.string.show_artist_subscriber_count)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistSubscriberCount,
                            onCheckedChange = onShowArtistSubscriberCountChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistSubscriberCount) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistSubscriberCountChange(!showArtistSubscriberCount) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.person),
                    title = { Text(stringResource(R.string.show_artist_monthly_listeners)) },
                    trailingContent = {
                        Switch(
                            checked = showMonthlyListeners,
                            onCheckedChange = onShowMonthlyListenersChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showMonthlyListeners) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowMonthlyListenersChange(!showMonthlyListeners) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.show_artist_video)) },
                    description = { Text(stringResource(R.string.show_artist_video_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistVideo,
                            onCheckedChange = onShowArtistVideoChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistVideo) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistVideoChange(!showArtistVideo) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.show_artist_background_video)) },
                    description = { Text(stringResource(R.string.show_artist_background_video_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistBackgroundVideo,
                            onCheckedChange = onShowArtistBackgroundVideoChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistBackgroundVideo) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistBackgroundVideoChange(!showArtistBackgroundVideo) },
                    isExpressive = true,
                    descriptionBelow = true
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.album_text),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.show_album_canvas)) },
                    description = { Text(stringResource(R.string.show_album_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = albumCanvasEnabled,
                            onCheckedChange = onAlbumCanvasEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (albumCanvasEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAlbumCanvasEnabledChange(!albumCanvasEnabled) },
                    isExpressive = true,
                    descriptionBelow = true
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.app_language),
            items = listOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        },
                        isExpressive = true
                    )
                } else {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        description = {
                            Text(
                                LanguageCodeToName.getOrElse(appLanguage) { stringResource(R.string.system_default) }
                            )
                        },
                        onClick = { showAppLanguageDialog = true },
                        isExpressive = true
                    )
                }
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.proxy),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.network_node),
                    title = { Text(stringResource(R.string.network_ip_version)) },
                    description = {
                        Text(
                            when (ipVersion) {
                                IpVersion.AUTO -> stringResource(R.string.ip_version_auto)
                                IpVersion.IPV4 -> stringResource(R.string.ip_version_ipv4)
                                IpVersion.IPV6 -> stringResource(R.string.ip_version_ipv6)
                            }
                        )
                    },
                    onClick = { showIpVersionDialog = true },
                    isExpressive = true
                ))
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi_proxy),
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        trailingContent = {
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = onProxyEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (proxyEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onProxyEnabledChange(!proxyEnabled) },
                        isExpressive = true
                    )
                )
                if (proxyEnabled) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.settings),
                            title = { Text(stringResource(R.string.config_proxy)) },
                            onClick = { showProxyConfigurationDialog = true },
                            isExpressive = true
                        )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_lrclib)) },
                    trailingContent = {
                        Switch(
                            checked = enableLrclib,
                            onCheckedChange = onEnableLrclibChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableLrclib) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableLrclibChange(!enableLrclib) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_kugou)) },
                    trailingContent = {
                        Switch(
                            checked = enableKugou,
                            onCheckedChange = onEnableKugouChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableKugou) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableKugouChange(!enableKugou) },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_better_lyrics)) },
                    description = { Text(stringResource(R.string.enable_better_lyrics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableBetterLyrics,
                            onCheckedChange = onEnableBetterLyricsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableBetterLyrics) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableBetterLyricsChange(!enableBetterLyrics) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text("Musixmatch") },
                    description = { Text("World's largest lyrics database (supports word-level and line-level sync)") },
                    trailingContent = {
                        Switch(
                            checked = enableMusixmatch,
                            onCheckedChange = onEnableMusixmatchChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableMusixmatch) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableMusixmatchChange(!enableMusixmatch) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_simpmusic)) },
                    description = { Text(stringResource(R.string.enable_simpmusic_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableSimpMusic,
                            onCheckedChange = onEnableSimpMusicChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableSimpMusic) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableSimpMusicChange(!enableSimpMusic) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text("YouLyPlus") },
                    description = { Text("LyricsPlus multi-server provider (YouLy+ extension backend)") },
                    trailingContent = {
                        Switch(
                            checked = enableYouLyPlus,
                            onCheckedChange = onEnableYouLyPlusChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableYouLyPlus) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableYouLyPlusChange(!enableYouLyPlus) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text("PaxSenix") },
                    description = { Text("Apple Music quality synced lyrics with syllable-level timing") },
                    trailingContent = {
                        Switch(
                            checked = enablePaxsenix,
                            onCheckedChange = onEnablePaxsenixChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enablePaxsenix) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnablePaxsenixChange(!enablePaxsenix) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_provider_priority)) },
                    description = { Text(stringResource(R.string.lyrics_provider_priority_desc)) },
                    onClick = { showProviderPriorityDialog = true },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanization)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanization,
                            onCheckedChange = onLyricsRomanizationChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanization) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizationChange(!lyricsRomanization) },
                    isExpressive = true
                )
            )
        )
//        Spacer(modifier = Modifier.height(27.dp))
//
//        Material3SettingsGroup(
//            title = "Wrapped",
//            items = listOf(
//                Material3SettingsItem(
//                    icon = painterResource(R.drawable.trending_up),
//                    title = { Text(stringResource(R.string.show_wrapped_card)) },
//                    trailingContent = {
//                        Switch(
//                            checked = showWrappedCard,
//                            onCheckedChange = onShowWrappedCardChange,
//                            thumbContent = {
//                                Icon(
//                                    painter = painterResource(
//                                        id = if (showWrappedCard) R.drawable.check else R.drawable.close
//                                    ),
//                                    contentDescription = null,
//                                    modifier = Modifier.size(SwitchDefaults.IconSize)
//                                )
//                            }
//                        )
//                    },
//                    onClick = { onShowWrappedCardChange(!showWrappedCard) }
//                )
//            )
//        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.randomize_home_order)) },
                    description = { Text(stringResource(R.string.randomize_home_order_desc)) },
                    trailingContent = {
                        Switch(
                            checked = randomizeHomeOrder,
                            onCheckedChange = onRandomizeHomeOrderChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (randomizeHomeOrder) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onRandomizeHomeOrderChange(!randomizeHomeOrder) },
                    isExpressive = true,
                    descriptionBelow = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.top_length)) },
                    description = { Text(lengthTop) },
                    onClick = { showTopLengthDialog = true },
                    isExpressive = true
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.home_outlined),
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    description = {
                        Text(
                            when (quickPicks) {
                                QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                                QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            }
                        )
                    },
                    onClick = { showQuickPicksDialog = true },
                    isExpressive = true
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.logs_heading),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bug_report),
                    title = { Text(stringResource(R.string.playback_logs)) },
                    description = { Text(stringResource(R.string.playback_logs_desc)) },
                    onClick = { showPlaybackLogsDialog = true },
                    isExpressive = true,
                    descriptionBelow = true
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
