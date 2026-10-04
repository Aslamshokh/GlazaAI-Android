package com.aslamshoh.glazaai.nav

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.live.LiveEventManager
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
 * Камера во время навигации. Что делает с кадром, зависит от этапа пути:
 *  — по дороге (WALKING): ищет препятствия (тот же backend, что живой режим «Предметы») и
 *    озвучивает только важное — критическое и высокое;
 *  — на остановке (WAITING): ищет подъезжающие машины и читает номер маршрута;
 *  — в транспорте и без навигации — молчит и не нагружает ноутбук.
 * Кадры идут по очереди: пока нет ответа на предыдущий, новые отбрасываются.
 */
class NavPipeline(
    private val scope: CoroutineScope,
    private val nav: NavController,
    private val onCritical: () -> Unit
) {
    private val sessionId = UUID.randomUUID().toString()
    private val events = LiveEventManager()
    private val inFlight = AtomicBoolean(false)
    @Volatile private var lastVehicleMs = 0L
    private var errorSpoken = false

    var tracks by mutableStateOf<List<LiveTrack>>(emptyList())
        private set
    var vehicles by mutableStateOf<List<VehicleSighting>>(emptyList())
        private set
    var latencyMs by mutableStateOf(0L)
        private set

    /** Вызывается камерой на фоновом потоке для каждого кадра. */
    fun onFrame(bitmap: Bitmap) {
        val mode = nav.cameraMode
        if (mode == CameraMode.OFF) {
            if (tracks.isNotEmpty() || vehicles.isNotEmpty()) {
                scope.launch {
                    tracks = emptyList()
                    vehicles = emptyList()
                }
            }
            return
        }
        // Номера читаем реже и в большем размере: нужна чёткость, а не частота.
        val now = SystemClock.elapsedRealtime()
        if (mode == CameraMode.VEHICLES && now - lastVehicleMs < 700) return
        if (!inFlight.compareAndSet(false, true)) return
        val dataUrl = try {
            if (mode == CameraMode.VEHICLES) ImageEncoding.dataUrl(bitmap, 1024, 78) else ImageEncoding.dataUrl(bitmap, 640, 70)
        } catch (e: Exception) {
            inFlight.set(false)
            return
        }
        if (mode == CameraMode.VEHICLES) lastVehicleMs = now
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                if (mode == CameraMode.VEHICLES) processVehicles(dataUrl) else processObstacles(dataUrl)
                latencyMs = SystemClock.elapsedRealtime() - started
                errorSpoken = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!errorSpoken) {
                    errorSpoken = true
                    SpeechSynthesizer.speakQueued(ApiClient.messageFor(e), SettingsStore.speechRate, false)
                }
                delay(1500)
            } finally {
                inFlight.set(false)
            }
        }
    }

    private suspend fun processObstacles(dataUrl: String) {
        val result = VisionService.liveFrame(dataUrl, sessionId)
        val all = result.tracks ?: emptyList()
        tracks = all
        vehicles = emptyList()
        // По дороге говорим только о важном, чтобы не заглушать подсказки маршрута.
        val important = all.filter { it.priority == "critical" || it.priority == "high" }
        val a = events.process(important, SystemClock.elapsedRealtime(), SpeechSynthesizer.isSpeaking)
        if (a != null) {
            SpeechSynthesizer.speakQueued(a.text, SettingsStore.speechRate, a.interrupt)
            if (a.priority == "critical" && SettingsStore.hapticsEnabled) onCritical()
        }
    }

    private suspend fun processVehicles(dataUrl: String) {
        val ref = nav.expectedRef
        val result = NavService.vehicleRef(dataUrl, ref)
        val list = result.vehicles ?: emptyList()
        vehicles = list
        tracks = emptyList()
        if (ref != null) {
            nav.vehicleAnnouncer.process(list, ref, SystemClock.elapsedRealtime())?.let {
                nav.onVehicleSpeech(it.text, it.interrupt)
                if (it.interrupt && SettingsStore.hapticsEnabled) onCritical()
            }
        }
    }

    /** «Что вокруг?»: подробный список всего, что камера видит сейчас. */
    fun describeAround(): String {
        val t = tracks
        return if (t.isEmpty()) "Камера пока ничего не видит." else events.describeAll(t)
    }
}
