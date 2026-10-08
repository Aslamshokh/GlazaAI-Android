package com.aslamshoh.glazaai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.camera.BarcodeCameraPreview
import com.aslamshoh.glazaai.camera.BarcodeHit
import com.aslamshoh.glazaai.camera.rememberBarcodeCameraController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.BarcodeResult
import com.aslamshoh.glazaai.network.BarcodeService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.Beeper
import com.aslamshoh.glazaai.util.QrInfo
import com.aslamshoh.glazaai.util.QrKind
import com.aslamshoh.glazaai.util.QrText
import com.aslamshoh.glazaai.util.RemoteImages
import com.aslamshoh.glazaai.util.ScanHints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран «Сканер товара» и «QR»: камера непрерывно ищет код (ML Kit на телефоне — быстро, без
 * интернета, понимает QR, штрихкоды EAN/UPC/Code128 и др.). Нашёл — сигнал, вибрация и голос.
 * Штрихкод товара ищется в открытых базах через backend; QR разбирается на месте: ссылка, Wi-Fi,
 * телефон, визитка, текст. Если код долго не находится, голос подсказывает, что поправить.
 * Срок годности здесь не показываем: в каталоге его нет, он напечатан на упаковке («Текст»).
 */
@Composable
fun BarcodeScreen(mode: ScanMode, onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val hasCamera = rememberCameraPermission()
    val controller = rememberBarcodeCameraController()

    var isLookingUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var product by remember { mutableStateOf<BarcodeResult?>(null) }
    var productImage by remember { mutableStateOf<Bitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<QrInfo?>(null) }
    var startedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var hintsDone by remember { mutableIntStateOf(0) }

    fun announceFound() {
        Beeper.success()
        if (SettingsStore.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun handleHit(hit: BarcodeHit) {
        if (product != null || qr != null || isLookingUp) return
        controller.paused = true
        announceFound()
        val value = hit.value.trim()
        if (hit.twoDimensional || !QrText.isProductCode(value)) {
            val info = if (hit.twoDimensional) QrText.describe(value) else QrInfo(QrKind.TEXT, "Штрихкод", "Штрихкод: $value", value)
            qr = info
            SpeechSynthesizer.speak(info.spoken, SettingsStore.speechRate)
            HistoryStore.addEntry(if (hit.twoDimensional) "QR-код" else "Штрихкод", info.title, info.spoken)
            return
        }
        isLookingUp = true
        errorMessage = null
        SpeechSynthesizer.speakQueued("Код найден. Ищу товар.", SettingsStore.speechRate, true)
        scope.launch {
            try {
                val result = BarcodeService.lookup(value)
                val image = RemoteImages.load(result.imageUrl)
                product = result
                productImage = image
                expanded = false
                HistoryStore.addEntry("Товар", result.productName ?: result.title, result.description, image)
                SpeechSynthesizer.speak(result.description, SettingsStore.speechRate)
            } catch (e: Exception) {
                val message = ApiClient.messageFor(e)
                errorMessage = message
                SpeechSynthesizer.speak(message, SettingsStore.speechRate)
                // Пауза, чтобы тот же код не отправился снова мгновенно.
                delay(3000)
                controller.paused = false
            } finally {
                isLookingUp = false
            }
        }
    }

    fun reset() {
        product = null
        productImage = null
        qr = null
        errorMessage = null
        expanded = false
        SpeechSynthesizer.stop()
        startedAt = SystemClock.elapsedRealtime()
        hintsDone = 0
        controller.paused = false
    }

    LaunchedEffect(controller) { controller.onResult = { hit -> handleHit(hit) } }

    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            SpeechSynthesizer.speakQueued(
                if (mode == ScanMode.QR) "Наведите камеру на QR-код и двигайте телефон медленно. Услышите сигнал, когда код найден."
                else "Наведите камеру на штрихкод товара и двигайте телефон медленно. Услышите сигнал, когда код найден.",
                SettingsStore.speechRate,
                false
            )
        }
    }

    // Если код долго не находится — голосом подсказываем, что поправить.
    LaunchedEffect(hasCamera, product, qr, controller.torchOn) {
        if (!hasCamera || product != null || qr != null) return@LaunchedEffect
        while (true) {
            delay(1000)
            if (isLookingUp) continue
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            ScanHints.next(elapsed, hintsDone, controller.hasTorch, controller.torchOn)?.let { (stage, text) ->
                hintsDone = stage + 1
                SpeechSynthesizer.speakQueued(text, SettingsStore.speechRate, false)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.release()
            SpeechSynthesizer.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            BarcodeCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
            if (product == null && qr == null) {
                BracketFrame(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(if (mode == ScanMode.QR) 0.62f else 0.84f)
                        .aspectRatio(if (mode == ScanMode.QR) 1f else 1.8f),
                    color = Theme.accent
                )
            }
        } else {
            CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
        }

        // верх: заголовок и переключатель режимов
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraTopBar(title = "Сканер", onBack = onBack)
            ScanModeSwitch(selected = mode, onSelect = onSwitchMode)
        }

        // подсказка / индикатор загрузки / фонарик
        if (hasCamera && isLookingUp) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (hasCamera && product == null && qr == null) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (controller.hasTorch) {
                    AssistPill(
                        label = if (controller.torchOn) "Фонарик: вкл" else "Фонарик: выкл",
                        on = controller.torchOn
                    ) { controller.setTorch(!controller.torchOn) }
                }
                Text(
                    if (mode == ScanMode.QR) "Наведите камеру на QR-код" else "Наведите камеру на штрих-код",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color(0x990A1020), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // низ: результат
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            errorMessage?.let { ErrorBanner(it) }

            qr?.let { info ->
                GlassSheet {
                    Text(info.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(info.copy, color = Theme.textPrimary.copy(alpha = 0.9f), fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(info.spoken, SettingsStore.speechRate)
                        }
                        val target = info.openUrl
                        if (target != null) {
                            ActionButton("Открыть", icon = Icons.Filled.OpenInBrowser, modifier = Modifier.weight(1f)) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            ActionButton("Копировать", icon = Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                                clipboard.setText(AnnotatedString(info.copy))
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    ActionButton("Сканировать снова", modifier = Modifier.fillMaxWidth()) { reset() }
                }
            }

            product?.let { result ->
                ProductCard(
                    result = result,
                    image = productImage,
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                    onSpeak = { SpeechSynthesizer.speak(result.description, SettingsStore.speechRate) },
                    onReadLabel = { onSwitchMode(ScanMode.TEXT) },
                    onSimilar = {
                        // Для найденного товара ищем аналоги, для неизвестного — сам штрихкод в интернете.
                        val query = if (result.found) "аналоги " + (result.productName ?: result.title) else "штрихкод ${result.code}"
                        val url = "https://www.google.com/search?q=" + Uri.encode(query)
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAgain = { reset() }
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    result: BarcodeResult,
    image: Bitmap?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSpeak: () -> Unit,
    onSimilar: () -> Unit,
    onReadLabel: () -> Unit,
    onAgain: () -> Unit
) {
    GlassSheet {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BitmapThumb(bitmap = image, size = 64.dp, fallback = Icons.Outlined.ShoppingBag)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.productName ?: result.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                val sub = result.quantity ?: if (result.found) null else "Код: ${result.code}"
                if (sub != null) Text(sub, color = Theme.textSecondary, fontSize = 14.sp)
            }
            CircleButton(
                icon = Icons.Filled.VolumeUp,
                description = "Озвучить описание товара",
                onClick = onSpeak,
                size = 48.dp,
                iconSize = 22.dp,
                container = Theme.accentSoft,
                tint = Theme.accent
            )
        }

        if (!result.found) {
            Text(result.description, color = Theme.textSecondary, fontSize = 14.sp)
            LabeledLine("Страна", result.country)
        } else {
            LabeledLine("Бренд", result.brand)
            LabeledLine("Страна", result.country)
            LabeledLine("Состав", result.ingredients, maxLines = if (expanded) Int.MAX_VALUE else 2)
            LabeledLine("Пищевая ценность (на 100 г)", result.nutrition)
            if (expanded) {
                LabeledLine("Аллергены", result.allergens)
                LabeledLine("Nutri-Score", result.nutriScore)
                LabeledLine("Штрих-код", result.code)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (result.found) {
                ActionButton(
                    if (expanded) "Свернуть" else "Подробнее",
                    modifier = Modifier.weight(1f),
                    onClick = onToggleExpanded
                )
            } else {
                ActionButton("Прочитать надпись", modifier = Modifier.weight(1f), onClick = onReadLabel)
            }
            ActionButton(
                if (result.found) "Найти похожее" else "Найти в интернете",
                icon = Icons.Outlined.Search,
                modifier = Modifier.weight(1f),
                onClick = onSimilar
            )
        }
        ActionButton("Сканировать снова", modifier = Modifier.fillMaxWidth(), onClick = onAgain)
    }
}

/** «Бренд: Milka» — подпись жирным, значение обычным; пустые значения не показываем. */
@Composable
private fun LabeledLine(label: String, value: String?, maxLines: Int = Int.MAX_VALUE) {
    if (value.isNullOrBlank()) return
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label: ") }
            append(value)
        },
        color = Theme.textPrimary.copy(alpha = 0.92f),
        fontSize = 14.sp,
        maxLines = maxLines
    )
}
