package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.aslamshoh.glazaai.util.HeadingProvider

/** Куда сейчас надо повернуть — для большой стрелки на экране. */
private enum class Turn(val angle: Float, val title: String) {
    STRAIGHT(0f, "Прямо"),
    LEFT(-45f, "Налево"),
    RIGHT(45f, "Направо"),
    BACK(180f, "Разворот")
}

/** Берём то направление, что названо в подсказке РАНЬШЕ всего: «идите прямо, затем направо» → прямо. */
private fun turnFrom(hint: String): Turn {
    val h = hint.lowercase()
    val found = listOf(
        Turn.LEFT to listOf("налево", "влево", "левее", "слева"),
        Turn.RIGHT to listOf("направо", "вправо", "правее", "справа"),
        Turn.BACK to listOf("разверн", "назад"),
        Turn.STRAIGHT to listOf("прямо", "вперёд", "вперед")
    ).mapNotNull { (turn, words) ->
        val idx = words.map { h.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (idx != null) turn to idx else null
    }
    return found.minByOrNull { it.second }?.first ?: Turn.STRAIGHT
}

/**
 * Экран 2 макета — «Навигация»: камера на весь экран, сверху карточка с направлением и
 * расстоянием, кнопка озвучки и компас; рамки препятствий; внизу подсказка и управление
 * голосом. Весь диалог («куда идём», варианты, подсказки) — прежний, голосовой.
 */
@Composable
fun NavigationScreen(initialDestination: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val location = remember { LocationProvider(context) }
    val voice = remember { VoiceInput(context) }
    val heading = remember { HeadingProvider(context) }
    val pipelineHolder = remember { arrayOfNulls<NavPipeline>(1) }
    val nav = remember {
        NavController(scope, location, voice) { pipelineHolder[0]?.describeAround() ?: "Камера пока ничего не видит." }
    }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val pipeline = remember { NavPipeline(scope, nav) { vibrateAlert(vibrator) }.also { pipelineHolder[0] = it } }
    val camera = rememberLiveCameraController()
    var frameAspect by remember { mutableFloatStateOf(0.75f) }

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
    // Пункт назначения пришёл с главного экрана («навигация до вокзала»).
    var destinationSent by remember { mutableStateOf(false) }
    LaunchedEffect(hasLocation, initialDestination) {
        if (!destinationSent && hasLocation && !initialDestination.isNullOrBlank()) {
            destinationSent = true
            nav.onTextSubmitted(initialDestination)
        }
    }

    DisposableEffect(Unit) {
        camera.frameListener = { bitmap ->
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (kotlin.math.abs(aspect - frameAspect) > 0.01f) frameAspect = aspect
            pipeline.onFrame(bitmap)
        }
        heading.start()
        onDispose {
            camera.release()
            nav.release()
            location.stop()
            heading.stop()
            SpeechSynthesizer.stop()
        }
    }

    var showTextInput by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    fun onMic() {
        if (!hasAudio || !hasLocation) permissionLauncher.launch(allPermissions) else nav.onMicClick()
    }

    val guiding = nav.dialog == NavDialog.GUIDING
    val turn = if (guiding && nav.phase == NavPhase.WALKING) turnFrom(nav.hint) else Turn.STRAIGHT

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ── камера, рамки препятствий / транспорта ──
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (hasCamera) {
                LiveCameraPreview(controller = camera, modifier = Modifier.fillMaxSize())
                val mapper = FrameMapper(maxWidth.value, maxHeight.value, frameAspect)
                if (nav.cameraMode == CameraMode.OBSTACLES) {
                    pipeline.tracks
                        .sortedBy { LiveEventManager.priorityRank(it.priority) }
                        .take(5)
                        .forEach { t ->
                            val rect = mapper.rect(t.box) ?: return@forEach
                            DetectionBox(
                                label = t.label,
                                distance = formatMeters(t.distanceM),
                                color = trackColor(t),
                                x = rect[0].dp,
                                y = rect[1].dp,
                                width = rect[2].dp,
                                height = rect[3].dp,
                                selected = false,
                                onClick = {
                                    SpeechSynthesizer.speak(
                                        "${t.label}, ${t.direction}, ${LiveEventManager.distancePhrase(t)}",
                                        com.aslamshoh.glazaai.store.SettingsStore.speechRate
                                    )
                                }
                            )
                        }
                }
                if (!camera.isReady) CameraHint(text = camera.errorMessage)
            } else {
                CameraHint(text = "Нужен доступ к камере")
            }
        }

        // ── стрелка и шевроны «по дороге» ──
        if (guiding && nav.phase == NavPhase.WALKING) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(top = 160.dp).rotate(turn.angle),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Theme.accent.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Theme.accent.copy(alpha = 0.55f), modifier = Modifier.size(56.dp))
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Theme.accent.copy(alpha = 0.9f), modifier = Modifier.size(120.dp))
            }
        }

        // ── верх: направление, озвучка, компас ──
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            DirectionCard(nav = nav, turn = turn)
            CircleButton(
                icon = Icons.Filled.VolumeUp,
                description = "Повторить подсказку",
                onClick = { nav.onRepeat() },
                size = 48.dp,
                iconSize = 22.dp,
                container = Color(0xCC0A1020)
            )
            Box(modifier = Modifier.weight(1f))
            if (heading.available) Compass(heading.degrees)
        }

        // ── низ: подсказка, варианты, управление ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (nav.cameraMode == CameraMode.VEHICLES) {
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

            nav.errorMessage?.let { ErrorBanner(it) }
            if (!hasLocation) {
                ErrorBanner("Нужен доступ к геолокации и микрофону. Нажмите кнопку ниже и разрешите доступ.")
            }

            when (nav.dialog) {
                NavDialog.CONFIRM_PLACE -> nav.places.forEachIndexed { i, p -> PlaceCard(p) { nav.pickPlace(i) } }
                NavDialog.CHOOSE_ROUTE -> nav.choices.forEachIndexed { i, c -> ChoiceCard(c) { nav.pickChoice(i) } }
                else -> {}
            }

            val status = when {
                voice.listening -> "Слушаю…"
                nav.busy -> "Думаю…"
                SpeechSynthesizer.isSpeaking -> "Говорю…"
                else -> ""
            }
            HintCard(nav = nav, turn = turn, status = status, partial = if (voice.listening) voice.partial else "")

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
}

