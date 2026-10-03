package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.model.RecognitionMode
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.BarcodeResult
import com.aslamshoh.glazaai.network.BarcodeService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch

/**
 * Отдельный экран для штрих-кодов и QR — сканирование непрерывное (ZXing), а не "сделайте
 * один снимок", поэтому экран устроен иначе, чем CaptureScreen. Зеркалит
 * GlazaAI-iOS/GlazaAI/Views/BarcodeScanView.swift.
 */
@Composable
fun BarcodeScreen(mode: RecognitionMode) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLookingUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var barcodeResult by remember { mutableStateOf<BarcodeResult?>(null) }
    var qrText by remember { mutableStateOf<String?>(null) }
    var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleScan(value: String, format: BarcodeFormat) {
        // QR обрабатывается одинаково независимо от текущей вкладки (Штрих-коды/QR-коды) —
        // это просто текст, а не товарный код для Open Food Facts.
        if (format == BarcodeFormat.QR_CODE) {
            qrText = value
            SpeechSynthesizer.speak(value, SettingsStore.speechRate)
            HistoryStore.addEntry("QR-код", "QR-код распознан", value)
            return
        }

        isLookingUp = true
        errorMessage = null
        scope.launch {
            try {
                val result = BarcodeService.lookup(value)
                barcodeResult = result
                HistoryStore.addEntry("Сканирование товара", result.title, result.description)
                SpeechSynthesizer.speak(result.description, SettingsStore.speechRate)
            } catch (e: Exception) {
                errorMessage = ApiClient.messageFor(e)
                scannerView?.resume()
            } finally {
                isLookingUp = false
            }
        }
    }

    fun reset() {
        barcodeResult = null
        qrText = null
        errorMessage = null
        scannerView?.resume()
    }

    DisposableEffect(Unit) {
        onDispose {
            scannerView?.pause()
            SpeechSynthesizer.stop()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        DecoratedBarcodeView(ctx).apply {
                            decodeContinuous(object : BarcodeCallback {
                                override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                                    if (barcodeResult != null || qrText != null) return
                                    handleScan(result.text, result.barcodeFormat)
                                }

                                override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>) {}
                            })
                            resume()
                            scannerView = this
                        }
                    }
                )
            } else {
                CenteredHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
            }

            if (hasCameraPermission && isLookingUp) {
                Box(
                    modifier = Modifier.align(Alignment.Center)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                }
            } else if (hasCameraPermission && barcodeResult == null && qrText == null) {
                Box(
                    modifier = Modifier.align(Alignment.Center)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (mode == RecognitionMode.QR) "Наведите камеру на QR-код" else "Наведите камеру на штрих-код",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.background)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            errorMessage?.let { ErrorBanner(it) }

            qrText?.let { text ->
                Box(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    ResultCard(
                        title = "QR-код распознан",
                        description = text,
                        badge = if (isUrl(text)) "Ссылка" else null,
                        badgeColor = Theme.accent,
                        isSpeaking = SpeechSynthesizer.isSpeaking,
                        onSpeak = { SpeechSynthesizer.speak(text, SettingsStore.speechRate) },
                        onStop = { SpeechSynthesizer.stop() }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isUrl(text)) {
                        PillButton(label = "Открыть ссылку", filled = true) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
                            context.startActivity(intent)
                        }
                    }
                    PillButton(label = "Сканировать снова", filled = false) { reset() }
                }
            }

            barcodeResult?.let { result ->
                Box(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    ResultCard(
                        title = result.title,
                        description = result.description,
                        badge = if (result.found) "Найден" else "Не найден",
                        badgeColor = if (result.found) Theme.success else Theme.warning,
                        extraChips = barcodeChips(result),
                        isSpeaking = SpeechSynthesizer.isSpeaking,
                        onSpeak = { SpeechSynthesizer.speak(result.description, SettingsStore.speechRate) },
                        onStop = { SpeechSynthesizer.stop() }
                    )
                }
                PillButton(label = "Сканировать снова", filled = false) { reset() }
            }
        }
    }
}

private fun barcodeChips(result: BarcodeResult): List<String> {
    val chips = mutableListOf<String>()
    result.brand?.let { chips.add(it) }
    result.quantity?.let { chips.add(it) }
    result.nutriScore?.let { chips.add("Nutri-Score: $it") }
    return chips
}

private fun isUrl(text: String): Boolean =
    text.lowercase().startsWith("http://") || text.lowercase().startsWith("https://")

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Theme.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Theme.accent else Theme.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (filled) androidx.compose.ui.graphics.Color.White else Theme.textPrimary,
            fontSize = 14.sp
        )
    }
}
