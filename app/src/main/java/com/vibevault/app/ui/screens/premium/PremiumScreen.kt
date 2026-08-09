package com.vibevault.app.ui.screens.premium

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

data class LinePath(
    val path: Path = Path(),
    val points: MutableList<Offset> = mutableListOf()
)

@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedPlanForSignature by remember { mutableStateOf<PremiumPlan?>(null) }
    var isSubscribed by remember { mutableStateOf(false) }

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
            price = "₹99.99 / month",
            note = "After trial period.",
            accentGradient = listOf(androidx.compose.material3.MaterialTheme.colorScheme.primary, androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
        ),
        
        PremiumPlan(
            name = "Family",
            accounts = "Up to 6 accounts",
            price = "₹250.99 / month",
            note = "For people living together.",
            accentGradient = listOf(Color(0xFF6C63FF), Color(0xFF9D97FF))
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
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
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                                    androidx.compose.material3.MaterialTheme.colorScheme.background
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
                                    imageVector = Icons.Default.ArrowBack,
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
                            text = if (isSubscribed) "You are Premium! ✨" else "Try Premium\nFree",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 42.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // CTA button
                        Button(
                            onClick = { selectedPlanForSignature = plans[0] },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "PREMIUM ACTIVE" else "GET STARTED",
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
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
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
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp)
                )
            }

            items(plans) { plan ->
                PlanCard(
                    plan = plan,
                    onSelect = { selectedPlanForSignature = plan }
                )
            }

            // ── Footer / Terms ────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "3 months free only open to users who haven't tried Premium before. Subscription auto-renews at ₹9.99/month unless cancelled. Terms apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Not now", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Native Signature Pad Dialog
    selectedPlanForSignature?.let { plan ->
        ReceiptSignatureDialog(
            plan = plan,
            onDismiss = { selectedPlanForSignature = null },
            onConfirm = {
                isSubscribed = true
                Toast.makeText(
                    context,
                    "🎉 Welcome to Premium! Subscribed to ${plan.name}.",
                    Toast.LENGTH_LONG
                ).show()
                selectedPlanForSignature = null
            }
        )
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
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Plan Card ───────────────────────────────────────────────
@Composable
fun PlanCard(plan: PremiumPlan, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
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
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.accounts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = plan.price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface),
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
// ── Native Jetpack Compose Signature Dialog ─────────────────
@Composable
fun ReceiptSignatureDialog(
    plan: PremiumPlan,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val paths = remember { mutableStateListOf<List<Offset>>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val hasSignature = paths.isNotEmpty() || currentPoints.isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF181818),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Receipt Authorization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Order summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(plan.name, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                            Text(plan.price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Estimated Tax (8%)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("₹8.00", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                        Divider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(plan.price, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00E9FF))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Customer Signature",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Sign below to authorize subscription",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                // Canvas for drawing signature
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101010))
                        .border(1.dp, Color(0xFF00E9FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var points = listOf(down.position)
                                    currentPoints = points
                                    down.consume()

                                    drag(down.id) { change ->
                                        change.consume()
                                        points = points + change.position
                                        currentPoints = points
                                    }

                                    if (points.isNotEmpty()) {
                                        paths.add(points)
                                        currentPoints = emptyList()
                                    }
                                }
                            }
                    ) {
                        val strokeStyle = Stroke(
                            width = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )

                        // Render finished paths
                        paths.forEach { pts ->
                            if (pts.size > 1) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(path = path, color = Color(0xFF00E9FF), style = strokeStyle)
                            } else if (pts.size == 1) {
                                drawCircle(color = Color(0xFF00E9FF), radius = 3.dp.toPx(), center = pts.first())
                            }
                        }

                        // Render active drawing path
                        if (currentPoints.size > 1) {
                            val path = Path().apply {
                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(path = path, color = Color(0xFF00E9FF), style = strokeStyle)
                        } else if (currentPoints.size == 1) {
                            drawCircle(color = Color(0xFF00E9FF), radius = 3.dp.toPx(), center = currentPoints.first())
                        }
                    }

                    TextButton(
                        onClick = {
                            paths.clear()
                            currentPoints = emptyList()
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Text("Clear", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onConfirm,
                    enabled = hasSignature,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E9FF),
                        contentColor = Color.Black,
                        disabledContainerColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = if (hasSignature) "CONFIRM & SUBSCRIBE" else "SIGN ABOVE TO PROCEED",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
