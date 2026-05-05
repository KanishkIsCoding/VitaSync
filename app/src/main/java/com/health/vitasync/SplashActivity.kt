package com.health.vitasync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.vitasync.ui.theme.VitaSyncTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitaSyncTheme  {
                SplashScreen {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    // ── Animation states ──────────────────────────────────────────────────────
    var started by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "logoAlpha"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400, easing = EaseOutCubic),
        label = "titleAlpha"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(600, delayMillis = 650, easing = EaseOutCubic),
        label = "subtitleAlpha"
    )

    val taglineAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(600, delayMillis = 900, easing = EaseOutCubic),
        label = "taglineAlpha"
    )

    // ── Pulse ring animation ──────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    // ── Arc progress animation ────────────────────────────────────────────────
    val arcProgress by animateFloatAsState(
        targetValue = if (started) 270f else 0f,
        animationSpec = tween(1200, delayMillis = 200, easing = EaseOutCubic),
        label = "arcProgress"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(2800)
        onFinished()
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    val bgTop = Color(0xFF0A0F1E)
    val bgBottom = Color(0xFF0D1B2A)
    val accentGreen = Color(0xFF00E5A0)
    val accentBlue = Color(0xFF00B4D8)
    val accentGlow = Color(0xFF00E5A0).copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(bgTop, bgBottom))
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Background decorative circles ─────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF00E5A0).copy(alpha = 0.04f),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.85f, size.height * 0.1f)
            )
            drawCircle(
                color = Color(0xFF00B4D8).copy(alpha = 0.04f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.1f, size.height * 0.85f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo Icon with rings and arc ──────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            ) {
                // Outer pulse ring
                Canvas(modifier = Modifier.size(180.dp)) {
                    drawCircle(
                        color = accentGreen.copy(alpha = pulseAlpha),
                        radius = size.minDimension / 2f * pulseScale,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Progress arc
                Canvas(modifier = Modifier.size(150.dp)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(accentBlue, accentGreen, accentBlue)
                        ),
                        startAngle = -225f,
                        sweepAngle = arcProgress,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner glow circle
                Canvas(modifier = Modifier.size(110.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accentGlow, Color.Transparent)
                        )
                    )
                }

                // Icon - heart pulse lines
                Canvas(modifier = Modifier.size(60.dp)) {
                    val w = size.width
                    val h = size.height
                    val mid = h / 2f
                    val points = listOf(
                        Offset(0f, mid),
                        Offset(w * 0.15f, mid),
                        Offset(w * 0.28f, h * 0.1f),
                        Offset(w * 0.42f, h * 0.9f),
                        Offset(w * 0.55f, mid * 0.4f),
                        Offset(w * 0.68f, mid * 1.6f),
                        Offset(w * 0.78f, mid),
                        Offset(w, mid)
                    )
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(accentBlue, accentGreen),
                                start = points[i],
                                end = points[i + 1]
                            ),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── App Name ──────────────────────────────────────────────────────
            Text(
                text = "VITA",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 12.sp,
                modifier = Modifier.alpha(titleAlpha)
            )

            // ── Subtitle ──────────────────────────────────────────────────────
            Text(
                text = "HEALTH CONNECT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accentGreen,
                letterSpacing = 6.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Tagline ───────────────────────────────────────────────────────
            Text(
                text = "Your health data,\nbeautifully exported.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }

        // ── Bottom version tag ────────────────────────────────────────────────
        Text(
            text = "v1.0",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(taglineAlpha)
        )
    }
}