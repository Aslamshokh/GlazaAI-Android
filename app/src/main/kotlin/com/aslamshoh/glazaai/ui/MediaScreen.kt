package com.aslamshoh.glazaai.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aslamshoh.glazaai.camera.rememberCameraCaptureController
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.DescribeService
import com.aslamshoh.glazaai.network.PdfPage
import com.aslamshoh.glazaai.network.ReadService
import com.aslamshoh.glazaai.network.VideoSegment
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.ImageLoading
import com.aslamshoh.glazaai.util.ReadSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что делаем на экране «Файлы и медиа». */
enum class MediaKind(val key: String, val label: String, val camera: Boolean, val intro: String) {
    PHOTO("photo", "Фото", true, "Описание фото как в книге: кто или что в кадре, что происходит. Снимите камерой или выберите фото."),
    GRAPH("graph", "График", true, "Описание графика или диаграммы: что изображено и как выглядит, без решения задачи."),
    HANDWRITING("handwriting", "Рукопись", true, "Чтение рукописного текста с разборчивым почерком. Снимите записку или тетрадь."),
    PDF("pdf", "PDF", false, "Чтение PDF, в том числе сканов без текста. Выберите файл."),
    VIDEO("video", "Видео", false, "Описание видео по фрагментам: несколько секунд видео, пауза, описание, и дальше.");

    companion object {
        fun of(key: String): MediaKind = entries.firstOrNull { it.key == key } ?: PHOTO
    }
}

private const val MAX_PDF_BYTES = 25 * 1024 * 1024
private const val MAX_VIDEO_BYTES = 60 * 1024 * 1024

