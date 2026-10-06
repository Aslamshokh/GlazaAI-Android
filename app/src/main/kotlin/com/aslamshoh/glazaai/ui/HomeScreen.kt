package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.LiveCameraPreview
import com.aslamshoh.glazaai.camera.rememberLiveCameraController
import com.aslamshoh.glazaai.live.LiveEventManager
import com.aslamshoh.glazaai.live.LivePipeline
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.ImageLoading
import com.aslamshoh.glazaai.util.ObjectInfo
import com.aslamshoh.glazaai.util.VoiceCommand
import com.aslamshoh.glazaai.util.VoiceCommands
import kotlinx.coroutines.launch

/** Результат «Подробнее» / фото из галереи, показывается в нижней карточке. */
private class PhotoCard(val title: String, val text: String, val thumb: Bitmap?)

/**
 * Экран 1 и 3 макета — «Главный экран · AI Vision» и «Распознавание предметов»: камера работает
 * непрерывно, backend находит предметы (YOLO), рамки подписаны названием и расстоянием.
 * Нажмите на рамку — внизу появится карточка предмета. Голос озвучивает найденное сам.
 */
@Composable
fun HomeScreen(
    onChip: (HomeChip) -> Unit,
    onSettings: () -> Unit,
    onVoiceCommand: (VoiceCommand) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()

    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val pipeline = remember { LivePipeline(scope) { vibrateAlert(vibrator) } }
    val controller = rememberLiveCameraController()
    val voice = remember { VoiceInput(context) }

    var frameAspect by remember { mutableFloatStateOf(0.75f) }
    val lastFrame = remember { arrayOfNulls<Bitmap>(1) }

    var selected by remember { mutableStateOf<LiveTrack?>(null) }
    var selectedThumb by remember { mutableStateOf<Bitmap?>(null) }
    var photoCard by remember { mutableStateOf<PhotoCard?>(null) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        controller.frameListener = { bitmap ->
            lastFrame[0] = bitmap
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (kotlin.math.abs(aspect - frameAspect) > 0.01f) frameAspect = aspect
            pipeline.onFrame(bitmap)
        }
        onDispose {
            controller.release()
            voice.cancel()
            SpeechSynthesizer.stop()
        }
    }

    // ── галерея: любое фото с телефона разбираем так же, как снимок ──
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = ImageLoading.decodeUri(context, uri)
        if (bitmap == null) {
            localError = "Не удалось открыть это фото."
            return@rememberLauncherForActivityResult
        }
        describePhoto(scope, bitmap, onBusy = { busy = it }, onError = { localError = it }) { card ->
            selected = null
            photoCard = card
        }
    }

    // ── микрофон: «ИИ Глаз, найди ключи», «прочитай текст», «навигация до вокзала» ──
    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    fun listenForCommand() {
        SpeechSynthesizer.stop()
        voice.listen { text ->
            val command = VoiceCommands.parse(text)
            if (command == VoiceCommand.Unknown) {
                SpeechSynthesizer.speak(
                    "Не поняла. Скажите, например: найди ключи, прочитай текст, навигация или сколько денег.",
                    SettingsStore.speechRate
                )
            } else if (command == VoiceCommand.WhatsAround) {
                pipeline.describeAll()
            } else {
                onVoiceCommand(command)
            }
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted
        if (granted) listenForCommand()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ── камера и рамки ──
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (hasCamera) {
                LiveCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                val mapper = FrameMapper(maxWidth.value, maxHeight.value, frameAspect)
                pipeline.tracks
                    .sortedBy { LiveEventManager.priorityRank(it.priority) }
                    .take(7)
                    .forEach { track ->
                        val rect = mapper.rect(track.box) ?: return@forEach
                        DetectionBox(
                            label = track.label,
                            distance = formatMeters(track.distanceM),
                            color = trackColor(track),
                            x = rect[0].dp,
                            y = rect[1].dp,
                            width = rect[2].dp,
                            height = rect[3].dp,
                            selected = selected?.trackId == track.trackId,
                            onClick = {
                                selected = track
                                photoCard = null
                                selectedThumb = lastFrame[0]?.let { ImageLoading.cropNormalized(it, track.box) }
                            }
                        )
                    }
                if (!controller.isReady) {
                    CameraHint(text = controller.errorMessage)
                }
            } else {
                CameraHint(text = "Нужен доступ к камере. Разрешите доступ в системном диалоге.")
            }
        }

        CameraTopBar(
            title = "ИИ Глаз",
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        pipeline.qualityHint?.let { hint ->
            Text(
                hint,
                color = Theme.warning,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0A1020))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // ── нижняя часть: карточка, режимы, кнопки ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val shownError = localError ?: pipeline.errorMessage
            if (shownError != null) ErrorBanner(shownError)

            val track = selected?.let { sel -> pipeline.tracks.firstOrNull { it.trackId == sel.trackId } ?: sel }
            val card = photoCard
            if (card != null) {
                InfoCard(
                    title = card.title,
                    lines = listOf(card.text),
                    thumb = card.thumb,
                    busy = false,
                    primaryLabel = "Озвучить",
                    onPrimary = { SpeechSynthesizer.speak(card.text, SettingsStore.speechRate) },
                    secondaryLabel = null,
                    onSecondary = {},
                    onClose = { photoCard = null }
                )
            } else if (track != null) {
                val distance = formatMeters(track.distanceM)
                val note = ObjectInfo.note(track.label)
                val lines = listOfNotNull(
                    distance?.let { "Расстояние: $it · ${track.direction}" } ?: track.direction,
                    note ?: if (track.approaching) "Приближается." else null
                )
                InfoCard(
                    title = track.label.replaceFirstChar { it.uppercase() },
                    lines = lines,
                    thumb = selectedThumb,
                    busy = busy,
                    primaryLabel = "Озвучить",
                    onPrimary = {
                        SpeechSynthesizer.speak(
                            "${track.label}, ${LiveEventManager.distancePhrase(track)}, ${track.direction}. ${note ?: ""}",
                            SettingsStore.speechRate
                        )
                    },
                    secondaryLabel = "Подробнее",
                    onSecondary = {
                        val frame = lastFrame[0]
                        if (frame == null) {
                            localError = "Камера ещё не дала кадр."
                        } else {
                            describePhoto(scope, frame, onBusy = { busy = it }, onError = { localError = it }) { result ->
                                selected = null
                                photoCard = result
                            }
                        }
                    },
                    onClose = { selected = null }
                )
            }

            // состояние и пауза
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val status = when {
                    pipeline.paused -> "Пауза — распознавание остановлено"
                    pipeline.latencyMs > 0 -> "Распознаю в реальном времени · ${pipeline.latencyMs} мс"
                    else -> "Запускаю распознавание…"
                }
                Text(
                    status,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x990A1020))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                TextButton(onClick = { pipeline.togglePause() }) {
                    Text(if (pipeline.paused) "Продолжить" else "Пауза", color = Theme.accent)
                }
            }

            ModeChipRow(selected = HomeChip.OBJECTS, onSelect = { chip -> if (chip != HomeChip.OBJECTS) onChip(chip) })

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    description = "Выбрать фото из галереи",
                    onClick = { galleryLauncher.launch("image/*") },
                    container = Color(0xCC1A2540)
                )
                BigShutter(
                    busy = busy,
                    description = "Что вокруг? Озвучить всё, что видно в кадре",
                    onClick = { pipeline.describeAll() }
                )
                CircleButton(
                    icon = Icons.Filled.Mic,
                    description = if (voice.listening) "Слушаю команду" else "Голосовая команда",
                    onClick = {
                        if (!hasAudio) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) else listenForCommand()
                    },
                    container = if (voice.listening) Theme.accent else Color(0xCC1A2540)
                )
            }
        }
    }
}

