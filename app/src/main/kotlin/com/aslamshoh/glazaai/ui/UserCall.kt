package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
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

    LaunchedEffect(permsOk) {
        if (!permsOk) return@LaunchedEffect
        launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantDisconnected -> end("Волонтёр завершил звонок.")
                    is RoomEvent.Disconnected -> end("Связь прервалась. Звонок завершён.")
                    is RoomEvent.Reconnecting -> {
                        status = "Связь пропала, восстанавливаю…"
                        SpeechSynthesizer.speak("Связь пропала. Восстанавливаю.", SettingsStore.speechRate)
                    }
                    is RoomEvent.Reconnected -> status = "Вы на связи"
                    is RoomEvent.DataReceived -> HelpText.chatDecode(event.data)?.let {
                        // волонтёр написал: читаем вслух и показываем на экране
                        messages.add(it)
                        SpeechSynthesizer.speak("Волонтёр пишет: $it", SettingsStore.speechRate)
                    }
                    else -> {}
                }
            }
        }
        try {
            // человек держит телефон камерой от себя — нужна задняя камера
            room.videoTrackCaptureDefaults = room.videoTrackCaptureDefaults.copy(position = CameraPosition.BACK)
            room.connect(info.url, info.token)
            room.localParticipant.setMicrophoneEnabled(true)
            if (info.publishVideo) room.localParticipant.setCameraEnabled(true)
            // говорить с волонтёром удобнее на громкой связи, когда телефон держат перед собой
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            delay(600)
            am.isSpeakerphoneOn = true
            status = "Вы на связи" + (volunteerName?.let { " с $it" } ?: "")
            SpeechSynthesizer.speak(
                "Вы на связи. Направьте камеру на то, что нужно показать, и говорите.",
                SettingsStore.speechRate
            )
        } catch (e: Exception) {
            end("Не удалось подключиться к видеозвонку. Проверьте интернет и попробуйте ещё раз.")
            return@LaunchedEffect
        }
        // если волонтёр так и не подключился, не ждём бесконечно
        delay(45_000)
        if (room.remoteParticipants.isEmpty()) end("Волонтёр не подключился. Попробуйте позвать ещё раз.")
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
