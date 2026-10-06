package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.LiveCameraPreview
import com.aslamshoh.glazaai.camera.rememberLiveCameraController
import com.aslamshoh.glazaai.live.LiveEventManager
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.FindMatch
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.ImageLoading
import com.aslamshoh.glazaai.util.VoiceCommand
import com.aslamshoh.glazaai.util.VoiceCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Экран 7 макета — «Поиск предмета»: скажите или напишите, что найти («ключи»), и медленно
 * поворачивайте телефон. Приложение ищет предмет в кадре (те же YOLO-распознавание и трекинг,
 * что и в живом режиме), обводит его зелёной рамкой и говорит, где он и как далеко. «Показать
 * путь» — голосом ведёт руку: левее, правее, ближе. Предмет должен быть среди ~600 классов,
 * которые знает модель; если её словарь его не содержит, приложение честно не найдёт его.
 */
@Composable
fun FindScreen(initialQuery: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberLiveCameraController()
    val voice = remember { VoiceInput(context) }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    val sessionId = remember { UUID.randomUUID().toString() }
    val inFlight = remember { AtomicBoolean(false) }
    // Запрос, который читает фоновый поток камеры (простой массив — без гонок со Compose-состоянием).
    val queryHolder = remember { arrayOf("") }
    val lastFrame = remember { arrayOfNulls<Bitmap>(1) }
    // [0] — когда в последний раз видели предмет, [1] — последняя подсказка «путь», [2] — последняя подсказка «ищу».
    val timers = remember { LongArray(3) }

    var query by remember { mutableStateOf(initialQuery) }
    var activeQuery by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<LiveTrack?>(null) }
    var foundThumb by remember { mutableStateOf<Bitmap?>(null) }
    var guiding by remember { mutableStateOf(false) }
    var frameAspect by remember { mutableFloatStateOf(0.75f) }
    var error by remember { mutableStateOf<String?>(null) }

    fun speak(text: String) = SpeechSynthesizer.speak(text, SettingsStore.speechRate)

    fun foundText(t: LiveTrack): String {
        val dir = t.direction.replaceFirstChar { it.uppercase() }
        val dist = t.distanceM?.let { "примерно ${LiveEventManager.distancePhrase(t)}" }
        return if (dist != null) "$dir, $dist." else "$dir."
    }

    fun guideText(t: LiveTrack): String {
        val d = t.distanceM
        if (d != null && d < 0.6) return "Совсем рядом. Протяните руку."
        val dir = when (t.direction) {
            "слева" -> "Левее"
            "справа" -> "Правее"
            else -> "Прямо перед вами"
        }
        return if (d != null) "$dir, ${LiveEventManager.distancePhrase(t)}." else "$dir."
    }

    fun startSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        query = q
        activeQuery = q
        queryHolder[0] = q
        found = null
        foundThumb = null
        guiding = false
        error = null
        timers[2] = SystemClock.elapsedRealtime()
        speak("Ищу: $q. Медленно поворачивайте камеру.")
    }

    fun stopSearch() {
        queryHolder[0] = ""
        activeQuery = ""
        found = null
        foundThumb = null
        guiding = false
        SpeechSynthesizer.stop()
    }

    fun onTracks(tracks: List<LiveTrack>, frame: Bitmap) {
        val q = queryHolder[0]
        if (q.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val match = tracks
            .filter { FindMatch.matches(q, it.label, it.labelEn) }
            .minByOrNull { it.distanceM ?: 99.0 }
        if (match != null) {
            timers[0] = now
            val first = found == null
            found = match
            if (first) {
                foundThumb = ImageLoading.cropNormalized(frame, match.box)
                val text = "Найдено: $q. ${foundText(match)}"
                speak(text)
                if (SettingsStore.hapticsEnabled) vibrateAlert(vibrator)
                HistoryStore.addEntry("Поиск", "Найдено: $q", foundText(match), foundThumb)
            } else if (guiding && now - timers[1] > 2200) {
                timers[1] = now
                SpeechSynthesizer.speakQueued(guideText(match), SettingsStore.speechRate, true)
            }
        } else {
            if (found != null && now - timers[0] > 2500) {
                found = null
                foundThumb = null
                if (guiding) speak("Потерял предмет. Медленно поверните камеру.")
            }
            if (found == null && now - timers[2] > 12000) {
                timers[2] = now
                speak("Пока не вижу. Медленно поворачивайте телефон.")
            }
        }
    }

    DisposableEffect(Unit) {
        controller.frameListener = { bitmap ->
            lastFrame[0] = bitmap
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (kotlin.math.abs(aspect - frameAspect) > 0.01f) frameAspect = aspect
            if (queryHolder[0].isNotEmpty() && inFlight.compareAndSet(false, true)) {
                val dataUrl = try {
                    ImageEncoding.dataUrl(bitmap, 960, 80)
                } catch (e: Exception) {
                    inFlight.set(false)
                    null
                }
                if (dataUrl != null) {
                    scope.launch {
                        try {
                            val result = VisionService.liveFrame(dataUrl, sessionId)
                            error = null
                            onTracks(result.tracks ?: emptyList(), bitmap)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = com.aslamshoh.glazaai.network.ApiClient.messageFor(e)
                            delay(1500)
                        } finally {
                            inFlight.set(false)
                        }
                    }
                }
            }
        }
        onDispose {
            controller.release()
            voice.cancel()
            SpeechSynthesizer.stop()
        }
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) startSearch(initialQuery)
    }

    // ── микрофон ──
    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    fun listen() {
        SpeechSynthesizer.stop()
        voice.listen { text ->
            if (text == null) {
                speak("Не расслышала. Нажмите на микрофон и назовите предмет.")
            } else {
                val cmd = VoiceCommands.parse(text)
                startSearch(if (cmd is VoiceCommand.Find && cmd.query.isNotBlank()) cmd.query else text)
            }
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted
        if (granted) listen()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (hasCamera) {
                LiveCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                val track = found
                if (track != null) {
                    val rect = FrameMapper(maxWidth.value, maxHeight.value, frameAspect).rect(track.box)
                    if (rect != null) {
                        Box(
                            modifier = Modifier
                                .offset(x = rect[0].dp, y = rect[1].dp)
                                .size(width = rect[2].dp, height = rect[3].dp)
                        ) {
                            BracketFrame(modifier = Modifier.fillMaxSize(), color = Theme.success, arm = 22.dp)
                        }
                    }
                }
                if (!controller.isReady) CameraHint(text = controller.errorMessage)
            } else {
                CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
            }
        }

        // ── строка поиска ──
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("Что найти? Например: ключи", color = Theme.textSecondary) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Theme.accent) },
                trailingIcon = {
                    if (activeQuery.isNotEmpty() && found == null) {
                        CircularProgressIndicator(color = Theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { startSearch(query) }),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xE60B1224),
                    unfocusedContainerColor = Color(0xE60B1224),
                    focusedIndicatorColor = Theme.accent,
                    unfocusedIndicatorColor = Theme.accent,
                    cursorColor = Theme.accent
                )
            )
            CircleButton(
                icon = Icons.Filled.Mic,
                description = if (voice.listening) "Слушаю" else "Назвать предмет голосом",
                onClick = { if (!hasAudio) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) else listen() },
                container = if (voice.listening) Theme.accent else Color(0xE60B1224)
            )
        }

        // ── низ: результат ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }
            val track = found
            if (track != null) {
                GlassSheet {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BitmapThumb(bitmap = foundThumb, size = 76.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Найдено: ${activeQuery.ifEmpty { track.label }}",
                                color = Theme.success,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(foundText(track), color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Озвучить", icon = Icons.Filled.VolumeUp, modifier = Modifier.weight(1f)) {
                            speak("${activeQuery}. ${foundText(track)}")
                        }
                        ActionButton(
                            if (guiding) "Остановить" else "Показать путь",
                            filled = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            guiding = !guiding
                            if (guiding) {
                                timers[1] = SystemClock.elapsedRealtime()
                                speak(guideText(track))
                            } else {
                                SpeechSynthesizer.stop()
                            }
                        }
                    }
                    ActionButton("Искать другое", modifier = Modifier.fillMaxWidth()) { stopSearch() }
                }
            } else if (activeQuery.isNotEmpty()) {
                GlassSheet {
                    Text("Ищу: $activeQuery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Медленно поворачивайте телефон. Приложение узнаёт только предметы из словаря модели (около 600 видов).",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
                    ActionButton("Остановить поиск", modifier = Modifier.fillMaxWidth()) { stopSearch() }
                }
            } else {
                GlassSheet {
                    Text(
                        "Нажмите на микрофон и скажите, что найти, или напишите это сверху.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
