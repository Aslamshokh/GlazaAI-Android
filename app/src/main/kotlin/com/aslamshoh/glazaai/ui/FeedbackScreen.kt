package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.FeedbackRequestBody
import com.aslamshoh.glazaai.network.FeedbackService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import kotlinx.coroutines.launch

private val CATEGORIES = listOf("Предложение", "Ошибка", "Непонятно", "Понравилось")

/**
 * «Оставить отзыв»: оценка 1–5, тип, текст (можно продиктовать голосом). Отправляется на сервер
 * приложения и приходит разработчику — так замечания тестеров не теряются.
 */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voice = remember { VoiceInput(context) }

    var rating by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(SettingsStore.testerName) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            voice.cancel()
            SpeechSynthesizer.stop()
        }
    }

    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    fun dictate() {
        SpeechSynthesizer.stop()
        voice.listen { text ->
            if (text == null) {
                SpeechSynthesizer.speak("Не расслышала. Нажмите на микрофон и скажите ещё раз.", SettingsStore.speechRate)
            } else {
                message = if (message.isBlank()) text else message.trimEnd() + " " + text
                SpeechSynthesizer.speak("Записала. Можно продиктовать ещё или нажать «Отправить».", SettingsStore.speechRate)
            }
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted
        if (granted) dictate()
    }

    fun send() {
        if (busy) return
        if (message.isBlank()) {
            error = "Напишите или продиктуйте, что вы хотите сказать."
            SpeechSynthesizer.speak(error!!, SettingsStore.speechRate)
            return
        }
        busy = true
        error = null
        SettingsStore.updateTesterName(name.trim())
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        val body = FeedbackRequestBody(
            message = message.trim(),
            rating = rating,
            category = category,
            tester = name.trim().ifEmpty { null },
            screen = "Профиль",
            appVersion = version,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".take(80)
        )
        scope.launch {
            try {
                FeedbackService.send(body)
                sent = true
                SpeechSynthesizer.speak("Спасибо! Отзыв отправлен.", SettingsStore.speechRate)
            } catch (e: Exception) {
                val m = ApiClient.messageFor(e)
                error = m
                SpeechSynthesizer.speak("Не удалось отправить. $m Текст сохранён на экране, попробуйте ещё раз.", SettingsStore.speechRate)
            } finally {
                busy = false
            }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Theme.surface,
        unfocusedContainerColor = Theme.surface,
        focusedIndicatorColor = Theme.accent,
        unfocusedIndicatorColor = Theme.divider,
        cursorColor = Theme.accent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary)
            }
            Text("Оставить отзыв", color = Theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        if (sent) {
            Text(
                "Спасибо! Ваш отзыв отправлен разработчику. Каждое замечание помогает сделать приложение удобнее.",
                color = Theme.textPrimary,
                fontSize = 16.sp
            )
            ActionButton("Написать ещё", filled = true, modifier = Modifier.fillMaxWidth()) {
                sent = false
                message = ""
                rating = null
                category = null
            }
            ActionButton("Назад", modifier = Modifier.fillMaxWidth(), onClick = onBack)
            return@Column
        }

        Text("Как вам приложение? Оценка от 1 до 5", color = Theme.textSecondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (1..5).forEach { n ->
                Choice(
                    label = n.toString(),
                    selected = rating == n,
                    description = "Оценка $n из 5",
                    modifier = Modifier.weight(1f)
                ) { rating = if (rating == n) null else n }
            }
        }

        Text("О чём отзыв?", color = Theme.textSecondary, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CATEGORIES.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { c ->
                        Choice(label = c, selected = category == c, description = c, modifier = Modifier.weight(1f)) {
                            category = if (category == c) null else c
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(4000) },
            label = { Text("Что понравилось, что неудобно, чего не хватает") },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
        )
        ActionButton(
            if (voice.listening) "Слушаю…" else "Продиктовать голосом",
            icon = Icons.Filled.Mic,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!hasAudio) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) else dictate()
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Ваше имя (необязательно)") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { ErrorBanner(it) }
        ActionButton(if (busy) "Отправляю…" else "Отправить", filled = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) { send() }
        Text(
            "Отправляется только то, что вы написали, плюс модель телефона и версия приложения. Фото и записи голоса не отправляются.",
            color = Theme.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Theme.accent else Theme.surfaceAlt)
            .clickable(onClickLabel = description, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
