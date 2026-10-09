package com.aslamshoh.glazaai.volunteer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.volunteer.ActiveCall
import com.aslamshoh.glazaai.volunteer.ChatMsg
import com.aslamshoh.glazaai.volunteer.OfferText
import com.aslamshoh.glazaai.volunteer.VolText
import com.aslamshoh.glazaai.volunteer.VolunteerRepo
import com.aslamshoh.glazaai.volunteer.net.LiveKitInfo
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Экран идущего звонка: внутри приложения (LiveKit) или запасной вариант через ссылку Jitsi. */
@Composable
fun CallHost(call: ActiveCall) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (call.livekit != null) {
        LiveCall(call) { reason ->
            scope.launch {
                VolunteerRepo.finishCall()
                if (reason != null) VolunteerRepo.showMessage(reason)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Идёт вызов", color = VColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(OfferText.title(call.userName, call.urgent), color = VColors.text, fontSize = 18.sp)
            Text("Язык: ${OfferText.languageName(call.language)}", color = VColors.textSecondary, fontSize = 14.sp)
            Text(
                "Сервер не настроен на видеозвонок внутри приложения, поэтому звонок откроется в Jitsi Meet. " +
                    "Не записывайте звонок и не просите личные данные.",
                color = VColors.textSecondary, fontSize = 14.sp
            )
            PrimaryButton("Открыть видеозвонок") { openUrl(context, call.roomUrl) }
            PrimaryButton("Завершить звонок", color = VColors.surfaceAlt) { scope.launch { VolunteerRepo.finishCall() } }
        }
    }
}

/**
 * Видеозвонок внутри приложения волонтёра: камера человека на весь экран, голос в обе стороны, текстовый чат.
 * Волонтёр публикует только микрофон. [onEnded] вызывается один раз: reason — что показать волонтёру (null — просто конец).
 */
@Composable
private fun LiveCall(call: ActiveCall, onEnded: (reason: String?) -> Unit) {
    val info: LiveKitInfo = call.livekit ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finished = remember { AtomicBoolean(false) }
    fun end(reason: String?) {
        if (finished.compareAndSet(false, true)) onEnded(reason)
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var permOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permOk = ok
        if (!ok) end("Для звонка нужен доступ к микрофону. Разрешите его в настройках телефона.")
    }
    LaunchedEffect(Unit) {
        if (!permOk) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val room = remember { LiveKit.create(context.applicationContext) }
    val renderer = remember { arrayOfNulls<TextureViewRenderer>(1) }
    val videoTrack = remember { arrayOfNulls<VideoTrack>(1) }
    DisposableEffect(Unit) {
        onDispose {
            try { renderer[0]?.let { videoTrack[0]?.removeRenderer(it) } } catch (_: Exception) {}
            room.disconnect()
            room.release()
            try { renderer[0]?.release() } catch (_: Exception) {}
        }
    }

    var status by remember { mutableStateOf("Соединяюсь…") }
    var hasVideo by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var chatOpen by remember { mutableStateOf(false) }
    var unread by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0L) }
    var draft by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMsg>() }

    LaunchedEffect(Unit) {
        while (true) { delay(1000); seconds++ }
    }

    LaunchedEffect(permOk) {
        if (!permOk) return@LaunchedEffect
        launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        val t = event.track
                        if (t is VideoTrack) {
                            videoTrack[0] = t
                            renderer[0]?.let { t.addRenderer(it) }
                            hasVideo = true
                            status = "Вы видите камеру человека"
                        }
                    }
                    is RoomEvent.DataReceived -> {
                        val text = VolText.chatDecode(event.data)
                        if (text != null) {
                            val m = ChatMsg(false, text, System.currentTimeMillis() / 1000)
                            messages.add(m)
                            VolunteerRepo.appendChat(call.requestId, call.userName, m)
                            if (!chatOpen) unread++
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> end("Человек завершил звонок.")
                    is RoomEvent.Disconnected -> end("Связь прервалась. Звонок завершён.")
                    is RoomEvent.Reconnecting -> status = "Связь пропала, восстанавливаю…"
                    is RoomEvent.Reconnected -> status = if (hasVideo) "Вы видите камеру человека" else "Жду видео…"
                    else -> {}
                }
            }
        }
        try {
            room.connect(info.url, info.token)
            room.localParticipant.setMicrophoneEnabled(true)
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            delay(600)
            am.isSpeakerphoneOn = true
            if (!hasVideo) status = "Жду видео от человека…"
        } catch (e: Exception) {
            end("Не удалось подключиться к видеозвонку. Проверьте интернет.")
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        val m = ChatMsg(true, text, System.currentTimeMillis() / 1000)
        messages.add(m)
        VolunteerRepo.appendChat(call.requestId, call.userName, m)
        scope.launch { room.localParticipant.publishData(VolText.chatEncode(text)) }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // верхняя строка: таймер и название
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(VolText.timer(seconds), color = VColors.textSecondary, fontSize = 13.sp)
                Text(VolText.callTitle(call.urgent), color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (call.lat != null && call.lon != null) {
                RoundIcon(Icons.Filled.LocationOn, "Показать место на карте", VColors.surfaceAlt) {
                    openUrl(context, "geo:${call.lat},${call.lon}?q=${call.lat},${call.lon}")
                }
                Spacer(Modifier.width(8.dp))
            }
            RoundIcon(Icons.Filled.Warning, "Пожаловаться и завершить", VColors.surfaceAlt) {
                scope.launch { VolunteerRepo.reportAndFinish("Жалоба волонтёра") }
            }
        }
        // видео человека
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureViewRenderer(ctx).also { r ->
                        room.initVideoRenderer(r)
                        renderer[0] = r
                        videoTrack[0]?.addRenderer(r)
                    }
                }
            )
            Text(
                status, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        // чат
        if (chatOpen) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (messages.isEmpty()) Text("Сообщений пока нет. Текст увидит человек в своём приложении.", color = VColors.textSecondary, fontSize = 13.sp)
                    ChatBubbles(messages.toList())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(draft, { draft = it.take(500) }, "Напишите сообщение…", modifier = Modifier.weight(1f))
                    RoundIcon(Icons.AutoMirrored.Filled.Send, "Отправить сообщение", VColors.accent) { send() }
                }
            }
        }
        // кнопки
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top
        ) {
            CallControl(Icons.Filled.Chat, "Чат", VColors.surfaceAlt, badge = if (unread > 0) unread else 0) {
                chatOpen = !chatOpen; if (chatOpen) unread = 0
            }
            CallControl(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Микрофон", if (muted) VColors.critical else VColors.surfaceAlt) {
                muted = !muted
                scope.launch { room.localParticipant.setMicrophoneEnabled(!muted) }
            }
            CallControl(if (speaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Динамик", VColors.surfaceAlt) {
                speaker = !speaker
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = speaker
            }
            CallControl(Icons.Filled.CallEnd, "Завершить", VColors.critical) { end(null) }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = Color.White) }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, color: Color, badge: Int = 0, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp)) }
            if (badge > 0) Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(VColors.critical), contentAlignment = Alignment.Center) {
                Text(badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = VColors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
