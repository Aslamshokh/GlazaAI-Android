package com.aslamshoh.glazaai.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.camera.LiveCameraPreview
import com.aslamshoh.glazaai.camera.rememberLiveCameraController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.NearItem
import com.aslamshoh.glazaai.network.NearService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Режим «Рядом» — ассистент ориентирования, а не навигатор. Только ближняя зона (радиус из
 * настроек, 2–5 м, по умолчанию 3): ступенька, бордюр, лестница, люди, столбы, вход, пешеходный
 * переход, светофор. Коротко и быстро; дальние объекты не называем — это задача навигатора.
 *
 * Кадры уходят по одному (следующий — после ответа), уменьшенными до 640 px — так отклик быстрее.
 * Одно и то же не повторяем чаще, чем раз в несколько секунд; очень близкое — с вибрацией.
 */
@Composable
fun NearScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberLiveCameraController()
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val sessionId = remember { UUID.randomUUID().toString() }
    val inFlight = remember { AtomicBoolean(false) }
    val lastSaid = remember { HashMap<String, Long>() }

    var items by remember { mutableStateOf<List<NearItem>>(emptyList()) }
    var paused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var latency by remember { mutableStateOf(0L) }
    val radius = SettingsStore.nearRadius

    fun announce(list: List<NearItem>) {
        val now = SystemClock.elapsedRealtime()
        // Сначала очень близкое, потом остальное; не больше двух фраз за раз.
        val fresh = list.filter { item ->
            val key = "${item.key}:${item.direction}"
            val cooldown = if (item.layer == "very_close") 2500L else 6000L
            now - (lastSaid[key] ?: 0L) > cooldown
        }.take(2)
        if (fresh.isEmpty()) return
        val urgent = fresh.any { it.layer == "very_close" && it.kind == "obstacle" }
        if (!urgent && SpeechSynthesizer.isSpeaking) return
        fresh.forEach { lastSaid["${it.key}:${it.direction}"] = now }
        if (urgent && SettingsStore.hapticsEnabled) vibrateAlert(vibrator)
        SpeechSynthesizer.speakQueued(fresh.joinToString(". ") { it.say }, SettingsStore.speechRate, urgent)
    }

    fun onFrame(bitmap: Bitmap) {
        if (paused || !inFlight.compareAndSet(false, true)) return
        val dataUrl = try {
            ImageEncoding.dataUrl(bitmap, 640, 75)
        } catch (e: Exception) {
            inFlight.set(false)
            return
        }
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val r = NearService.frame(dataUrl, sessionId, radius)
                val list = r.items.orEmpty()
                items = list
                error = null
                latency = SystemClock.elapsedRealtime() - started
                announce(list)
            } catch (e: Exception) {
                val message = ApiClient.messageFor(e)
                if (error != message) SpeechSynthesizer.speakQueued(message, SettingsStore.speechRate, false)
                error = message
            } finally {
                inFlight.set(false)
            }
        }
    }

    DisposableEffect(Unit) {
        controller.frameListener = { onFrame(it) }
        onDispose {
            controller.release()
            SpeechSynthesizer.stop()
        }
    }
    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            SpeechSynthesizer.speakQueued(
                "Режим рядом. Вижу на ${radius.roundToInt()} ${meters(radius.roundToInt())} вперёд. Говорю только о том, что близко.",
                SettingsStore.speechRate,
                true
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) LiveCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
        else CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            CameraTopBar(title = "Рядом", onBack = onBack, hint = Hints.NEAR)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }
            GlassSheet {
                Text(
                    "Радиус: ${radius.roundToInt()} ${meters(radius.roundToInt())}" +
                        if (latency > 0) " · отклик ${latency} мс" else "",
                    color = Theme.textSecondary,
                    fontSize = 13.sp
                )
                val summary = when {
                    paused -> "Пауза"
                    items.isEmpty() -> "Рядом ничего не мешает"
                    else -> items.take(3).joinToString(". ") { it.say }
                }
                // liveRegion: TalkBack сам прочитает изменения, даже если голос приложения выключен.
                Text(
                    summary,
                    color = if (items.any { it.layer == "very_close" && it.kind == "obstacle" }) Theme.warning else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton(if (paused) "Продолжить" else "Пауза", filled = true, modifier = Modifier.weight(1f)) {
                        paused = !paused
                        SpeechSynthesizer.speakQueued(if (paused) "Пауза" else "Продолжаю", SettingsStore.speechRate, true)
                    }
                    ActionButton("Повторить", modifier = Modifier.weight(1f)) {
                        val text = if (items.isEmpty()) "Рядом ничего не мешает." else items.take(3).joinToString(". ") { it.say }
                        SpeechSynthesizer.speakQueued(text, SettingsStore.speechRate, true)
                    }
                }
            }
        }
    }
}

private fun meters(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "метр"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "метра"
    else -> "метров"
}
