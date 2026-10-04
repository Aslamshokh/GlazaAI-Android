package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.camera.LiveCameraPreview
import com.aslamshoh.glazaai.camera.rememberLiveCameraController
import com.aslamshoh.glazaai.live.LiveEventManager
import com.aslamshoh.glazaai.nav.CameraMode
import com.aslamshoh.glazaai.nav.LocationProvider
import com.aslamshoh.glazaai.nav.NavController
import com.aslamshoh.glazaai.nav.NavDialog
import com.aslamshoh.glazaai.nav.NavPhase
import com.aslamshoh.glazaai.nav.NavPipeline
import com.aslamshoh.glazaai.nav.NavPlace
import com.aslamshoh.glazaai.nav.NavText
import com.aslamshoh.glazaai.nav.RouteChoice
import com.aslamshoh.glazaai.nav.VoiceInput
import com.aslamshoh.glazaai.speech.SpeechSynthesizer

/**
 * Экран «Навигация»: человек говорит, куда идти, приложение предлагает варианты (пешком,
 * маршрутка, автобус), ведёт голосом по пути, на остановке камерой читает номер подъезжающей
 * машины. Всё управляется голосом — экран крупный и контрастный для слабовидящих и помощника.
 */
@Composable
fun NavigationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val location = remember { LocationProvider(context) }
    val voice = remember { VoiceInput(context) }
    val pipelineHolder = remember { arrayOfNulls<NavPipeline>(1) }
    val nav = remember {
        NavController(scope, location, voice) { pipelineHolder[0]?.describeAround() ?: "Камера пока ничего не видит." }
    }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val pipeline = remember { NavPipeline(scope, nav) { vibrateAlert(vibrator) }.also { pipelineHolder[0] = it } }
    val camera = rememberLiveCameraController()

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var hasAudio by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var hasLocation by remember {
        mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = granted(Manifest.permission.CAMERA)
        hasAudio = granted(Manifest.permission.RECORD_AUDIO)
        hasLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val allPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    LaunchedEffect(Unit) {
        if (allPermissions.any { !granted(it) }) permissionLauncher.launch(allPermissions)
    }
    LaunchedEffect(hasLocation) {
        if (hasLocation) location.start()
    }

    DisposableEffect(Unit) {
        camera.frameListener = { bitmap -> pipeline.onFrame(bitmap) }
        onDispose {
            camera.release()
            nav.release()
            location.stop()
            SpeechSynthesizer.stop()
        }
    }

    var showTextInput by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    fun onMic() {
        if (!hasAudio || !hasLocation) {
            permissionLauncher.launch(allPermissions)
        } else {
            nav.onMicClick()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CameraCard(nav, pipeline, camera, hasCamera)

            val status = when {
                voice.listening -> "Слушаю…"
                nav.busy -> "Думаю…"
                SpeechSynthesizer.isSpeaking -> "Говорю…"
                else -> ""
            }
            HintCard(nav, status, partial = if (voice.listening) voice.partial else "")

            nav.errorMessage?.let { ErrorBanner(it) }
            if (!hasLocation) {
                ErrorBanner("Нужен доступ к геолокации и микрофону. Нажмите кнопку ниже и разрешите доступ.")
            }

            when (nav.dialog) {
                NavDialog.CONFIRM_PLACE -> nav.places.forEachIndexed { i, p -> PlaceCard(p) { nav.pickPlace(i) } }
                NavDialog.CHOOSE_ROUTE -> nav.choices.forEachIndexed { i, c -> ChoiceCard(c) { nav.pickChoice(i) } }
                else -> {}
            }

            if (showTextInput) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Куда идём? Например: вокзал", color = Theme.textSecondary) },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            nav.onTextSubmitted(typed)
                            typed = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Theme.accent, contentColor = Color.White)
                    ) { Text("OK") }
                }
            }
        }

        Controls(
            nav = nav,
            micLabel = when (nav.dialog) {
                NavDialog.IDLE -> "Сказать, куда идти"
                NavDialog.GUIDING -> "Команда голосом"
                else -> "Ответить голосом"
            },
            onMic = { onMic() },
            onToggleText = { showTextInput = !showTextInput },
            textShown = showTextInput
        )
    }
}

