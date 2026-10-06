package com.aslamshoh.glazaai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.Toast
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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.BarcodeResult
import com.aslamshoh.glazaai.network.BarcodeService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.RemoteImages
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран 4 макета — «Сканер товара» (и QR-коды): камера непрерывно ищет штрих-код (ZXing на
 * устройстве), по найденному коду backend берёт данные товара из Open Food Facts. Результат —
 * карточка товара: фото, название, бренд, страна, состав, пищевая ценность.
 * Срок годности здесь не показываем сознательно: в каталоге товаров его нет, он напечатан на
 * конкретной упаковке (его читает вкладка «Текст»).
 */
@Composable
fun BarcodeScreen(mode: ScanMode, onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hasCamera = rememberCameraPermission()

    var isLookingUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var product by remember { mutableStateOf<BarcodeResult?>(null) }
    var productImage by remember { mutableStateOf<Bitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf<String?>(null) }
    var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    fun handleScan(value: String, format: BarcodeFormat) {
        // QR — это просто текст, а не товарный код для Open Food Facts.
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
                val image = RemoteImages.load(result.imageUrl)
                product = result
                productImage = image
                expanded = false
                HistoryStore.addEntry("Товар", result.productName ?: result.title, result.description, image)
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
        product = null
        productImage = null
        qrText = null
        errorMessage = null
        expanded = false
        SpeechSynthesizer.stop()
        scannerView?.resume()
    }

    LaunchedEffect(scannerView) {
        if (scannerView != null) {
            delay(600)
            scannerView?.resume()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scannerView?.pause()
            SpeechSynthesizer.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    DecoratedBarcodeView(ctx).apply {
                        // Свою «прицельную» рамку рисуем сами (как на макете) — стандартную прячем.
                        viewFinder.visibility = View.GONE
                        statusView.visibility = View.GONE
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                                if (product != null || qrText != null || isLookingUp) return
                                handleScan(result.text, result.barcodeFormat)
                            }

                            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>) {}
                        })
                        // resume() — не здесь, а чуть позже (см. LaunchedEffect ниже): предыдущий экран
                        // с камерой должен успеть её отпустить, иначе сканер откроется чёрным.
                        scannerView = this
                    }
                }
            )
            if (product == null && qrText == null) {
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

        // подсказка / индикатор загрузки
        if (hasCamera && isLookingUp) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (hasCamera && product == null && qrText == null) {
            Text(
                if (mode == ScanMode.QR) "Наведите камеру на QR-код" else "Наведите камеру на штрих-код",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color(0x990A1020), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
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

            qrText?.let { text ->
                val isUrl = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)
                GlassSheet {
                    Text("QR-код распознан", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text, color = Theme.textPrimary.copy(alpha = 0.9f), fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(text, SettingsStore.speechRate)
                        }
                        if (isUrl) {
                            ActionButton("Открыть", icon = Icons.Filled.OpenInBrowser, modifier = Modifier.weight(1f)) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            ActionButton("Копировать", icon = Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                                clipboard.setText(AnnotatedString(text))
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
                    onSimilar = {
                        val name = result.productName ?: result.title
                        val url = "https://www.google.com/search?q=" + Uri.encode("аналоги $name")
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
            Text(
                "Этого товара нет в базе Open Food Facts. База лучше всего знает продукты питания.",
                color = Theme.textSecondary,
                fontSize = 14.sp
            )
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
            ActionButton(
                if (expanded) "Свернуть" else "Подробнее",
                modifier = Modifier.weight(1f),
                onClick = onToggleExpanded
            )
            ActionButton(
                "Найти похожее",
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
