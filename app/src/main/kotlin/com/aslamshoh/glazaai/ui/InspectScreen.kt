package com.aslamshoh.glazaai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.InspectResult
import com.aslamshoh.glazaai.network.InspectService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.Beeper
import com.aslamshoh.glazaai.util.ImageEncoding
import kotlinx.coroutines.launch

/**
 * «В руке»: человек держит вещь перед камерой и нажимает кнопку — приложение называет предмет,
 * его цвет, а для овощей и фруктов оценивает состояние по цвету (с оговоркой, что проверить нужно
 * на ощупь и по запаху). Распознаёт сервер.
 */
@Composable
fun InspectScreen(onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<InspectResult?>(null) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            SpeechSynthesizer.stop()
        }
    }

    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            SpeechSynthesizer.speakQueued(
                "Поднесите вещь к камере на расстоянии вытянутой руки и нажмите большую кнопку. " +
                    "Скажу, что это, какого цвета, а для овощей и фруктов оценю состояние.",
                SettingsStore.speechRate,
                false
            )
        }
    }

    fun capture() {
        if (busy || !hasCamera) return
        error = null
        busy = true
        Beeper.shutter()
        SpeechSynthesizer.speakQueued("Смотрю…", SettingsStore.speechRate, true)
        controller.captureFrame { bitmap ->
            if (bitmap == null) {
                busy = false
                error = "Не удалось получить кадр с камеры. Попробуйте ещё раз."
                SpeechSynthesizer.speak(error.orEmpty(), SettingsStore.speechRate)
                return@captureFrame
            }
            val dataUrl = ImageEncoding.dataUrl(bitmap, maxDimension = 1280, quality = 85)
            scope.launch {
                try {
                    val r = InspectService.inspect(dataUrl)
                    result = r
                    snapshot = bitmap
                    HistoryStore.addEntry("В руке", r.title, r.description, bitmap)
                    SpeechSynthesizer.speak(r.description, SettingsStore.speechRate)
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
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.7f).aspectRatio(1f),
                color = Theme.accent
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraTopBar(title = "Что в руке", onBack = onBack)
            ScanModeSwitch(selected = ScanMode.HAND, onSelect = onSwitchMode)
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

            if (current == null) {
                GlassSheet {
                    Text(
                        "Держите вещь перед камерой, чтобы она занимала середину кадра, при хорошем свете. " +
                            "Подойдут продукты, овощи, фрукты, вещи в руке.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
                    CaptureAssistRow(controller, null)
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
                            current.colorText?.let { Text(it, color = Theme.textSecondary, fontSize = 14.sp) }
                        }
                    }

                    current.conditionState?.let { state ->
                        val attention = current.attention == true
                        val tint = if (attention) Theme.warning else Theme.accent
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, tint, RoundedCornerShape(14.dp))
                                .background(Theme.surfaceAlt)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Состояние: $state", color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            current.condition?.let { Text(it, color = Color.White, fontSize = 14.sp) }
                            Text(
                                "Это оценка только по цвету: проверьте на ощупь и по запаху.",
                                color = Theme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    val alternatives = current.alternatives.orEmpty()
                    if (alternatives.isNotEmpty()) {
                        Text(
                            "Также может быть: " + alternatives.joinToString(", "),
                            color = Theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Text(current.description, color = Theme.textPrimary.copy(alpha = 0.92f), fontSize = 15.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            SpeechSynthesizer.speak(current.description, SettingsStore.speechRate)
                        }
                        ActionButton("Прочитать надпись", modifier = Modifier.weight(1f)) { onSwitchMode(ScanMode.TEXT) }
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
