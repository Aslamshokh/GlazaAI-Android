package com.aslamshoh.glazaai.util

import android.media.AudioManager
import android.media.ToneGenerator

/** Короткие звуковые сигналы: незрячему важно слышать, что кадр снят или код найден. */
object Beeper {
    /** Снимок сделан. */
    fun shutter() = tone(ToneGenerator.TONE_PROP_BEEP, 120)

    /** Код найден / распознано. */
    fun success() = tone(ToneGenerator.TONE_PROP_ACK, 160)

    private fun tone(type: Int, durationMs: Int) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            generator.startTone(type, durationMs)
            // ToneGenerator надо освободить, иначе утекают ресурсы звука.
            Thread {
                try {
                    Thread.sleep(durationMs + 150L)
                } catch (_: InterruptedException) {
                }
                generator.release()
            }.start()
        } catch (e: Exception) {
            // Нет звука — не страшно, голос всё равно скажет.
        }
    }
}
