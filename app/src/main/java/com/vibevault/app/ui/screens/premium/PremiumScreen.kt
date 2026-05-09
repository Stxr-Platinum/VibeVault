package com.vibevault.app.ui.screens.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevault.app.ui.theme.*

// ── Data classes ────────────────────────────────────────────
data class PremiumFeature(val icon: ImageVector, val title: String, val subtitle: String)
data class PremiumPlan(
    val name: String,
    val accounts: String,
    val price: String,
    val note: String,
    val accentGradient: List<Color>
)

@Composable
fun PremiumScreen(onBack: () -> Unit) {

    val features = listOf(
        PremiumFeature(Icons.Default.MusicOff, "Ad-free music", "Enjoy uninterrupted music without interruptions."),
        PremiumFeature(Icons.Default.CloudDownload, "Download offline", "Take your music everywhere, even without data."),
        PremiumFeature(Icons.Default.PlayCircle, "Play any song", "On mobile, any track is just a tap away."),
        PremiumFeature(Icons.Default.Equalizer, "High quality audio", "Hear the music exactly as the artists intended."),
        PremiumFeature(Icons.Default.Groups, "Listen with friends", "Start a Jam and listen together in real-time.")
    )

    val plans = listOf(
        PremiumPlan(
            name = "Agri Special",
            accounts = "1 account",
            price = "₹9.99 / month",
            note = "After trial period.",
            accentGradient = listOf(Color(0xFF1DB954), Color(0xFF53E076))
        ),
        PremiumPlan(
            name = "Chintu Special",
            accounts = "1 account",
            price = "₹9.99 / month",
            note = "Eligible students only.",
            accentGradient = listOf(Color(0xFFFF767B), Color(0xFFFFB3B3))
        ),
        PremiumPlan(
            name = "Family",
            accounts = "Up to 6 accounts",
            price = "₹15.99 / month",
            note = "For people living together.",
            accentGradient = listOf(Color(0xFF6C63FF), Color(0xFF9D97FF))
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Hero Section ────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1DB954),
                                    Color(0xFF0D7A35),
                                    VibeBg
                                )
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        // Top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "VIBE VAULT",
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 2.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.size(40.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title
                        Text(
                            text = "Try Premium\nFree",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 42.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // CTA button
                        Button(
                            onClick = { /* TODO */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "GET STARTED",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "3 months free · Then ₹9.99/month",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }

            // ── "Why go Premium?" Section ──────────────────────
            item {
                Text(
                    text = "Why go Premium?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeOnSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp)
                )
            }

            items(features) { feature ->
                FeatureRow(feature)
            }

            // ── "Pick your plan" Section ──────────────────────
            item {
                Text(
                    text = "Pick your plan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeOnSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp)
                )
            }

            items(plans) { plan ->
                PlanCard(plan)
            }

            // ── Footer / Terms ────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "3 months free only open to users who haven't tried Premium before. Subscription auto-renews at ₹9.99/month unless cancelled. Terms apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeOnSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Not now", color = VibeOnSurfaceVariant)
                }
            }
        }
    }
}

// ── Feature Row ─────────────────────────────────────────────
@Composable
fun FeatureRow(feature: PremiumFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VibeSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = VibePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VibeOnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VibeOnSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Plan Card ───────────────────────────────────────────────
@Composable
fun PlanCard(plan: PremiumPlan) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = VibeSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Gradient accent bar at top of card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(colors = plan.accentGradient)
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VibeOnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.accounts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = plan.price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VibeOnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { /* TODO */ },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeOnSurface),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "GET ${plan.name.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
