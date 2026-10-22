package com.vibrosub.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.vibrosub.player.model.HapticEngineMode
import com.vibrosub.player.model.HapticSettings
import com.vibrosub.player.ui.theme.BassPunchOrange
import com.vibrosub.player.ui.theme.DeepBackground
import com.vibrosub.player.ui.theme.ElectricCyan
import com.vibrosub.player.ui.theme.SubwooferViolet
import com.vibrosub.player.ui.theme.SurfaceBorder
import com.vibrosub.player.ui.theme.SurfaceDarkElevated
import com.vibrosub.player.ui.theme.TextPrimary
import com.vibrosub.player.ui.theme.TextSecondary
import com.vibrosub.player.ui.theme.TextTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HapticSettingsCard(
    settings: HapticSettings,
    onSettingsChanged: (HapticSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedAdvanced by remember { mutableStateOf(false) }

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
            // Master Vibration Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = "Haptics",
                        tint = if (settings.enabled) ElectricCyan else TextTertiary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Тактильный бас (Вибрация)",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = if (settings.enabled) "Активно (LRA / Тактильный отклик)" else "Выключено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.enabled) ElectricCyan else TextSecondary
                        )
                    }
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepBackground,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Master Intensity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Общая сила отдачи:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = "${(settings.intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
            }

            Slider(
                value = settings.intensity,
                onValueChange = { onSettingsChanged(settings.copy(intensity = it)) },
                valueRange = 0.0f..2.0f,
                steps = 19,
                enabled = settings.enabled,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = SurfaceBorder
                )
            )

            // Preset Intensity Quick Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0.5f to "50%", 1.0f to "100%", 1.5f to "150%", 2.0f to "200% MAX").forEach { (v, label) ->
                    val isSelected = Math.abs(settings.intensity - v) < 0.08f
                    Text(
                        text = label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ElectricCyan else DeepBackground)
                            .clickable(enabled = settings.enabled) {
                                onSettingsChanged(settings.copy(intensity = v))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (isSelected) DeepBackground else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Engine Mode Selection
            Text(
                text = "Режим тактильного движка:",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            HapticEngineMode.values().forEach { mode ->
                val isSelected = settings.engineMode == mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricCyan else SurfaceBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(if (isSelected) Color(0x1A00E5FF) else DeepBackground)
                        .clickable(enabled = settings.enabled) {
                            onSettingsChanged(settings.copy(engineMode = mode))
                        }
                        .padding(14.dp)
                ) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSelected) ElectricCyan else TextPrimary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Tuning Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expandedAdvanced = !expandedAdvanced }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = SubwooferViolet,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Расширенная настройка виброотклика",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                }
                Icon(
                    imageVector = if (expandedAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expandedAdvanced) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Kick Punch (THUD)
                    Text(
                        text = "Сила удара бочки (PRIMITIVE_THUD): ${(settings.thudStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.thudStrength,
                        onValueChange = { onSettingsChanged(settings.copy(thudStrength = it)) },
                        valueRange = 0.0f..1.5f,
                        enabled = settings.enabled,
                        colors = SliderDefaults.colors(thumbColor = BassPunchOrange, activeTrackColor = BassPunchOrange)
                    )

                    // Snare Click
                    Text(
                        text = "Четкость щелчка (PRIMITIVE_CLICK): ${(settings.clickStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.clickStrength,
                        onValueChange = { onSettingsChanged(settings.copy(clickStrength = it)) },
                        valueRange = 0.0f..1.5f,
                        enabled = settings.enabled,
                        colors = SliderDefaults.colors(thumbColor = SubwooferViolet, activeTrackColor = SubwooferViolet)
                    )

                    // Sub-Bass Rumble
                    Text(
                        text = "Рокот суб-баса (Rumble): ${(settings.rumbleStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.rumbleStrength,
                        onValueChange = { onSettingsChanged(settings.copy(rumbleStrength = it)) },
                        valueRange = 0.0f..2.0f,
                        enabled = settings.enabled,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )

                    // Chime / Bell Melody Strength (Radiohead)
                    Text(
                        text = "Колокольчики и мелодия (PRIMITIVE_LOW_TICK): ${(settings.chimeStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.chimeStrength,
                        onValueChange = { onSettingsChanged(settings.copy(chimeStrength = it)) },
                        valueRange = 0.0f..2.0f,
                        enabled = settings.enabled,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Отклик на колокольчики и мелодию", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Для песен вроде No Surprises и акустики", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.enableMelodyChimes,
                            onCheckedChange = { onSettingsChanged(settings.copy(enableMelodyChimes = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Щелчки на снейр и перкуссию", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Switch(
                            checked = settings.enableClicksOnSnare,
                            onCheckedChange = { onSettingsChanged(settings.copy(enableClicksOnSnare = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Тактильная волна по громкости", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Switch(
                            checked = settings.enableContinuousGroove,
                            onCheckedChange = { onSettingsChanged(settings.copy(enableContinuousGroove = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Low-Pass Filter Cutoff
                    Text(
                        text = "Частота среза баса (Low-pass Cutoff): ${settings.bassCutoffHz} Hz",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(80, 120, 150, 200, 300).forEach { freq ->
                            val isSelected = settings.bassCutoffHz == freq
                            Text(
                                text = "$freq Hz",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SubwooferViolet else DeepBackground)
                                    .clickable(enabled = settings.enabled) {
                                        onSettingsChanged(settings.copy(bassCutoffHz = freq))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Noise Gate / Sensitivity
                    Text(
                        text = "Порог чувствительности: ${(settings.sensitivityThreshold * 100).toInt()}% (чем ниже, тем легче срабатывает)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = settings.sensitivityThreshold,
                        onValueChange = { onSettingsChanged(settings.copy(sensitivityThreshold = it)) },
                        valueRange = 0.02f..0.30f,
                        enabled = settings.enabled
                    )
                }
            }
        }
    }
}
