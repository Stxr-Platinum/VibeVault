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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibevault.app.ui.components.UserAvatar
import com.vibevault.app.ui.theme.*

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
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text("Edit Profile", style = MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.onAvatarUrlChange(it.toString()) }
        }

        // Avatar Preview
        UserAvatar(
            avatarUrl = uiState.avatarUrl,
            displayName = uiState.username,
            size = 120.dp,
            modifier = Modifier.clip(CircleShape).clickable { launcher.launch("image/*") }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap to change picture",
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { launcher.launch("image/*") }
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error!!,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = viewModel::saveProfile,
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Save Profile", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
