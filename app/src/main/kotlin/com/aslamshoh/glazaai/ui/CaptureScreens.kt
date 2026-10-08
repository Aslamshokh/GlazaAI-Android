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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.aslamshoh.glazaai.camera.CameraCaptureController
import com.aslamshoh.glazaai.camera.CameraPreview
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.CurrencyResult
import com.aslamshoh.glazaai.network.CurrencyService
import com.aslamshoh.glazaai.network.OcrService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.SignGlossary
import com.aslamshoh.glazaai.util.Translator
import kotlinx.coroutines.launch
import java.util.Locale

/** Камера для одиночного снимка (Текст, Валюта) с понятным сообщением вместо чёрного экрана. */
@Composable
internal fun CaptureCamera(controller: CameraCaptureController, hasPermission: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
        }
        if (!hasPermission) {
            CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
        } else if (!controller.isReady) {
            CameraHint(text = controller.errorMessage)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════════
// Экран 5 — Текст / OCR + перевод
// ════════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun TextScreen(onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()

    var busy by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf<String?>(null) }
    var translated by remember { mutableStateOf<String?>(null) }
    var explanation by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) } // 0 — Текст, 1 — Перевод
    var reversed by remember { mutableStateOf(false) }
    var uncertainNote by remember { mutableStateOf<String?>(null) }
    var failedRead by remember { mutableStateOf(0) } // растёт при каждой неудаче: автосъёмка ждёт движения камеры

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            SpeechSynthesizer.stop()
        }
    }

    // Направление перевода определяется по самому тексту: кириллица → на английский и наоборот.
    val sourceLang = original?.let { if (Translator.isCyrillic(it) != reversed) Translator.RU else Translator.EN } ?: Translator.RU
    val targetLang = if (sourceLang == Translator.RU) Translator.EN else Translator.RU

    fun runTranslation() {
        val text = original ?: return
        translating = true
        error = null
        scope.launch {
            try {
                translated = Translator.translate(text, sourceLang, targetLang)
                tab = 1
            } catch (e: Exception) {
                error = "Не удалось перевести. При первом переводе нужен интернет — приложение скачает языковую модель, " +
                    "дальше перевод работает без сети."
            } finally {
                translating = false
            }
        }
    }

    fun capture() {
        if (busy || !hasCamera) return
        error = null
        busy = true
        SpeechSynthesizer.speakQueued("Читаю…", SettingsStore.speechRate, true)
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
                    val r = OcrService.recognize(dataUrl, SettingsStore.appLanguage)
                    val text = r.recognizedText.ifBlank { "" }.trim()
                    if (text.isEmpty()) {
                        val message = r.description.ifBlank { "Текст не найден. Поднесите камеру ближе и попробуйте ещё раз." }
                        error = message
                        failedRead++
                        SpeechSynthesizer.speak(message, SettingsStore.speechRate)
                    } else {
                        uncertainNote = r.description.takeIf { "неуверенно" in it }
                        original = text
                        translated = null
                        explanation = null
                        tab = 0
                        reversed = false
                        HistoryStore.addEntry("Текст", "Текст: " + text.take(28).replace('\n', ' '), text, bitmap)
                        SpeechSynthesizer.speak(
                            text,
                            SettingsStore.speechRate,
                            if (Translator.isCyrillic(text)) null else Locale.ENGLISH
                        )
                        uncertainNote?.let { SpeechSynthesizer.speakQueued(it, SettingsStore.speechRate, false) }
                    }
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

    val auto = rememberAutoCapture(controller, active = original == null && !busy && hasCamera) { capture() }
    LaunchedEffect(failedRead) { if (failedRead > 0) auto.needMovement = true }
    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            SpeechSynthesizer.speakQueued(
                "Наведите камеру на текст. Снимок сделается сам, когда кадр будет чётким. Можно и нажать большую кнопку внизу.",
                SettingsStore.speechRate,
                false
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CaptureCamera(controller, hasCamera)

        if (original == null && hasCamera) {
            BracketFrame(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.82f).aspectRatio(1.6f),
                color = Theme.accent
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraTopBar(title = "Текст", onBack = onBack)
            ScanModeSwitch(selected = ScanMode.TEXT, onSelect = onSwitchMode)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }

            val text = original
            if (text == null) {
                GlassSheet {
                    Text(
                        "Наведите камеру на текст: приложение снимет само, когда кадр станет чётким, и прочитает вслух. " +
                            "Или нажмите кнопку.",
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
                    // вкладки «Текст» / «Перевод»
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TabPill("Текст", tab == 0, Modifier.weight(1f)) { tab = 0 }
                        TabPill("Перевод", tab == 1, Modifier.weight(1f)) {
                            tab = 1
                            if (translated == null && !translating) runTranslation()
                        }
                    }

                    val shown = if (tab == 0) text else translated
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Theme.surfaceAlt)
                            .padding(14.dp)
                    ) {
                        Box(modifier = Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                            if (shown != null) {
                                Text(shown, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                            } else {
                                CircularProgressIndicator(color = Theme.accent)
                            }
                        }
                        val from = sourceLang.uppercase()
                        val to = targetLang.uppercase()
                        Text(
                            "$from ⇄ $to",
                            color = Theme.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Поменять направление перевода", role = Role.Button) {
                                    reversed = !reversed
                                    translated = null
                                    tab = 0
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    uncertainNote?.let { Text(it, color = Theme.warning, fontSize = 13.sp) }
                    explanation?.let {
                        Text(it, color = Theme.textPrimary.copy(alpha = 0.9f), fontSize = 14.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Читать", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            val current = if (tab == 1 && translated != null) translated!! else text
                            val lang = if (tab == 1 && translated != null) targetLang else sourceLang
                            SpeechSynthesizer.speak(
                                current,
                                SettingsStore.speechRate,
                                if (lang == Translator.EN) Locale.ENGLISH else null
                            )
                        }
                        ActionButton(
                            if (translating) "Перевожу…" else "Перевести",
                            icon = Icons.Filled.Translate,
                            filled = true,
                            enabled = !translating,
                            modifier = Modifier.weight(1f)
                        ) { runTranslation() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Копировать", icon = Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                            val current = if (tab == 1 && translated != null) translated!! else text
                            clipboard.setText(AnnotatedString(current))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                        ActionButton("Объяснить", icon = Icons.Outlined.Info, modifier = Modifier.weight(1f)) {
                            val note = SignGlossary.explain(text)
                                ?: "Смысл этой надписи приложение объяснить не может: для этого нужен облачный ИИ, " +
                                "его в приложении пока нет. Могу перевести надпись и прочитать её вслух."
                            explanation = note
                            SpeechSynthesizer.speak(note, SettingsStore.speechRate)
                        }
                    }
                    ActionButton("Снять заново", modifier = Modifier.fillMaxWidth()) {
                        original = null
                        translated = null
                        explanation = null
                        uncertainNote = null
                        error = null
                        SpeechSynthesizer.stop()
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

// ════════════════════════════════════════════════════════════════════════════════════════════
// Экран 6 — Валюта
// ════════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun CurrencyScreen(onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CurrencyResult?>(null) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }

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
        controller.captureFrame { bitmap ->
            if (bitmap == null) {
                busy = false
                error = "Не удалось получить кадр с камеры. Попробуйте ещё раз."
                return@captureFrame
            }
            val dataUrl = ImageEncoding.dataUrl(bitmap)
            scope.launch {
                try {
                    val r = CurrencyService.recognize(dataUrl)
                    result = r
                    snapshot = bitmap
                    val headline = if (r.denomination != "неизвестно") r.denomination else r.title
                    HistoryStore.addEntry("Валюта", headline, r.description, bitmap)
                    SpeechSynthesizer.speak(r.description, SettingsStore.speechRate)
                } catch (e: Exception) {
                    error = ApiClient.messageFor(e)
                } finally {
                    busy = false
                }
            }
        }
    }

    val current = result
    val recognized = current != null && !current.lowConfidenceWarning

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CaptureCamera(controller, hasCamera)

        if (hasCamera) {
            BracketFrame(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.88f).aspectRatio(2.1f),
                color = if (recognized) Theme.success else Theme.accent
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraTopBar(title = "Валюта", onBack = onBack)
            ScanModeSwitch(selected = ScanMode.CURRENCY, onSelect = onSwitchMode)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }

            if (current == null) {
                GlassSheet {
                    Text(
                        "Положите купюру на ладонь или ровную поверхность, направьте камеру и нажмите кнопку.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShutterButton(isBusy = busy) { capture() }
                    }
                }
            } else {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BitmapThumb(bitmap = snapshot, size = 84.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val headline = if (current.denomination != "неизвестно") current.denomination else current.title
                            Text(headline, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            if (current.currency != "неизвестно") {
                                Text(current.currency, color = Color.White, fontSize = 16.sp)
                            }
                            if (current.lowConfidenceWarning) {
                                Text("Низкая уверенность — снимите ещё раз ближе", color = Theme.warning, fontSize = 13.sp)
                            }
                        }
                    }
                    if (current.description.isNotBlank()) {
                        Text(current.description, color = Theme.textSecondary, fontSize = 14.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(current.description, SettingsStore.speechRate)
                        }
                        ActionButton("Снять снова", modifier = Modifier.weight(1f)) {
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
}
