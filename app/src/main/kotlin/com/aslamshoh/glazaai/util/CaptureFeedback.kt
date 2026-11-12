package com.aslamshoh.glazaai.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Сигнал «кадр снят — можно убрать телефон»: короткая вибрация + звук.
 * Звучит сразу, как только камера отдала кадр, — ещё до распознавания на сервере,
 * чтобы незрячий не держал телефон над листом или купюрой лишние секунды.
 */
object CaptureFeedback {
    const val SPOKEN = "Снято, можно убрать."

    fun captured(context: Context) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60), -1))
            }
        } catch (_: Exception) {
            // нет вибромотора — хватит звука и голоса
        }
        Beeper.success()
    }
}
