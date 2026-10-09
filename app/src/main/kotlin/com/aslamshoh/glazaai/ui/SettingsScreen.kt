package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import kotlin.math.round

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    var backendUrl by remember { mutableStateOf(SettingsStore.backendBaseUrl) }
    var apiKey by remember { mutableStateOf(SettingsStore.backendApiKey) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary)
            }
            Text("Настройки", color = Theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        SettingsSection(title = "Сервер (backend)") {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = {
                    backendUrl = it
                    SettingsStore.updateBackendBaseUrl(it)
                },
                label = { Text("https://ваш-адрес.например.trycloudflare.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    SettingsStore.updateBackendApiKey(it)
                },
                label = { Text("X-API-Key (необязательно)") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Тот же backend, что использует web- и iOS-версии EYES AI (glaza-ai-backend). " +
                    "Укажите адрес сервера — локальный (http://192.168.х.х:8000) или через туннель " +
                    "(cloudflared/ngrok). X-API-Key нужен, только если задан BACKEND_API_KEY на сервере.",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
        }

        SettingsSection(title = "Речь") {
            var rate by remember { mutableStateOf(SettingsStore.speechRate) }
            Text("Скорость речи: ${roundTo1(rate)}×", color = Theme.textPrimary, fontSize = 14.sp)
            Slider(
                value = rate,
                onValueChange = {
                    rate = it
                    SettingsStore.updateSpeechRate(it)
                },
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = Theme.accent, activeTrackColor = Theme.accent)
            )
            TextButton(onClick = { SpeechSynthesizer.speak("Это пример озвучивания результата.", SettingsStore.speechRate) }) {
                Text("Проверить голос", color = Theme.accent)
            }
        }

        SettingsSection(title = "Другое") {
            var haptics by remember { mutableStateOf(SettingsStore.hapticsEnabled) }
            var highContrast by remember { mutableStateOf(SettingsStore.highContrast) }

            SettingsRow(label = "Вибрация") {
                Switch(
                    checked = haptics,
                    onCheckedChange = {
                        haptics = it
                        SettingsStore.updateHapticsEnabled(it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent)
                )
            }
            SettingsRow(label = "Высокий контраст") {
                Switch(
                    checked = highContrast,
                    onCheckedChange = {
                        highContrast = it
                        SettingsStore.updateHighContrast(it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent)
                )
            }
        }

        SettingsSection(title = "История") {
            Button(
                onClick = { showClearConfirm = true },
                enabled = HistoryStore.entries.isNotEmpty()
            ) {
                Text("Очистить историю")
            }
            if (showClearConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        HistoryStore.clear()
                        showClearConfirm = false
                    }) { Text("Подтвердить", color = Theme.critical) }
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Отмена", color = Theme.textSecondary)
                    }
                }
            }
        }

        SettingsSection(title = "О приложении") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Версия", color = Theme.textSecondary, fontSize = 14.sp)
                Text("1.0.0", color = Theme.textSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Theme.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                .background(Theme.surface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Theme.textPrimary, fontSize = 14.sp)
        trailing()
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Theme.textPrimary,
    unfocusedTextColor = Theme.textPrimary,
    focusedContainerColor = Theme.surfaceAlt,
    unfocusedContainerColor = Theme.surfaceAlt,
    focusedIndicatorColor = Theme.accent,
    unfocusedIndicatorColor = Theme.divider,
    focusedLabelColor = Theme.textSecondary,
    unfocusedLabelColor = Theme.textSecondary,
    cursorColor = Theme.accent
)

private fun roundTo1(value: Float): Float = round(value * 10f) / 10f
