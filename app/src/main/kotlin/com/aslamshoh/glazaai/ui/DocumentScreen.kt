package com.aslamshoh.glazaai.ui

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.DocumentResult
import com.aslamshoh.glazaai.network.DocumentService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
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

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
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
                return@captureFrame
            }
            val dataUrl = ImageEncoding.dataUrl(bitmap, maxDimension = 2000, quality = 88)
            scope.launch {
                try {
                    val r = DocumentService.read(dataUrl, kind)
                    result = r
                    snapshot = bitmap
                    HistoryStore.addEntry("Документ", r.title + (r.store?.let { ": $it" } ?: ""), DocumentText.readAll(r), bitmap)
                    SpeechSynthesizer.speak(DocumentText.afterCapture(r), SettingsStore.speechRate)
                } catch (e: Exception) {
                    val message = ApiClient.messageFor(e)
                    error = message
                    SpeechSynthesizer.speak(message, SettingsStore.speechRate)
                } finally {
                    busy = false
                }
            }
        }
    }

    val current = result

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
                        "Положите чек или документ на ровную поверхность при хорошем свете, чтобы он занимал весь кадр, " +
                            "и нажмите кнопку. Приложение скажет магазин, дату, итог и позиции — или поля документа.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
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
                    ActionButton("Снять заново", modifier = Modifier.fillMaxWidth()) {
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
