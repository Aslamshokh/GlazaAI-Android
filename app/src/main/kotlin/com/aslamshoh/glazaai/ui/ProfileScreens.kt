package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.Translator
import kotlinx.coroutines.launch
import kotlin.math.round

private const val FREE_LIMIT = 10

// ════════════════════════════════════════════════════════════════════════════════════════════
// Экран 9 — Профиль и PRO
// ════════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenPro: () -> Unit
) {
    var showLanguage by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    // Счётчик только показывает, сколько распознаваний сделано сегодня; ничего не блокирует.
    val usedToday = HistoryStore.entries.count { it.timestampMs >= startOfToday() }
    val left = (FREE_LIMIT - usedToday).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // шапка: логотип, название, шестерёнка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Theme.surface)
                    .border(1.5.dp, Theme.accent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.RemoveRedEye, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(32.dp))
            }
            Text("ИИ Глаз", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = Color.White)
            }
        }

        if (!SettingsStore.isBackendConfigured) {
            Text(
                "Сервер не настроен. Нажмите сюда и укажите адрес сервера — без него распознавание не заработает.",
                color = Theme.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.cornerRadiusSmall))
                    .background(Theme.warning.copy(alpha = 0.18f))
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(12.dp)
            )
        }

        // тариф
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                .background(Theme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Бесплатный тариф", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Осталось $left распознаваний", color = Theme.textSecondary, fontSize = 14.sp)
                Text("$left / $FREE_LIMIT", color = Theme.textSecondary, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { left.toFloat() / FREE_LIMIT },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Theme.accent,
                trackColor = Theme.surfaceAlt
            )
            Text(
                "Сейчас приложение работает без ограничений — счётчик только для примера.",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Theme.accent)
                    .clickable(role = Role.Button, onClick = onOpenPro)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Перейти на PRO", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // список настроек
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                .background(Theme.surface)
        ) {
            ProfileRow(Icons.Outlined.History, "История", null, onClick = onOpenHistory)
            HorizontalDivider(color = Theme.divider)
            ProfileRow(
                Icons.Outlined.Public,
                "Языки",
                if (SettingsStore.appLanguage == "en") "English" else "Русский",
                onClick = { showLanguage = true }
            )
            HorizontalDivider(color = Theme.divider)
            ProfileRow(
                Icons.Outlined.RecordVoiceOver,
                "Голос",
                "Скорость ${roundTo1(SettingsStore.speechRate)}×",
                onClick = { showVoice = true }
            )
            HorizontalDivider(color = Theme.divider)
            SwitchRow(
                Icons.Outlined.Notifications,
                "Вибрация и оповещения",
                SettingsStore.hapticsEnabled
            ) { SettingsStore.updateHapticsEnabled(it) }
            HorizontalDivider(color = Theme.divider)
            ProfileRow(Icons.Outlined.CloudDownload, "Скачать офлайн-модели", null, onClick = onOpenOffline)
            HorizontalDivider(color = Theme.divider)
            ProfileRow(Icons.Outlined.HelpOutline, "Помощь", null, onClick = { showHelp = true })
        }

        Text(
            "ИИ Глаз · версия 1.0.0",
            color = Theme.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            containerColor = Theme.surface,
            title = { Text("Язык", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Язык, на котором распознаётся текст на снимках.",
                        color = Theme.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf("ru" to "Русский", "en" to "English").forEach { (code, name) ->
                        val selected = SettingsStore.appLanguage == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    SettingsStore.updateAppLanguage(code)
                                    showLanguage = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Выбрано", tint = Theme.accent)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text("Закрыть", color = Theme.accent) } }
        )
    }

    if (showVoice) {
        var rate by remember { mutableStateOf(SettingsStore.speechRate) }
        AlertDialog(
            onDismissRequest = { showVoice = false },
            containerColor = Theme.surface,
            title = { Text("Голос", color = Color.White) },
            text = {
                Column {
                    Text("Скорость речи: ${roundTo1(rate)}×", color = Color.White, fontSize = 15.sp)
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
                    Text(
                        "Сам голос (мужской или женский) выбирается в настройках Android: «Синтез речи».",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { SpeechSynthesizer.speak("Это пример озвучивания результата.", SettingsStore.speechRate) }) {
                    Text("Проверить голос", color = Theme.accent)
                }
            },
            dismissButton = { TextButton(onClick = { showVoice = false }) { Text("Закрыть", color = Theme.textSecondary) } }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = Theme.surface,
            title = { Text("Помощь", color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Главная — камера сама находит предметы и говорит, что и где. Кнопка по центру: «что вокруг?». Микрофон — голосовая команда.",
                        "Голосовые команды: «найди ключи», «прочитай текст», «сколько денег», «навигация до вокзала», «что вокруг».",
                        "Навигация — скажите, куда идти: приложение предложит пешком или транспортом и поведёт голосом.",
                        "Сканер — штрих-код товара, текст (с переводом русский ⇄ английский), купюры и QR-коды.",
                        "Если ничего не распознаётся — проверьте адрес сервера: Профиль → шестерёнка → «Сервер».",
                        "Перевод работает на телефоне; при первом переводе нужен интернет, чтобы скачать языковую модель."
                    ).forEach { Text("• $it", color = Theme.textPrimary.copy(alpha = 0.92f), fontSize = 14.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Понятно", color = Theme.accent) } }
        )
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Theme.textSecondary, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Theme.textSecondary)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent)
        )
    }
}

private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun roundTo1(value: Float): Float = round(value * 10f) / 10f

// ════════════════════════════════════════════════════════════════════════════════════════════
// Скачать офлайн-модели
// ════════════════════════════════════════════════════════════════════════════════════════════

private enum class ModelState { UNKNOWN, MISSING, READY, BUSY }

@Composable
fun OfflineModelsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ru by remember { mutableStateOf(ModelState.UNKNOWN) }
    var en by remember { mutableStateOf(ModelState.UNKNOWN) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        ru = try { if (Translator.isModelDownloaded(Translator.RU)) ModelState.READY else ModelState.MISSING } catch (e: Exception) { ModelState.UNKNOWN }
        en = try { if (Translator.isModelDownloaded(Translator.EN)) ModelState.READY else ModelState.MISSING } catch (e: Exception) { ModelState.UNKNOWN }
    }
    LaunchedEffect(Unit) { refresh() }

    fun download(lang: String, set: (ModelState) -> Unit) {
        set(ModelState.BUSY)
        message = null
        scope.launch {
            try {
                Translator.downloadModel(lang)
            } catch (e: Exception) {
                message = "Не удалось скачать модель. Проверьте интернет и попробуйте ещё раз."
            }
            refresh()
        }
    }

    fun delete(lang: String, set: (ModelState) -> Unit) {
        set(ModelState.BUSY)
        scope.launch {
            try {
                Translator.deleteModel(lang)
            } catch (e: Exception) {
                message = "Не удалось удалить модель."
            }
            refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text("Офлайн-модели", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Языковые модели для перевода русский ⇄ английский. Скачайте их один раз по Wi-Fi — потом перевод " +
                "работает на телефоне без интернета. Размер каждой — около 30 МБ.",
            color = Theme.textSecondary,
            fontSize = 14.sp
        )
        message?.let { ErrorBanner(it) }

        ModelCard("Русский", ru, onDownload = { download(Translator.RU) { ru = it } }, onDelete = { delete(Translator.RU) { ru = it } })
        ModelCard("Английский", en, onDownload = { download(Translator.EN) { en = it } }, onDelete = { delete(Translator.EN) { en = it } })

        Text(
            "Распознавание предметов, текста на фото, купюр и штрих-кодов выполняет ваш сервер (ноутбук с " +
                "backend), поэтому для него модели на телефон качать не нужно.",
            color = Theme.textSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ModelCard(name: String, state: ModelState, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (state) {
                    ModelState.READY -> "Скачана — работает без интернета"
                    ModelState.MISSING -> "Не скачана"
                    ModelState.BUSY -> "Подождите…"
                    ModelState.UNKNOWN -> "Состояние неизвестно"
                },
                color = if (state == ModelState.READY) Theme.success else Theme.textSecondary,
                fontSize = 13.sp
            )
        }
        when (state) {
            ModelState.BUSY -> CircularProgressIndicator(color = Theme.accent, modifier = Modifier.size(28.dp))
            ModelState.READY -> ActionButton("Удалить", onClick = onDelete)
            else -> ActionButton("Скачать", filled = true, onClick = onDownload)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════════
// Экран 10 — Тарифы PRO (только вид, оплаты нет)
// ════════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun ProScreen(onBack: () -> Unit) {
    var yearly by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Theme.proTop, Theme.proBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            }
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFFC58BFF),
                modifier = Modifier.size(52.dp)
            )
            Text("ИИ Глаз PRO", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Видит больше. Понимает больше.", color = Color(0xFFD6C6F5), fontSize = 15.sp)

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Безлимитные распознавания",
                    "Продвинутая навигация",
                    "Поиск предметов",
                    "Анализ документов",
                    "Перевод через камеру",
                    "Информация о товарах",
                    "Приоритетная обработка",
                    "История без ограничений"
                ).forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.success, modifier = Modifier.size(22.dp))
                        Text(feature, color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlanCard(
                    title = "1 месяц",
                    price = "399 ₽/мес.",
                    note = null,
                    selected = !yearly,
                    modifier = Modifier.weight(1f)
                ) { yearly = false }
                PlanCard(
                    title = "12 месяцев",
                    price = "3 490 ₽/год",
                    note = "(экономия 27%)",
                    selected = yearly,
                    modifier = Modifier.weight(1f)
                ) { yearly = true }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8B3DFF), Color(0xFF6A2DFF))))
                    .clickable(role = Role.Button) { showInfo = true },
                contentAlignment = Alignment.Center
            ) {
                Text("Попробовать 3 дня бесплатно", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Отмена в любой момент. Затем " + if (yearly) "3 490 ₽/год." else "399 ₽/мес.",
                color = Color(0xFFB9A8DB),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Предпросмотр: оплата пока не подключена.",
                color = Color(0xFFB9A8DB),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = Theme.surface,
            title = { Text("Оплата пока не подключена", color = Color.White) },
            text = {
                Text(
                    "Это предпросмотр экрана тарифов. Сейчас все функции приложения доступны бесплатно, и ничего не списывается.",
                    color = Theme.textSecondary
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Понятно", color = Theme.accent) } }
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    note: String?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(if (selected) 2.dp else 1.dp, if (selected) Theme.proAccent else Color(0x33FFFFFF), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) Theme.proAccent else Color(0x88FFFFFF), CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(if (selected) Theme.proAccent else Color.Transparent)
            )
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(price, color = Color(0xFFD6C6F5), fontSize = 14.sp)
        if (note != null) Text(note, color = Color(0xFFB9A8DB), fontSize = 11.sp)
    }
}
