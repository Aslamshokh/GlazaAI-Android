package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.network.LiveKitInfo
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.Beeper
import com.aslamshoh.glazaai.util.HelpText
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.CameraPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Видеозвонок с волонтёром внутри приложения (LiveKit). Включает ЗАДНЮЮ камеру и микрофон человека,
 * волонтёр видит картинку и говорит голосом. Экран не гаснет, пока идёт звонок.
 * Когда звонок закончился (человек нажал «Завершить», волонтёр отключился или пропала связь), вызывается [onEnded]
 * один раз; reason — что сказать вслух (null — просто «звонок завершён»).
 */
@Composable
fun UserCall(info: LiveKitInfo, volunteerName: String?, onEnded: (reason: String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finished = remember { AtomicBoolean(false) }
    fun end(reason: String?) {
        if (finished.compareAndSet(false, true)) onEnded(reason)
    }

    // экран не гаснет во время звонка
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var permsOk by remember { mutableStateOf(has(Manifest.permission.CAMERA) && has(Manifest.permission.RECORD_AUDIO)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permsOk = result.values.all { it }
        if (!permsOk) end("Для видеозвонка нужен доступ к камере и микрофону. Разрешите его в настройках телефона.")
    }
    LaunchedEffect(Unit) {
        if (!permsOk) launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val room = remember { LiveKit.create(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            room.disconnect()
            room.release()
        }
    }

    var status by remember { mutableStateOf("Соединяю с волонтёром…") }
    var muted by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<String>() }

    // громкая связь: на Android 12+ — через выбор устройства связи, раньше — isSpeakerphoneOn
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun speaker(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    am.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { am.setCommunicationDevice(it) }
                } else am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = on
            }
        } catch (_: Exception) {
            // не получилось — звук пойдёт в обычный динамик
        }
    }
    DisposableEffect(Unit) { onDispose { speaker(false) } }

    // Волонтёр в звонке? Пока нет — ждём; ушёл — ждём, вдруг просто моргнула связь.
    var volunteerHere by remember { mutableStateOf(false) }
    var leftAt by remember { mutableStateOf(0L) }
    var everConnected by remember { mutableStateOf(false) }
    var lostAt by remember { mutableStateOf(0L) }

    suspend fun connectWithRetry(): String? {
        var last: String? = null
        for (attempt in 1..3) {
            try {
                room.connect(info.url, info.token)
                return null
            } catch (e: Exception) {
                last = e.message ?: e.javaClass.simpleName
                status = "Не получилось соединиться, пробую ещё…"
                delay(1500L * attempt)
            }
        }
        return last
    }

    LaunchedEffect(permsOk) {
        if (!permsOk) return@LaunchedEffect
        launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        val first = !volunteerHere
                        volunteerHere = true
                        leftAt = 0L
                        status = "Вы на связи" + (volunteerName?.let { " с $it" } ?: "")
                        if (first) {
                            Beeper.success()
                            SpeechSynthesizer.speak(
                                "Волонтёр подключился. Направьте камеру на то, что нужно показать, и говорите.",
                                SettingsStore.speechRate
                            )
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> if (room.remoteParticipants.isEmpty()) {
                        volunteerHere = false
                        leftAt = System.currentTimeMillis()
                        status = "Волонтёр отключился, жду 15 секунд…"
                        SpeechSynthesizer.speak("Волонтёр отключился. Подожду, вдруг пропала связь.", SettingsStore.speechRate)
                    }
                    // Обрыв нашей связи: не кладём трубку сразу — сторож ниже переподключится.
                    is RoomEvent.Disconnected -> if (everConnected && lostAt == 0L) {
                        lostAt = System.currentTimeMillis()
                        status = "Связь прервалась, переподключаюсь…"
                        SpeechSynthesizer.speak("Связь прервалась. Переподключаюсь.", SettingsStore.speechRate)
                    }
                    is RoomEvent.Reconnecting -> {
                        status = "Связь пропала, восстанавливаю…"
                        SpeechSynthesizer.speak("Связь пропала. Восстанавливаю.", SettingsStore.speechRate)
                    }
                    is RoomEvent.Reconnected -> {
                        status = if (volunteerHere) "Вы на связи" else "Жду волонтёра…"
                        SpeechSynthesizer.speak("Связь восстановлена.", SettingsStore.speechRate)
                    }
                    is RoomEvent.DataReceived -> HelpText.chatDecode(event.data)?.let {
                        // волонтёр написал: читаем вслух и показываем на экране
                        messages.add(it)
                        SpeechSynthesizer.speak("Волонтёр пишет: $it", SettingsStore.speechRate)
                    }
                    else -> {}
                }
            }
        }
        // человек держит телефон камерой от себя — нужна задняя камера
        room.videoTrackCaptureDefaults = room.videoTrackCaptureDefaults.copy(position = CameraPosition.BACK)
        // слабый интернет: три попытки подключиться
        val err = connectWithRetry()
        if (err != null) {
            end("Не удалось подключиться к видеозвонку. Проверьте интернет и попробуйте ещё раз. ($err)")
            return@LaunchedEffect
        }
        everConnected = true
        try {
            room.localParticipant.setMicrophoneEnabled(true)
            if (info.publishVideo) room.localParticipant.setCameraEnabled(true)
        } catch (e: Exception) {
            SpeechSynthesizer.speak("Камера или микрофон не включились. Волонтёр может вас не видеть.", SettingsStore.speechRate)
        }
        delay(600)
        speaker(true)
        if (room.remoteParticipants.isNotEmpty()) {
            volunteerHere = true
            status = "Вы на связи" + (volunteerName?.let { " с $it" } ?: "")
            Beeper.success()
            SpeechSynthesizer.speak(
                "Вы на связи. Направьте камеру на то, что нужно показать, и говорите.",
                SettingsStore.speechRate
            )
        } else {
            status = "Жду, когда волонтёр войдёт в звонок…"
            SpeechSynthesizer.speak("Звонок открыт. Волонтёр сейчас подключится, подождите.", SettingsStore.speechRate)
        }
        // сторож: волонтёр так и не пришёл (45 с) или ушёл и не вернулся (15 с)
        var waited = 0
        while (true) {
            delay(1000)
            waited++
            if (!volunteerHere && room.remoteParticipants.isNotEmpty()) volunteerHere = true
            if (lostAt > 0L) {
                val again = connectWithRetry()
                if (again != null) {
                    end("Связь прервалась. Звонок завершён. ($again)")
                    return@LaunchedEffect
                }
                lostAt = 0L
                try {
                    room.localParticipant.setMicrophoneEnabled(!muted)
                    if (info.publishVideo) room.localParticipant.setCameraEnabled(true)
                } catch (_: Exception) {
                }
                speaker(true)
                status = if (volunteerHere) "Вы на связи" else "Жду волонтёра…"
                SpeechSynthesizer.speak("Связь восстановлена.", SettingsStore.speechRate)
            }
            if (!volunteerHere && leftAt == 0L) {
                if (waited == 20) SpeechSynthesizer.speak("Волонтёр ещё подключается.", SettingsStore.speechRate)
                if (waited >= 45) {
                    end("Волонтёр не подключился. Попробуйте позвать ещё раз.")
                    return@LaunchedEffect
                }
            }
            if (!volunteerHere && leftAt > 0L && System.currentTimeMillis() - leftAt > 15_000) {
                end("Волонтёр завершил звонок.")
                return@LaunchedEffect
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(status, color = Theme.textPrimary, fontSize = 18.sp)
        Text(
            "Камера смотрит от вас. Волонтёр видит картинку и слышит вас.",
            color = Theme.textSecondary, fontSize = 14.sp
        )
        messages.takeLast(3).forEach { Text("Волонтёр: $it", color = Theme.textPrimary, fontSize = 16.sp) }
        HelpButton(if (muted) "Включить микрофон" else "Выключить микрофон", Theme.surfaceAlt, null) {
            muted = !muted
            scope.launch { room.localParticipant.setMicrophoneEnabled(!muted) }
            SpeechSynthesizer.speak(if (muted) "Микрофон выключен." else "Микрофон включён.", SettingsStore.speechRate)
        }
        HelpButton("Завершить звонок", Theme.critical, null) { end(null) }
    }
}
