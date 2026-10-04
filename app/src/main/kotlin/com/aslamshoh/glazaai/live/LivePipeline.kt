package com.aslamshoh.glazaai.live

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Конвейер живого режима: кадр с камеры -> backend (/vision/live-frame: YOLO + трекинг +
 * расстояние + приоритет) -> менеджер событий (что и когда озвучить) -> голос / вибрация.
 *
 * Кадры отправляются по очереди: пока не пришёл ответ на предыдущий, новые отбрасываются —
 * так частота кадров сама подстраивается под скорость сети и ноутбука (обычно 2–5 в секунду).
 */
class LivePipeline(
    private val scope: CoroutineScope,
    private val onCritical: () -> Unit
) {
    val sessionId: String = UUID.randomUUID().toString()

    private val events = LiveEventManager()
    private val inFlight = AtomicBoolean(false)
    private var errorSpoken = false

    var tracks by mutableStateOf<List<LiveTrack>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var qualityHint by mutableStateOf<String?>(null)
        private set
    var latencyMs by mutableStateOf(0L)
        private set
    var paused by mutableStateOf(false)
        private set

    /** Вызывается камерой на фоновом потоке для каждого кадра. */
    fun onFrame(bitmap: Bitmap) {
        if (paused) return
        if (!inFlight.compareAndSet(false, true)) return
        val dataUrl = try {
            ImageEncoding.dataUrl(bitmap, 640, 70)
        } catch (e: Exception) {
            inFlight.set(false)
            return
        }
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val result = VisionService.liveFrame(dataUrl, sessionId)
                val list = result.tracks ?: emptyList()
                tracks = list
                qualityHint = result.qualityHint
                errorMessage = null
                errorSpoken = false
                latencyMs = SystemClock.elapsedRealtime() - started

                val announcement = events.process(
                    tracks = list,
                    nowMs = SystemClock.elapsedRealtime(),
                    isSpeaking = SpeechSynthesizer.isSpeaking
                )
                if (announcement != null) {
                    SpeechSynthesizer.speakQueued(
                        announcement.text,
                        SettingsStore.speechRate,
                        announcement.interrupt
                    )
                    if (announcement.priority == "critical" && SettingsStore.hapticsEnabled) {
                        onCritical()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = ApiClient.messageFor(e)
                errorMessage = message
                // Незрячий пользователь не видит красную плашку — об обрыве связи говорим
                // вслух, но один раз за обрыв, а не на каждый неудачный кадр.
                if (!errorSpoken) {
                    errorSpoken = true
                    SpeechSynthesizer.speakQueued(message, SettingsStore.speechRate, true)
                }
                delay(1500) // не заваливаем сервер запросами, пока он недоступен
            } finally {
                inFlight.set(false)
            }
        }
    }

    fun togglePause() {
        paused = !paused
        if (paused) {
            SpeechSynthesizer.stop()
        } else {
            events.reset()
        }
    }

    /** Кнопка «Что вокруг?» — подробный список всего, что видно сейчас, включая то, о чём
     * автоматический режим молчит (низкий приоритет). */
    fun describeAll() {
        SpeechSynthesizer.speakQueued(events.describeAll(tracks), SettingsStore.speechRate, true)
    }
}
