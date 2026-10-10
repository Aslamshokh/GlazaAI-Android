package com.aslamshoh.glazaai.util

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * «Свет звуком» (как канал Light в Seeing AI): чем светлее в кадре, тем выше тон. Тёмное — низкий гул,
 * яркое окно или лампа — высокий писк. Чистая математика — проверяется тестами без Android.
 */
object LightTone {
    const val SAMPLE_RATE = 22050
    const val MIN_HZ = 220.0   // почти полная темнота
    const val MAX_HZ = 1760.0  // три октавы выше — яркий свет

    /** Яркость кадра 0..255 → частота, Гц. Шкала по октавам, с гаммой, чтобы в тусклом свете разница была слышна. */
    fun frequency(luma: Double): Double {
        val t = (luma / 255.0).coerceIn(0.0, 1.0).pow(0.6)
        return MIN_HZ * 2.0.pow(3.0 * t)
    }

    /** Кусок синусоиды [ms] миллисекунд с плавным началом и концом (без щелчков между кусочками). */
    fun samples(hz: Double, ms: Int, volume: Double = 0.4): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val fade = (SAMPLE_RATE / 200).coerceAtMost(n / 2) // 5 мс
        return ShortArray(n) { i ->
            val env = when {
                i < fade -> i.toDouble() / fade
                i >= n - fade -> (n - 1 - i).toDouble() / fade
                else -> 1.0
            }
            (sin(2.0 * PI * hz * i / SAMPLE_RATE) * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Фраза-подсказка перед началом. */
    const val INTRO = "Свет звуком. Чем светлее, тем выше звук. Двигайте телефон. Остановить: скажите «стоп» или закройте карточку."
}