/** Зелёная стрелка + «Прямо / 20 метров» (левый верхний угол макета). */
@Composable
private fun DirectionCard(nav: NavController, turn: Turn) {
    val title = when {
        nav.dialog != NavDialog.GUIDING -> "Навигация"
        nav.phase == NavPhase.WAITING -> "Ждём"
        nav.phase == NavPhase.RIDING -> "Едем"
        nav.phase == NavPhase.ARRIVED -> "Прибыли"
        else -> turn.title
    }
    val sub = when {
        nav.dialog != NavDialog.GUIDING -> "Скажите, куда идти"
        nav.phase == NavPhase.ARRIVED -> "Вы на месте"
        else -> nav.remainingM?.let { NavText.meters(it) } ?: ""
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC0A1020))
            .border(1.dp, Theme.success, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = Theme.success,
            modifier = Modifier.size(34.dp).rotate(turn.angle)
        )
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) Text(sub, color = Color.White, fontSize = 14.sp)
        }
    }
}

/** Круглый компас: «N» вращается вместе с телефоном, синяя стрелка всегда смотрит вперёд. */
@Composable
private fun Compass(degrees: Float) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xCC0A1020))
            .border(1.5.dp, Color(0x66FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(72.dp).rotate(-degrees), contentAlignment = Alignment.TopCenter) {
            Text("N", color = Theme.critical, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
        Canvas(modifier = Modifier.size(72.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawLine(Theme.accent, c, Offset(c.x, c.y - 22.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Theme.accent, radius = 4.dp.toPx(), center = c)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Нижняя карточка с текущей подсказкой (макет: «Идите прямо / 20 метров, затем поверните направо»). */
@Composable
private fun HintCard(nav: NavController, turn: Turn, status: String, partial: String) {
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
    GlassSheet {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Theme.textSecondary, fontSize = 13.sp)
            if (status.isNotEmpty()) Text(status, color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nav.dialog == NavDialog.GUIDING && nav.phase == NavPhase.WALKING) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = Theme.accent,
                    modifier = Modifier.size(36.dp).rotate(turn.angle)
                )
            }
            Text(nav.hint, color = Theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
        }
        nav.remainingM?.let { rem ->
            if (nav.dialog == NavDialog.GUIDING && nav.phase != NavPhase.ARRIVED) {
                Text("Осталось ${NavText.meters(rem)}", color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            .background(Theme.surface.copy(alpha = 0.96f))
            .clickable(role = Role.Button, onClick = onClick)
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
            .background(Theme.surface.copy(alpha = 0.96f))
            .clickable(role = Role.Button, onClick = onClick)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onMic,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Theme.accent, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(26.dp))
            Text("  $micLabel", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (nav.dialog != NavDialog.IDLE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Повторить", modifier = Modifier.weight(1f)) { nav.onRepeat() }
                ActionButton("Где я?", modifier = Modifier.weight(1f)) { nav.onWhere() }
                if (nav.dialog == NavDialog.GUIDING && nav.phase == NavPhase.WAITING) {
                    ActionButton("Я сел", modifier = Modifier.weight(1f)) { nav.onBoarded() }
                }
            }
            Button(
                onClick = { nav.stop() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.critical.copy(alpha = 0.25f), contentColor = Color(0xFFFF8A8A))
            ) { Text("Остановить навигацию", fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = onToggleText, modifier = Modifier.fillMaxWidth()) {
            Text(if (textShown) "Скрыть ввод текстом" else "Ввести текстом вместо голоса", color = Theme.accent, fontSize = 14.sp)
        }
    }
}
