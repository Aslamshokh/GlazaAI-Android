package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.CameraPreview
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.MemoryItem
import com.aslamshoh.glazaai.store.MemoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.MemoryText
import com.aslamshoh.glazaai.util.PlaceFix
import com.aslamshoh.glazaai.util.VoiceCommand
import com.aslamshoh.glazaai.util.VoiceCommands
import kotlinx.coroutines.launch

/**
 * «Память вещей»: список того, что вы просили запомнить («ключи — на кухонном столе, 2 часа назад»),
 * с кнопками «Где это?» и «Забыть», и кнопка «Запомнить вещь» — своя камера, название и место.
 * Голосом то же самое делается на главном экране: «запомни ключи здесь» / «где мои ключи?».
 */
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        AddMemoryScreen(onClose = { adding = false })
    } else {
        MemoryListScreen(onBack = onBack, onAdd = { adding = true })
    }
}

@Composable
private fun MemoryListScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = MemoryStore.items
    var busyId by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf<MemoryOutcome?>(null) }
    var toForget by remember { mutableStateOf<MemoryItem?>(null) }

    DisposableEffect(Unit) { onDispose { SpeechSynthesizer.stop() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary)
            }
            Text("Память вещей", color = Theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        ActionButton("Запомнить вещь", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onAdd)
        Text(
            "Совет: на главном экране скажите «запомни ключи здесь», а потом спросите «где мои ключи?».",
            color = Theme.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Пока ничего не запомнено. Положите вещь, нажмите «Запомнить вещь» — приложение снимет место, " +
                        "запишет GPS и время, а потом подскажет, где вы её оставили.",
                    color = Theme.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.toList(), key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                            .background(Theme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            BitmapThumb(bitmap = MemoryStore.loadThumb(item), size = 72.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    item.name.replaceFirstChar { it.uppercase() },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    MemoryText.whenPhrase(item.savedAtMs, System.currentTimeMillis()),
                                    color = Theme.accent,
                                    fontSize = 13.sp
                                )
                                if (item.place.isNotBlank()) Text(item.place, color = Color.White, fontSize = 14.sp)
                                if (item.around.isNotBlank()) {
                                    Text("Рядом: ${item.around}", color = Theme.textSecondary, fontSize = 13.sp)
                                }
                                if (item.lat == null) {
                                    Text("Без GPS — только фото и описание", color = Theme.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(
                                if (busyId == item.id) "Ищу…" else "Где это?",
                                icon = Icons.Filled.VolumeUp,
                                modifier = Modifier.weight(1f),
                                enabled = busyId == null
                            ) {
                                busyId = item.id
                                scope.launch {
                                    try {
                                        val outcome = recallItem(context, item)
                                        answer = outcome
                                        SpeechSynthesizer.speak(outcome.text, SettingsStore.speechRate)
                                    } finally {
                                        busyId = null
                                    }
                                }
                            }
                            ActionButton("Забыть", modifier = Modifier.weight(1f)) { toForget = item }
                        }
                    }
                }
                item { Box(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    answer?.let { outcome ->
        AlertDialog(
            onDismissRequest = { answer = null },
            containerColor = Theme.surface,
            title = { Text(outcome.title, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    outcome.thumb?.let { BitmapThumb(bitmap = it, size = 140.dp) }
                    Text(outcome.text, color = Theme.textPrimary.copy(alpha = 0.92f), fontSize = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { SpeechSynthesizer.speak(outcome.text, SettingsStore.speechRate) }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Theme.accent)
                    Text("  Повторить", color = Theme.accent)
                }
            },
            dismissButton = { TextButton(onClick = { answer = null }) { Text("Закрыть", color = Theme.textSecondary) } }
        )
    }

    toForget?.let { item ->
        AlertDialog(
            onDismissRequest = { toForget = null },
            containerColor = Theme.surface,
            title = { Text("Забыть «${item.name}»?", color = Color.White) },
            text = { Text("Запись о месте и снимок будут удалены с телефона.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    MemoryStore.remove(item.id)
                    toForget = null
                }) { Text("Забыть", color = Theme.critical) }
            },
            dismissButton = { TextButton(onClick = { toForget = null }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

/** Камера + название вещи (текстом или голосом) + «Запомнить здесь». */
@Composable
private fun AddMemoryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()
    val voice = remember { VoiceInput(context) }

    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<MemoryOutcome?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            voice.cancel()
            SpeechSynthesizer.stop()
        }
    }

    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    fun listen() {
        SpeechSynthesizer.stop()
        voice.listen { text ->
            if (text == null) {
                SpeechSynthesizer.speak("Не расслышала. Нажмите на микрофон и назовите вещь.", SettingsStore.speechRate)
            } else {
                // Можно сказать и просто «ключи», и целиком «запомни ключи на столе».
                val cmd = VoiceCommands.parse(text)
                if (cmd is VoiceCommand.Remember && cmd.item.isNotBlank()) {
                    name = cmd.item
                    if (cmd.place.isNotBlank()) place = cmd.place
                } else {
                    name = MemoryText.cleanItem(text)
                }
            }
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted
        if (granted) listen()
    }

    // GPS: спрашиваем разрешение один раз, когда человек открыл «Запомнить вещь».
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!PlaceFix.hasPermission(context)) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun save() {
        val title = MemoryText.cleanItem(name)
        if (busy || title.isEmpty()) return
        busy = true
        error = null
        SpeechSynthesizer.speakQueued("Запоминаю. Подождите несколько секунд.", SettingsStore.speechRate, true)
        controller.captureFrame { bitmap ->
            scope.launch {
                try {
                    val result = rememberHere(context, title, place.trim().lowercase(), bitmap, emptyList())
                    outcome = result
                    SpeechSynthesizer.speak(result.text, SettingsStore.speechRate)
                } catch (e: Exception) {
                    error = "Не удалось запомнить. Попробуйте ещё раз."
                } finally {
                    busy = false
                }
            }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xE60B1224),
        unfocusedContainerColor = Color(0xE60B1224),
        focusedIndicatorColor = Theme.accent,
        unfocusedIndicatorColor = Theme.accent,
        cursorColor = Theme.accent
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
            if (!controller.isReady) CameraHint(text = controller.errorMessage)
        } else {
            CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
        }

        CameraTopBar(title = "Запомнить вещь", onBack = onClose, modifier = Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }
            val done = outcome
            if (done != null) {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        BitmapThumb(bitmap = done.thumb, size = 72.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(done.title, color = Theme.success, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(done.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(done.text, SettingsStore.speechRate)
                        }
                        ActionButton("Готово", filled = true, modifier = Modifier.weight(1f), onClick = onClose)
                    }
                }
            } else {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Что запомнить", color = Theme.textSecondary) },
                            placeholder = { Text("Например: ключи", color = Theme.textSecondary) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = fieldColors
                        )
                        CircleButton(
                            icon = Icons.Filled.Mic,
                            description = if (voice.listening) "Слушаю" else "Назвать вещь голосом",
                            onClick = { if (!hasAudio) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) else listen() },
                            container = if (voice.listening) Theme.accent else Theme.surfaceAlt
                        )
                    }
                    OutlinedTextField(
                        value = place,
                        onValueChange = { place = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Где (необязательно)", color = Theme.textSecondary) },
                        placeholder = { Text("Например: на кухонном столе", color = Theme.textSecondary) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        colors = fieldColors
                    )
                    Text(
                        "Направьте камеру на место, где лежит вещь, и нажмите кнопку.",
                        color = Theme.textSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Theme.accent, modifier = Modifier.size(32.dp))
                        } else {
                            ActionButton(
                                "Запомнить здесь",
                                filled = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank() && hasCamera,
                                onClick = { save() }
                            )
                        }
                    }
                }
            }
        }
    }
}
