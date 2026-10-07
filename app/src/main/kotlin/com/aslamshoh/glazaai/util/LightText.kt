package com.aslamshoh.glazaai.util

import kotlin.math.roundToInt

/**
 * Ответ на «включён ли свет?» по освещённости в люксах (датчик света телефона).
 * Порядок величин: тёмная комната 1–10 лк, тусклый свет 30–100, обычная комната 100–500,
 * яркий офис/у окна 500–2000, пасмурный день снаружи 5 000+, солнце 20 000+.
 * Чистая логика без Android — проверяется тестами.
 */
object LightText {
    enum class Level { DARK, DIM, ON, BRIGHT, DAYLIGHT }

    fun level(lux: Float): Level = when {
        lux < 8f -> Level.DARK
        lux < 60f -> Level.DIM
        lux < 600f -> Level.ON
        lux < 3000f -> Level.BRIGHT
        else -> Level.DAYLIGHT
    }

    fun answer(lux: Float?, frameLuma: Double?): String {
        if (lux != null) {
            val n = roundLux(lux)
            return when (level(lux)) {
                Level.DARK -> "Нет, очень темно: света почти нет, около $n ${luxWord(n)}."
                Level.DIM -> "Свет очень тусклый, около $n ${luxWord(n)}. Возможно, горит только слабая лампа."
                Level.ON -> "Да, свет включён: обычное комнатное освещение, около $n ${luxWord(n)}."
                Level.BRIGHT -> "Да, очень светло: яркий свет или окно рядом, около $n ${luxWord(n)}."
                Level.DAYLIGHT -> "Очень ярко, как при дневном свете на улице, около $n ${luxWord(n)}."
            }
        }
        if (frameLuma != null) {
            // Камера сама подстраивает яркость, поэтому оценка грубая — честно об этом говорим.
            return when {
                frameLuma < 35 -> "Датчика света в телефоне нет. По камере: похоже, темно."
                frameLuma < 90 -> "Датчика света в телефоне нет. По камере: свет тусклый."
                else -> "Датчика света в телефоне нет. По камере: похоже, светло."
            }
        }
        return "Не удалось определить освещённость."
    }

    fun roundLux(lux: Float): Int = when {
        lux < 20 -> lux.roundToInt()
        lux < 200 -> (lux / 5).roundToInt() * 5
        else -> (lux / 50).roundToInt() * 50
    }

    private fun luxWord(n: Int): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> "люксов"
            n10 == 1 -> "люкс"
            n10 in 2..4 -> "люкса"
            else -> "люксов"
        }
    }
}
