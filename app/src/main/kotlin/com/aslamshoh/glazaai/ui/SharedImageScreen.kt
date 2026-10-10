package com.aslamshoh.glazaai.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.OcrService
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.ImageLoading
import com.aslamshoh.glazaai.util.SharedImageText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * «Поделиться» из другого приложения: картинка из мессенджера, браузера или галереи → описание и текст на ней вслух.
 * Описание и распознавание текста идут параллельно; если одно из двух не вышло, озвучивается второе.
 */
@Composable
fun SharedImageScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) { onDispose { SpeechSynthesizer.stop() } }

    LaunchedEffect(uri) {
        busy = true
        error = null
        SpeechSynthesizer.speakQueued("Получила картинку. Смотрю, что на ней.", SettingsStore.speechRate, true)
        val bmp = ImageLoading.decodeUri(context, uri)
        if (bmp == null) {
            busy = false
            error = "Не удалось открыть эту картинку."
            SpeechSynthesizer.speak(error.orEmpty(), SettingsStore.speechRate)
            return@LaunchedEffect
        }
        bitmap = bmp
        val dataUrl = ImageEncoding.dataUrl(bmp, maxDimension = 1600, quality = 85)
        var scene: String? = null
        var ocr: String? = null
        var failure: Exception? = null
        coroutineScope {
            val a = async { try { VisionService.describeScene(dataUrl).description } catch (e: Exception) { failure = e; null } }
            val b = async { try { OcrService.recognize(dataUrl, SettingsStore.appLanguage).recognizedText } catch (e: Exception) { failure = e; null } }
            scene = a.await()
            ocr = b.await()
        }
        busy = false
        if (scene == null && ocr == null) {
            val message = failure?.let { ApiClient.messageFor(it) } ?: "Не удалось разобрать картинку."
            error = message
            SpeechSynthesizer.speak(message, SettingsStore.speechRate)
        } else {
            val said = SharedImageText.compose(scene, ocr)
            text = said
            HistoryStore.addEntry("Поделились", scene?.take(60) ?: "Картинка", said, bmp)
            SpeechSynthesizer.speak(said, SettingsStore.speechRate)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Theme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CameraTopBar(title = "Картинка из другого приложения", onBack = onBack)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let { ErrorBanner(it) }
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        BitmapThumb(bitmap = bitmap, size = 96.dp)
                        Text(
                            if (busy) "Смотрю, что на картинке…" else text ?: "Готово",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val t = text
                    if (t != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                                SpeechSynthesizer.speak(t, SettingsStore.speechRate)
                            }
                            ActionButton("Копировать", icon = Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                                clipboard.setText(AnnotatedString(t))
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
