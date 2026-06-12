package com.vibevault.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*
import java.io.File

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VibeOnSurface)
            }
            Spacer(Modifier.weight(1f))
            Text("Edit Profile", style = MaterialTheme.typography.titleMedium, color = VibeOnSurface)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.onImagePicked(it) }
        }

        // Avatar Preview — use File for local paths, or UserAvatar for URLs
        val avatarUrl = uiState.avatarUrl
        val avatarModel: Any? = when {
            avatarUrl.startsWith("/") -> File(avatarUrl) // Internal storage path
            avatarUrl.isNotBlank() -> avatarUrl           // URL or content:// URI
            else -> null
        }

        if (avatarModel != null) {
            SubcomposeAsyncImage(
                model = avatarModel,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable { launcher.launch("image/*") },
                contentScale = ContentScale.Crop,
                loading = {
                    UserAvatar(
                        avatarUrl = null,
                        displayName = uiState.username,
                        size = 120.dp
                    )
                },
                error = {
                    UserAvatar(
                        avatarUrl = null,
                        displayName = uiState.username,
                        size = 120.dp
                    )
                }
            )
        } else {
            UserAvatar(
                avatarUrl = null,
                displayName = uiState.username,
                size = 120.dp,
                modifier = Modifier.clip(CircleShape).clickable { launcher.launch("image/*") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap to change picture",
            style = MaterialTheme.typography.labelMedium,
            color = VibePrimary,
            modifier = Modifier.clickable { launcher.launch("image/*") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::saveAvatar,
            colors = ButtonDefaults.buttonColors(containerColor = VibePrimary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(40.dp)
        ) {
            if (uiState.isAvatarLoading) {
                CircularProgressIndicator(color = VibeOnPrimary, modifier = Modifier.size(20.dp))
            } else {
                Text("Save Avatar", color = VibeOnPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VibeOnSurface,
                unfocusedTextColor = VibeOnSurface,
                focusedBorderColor = VibePrimary,
                unfocusedBorderColor = VibeOutlineVariant,
                focusedLabelColor = VibePrimary,
                unfocusedLabelColor = VibeOnSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error!!,
                color = VibeError,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = viewModel::saveUsername,
            colors = ButtonDefaults.buttonColors(containerColor = VibePrimary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState.isUsernameLoading) {
                CircularProgressIndicator(color = VibeOnPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Save Username", color = VibeOnPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
