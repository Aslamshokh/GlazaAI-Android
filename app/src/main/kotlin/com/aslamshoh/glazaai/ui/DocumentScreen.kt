package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.DocumentResult
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.network.DocumentService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.DocQa
import com.aslamshoh.glazaai.util.DocumentText
import com.aslamshoh.glazaai.util.ImageEncoding
import kotlinx.coroutines.launch

/**
 * Режим «Чек / Документ»: фото → магазин, дата, итог, позиции (чек) или «поле — значение» (паспорт,
 * справка, счёт). Распознаёт сервер; текст чека нигде на сервере не сохраняется.
 */
@Composable
fun DocumentScreen(onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DocumentResult?>(null) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
    var kind by remember { mutableStateOf("auto") } // auto / receipt / document
    var failedRead by remember { mutableStateOf(0) }
    var answer by remember { mutableStateOf<String?>(null) }
    var largeText by remember { mutableStateOf<String?>(null) }
    largeText?.let { LargeTextDialog(it) { largeText = null } }
    val voice = remember { VoiceInput(context) }
    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    // «Чат с документом»: спрашиваем голосом, отвечаем по уже распознанному тексту — прямо на телефоне.
    fun ask(doc: DocumentResult) {
        SpeechSynthesizer.stop()
        voice.listen { text ->
            val reply = if (text == null) "Я не расслышала вопрос. " + DocQa.hint(doc) else DocQa.answer(doc, text)
            answer = reply
            SpeechSynthesizer.speak(reply, SettingsStore.speechRate)
        }
    }
    var pendingDoc by remember { mutableStateOf<DocumentResult?>(null) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted
        val d = pendingDoc
        if (granted && d != null) ask(d)
        else if (!granted) SpeechSynthesizer.speak("Без микрофона вопрос задать нельзя. Разрешите доступ к микрофону в настройках.", SettingsStore.speechRate)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            voice.cancel()
            SpeechSynthesizer.stop()
        }
    }

    fun capture() {
        if (busy || !hasCamera) return
        error = null
        busy = true
        SpeechSynthesizer.speakQueued("Снимаю. Подождите, читаю документ. Это может занять до полуминуты.", SettingsStore.speechRate, true)
        controller.captureFrame { bitmap ->
            if (bitmap == null) {
                busy = false
                error = "Не удалось получить кадр с камеры. Попробуйте ещё раз."
                failedRead++
                SpeechSynthesizer.speak(error.orEmpty(), SettingsStore.speechRate)
                return@captureFrame
            }
            val dataUrl = ImageEncoding.dataUrl(bitmap, maxDimension = 2000, quality = 88)
            scope.launch {
                try {
                    val r = DocumentService.read(dataUrl, kind)
                    result = r
                    snapshot = bitmap
                    HistoryStore.addEntry("Документ", r.title + (r.store?.let { ": $it" } ?: ""), DocumentText.readAll(r), bitmap)
                    answer = null
                    SpeechSynthesizer.speak(DocumentText.afterCapture(r) + " Можно задать вопрос по документу: нажмите «Спросить про документ».", SettingsStore.speechRate)
                } catch (e: Exception) {
                    val message = ApiClient.messageFor(e)
                    error = message
                    failedRead++
                    SpeechSynthesizer.speak(message, SettingsStore.speechRate)
                } finally {
                    busy = false
                }
            }
        }
    }

    val current = result
    val auto = rememberAutoCapture(controller, active = current == null && !busy && hasCamera, holdMs = 900) { capture() }
    LaunchedEffect(failedRead) { if (failedRead > 0) auto.needMovement = true }
    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            SpeechSynthesizer.speakQueued(
                "Положите чек или документ ровно, чтобы он занимал весь кадр. Снимок сделается сам, когда кадр будет чётким. Можно нажать большую кнопку.",
                SettingsStore.speechRate,
                false
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CaptureCamera(controller, hasCamera)

        if (current == null && hasCamera) {
            BracketFrame(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).aspectRatio(0.72f),
                color = Theme.accent
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraTopBar(title = "Чек и документы", onBack = onBack)
            ScanModeSwitch(selected = ScanMode.DOC, onSelect = onSwitchMode)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }

            if (current == null) {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KindPill("Авто", kind == "auto", Modifier.weight(1f)) { kind = "auto" }
                        KindPill("Чек", kind == "receipt", Modifier.weight(1f)) { kind = "receipt" }
                        KindPill("Документ", kind == "document", Modifier.weight(1f)) { kind = "document" }
                    }
                    Text(
                        "Положите чек или документ на ровную поверхность при хорошем свете, чтобы он занимал весь кадр. " +
                            "Приложение снимет само, когда кадр станет чётким, и скажет магазин, дату, итог и позиции — или поля документа.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
                    CaptureAssistRow(controller, auto)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShutterButton(isBusy = busy) { capture() }
                    }
                }
            } else {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        BitmapThumb(bitmap = snapshot, size = 72.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(current.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            current.store?.let { Text(it, color = Color.White, fontSize = 15.sp) }
                            current.date?.let { Text(it, color = Theme.textSecondary, fontSize = 13.sp) }
                        }
                    }
                    current.warnings.orEmpty().forEach { Text(it, color = Theme.warning, fontSize = 13.sp) }
                    Text(current.description, color = Theme.textPrimary.copy(alpha = 0.92f), fontSize = 15.sp)

                    val rows = current.fields.orEmpty()
                    if (rows.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Theme.surfaceAlt)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rows.forEach { f ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(f.label, color = Theme.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                                    Text(f.value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
                                }
                            }
                        }
                    }
                    val items = current.items.orEmpty()
                    if (items.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Theme.surfaceAlt)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items.forEach { Text(it.spoken.ifBlank { it.name }, color = Color.White, fontSize = 14.sp) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Всё", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(DocumentText.readAll(current), SettingsStore.speechRate)
                        }
                        ActionButton("Итог", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(DocumentText.total(current), SettingsStore.speechRate)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (current.kind == "receipt") {
                            ActionButton("Позиции", modifier = Modifier.weight(1f)) {
                                SpeechSynthesizer.speak(DocumentText.items(current), SettingsStore.speechRate)
                            }
                        } else {
                            ActionButton("Все поля", modifier = Modifier.weight(1f)) {
                                SpeechSynthesizer.speak(DocumentText.fields(current), SettingsStore.speechRate)
                            }
                        }
                        ActionButton("Копировать", icon = Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(current.recognizedText.orEmpty().ifBlank { DocumentText.readAll(current) }))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                    }
                    answer?.let { a ->
                        Text(a, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ActionButton(
                        if (voice.listening) "Слушаю…" else "Спросить про документ",
                        icon = Icons.Filled.Mic,
                        filled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!hasAudio) {
                            pendingDoc = current
                            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else ask(current)
                    }
                    ActionButton("Крупно", modifier = Modifier.fillMaxWidth()) {
                        largeText = current.recognizedText.orEmpty().ifBlank { DocumentText.readAll(current) }
                    }
                    ActionButton("Снять заново", modifier = Modifier.fillMaxWidth()) {
                        answer = null
                        result = null
                        snapshot = null
                        error = null
                        SpeechSynthesizer.stop()
                    }
                }
            }
        }
    }
}

@Composable
private fun KindPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Theme.accent else Theme.surfaceAlt)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
