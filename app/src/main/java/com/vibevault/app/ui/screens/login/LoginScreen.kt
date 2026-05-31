package com.vibevault.app.ui.screens.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.util.Log
import com.vibevault.app.BuildConfig
import com.vibevault.app.ui.theme.*

/**
 * LoginScreen — Matches Stitch "Login / Onboarding" (774a24f2).
 *
 * Layout order (per Stitch design):
 *   1. Brand logo + title + tagline
 *   2. Social auth buttons (Google, Facebook)
 *   3. "OR" divider
 *   4. Email + Password fields
 *   5. Forgot password link
 *   6. Primary "Log In" button
 *   7. Sign up toggle
 *   8. Footer links (TERMS, PRIVACY, SUPPORT)
 *
 * Wrapped in a glassmorphic card container with subtle border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                viewModel.onGoogleIdTokenReceived(idToken, onLoginSuccess)
            }
        } catch (e: ApiException) {
            Log.e("LoginScreen", "Google sign in failed", e)
            viewModel.onError("Google sign in failed: ${e.statusCode} ${e.message}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Brand Logo ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(VibePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "VibeVault",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Vibe Vault",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Secure your sound, own the vibe.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeOnSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // ── Social Auth Buttons ────────────────────────
            OutlinedButton(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("Continue with Google", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { /* Facebook auth */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text("Continue with Facebook", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(32.dp))

            // ── OR Divider ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VibeOnSurfaceVariant.copy(alpha = 0.6f)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Input Fields ───────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF121212)
            ) {
                TextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Email Address", color = VibeOnSurfaceVariant.copy(alpha = 0.4f))
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = VibePrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF121212)
            ) {
                TextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Password", color = VibeOnSurfaceVariant.copy(alpha = 0.4f))
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility
                                              else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = VibeOnSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = VibePrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            // ── Forgot Password ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { }) {
                    Text(
                        "Forgot password?",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeOnSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // ── Error Message ───────────────────────────────
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeError,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Primary Log In Button ───────────────────────
            Button(
                onClick = { viewModel.submit(onLoginSuccess) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibePrimary,
                    contentColor = Color.Black
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (uiState.isSignUp) "Create Account" else "Log In",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Sign Up Toggle ──────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (uiState.isSignUp) "Already have an account? "
                    else "Don't have an account? ",
                    color = VibeOnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (uiState.isSignUp) "Sign In" else "Sign Up",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.clickable { viewModel.toggleMode() }
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(48.dp))

            // ── Footer ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TERMS", style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant.copy(alpha = 0.5f))
                Text("    |    ", style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant.copy(alpha = 0.2f))
                Text("PRIVACY", style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant.copy(alpha = 0.5f))
                Text("    |    ", style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant.copy(alpha = 0.2f))
                Text("SUPPORT", style = MaterialTheme.typography.labelSmall, color = VibeOnSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
