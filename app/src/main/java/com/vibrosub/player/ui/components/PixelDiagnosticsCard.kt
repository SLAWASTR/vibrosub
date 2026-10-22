package com.vibrosub.player.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.vibrosub.player.haptics.HapticHardwareStatus
import com.vibrosub.player.ui.theme.DeepBackground
import com.vibrosub.player.ui.theme.ElectricCyan
import com.vibrosub.player.ui.theme.SurfaceDarkElevated
import com.vibrosub.player.ui.theme.TextPrimary
import com.vibrosub.player.ui.theme.TextSecondary
import com.vibrosub.player.ui.theme.TextTertiary
import com.vibrosub.player.ui.theme.VividGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelDiagnosticsCard(
    status: HapticHardwareStatus,
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
                    imageVector = Icons.Default.DeveloperBoard,
                    contentDescription = "Diagnostics",
                    tint = ElectricCyan,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Аппаратный статус вибромотора",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Диагностика тактильного привода устройства",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Info Row
            DiagnosticRow("Устройство:", status.deviceName)
            DiagnosticRow("ОС:", status.androidVersion)
            DiagnosticRow(
                "Тип вибромотора:",
                if (status.deviceName.contains("Pixel", ignoreCase = true)) {
                    "Cirrus Logic High-Definition LRA"
                } else {
                    "Линейный резонансный вибромотор (LRA)"
                }
            )
            DiagnosticStatusRow("Управление амплитудой (256 уровней):", status.hasAmplitudeControl)
            DiagnosticStatusRow("Google AudioFX HapticGenerator DSP:", status.supportsHapticGenerator)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Аппаратные тактильные примитивы Android 14+:",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 14.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimitiveBadge("PRIMITIVE_THUD (Бочка)", status.supportsThud)
                PrimitiveBadge("PRIMITIVE_CLICK (Щелчок)", status.supportsClick)
                PrimitiveBadge("PRIMITIVE_LOW_TICK", status.supportsLowTick)
                PrimitiveBadge("PRIMITIVE_QUICK_RISE", status.supportsQuickRise)
                PrimitiveBadge("PRIMITIVE_SLOW_RISE", status.supportsSlowRise)
                PrimitiveBadge("PRIMITIVE_SPIN", status.supportsSpin)
                PrimitiveBadge("PRIMITIVE_QUICK_FALL", status.supportsQuickFall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepBackground)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "VibroSub автоматически оптимизирует отклик под вибромотор вашего смартфона, раскрывая максимум на устройствах с быстрыми приводами LRA (Google Pixel, Samsung Galaxy, OnePlus, Xiaomi и др.).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun DiagnosticStatusRow(label: String, isSupported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSupported) VividGreen else TextTertiary)
            )
            Text(
                text = if (isSupported) " Поддерживается" else " Недоступно",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSupported) VividGreen else TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PrimitiveBadge(name: String, isSupported: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSupported) DeepBackground else Color(0x33333333))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSupported) VividGreen else TextTertiary)
            )
            Text(
                text = " $name",
                color = if (isSupported) TextPrimary else TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}
