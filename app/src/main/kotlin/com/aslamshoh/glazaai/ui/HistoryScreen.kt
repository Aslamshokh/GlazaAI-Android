package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryEntry
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Экран 8 макета — «История распознаваний»: список по дням с миниатюрами; «Очистить» — всё. */
@Composable
fun HistoryScreen() {
    var confirmClear by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<HistoryEntry?>(null) }
    val entries = HistoryStore.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("История распознаваний", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Очистить", color = Theme.accent, fontSize = 15.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Здесь появятся ваши результаты: предметы, текст, товары, деньги.",
                    color = Theme.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            val groups = entries.groupBy { dayLabel(it.timestampMs) }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groups.forEach { (label, list) ->
                    item(key = "header-$label") {
                        Text(
                            label,
                            color = Theme.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    items(list, key = { it.id }) { entry ->
                        HistoryRow(entry) { opened = entry }
                        HorizontalDivider(color = Theme.divider)
                    }
                }
                item { Box(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Theme.surface,
            title = { Text("Очистить историю?", color = Color.White) },
            text = { Text("Все сохранённые результаты будут удалены с телефона.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    HistoryStore.clear()
                    confirmClear = false
                }) { Text("Очистить", color = Theme.critical) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = Theme.textSecondary) }
            }
        )
    }

    opened?.let { entry ->
        AlertDialog(
            onDismissRequest = { opened = null },
            containerColor = Theme.surface,
            title = { Text(entry.title, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryStore.loadThumb(entry)?.let { BitmapThumb(bitmap = it, size = 120.dp) }
                    Text(
                        timeLabel(entry.timestampMs) + " · " + entry.module,
                        color = Theme.accent,
                        fontSize = 13.sp
                    )
                    Text(entry.description, color = Theme.textPrimary.copy(alpha = 0.92f), fontSize = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { SpeechSynthesizer.speak(entry.description, SettingsStore.speechRate) }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Theme.accent)
                    Text("  Озвучить", color = Theme.accent)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        HistoryStore.remove(entry.id)
                        opened = null
                    }) { Text("Удалить", color = Theme.critical) }
                    TextButton(onClick = { opened = null }) { Text("Закрыть", color = Theme.textSecondary) }
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Открыть запись", role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BitmapThumb(bitmap = HistoryStore.loadThumb(entry), size = 52.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(timeLabel(entry.timestampMs) + " · " + entry.module, color = Theme.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Theme.textSecondary)
    }
}

private fun timeLabel(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun dayLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, then)) return "Сегодня"
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(yesterday, then)) return "Вчера"
    return SimpleDateFormat("d MMMM", Locale("ru")).format(Date(ms))
}
