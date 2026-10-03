package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.CameraPreview
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.model.RecognitionMode
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.CurrencyService
import com.aslamshoh.glazaai.network.OcrService
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import kotlinx.coroutines.launch

private data class DisplayResult(
    val title: String,
    val description: String,
    val badge: String?,
    val badgeColor: androidx.compose.ui.graphics.Color,
    val chips: List<String>,
    val historyModule: String
)

/**
 * Один экран камеры для режимов с одиночным снимком: Валюта, Текст, Предметы, Документы —
 * зеркалит GlazaAI-iOS/GlazaAI/Views/CameraScreenView.swift. Переключение между этими
 * режимами возможно прямо на экране (пилюля внизу), камера при этом не перезапускается.
 */
@Composable
fun CaptureScreen(initialMode: RecognitionMode) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DisplayResult?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val controller = rememberCameraCaptureController()
    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            SpeechSynthesizer.stop()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasCameraPermission) {
                CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
            }
            if (!hasCameraPermission) {
                CenteredHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
            } else if (!controller.isReady) {
                CenteredHint(text = controller.errorMessage)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.background)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecognitionMode.singleShotModes.forEach { m ->
                    ModePill(
                        text = m.title,
                        selected = m == mode,
                        onClick = {
                            mode = m
                            result = null
                            errorMessage = null
                        }
                    )
                }
            }

            errorMessage?.let { ErrorBanner(it) }

            result?.let { r ->
                Box(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    ResultCard(
                        title = r.title,
                        description = r.description,
                        badge = r.badge,
                        badgeColor = r.badgeColor,
                        extraChips = r.chips,
                        isSpeaking = SpeechSynthesizer.isSpeaking,
                        onSpeak = { SpeechSynthesizer.speak(r.description, SettingsStore.speechRate) },
                        onStop = { SpeechSynthesizer.stop() }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShutterButton(isBusy = isProcessing) {
                    if (isProcessing || !hasCameraPermission) return@ShutterButton
                    errorMessage = null
                    isProcessing = true
                    controller.captureFrame { bitmap ->
                        if (bitmap == null) {
                            isProcessing = false
                            errorMessage = "Не удалось получить кадр с камеры. Попробуйте ещё раз."
                            return@captureFrame
                        }
                        val dataUrl = ImageEncoding.dataUrl(bitmap)
                        scope.launch {
                            try {
                                val display = recognize(mode, dataUrl)
                                result = display
                                HistoryStore.addEntry(display.historyModule, display.title, display.description)
                                SpeechSynthesizer.speak(display.description, SettingsStore.speechRate)
                            } catch (e: Exception) {
                                errorMessage = ApiClient.messageFor(e)
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun recognize(mode: RecognitionMode, dataUrl: String): DisplayResult = when (mode) {
    RecognitionMode.CURRENCY -> {
        val r = CurrencyService.recognize(dataUrl)
        DisplayResult(
            title = r.title,
            description = r.description,
            badge = if (r.lowConfidenceWarning) "Низкая уверенность" else null,
            badgeColor = Theme.warning,
            chips = listOf(r.currency, r.denomination).filter { it != "неизвестно" },
            historyModule = "Распознавание денег"
        )
    }
    RecognitionMode.TEXT, RecognitionMode.DOCUMENTS -> {
        val r = OcrService.recognize(dataUrl, "ru")
        DisplayResult(
            title = r.title,
            description = r.recognizedText.ifEmpty { r.description },
            badge = null,
            badgeColor = Theme.success,
            chips = emptyList(),
            historyModule = if (mode == RecognitionMode.DOCUMENTS) "Документы" else "Чтение текста"
        )
    }
    RecognitionMode.OBJECTS -> {
        val r = VisionService.describeScene(dataUrl)
        DisplayResult(
            title = r.title,
            description = r.description,
            badge = null,
            badgeColor = Theme.success,
            chips = r.objects.map { obj -> obj.direction?.let { "${obj.label} ($it)" } ?: obj.label },
            historyModule = "Распознавание предметов"
        )
    }
    RecognitionMode.BARCODE, RecognitionMode.QR -> {
        // Эти режимы не используют CaptureScreen — см. BarcodeScreen.kt.
        throw IllegalStateException("Barcode/QR обрабатываются отдельным экраном")
    }
}

@Composable
private fun CenteredHint(text: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(
                text,
                color = Theme.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            CircularProgressIndicator(color = Theme.accent)
        }
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Theme.accent else Theme.surfaceAlt)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (selected) androidx.compose.ui.graphics.Color.White else Theme.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
