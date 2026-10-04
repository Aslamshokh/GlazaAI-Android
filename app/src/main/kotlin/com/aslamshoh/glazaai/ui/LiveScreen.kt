package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.LiveCameraPreview
import com.aslamshoh.glazaai.camera.rememberLiveCameraController
import com.aslamshoh.glazaai.live.LiveEventManager
import com.aslamshoh.glazaai.live.LivePipeline
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.speech.SpeechSynthesizer

/**
 * Живой режим «Предметы» (этапы 1–2 ТЗ): камера работает непрерывно, каждый кадр уходит на
 * backend (YOLO + трекинг + оценка расстояния), найденное озвучивается с приоритетами и без
 * повторов (см. live/LiveEventManager.kt). Рамки на превью — для проверки качества
 * распознавания; незрячему пользователю нужен голос, а не картинка.
 */
@Composable
fun LiveScreen(onOpenPhoto: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val pipeline = remember { LivePipeline(scope) { vibrateAlert(vibrator) } }
    val controller = rememberLiveCameraController()

    DisposableEffect(Unit) {
        controller.frameListener = { bitmap -> pipeline.onFrame(bitmap) }
        onDispose {
            controller.release()
            SpeechSynthesizer.stop()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.background)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                LiveCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                pipeline.tracks.forEach { track ->
                    TrackOverlay(track = track, containerWidth = maxWidth, containerHeight = maxHeight)
                }
                if (!controller.isReady) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val error = controller.errorMessage
                        if (error != null) {
                            Text(
                                error,
                                color = Theme.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        } else {
                            CircularProgressIndicator(color = Theme.accent)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Нужен доступ к камере. Разрешите доступ в системном диалоге.",
                        color = Theme.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val status = when {
                pipeline.paused -> "Пауза — распознавание остановлено."
                pipeline.latencyMs > 0 -> "Распознаю в реальном времени · ответ за ${pipeline.latencyMs} мс"
                else -> "Запускаю распознавание…"
            }
            Text(status, color = Theme.textSecondary, fontSize = 13.sp)

            pipeline.qualityHint?.let { hint ->
                Text(hint, color = Theme.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            pipeline.errorMessage?.let { ErrorBanner(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { pipeline.togglePause() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pipeline.paused) Theme.success else Theme.surfaceAlt,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (pipeline.paused) "Продолжить" else "Пауза")
                }
                Button(
                    onClick = { pipeline.describeAll() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Theme.accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Что вокруг?")
                }
            }

            TextButton(onClick = onOpenPhoto) {
                Text("Сделать фото и описать подробнее", color = Theme.accent)
            }

            val visible = pipeline.tracks.take(8)
            if (visible.isNotEmpty()) {
                Text("Сейчас в кадре", color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                visible.forEach { t ->
                    Text(
                        text = trackLine(t),
                        color = priorityColor(t.priority),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun trackLine(t: LiveTrack): String {
    val sb = StringBuilder()
    sb.append(t.label.replaceFirstChar { it.uppercase() })
    sb.append(" · ").append(t.direction)
    sb.append(" · ").append(LiveEventManager.distancePhrase(t))
    if (t.approaching) sb.append(" · приближается")
    return sb.toString()
}

private fun priorityColor(priority: String): Color = when (priority) {
    "critical" -> Theme.critical
    "high" -> Theme.warning
    "medium" -> Theme.accent
    else -> Theme.textSecondary
}

/** Рамка найденного объекта поверх превью. Координаты backend нормированы (0..1) относительно
 * кадра; контейнер превью имеет те же пропорции 3:4, поэтому масштабируются напрямую. */
@Composable
private fun TrackOverlay(track: LiveTrack, containerWidth: Dp, containerHeight: Dp) {
    if (track.box.size < 4) return
    val x1 = track.box[0].toFloat().coerceIn(0f, 1f)
    val y1 = track.box[1].toFloat().coerceIn(0f, 1f)
    val x2 = track.box[2].toFloat().coerceIn(0f, 1f)
    val y2 = track.box[3].toFloat().coerceIn(0f, 1f)
    val color = priorityColor(track.priority)

    Box(
        modifier = Modifier
            .offset(x = containerWidth * x1, y = containerHeight * y1)
            .size(
                width = containerWidth * (x2 - x1).coerceAtLeast(0f),
                height = containerHeight * (y2 - y1).coerceAtLeast(0f)
            )
            .border(2.dp, color)
    ) {
        Text(
            text = track.label,
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .background(color.copy(alpha = 0.75f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun vibrateAlert(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
}
