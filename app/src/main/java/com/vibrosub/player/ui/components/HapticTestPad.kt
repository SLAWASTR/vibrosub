package com.vibrosub.player.ui.components

import android.os.Build
import android.os.VibrationEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibrosub.player.haptics.PixelHapticEngine
import com.vibrosub.player.ui.theme.BassPunchOrange
import com.vibrosub.player.ui.theme.DeepBackground
import com.vibrosub.player.ui.theme.ElectricCyan
import com.vibrosub.player.ui.theme.SubwooferViolet
import com.vibrosub.player.ui.theme.SurfaceBorder
import com.vibrosub.player.ui.theme.SurfaceDarkElevated
import com.vibrosub.player.ui.theme.TextPrimary
import com.vibrosub.player.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HapticTestPad(
    hapticEngine: PixelHapticEngine,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Test Pad",
                    tint = BassPunchOrange,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Интерактивный тест виброотклика",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Нажимайте на кнопки, чтобы почувствовать примитивы Pixel 8 Pro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HapticButton(
                        title = "THUD (Бочка)",
                        subtitle = "Глубокий удар",
                        accentColor = BassPunchOrange,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD) }
                    )
                    HapticButton(
                        title = "CLICK (Щелчок)",
                        subtitle = "Резкий транзиент",
                        accentColor = ElectricCyan,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK) }
                    )
                    HapticButton(
                        title = "LOW TICK",
                        subtitle = "Мягкий низкий тик",
                        accentColor = SubwooferViolet,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) }
                    )
                    HapticButton(
                        title = "SPIN",
                        subtitle = "Эффект вращения",
                        accentColor = ElectricCyan,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN) }
                    )
                    HapticButton(
                        title = "QUICK RISE",
                        subtitle = "Быстрый разгон",
                        accentColor = SubwooferViolet,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) }
                    )
                    HapticButton(
                        title = "QUICK FALL",
                        subtitle = "Быстрый спад",
                        accentColor = BassPunchOrange,
                        onClick = { hapticEngine.testPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform Sweep Button
            Button(
                onClick = { hapticEngine.testWaveformDemo() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBackground,
                    contentColor = ElectricCyan
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ElectricCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Waveform",
                    tint = ElectricCyan,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Тест нарастающей волны сабвуфера (Waveform Sweep)",
                    fontSize = 13.sp,
                    color = ElectricCyan
                )
            }
        }
    }
}

@Composable
private fun HapticButton(
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeepBackground)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 13.sp,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