@Composable
fun MediaScreen(initialKind: String, onSwitchMode: (ScanMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = rememberCameraPermission()
    val controller = rememberCameraCaptureController()

    var kind by remember { mutableStateOf(MediaKind.of(initialKind)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf<String?>(null) }
    var body by remember { mutableStateOf<String?>(null) }
    var spoken by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<PdfPage>>(emptyList()) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var segments by remember { mutableStateOf<List<VideoSegment>>(emptyList()) }
    var segIndex by remember { mutableIntStateOf(-1) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var askCloud by remember { mutableStateOf(false) }
    var largeText by remember { mutableStateOf<String?>(null) }
    largeText?.let { LargeTextDialog(it) { largeText = null } }

    fun reset() {
        playJob?.cancel()
        videoView?.stopPlayback()
        title = null; body = null; spoken = null; note = null; error = null
        pages = emptyList(); pageIndex = 0
        segments = emptyList(); segIndex = -1; videoUri = null
    }

    fun say(text: String) = SpeechSynthesizer.speak(text, SettingsStore.speechRate)

    fun fail(e: Exception) {
        val message = ApiClient.messageFor(e)
        error = message
        say(message)
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            controller.unbind()
            SpeechSynthesizer.stop()
        }
    }
    LaunchedEffect(kind) {
        SpeechSynthesizer.speakQueued(kind.intro, SettingsStore.speechRate, true)
    }

    // ── Фото / график / рукопись ──
    fun processImage(bitmap: Bitmap) {
        if (kind == MediaKind.GRAPH && !SettingsStore.allowCloud) {
            askCloud = true
            return
        }
        busy = true
        error = null
        say("Обрабатываю…")
        val dataUrl = ImageEncoding.dataUrl(bitmap, maxDimension = 1800, quality = 88)
        scope.launch {
            try {
                when (kind) {
                    MediaKind.GRAPH -> {
                        val r = DescribeService.graph(dataUrl, true)
                        title = r.title; body = listOfNotNull(r.description, r.text?.takeIf { it.isNotBlank() }?.let { "Надписи: $it" }).joinToString("\n\n")
                        spoken = r.spoken ?: r.description
                    }
                    MediaKind.HANDWRITING -> {
                        val r = ReadService.handwriting(dataUrl, SettingsStore.allowCloud)
                        title = r.title; body = r.recognizedText.orEmpty().ifBlank { r.description }
                        spoken = r.spoken?.takeIf { it.isNotBlank() } ?: r.description
                        note = if (r.source == "local") r.description else null
                    }
                    else -> {
                        val r = DescribeService.photo(dataUrl, SettingsStore.allowCloud)
                        title = r.title; body = listOfNotNull(r.description, r.text?.takeIf { it.isNotBlank() }?.let { "Текст на фото: $it" }).joinToString("\n\n")
                        spoken = r.spoken ?: r.description
                        note = if (r.source == "local") "Кратко, без облака. Для подробного описания включите «Умный режим» в настройках." else null
                    }
                }
                HistoryStore.addEntry(kind.label, title.orEmpty(), body.orEmpty(), bitmap)
                spoken?.let { say(it) }
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy = false
            }
        }
    }

    fun capture() {
        if (busy || !hasCamera) return
        controller.captureFrame { bitmap ->
            if (bitmap == null) {
                error = "Не удалось получить кадр с камеры. Попробуйте ещё раз."
                say(error.orEmpty())
            } else {
                processImage(bitmap)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = ImageLoading.decodeUri(context, uri, maxSide = 1800)
            if (bmp == null) {
                error = "Не удалось открыть фото."
                say(error.orEmpty())
            } else {
                reset()
                processImage(bmp)
            }
        }
    }

    // ── PDF ──
    fun readPage(i: Int) {
        val p = pages.getOrNull(i) ?: return
        pageIndex = i
        val text = p.spoken?.takeIf { it.isNotBlank() } ?: ReadSpeech.all(p.blocks.orEmpty(), SettingsStore.tableMode)
        body = p.text.orEmpty().ifBlank { "Пустая страница." }
        say("Страница ${p.page}. " + text.ifBlank { "Текста нет." })
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reset()
        busy = true
        say("Читаю PDF. Сканы распознаются дольше, подождите.")
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    ?: throw IllegalStateException("Не удалось открыть файл.")
                if (bytes.size > MAX_PDF_BYTES) throw IllegalStateException("PDF слишком большой: больше 25 МБ.")
                val dataUrl = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                val r = ReadService.pdf(dataUrl, SettingsStore.tableMode)
                pages = r.pages.orEmpty()
                title = r.title
                note = r.description
                HistoryStore.addEntry("PDF", r.description, pages.joinToString("\n\n") { it.text.orEmpty() }.take(4000))
                say(r.description)
                if (pages.isNotEmpty()) {
                    delay(400)
                    readPage(0)
                }
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy = false
            }
        }
    }

    // ── Видео ──
    fun describeSegment(i: Int) {
        val s = segments.getOrNull(i) ?: return
        segIndex = i
        val extra = s.text?.takeIf { it.isNotBlank() }?.let { " Надпись: $it." } ?: ""
        say("${s.label}. ${s.description}$extra")
    }

    /** Как в PixiBot: проиграть фрагмент, пауза, описание, и дальше. */
    fun playGuided(from: Int) {
        val vv = videoView ?: return
        playJob?.cancel()
        playJob = scope.launch {
            for (i in from until segments.size) {
                val s = segments[i]
                segIndex = i
                vv.seekTo((s.start * 1000).toInt())
                vv.start()
                delay(((s.end - s.start) * 1000).toLong().coerceAtLeast(500))
                vv.pause()
                describeSegment(i)
                delay(400)
                snapshotFlow { SpeechSynthesizer.isSpeaking }.first { !it }
            }
            say("Видео закончилось.")
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reset()
        busy = true
        say("Описываю видео по фрагментам. Это может занять пару минут.")
        scope.launch {
            try {
                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    ?: throw IllegalStateException("Не удалось открыть видео.")
                if (bytes.size > MAX_VIDEO_BYTES) throw IllegalStateException("Видео слишком большое: больше 60 МБ.")
                val r = DescribeService.video(bytes, mime, SettingsStore.allowCloud)
                segments = r.segments.orEmpty()
                videoUri = uri
                title = r.title
                note = r.description
                body = segments.joinToString("\n") { "${it.label} — ${it.description}" }
                HistoryStore.addEntry("Видео", r.title, body.orEmpty())
                say(r.description + " Нажмите «Смотреть» — видео пойдёт кусками с паузами и описанием, или «Текстом» — только описание.")
            } catch (e: Exception) {
                fail(e)
            } finally {
                busy = false
            }
        }
    }

    if (askCloud) {
        AlertDialog(
            onDismissRequest = { askCloud = false },
            containerColor = Theme.surface,
            title = { Text("Включить умный режим?", color = Theme.textPrimary) },
            text = {
                Text(
                    "Графики, подробное описание фото, рукописный текст и видео описывает облачный ИИ. Фото отправится во внешний " +
                        "сервис только для ответа и нигде не сохраняется. Без умного режима всё остальное работает на вашем сервере.",
                    color = Theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SettingsStore.updateAllowCloud(true)
                    askCloud = false
                    say("Умный режим включён. Сделайте снимок ещё раз.")
                }) { Text("Включить", color = Theme.accent) }
            },
            dismissButton = { TextButton(onClick = { askCloud = false }) { Text("Не сейчас", color = Theme.textSecondary) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (kind.camera) CaptureCamera(controller, hasCamera)

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CameraTopBar(title = "Файлы и медиа", onBack = onBack, hint = Hints.MEDIA)
            ScanModeSwitch(selected = ScanMode.MEDIA, onSelect = onSwitchMode)
            Row(
                modifier = Modifier
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaKind.entries.forEach { k ->
                    AssistPill(k.label, on = k == kind) {
                        if (k != kind) {
                            reset()
                            kind = k
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { ErrorBanner(it) }
            GlassSheet {
                if (title == null) {
                    Text(kind.intro, color = Theme.textSecondary, fontSize = 14.sp)
                    if (!SettingsStore.allowCloud && kind != MediaKind.PDF) {
                        Text(
                            if (kind == MediaKind.GRAPH) "Нужен «Умный режим» (облачный ИИ) — включите его в настройках или при первом снимке."
                            else "Без «Умного режима» — кратко и без облака.",
                            color = Theme.warning,
                            fontSize = 13.sp
                        )
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Theme.accent)
                        Text("Обрабатываю…", color = Color.White, fontSize = 15.sp)
                    }
                }

                title?.let { Text(it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                note?.let { Text(it, color = Theme.textSecondary, fontSize = 13.sp) }

                // Видео: плеер и пошаговое описание
                val uri = videoUri
                if (kind == MediaKind.VIDEO && uri != null) {
                    AndroidView(
                        factory = { ctx -> VideoView(ctx).also { v -> v.setVideoURI(uri); videoView = v } },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    )
                    if (segIndex >= 0) {
                        Text("Фрагмент ${segIndex + 1} из ${segments.size}", color = Theme.textSecondary, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Смотреть", icon = Icons.Filled.PlayArrow, filled = true, modifier = Modifier.weight(1f)) {
                            playGuided(if (segIndex in 0 until segments.size - 1) segIndex + 1 else 0)
                        }
                        ActionButton("Стоп", icon = Icons.Filled.Stop, modifier = Modifier.weight(1f)) {
                            playJob?.cancel()
                            videoView?.pause()
                            SpeechSynthesizer.stop()
                        }
                        ActionButton("Текстом", modifier = Modifier.weight(1f)) {
                            playJob?.cancel()
                            say(segments.joinToString(". ") { "${it.label}. ${it.description}" })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Назад", enabled = segIndex > 0, modifier = Modifier.weight(1f)) {
                            playJob?.cancel(); describeSegment(segIndex - 1)
                        }
                        ActionButton("Дальше", enabled = segIndex < segments.size - 1, modifier = Modifier.weight(1f)) {
                            playJob?.cancel(); describeSegment(segIndex + 1)
                        }
                    }
                }

                // PDF: постраничное чтение
                if (kind == MediaKind.PDF && pages.isNotEmpty()) {
                    Text("Страница ${pages[pageIndex].page} из ${pages.size}", color = Theme.textSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Назад", enabled = pageIndex > 0, modifier = Modifier.weight(1f)) { readPage(pageIndex - 1) }
                        ActionButton("Дальше", enabled = pageIndex < pages.size - 1, modifier = Modifier.weight(1f)) { readPage(pageIndex + 1) }
                    }
                }

                body?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Theme.surfaceAlt)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(it, color = Color.White, fontSize = 18.sp, lineHeight = 24.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton("Читать", icon = Icons.Filled.VolumeUp, filled = true, modifier = Modifier.weight(1f)) {
                            if (kind == MediaKind.PDF) readPage(pageIndex) else say(spoken ?: it)
                        }
                        ActionButton("Крупно", modifier = Modifier.weight(1f)) { largeText = it }
                    }
                }

                // Кнопки ввода
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (kind) {
                        MediaKind.PDF -> ActionButton("Открыть PDF", icon = Icons.Outlined.Description, filled = true, enabled = !busy, modifier = Modifier.weight(1f)) {
                            pdfPicker.launch("application/pdf")
                        }
                        MediaKind.VIDEO -> ActionButton("Открыть видео", icon = Icons.Outlined.VideoLibrary, filled = true, enabled = !busy, modifier = Modifier.weight(1f)) {
                            videoPicker.launch("video/*")
                        }
                        else -> {
                            ActionButton("Галерея", icon = Icons.Outlined.PhotoLibrary, enabled = !busy, modifier = Modifier.weight(1f)) {
                                imagePicker.launch("image/*")
                            }
                            ShutterButton(isBusy = busy) {
                                reset()
                                capture()
                            }
                        }
                    }
                }
            }
        }
    }
}