@Composable
private fun CameraCard(nav: NavController, pipeline: NavPipeline, camera: com.aslamshoh.glazaai.camera.LiveCameraController, hasCamera: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(Theme.cornerRadiusLarge))
            .background(Color.Black)
    ) {
        if (hasCamera) {
            LiveCameraPreview(controller = camera, modifier = Modifier.fillMaxSize())
            if (!camera.isReady) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val error = camera.errorMessage
                    if (error != null) Text(error, color = Theme.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    else CircularProgressIndicator(color = Theme.accent)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нужен доступ к камере", color = Theme.textSecondary, fontSize = 14.sp)
            }
        }

        val mode = nav.cameraMode
        val chipText = when (mode) {
            CameraMode.OBSTACLES -> "камера следит за дорогой"
            CameraMode.VEHICLES -> "камера ищет ${nav.expectedRef?.let { "маршрут $it" } ?: "транспорт"}"
            CameraMode.OFF -> "камера на паузе"
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.background.copy(alpha = 0.78f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (mode == CameraMode.OFF) Theme.textSecondary else Theme.success)
            )
            Text(chipText, color = Color.White, fontSize = 12.sp)
        }

        // Подписи найденного: ближайшее препятствие или машины на остановке.
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (mode == CameraMode.OBSTACLES) {
                pipeline.tracks.sortedBy { LiveEventManager.priorityRank(it.priority) }.take(2).forEach { t ->
                    val color = when (t.priority) {
                        "critical" -> Theme.critical
                        "high" -> Theme.warning
                        else -> Theme.accent
                    }
                    Chip("${t.label} · ${t.direction} · ${LiveEventManager.distancePhrase(t)}", color)
                }
            } else if (mode == CameraMode.VEHICLES) {
                pipeline.vehicles.take(2).forEach { v ->
                    val ref = v.refs?.firstOrNull()
                    val text = when (v.match) {
                        true -> "${v.label} · № ${ref ?: ""} ✓"
                        false -> "${v.label} · № ${ref ?: "?"}"
                        null -> "${v.label} · номер не виден"
                    }
                    Chip(text, if (v.match == true) Theme.success else Theme.accent)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun HintCard(nav: NavController, status: String, partial: String) {
    val label = when (nav.dialog) {
        NavDialog.IDLE -> "Навигация"
        NavDialog.ASK_DEST -> "Куда идём"
        NavDialog.CONFIRM_PLACE -> "Какое место"
        NavDialog.CHOOSE_ROUTE -> "Как добраться"
        NavDialog.GUIDING -> when (nav.phase) {
            NavPhase.WALKING -> "Идём"
            NavPhase.WAITING -> "Ждём транспорт"
            NavPhase.RIDING -> "Едем"
            NavPhase.ARRIVED -> "Прибыли"
            NavPhase.IDLE -> "Маршрут"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Theme.textSecondary, fontSize = 13.sp)
            if (status.isNotEmpty()) Text(status, color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(nav.hint, color = Theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
        nav.remainingM?.let { rem ->
            if (nav.dialog == NavDialog.GUIDING && nav.phase != NavPhase.ARRIVED) {
                Text("Осталось ${NavText.meters(rem)}", color = Theme.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val said = if (partial.isNotBlank()) partial else nav.heard
        if (said.isNotBlank()) {
            Text("Вы сказали: «$said»", color = Theme.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PlaceCard(place: NavPlace, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(place.name, color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val tail = listOfNotNull(
            place.address.takeIf { it.isNotBlank() && !it.startsWith(place.name) },
            place.distanceM?.let { "${NavText.meters(it)} от вас" }
        ).joinToString(" · ")
        if (tail.isNotEmpty()) Text(tail, color = Theme.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ChoiceCard(choice: RouteChoice, onClick: () -> Unit) {
    val (badge, title, subtitle) = when (choice) {
        is RouteChoice.Walk -> Triple(
            "пешком",
            "Пешком",
            "${NavText.meters(choice.route.distanceM)} · около ${NavText.minutes(maxOf(choice.route.durationS / 60, 1))}"
        )
        is RouteChoice.Transit -> {
            val o = choice.option
            Triple(
                o.ref.ifBlank { "№" },
                "${o.vehicle.replaceFirstChar { it.uppercase() }} ${o.ref}",
                "Сесть: «${o.boardStop.name}», ${NavText.meters(o.boardStop.walkM)} · ${NavText.stops(o.stopsCount)} · " +
                    "выйти: «${o.alightStop.name}» · ≈ ${NavText.minutes(o.etaMin)}"
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (choice is RouteChoice.Transit) Theme.accent else Theme.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                badge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (choice is RouteChoice.Transit) 20.sp else 11.sp
            )
        }
        Column {
            Text(title, color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Theme.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Controls(
    nav: NavController,
    micLabel: String,
    onMic: () -> Unit,
    onToggleText: () -> Unit,
    textShown: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onMic,
            modifier = Modifier.fillMaxWidth().height(66.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Theme.accent, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(26.dp))
            Text("  $micLabel", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (nav.dialog != NavDialog.IDLE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallButton("Повторить", Modifier.weight(1f)) { nav.onRepeat() }
                SmallButton("Где я?", Modifier.weight(1f)) { nav.onWhere() }
                if (nav.dialog == NavDialog.GUIDING && nav.phase == NavPhase.WAITING) {
                    SmallButton("Я сел", Modifier.weight(1f)) { nav.onBoarded() }
                }
            }
            Button(
                onClick = { nav.stop() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.critical.copy(alpha = 0.16f), contentColor = Color(0xFFFF8A8A))
            ) { Text("Остановить навигацию", fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = onToggleText, modifier = Modifier.fillMaxWidth()) {
            Text(if (textShown) "Скрыть ввод текстом" else "Ввести текстом вместо голоса", color = Theme.accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SmallButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Theme.surfaceAlt, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

private fun vibrateAlert(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
}

/** Карточка входа в навигацию на главном экране (под блоком «Что перед мной?»). */
@Composable
fun NavigationEntryCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Theme.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(26.dp))
        }
        Column {
            Text("Навигация", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Скажите, куда идти: пешком или на маршрутке, подсказки по пути и номер подъезжающей машины",
                color = Theme.textSecondary,
                fontSize = 14.sp
            )
        }
    }
}
