package com.vibrosub.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibrosub.player.ui.theme.BassPunchOrange
import com.vibrosub.player.ui.theme.ElectricCyan
import com.vibrosub.player.ui.theme.SubwooferViolet
import com.vibrosub.player.ui.theme.SurfaceDarkElevated
import com.vibrosub.player.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SubwooferVisualizer(
    bassPulse: Float,
    spectrumBars: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth animated pulse with quick attack and smooth decay
    val animatedPulse = remember { Animatable(0f) }

    LaunchedEffect(bassPulse) {
        animatedPulse.animateTo(
            targetValue = bassPulse,
            animationSpec = tween(durationMillis = 35, easing = LinearEasing)
        )
    }

    // Continuous idle breathing ring transition when playing
    val infiniteTransition = rememberInfiniteTransition(label = "ring_transition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDarkElevated)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Subwoofer Animated Cone
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.44f
                val pulse = if (isPlaying) animatedPulse.value else 0f

                // 1. Radiating Acoustic Shockwaves
                if (isPlaying && pulse > 0.2f) {
                    val shockRadius1 = baseRadius * (1f + wavePhase * 0.35f * pulse)
                    val alpha1 = ((1f - wavePhase) * 0.7f * pulse).coerceIn(0f, 1f)
                    drawCircle(
                        color = ElectricCyan.copy(alpha = alpha1),
                        radius = shockRadius1,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    val shockRadius2 = baseRadius * (1.1f + ((wavePhase + 0.5f) % 1f) * 0.25f * pulse)
                    val alpha2 = ((1f - ((wavePhase + 0.5f) % 1f)) * 0.4f * pulse).coerceIn(0f, 1f)
                    drawCircle(
                        color = SubwooferViolet.copy(alpha = alpha2),
                        radius = shockRadius2,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 2. Outer Metal Speaker Basket & Bolts
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2B3245), Color(0xFF13161F)),
                        center = center,
                        radius = baseRadius
                    ),
                    radius = baseRadius,
                    center = center
                )

                drawCircle(
                    color = Color(0xFF384259),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // 8 Speaker Rim Hex Screws
                for (i in 0 until 8) {
                    val angle = (i * Math.PI / 4.0).toFloat()
                    val screwDist = baseRadius * 0.93f
                    val screwCenter = Offset(
                        center.x + screwDist * cos(angle),
                        center.y + screwDist * sin(angle)
                    )
                    drawCircle(
                        color = Color(0xFF0F1118),
                        radius = 4.dp.toPx(),
                        center = screwCenter
                    )
                    drawCircle(
                        color = Color(0xFF6B7A99),
                        radius = 2.dp.toPx(),
                        center = screwCenter
                    )
                }

                // 3. High-Excursion Rubber Roll Surround
                val surroundRadius = baseRadius * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF181B24), Color(0xFF0C0E14)),
                        center = center,
                        radius = surroundRadius
                    ),
                    radius = surroundRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF2E3547),
                    radius = surroundRadius,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )

                // 4. Moving Carbon Subwoofer Cone (Excursion displacement scaled by pulse)
                val coneRadius = surroundRadius * (0.88f + pulse * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF262C3D),
                            Color(0xFF181C26),
                            Color(0xFF0D0F15)
                        ),
                        center = center,
                        radius = coneRadius
                    ),
                    radius = coneRadius,
                    center = center
                )

                // 5. Inverted Dust Cap (Core)
                val dustCapRadius = coneRadius * (0.42f + pulse * 0.08f)
                val dustCapGlowColor = when {
                    pulse > 0.65f -> BassPunchOrange
                    pulse > 0.35f -> SubwooferViolet
                    else -> ElectricCyan
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF151821), Color(0xFF08090D)),
                        center = center,
                        radius = dustCapRadius
                    ),
                    radius = dustCapRadius,
                    center = center
                )

                // Glowing Neon Accent Ring around Dust Cap
                drawCircle(
                    color = dustCapGlowColor.copy(alpha = 0.8f + pulse * 0.2f),
                    radius = dustCapRadius,
                    center = center,
                    style = Stroke(width = (2.5f + pulse * 4f).dp.toPx())
                )

                // Center Subwoofer Logo Dot
                drawCircle(
                    color = dustCapGlowColor,
                    radius = 5.dp.toPx() * (1f + pulse * 0.5f),
                    center = center
                )
            }
        }

        // Live FFT Spectrum Equalizer Bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val bars = if (isPlaying && spectrumBars.isNotEmpty()) spectrumBars else FloatArray(16) { 0.05f }
            bars.forEachIndexed { index, value ->
                val barHeight = (value.coerceIn(0.04f, 1f) * 36f).dp
                val barColor = when {
                    index < 4 -> BassPunchOrange // Bass / Sub
                    index < 9 -> ElectricCyan   // Mid
                    else -> SubwooferViolet     // Treble
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SUB-BASS (20-80Hz)",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = BassPunchOrange,
                fontSize = 10.sp
            )
            Text(
                text = "MID (500Hz)",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                fontSize = 10.sp
            )
            Text(
                text = "TREBLE (4kHz)",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
