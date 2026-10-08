package com.aslamshoh.glazaai.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.camera.CameraCaptureController
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.AutoCaptureGate
import com.aslamshoh.glazaai.util.Beeper
import com.aslamshoh.glazaai.util.FrameHint
import com.aslamshoh.glazaai.util.HintSpeaker

/** Состояние автосъёмки для экрана: включена ли и что сейчас видит камера. */
class AutoCaptureState {
    var enabled by mutableStateOf(true)
    var hint by mutableStateOf<FrameHint?>(null)

    /**
     * После неудачного чтения не снимаем снова, пока человек не сдвинет камеру: иначе приложение
     * в цикле фотографировало бы одну и ту же неудачную сцену.
     */
    var needMovement by mutableStateOf(false)
}

/**
 * Автосъёмка: пока [active], следит за кадром и сама нажимает «снять», когда изображение чёткое,
 * светлое и неподвижное. Голосом подсказывает, что поправить («Держите ровно», «Слишком темно»).
 * Кнопка съёмки на экране остаётся — автосъёмка её не заменяет, а дополняет.
 */
@Composable
fun rememberAutoCapture(
    controller: CameraCaptureController,
    active: Boolean,
    holdMs: Long = 700,
    onFire: () -> Unit
): AutoCaptureState {
    val state = remember { AutoCaptureState() }
    val gate = remember(holdMs) { AutoCaptureGate(holdMs = holdMs) }
    val speaker = remember { HintSpeaker() }
    val fire by rememberUpdatedState(onFire)

    LaunchedEffect(controller.isReady, active, state.enabled) {
        gate.reset()
        speaker.reset()
        state.hint = null
        if (!controller.isReady || !active || !state.enabled) {
            controller.qualityListener = null
            return@LaunchedEffect
        }
        controller.qualityListener = { stats ->
            val now = SystemClock.elapsedRealtime()
            val decision = gate.update(stats, now)
            state.hint = decision.hint
            if (state.needMovement) {
                if (decision.hint == FrameHint.MOVING || decision.hint == FrameHint.NO_CONTENT) state.needMovement = false
            } else if (decision.fire) {
                controller.qualityListener = null
                Beeper.shutter()
                fire()
            } else {
                speaker.next(decision.hint, now)?.let {
                    SpeechSynthesizer.speakQueued(it, SettingsStore.speechRate, false)
                }
            }
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.qualityListener = null }
    }
    return state
}

/** Короткая надпись про состояние кадра для экрана. */
fun FrameHint?.label(): String? = when (this) {
    null -> null
    FrameHint.GOOD -> "Кадр чёткий — снимаю…"
    else -> spoken
}

/**
 * Строка помощи над кнопкой съёмки: что сейчас видит камера, переключатель автосъёмки и фонарик.
 * [auto] = null — автосъёмки на экране нет (останутся только подсказка и фонарик).
 */
@Composable
fun CaptureAssistRow(controller: CameraCaptureController, auto: AutoCaptureState?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val active = auto?.takeIf { it.enabled }
        active?.hint.label()?.let {
            Text(
                it,
                color = if (active?.hint == FrameHint.GOOD) Theme.accent else Theme.warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (auto != null) {
                AssistPill(
                    label = if (auto.enabled) "Автосъёмка: вкл" else "Автосъёмка: выкл",
                    on = auto.enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    auto.enabled = !auto.enabled
                    SpeechSynthesizer.speak(
                        if (auto.enabled) "Автосъёмка включена." else "Автосъёмка выключена. Снимайте кнопкой.",
                        SettingsStore.speechRate
                    )
                }
            }
            if (controller.hasTorch) {
                AssistPill(
                    label = if (controller.torchOn) "Фонарик: вкл" else "Фонарик: выкл",
                    on = controller.torchOn,
                    modifier = Modifier.weight(1f)
                ) { controller.setTorch(!controller.torchOn) }
            }
        }
    }
}

@Composable
internal fun AssistPill(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Theme.accentSoft else Theme.surfaceAlt)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) Theme.accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
