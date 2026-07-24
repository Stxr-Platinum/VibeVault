package com.vibevault.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*

/**
 * ProfileScreen — Matches Stitch "Profile / Settings" (6fa8015e).
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    onLogout: () -> Unit,
    onEditProfileClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Delete account dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var adminPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Navigate to login after account deletion
    LaunchedEffect(uiState.isAccountDeleted) {
        if (uiState.isAccountDeleted) {
            onLogout()
        }
    }

    // ── Delete Account Confirmation Dialog ──────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                adminPassword = ""
                viewModel.clearDeleteError()
            },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            textContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text("Delete Account", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column {
                    Text(
                        "This action is permanent and cannot be undone. Enter admin password to confirm.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = {
                            adminPassword = it
                            viewModel.clearDeleteError()
                        },
                        label = { Text("Admin Password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            unfocusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.deleteError!!,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(adminPassword) },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    adminPassword = ""
                    viewModel.clearDeleteError()
                }) {
                    Text("Cancel", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text("Profile", style = MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        // ── Avatar + Name ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                avatarUrl = uiState.avatarUrl,
                displayName = uiState.username ?: uiState.accountHolderName,
                size = 96.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                uiState.username ?: "VibeVault User",
                style = MaterialTheme.typography.titleLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            if (uiState.accountHolderName != null) {
                Text(
                    uiState.accountHolderName!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                uiState.email ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))

            // Subscription badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.isPremium) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    if (uiState.isPremium) "✦ Premium" else "Free Plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isPremium) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        // ── Settings Sections ──────────────────────────────
        SettingsSection("Account") {
            SettingsItem(Icons.Default.Person, "Edit Profile") { onEditProfileClick() }
            if (!uiState.isPremium) {
                SettingsItem(Icons.Default.WorkspacePremium, "Upgrade to Premium", androidx.compose.material3.MaterialTheme.colorScheme.primary) {
                    onPremiumClick()
                }
            }
        }

        SettingsSection("Playback") {
            SettingsItem(Icons.Default.HighQuality, "Audio Quality") { }
            SettingsItem(Icons.Default.Download, "Download Settings") { }
            SettingsItem(Icons.Default.Equalizer, "Equalizer") { }
        }

        SettingsSection("General") {
            SettingsItem(Icons.Default.Notifications, "Notifications") { }
            SettingsItem(Icons.Default.Storage, "Storage") { }
            SettingsItem(Icons.Default.Info, "About") { }
        }

        Spacer(Modifier.height(16.dp))

        // ── Logout ─────────────────────────────────────────
        TextButton(
            onClick = {
                viewModel.logout()
                onLogout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Icon(Icons.Default.Logout, "Logout", tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Log Out", color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }

        // ── Delete Account ────────────────────────────────
        TextButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Icon(Icons.Default.DeleteForever, "Delete Account", tint = androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            Spacer(Modifier.width(8.dp))
            Text("Delete Account", color = androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "VibeVault v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, "Navigate", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