/** Большая синяя кнопка по центру (макет, экран 1). */
@Composable
private fun BigShutter(busy: Boolean, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(4.dp, Theme.accent, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Theme.accent)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
        } else {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

/** Карточка предмета/описания снизу: миниатюра, название, строки, две кнопки, крестик. */
@Composable
private fun InfoCard(
    title: String,
    lines: List<String>,
    thumb: Bitmap?,
    busy: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    onClose: () -> Unit
) {
    GlassSheet {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            BitmapThumb(bitmap = thumb, size = 72.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                lines.forEach { Text(it, color = Theme.textSecondary, fontSize = 13.sp) }
            }
            CircleButton(
                icon = Icons.Filled.Close,
                description = "Закрыть карточку",
                onClick = onClose,
                size = 40.dp,
                iconSize = 20.dp,
                container = Theme.surfaceAlt
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(primaryLabel, icon = Icons.Filled.VolumeUp, modifier = Modifier.weight(1f), onClick = onPrimary)
            if (secondaryLabel != null) {
                ActionButton(
                    if (busy) "Смотрю…" else secondaryLabel,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = onSecondary
                )
            }
        }
    }
}

/** Отправляет снимок на /vision/describe-scene и показывает ответ карточкой + пишет в историю. */
private fun describePhoto(
    scope: kotlinx.coroutines.CoroutineScope,
    bitmap: Bitmap,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onResult: (PhotoCard) -> Unit
) {
    onBusy(true)
    scope.launch {
        try {
            val r = VisionService.describeScene(ImageEncoding.dataUrl(bitmap))
            HistoryStore.addEntry("Предметы", r.title, r.description, bitmap)
            SpeechSynthesizer.speak(r.description, SettingsStore.speechRate)
            onResult(PhotoCard(r.title, r.description, bitmap))
        } catch (e: Exception) {
            onError(ApiClient.messageFor(e))
        } finally {
            onBusy(false)
        }
    }
}

internal fun trackColor(t: LiveTrack): Color =
    if (t.priority == "critical") Theme.critical
    else Theme.boxPalette[Math.floorMod(t.trackId, Theme.boxPalette.size)]

/**
 * Переводит нормированные координаты кадра (0..1) в координаты экрана. Превью камеры
 * растянуто на весь экран с обрезкой по краям (FILL_CENTER), поэтому учитываем и масштаб,
 * и сдвиг — иначе рамки «уплывали» бы относительно предметов.
 */
internal class FrameMapper(private val cw: Float, private val ch: Float, private val aspect: Float) {
    private val containerAspect = if (ch > 0f) cw / ch else aspect
    private val dispW = if (containerAspect < aspect) ch * aspect else cw
    private val dispH = if (containerAspect < aspect) ch else cw / aspect
    private val offX = (dispW - cw) / 2f
    private val offY = (dispH - ch) / 2f

    /** [x, y, ширина, высота] в dp либо null, если рамка слишком мала/вне экрана. */
    fun rect(box: List<Double>): FloatArray? {
        if (box.size < 4) return null
        val x1 = (box[0].toFloat() * dispW - offX).coerceIn(0f, cw)
        val y1 = (box[1].toFloat() * dispH - offY).coerceIn(0f, ch)
        val x2 = (box[2].toFloat() * dispW - offX).coerceIn(0f, cw)
        val y2 = (box[3].toFloat() * dispH - offY).coerceIn(0f, ch)
        if (x2 - x1 < 10f || y2 - y1 < 10f) return null
        return floatArrayOf(x1, y1, x2 - x1, y2 - y1)
    }
}
