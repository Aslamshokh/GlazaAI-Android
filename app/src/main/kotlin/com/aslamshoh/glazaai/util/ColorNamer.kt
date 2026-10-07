package com.aslamshoh.glazaai.util

import kotlin.math.max
import kotlin.math.min

/** Один цвет из отчёта: название, доля пикселей (0..1) и среднее значение для проверки. */
data class NamedColor(val name: String, val share: Double)

/** Что нашли в области: цвета по убыванию доли. */
data class ColorReport(val colors: List<NamedColor>) {
    val primary: NamedColor? get() = colors.firstOrNull()
}

/**
 * Названия цветов по пикселям — чистая математика без Android (проверяется тестами).
 * Каждый пиксель относим к «семейству» (чёрный, белый, серый, красный, … коричневый, бежевый),
 * считаем доли, а оттенок («тёмно-синий», «светло-серый») берём по среднему значению семейства.
 * Названия — в мужском роде, чтобы после слова «цвет» всегда звучало правильно.
 */
object ColorNamer {
    private enum class Family { BLACK, WHITE, GRAY, RED, ORANGE, BROWN, BEIGE, YELLOW, OLIVE, GREEN, TEAL, SKY, BLUE, PURPLE, PINK }

    private class Acc(var count: Int = 0, var sumS: Double = 0.0, var sumV: Double = 0.0)

    /** pixels — ARGB (как Bitmap.getPixels). Прозрачные пропускаются. */
    fun analyze(pixels: IntArray): ColorReport {
        val acc = HashMap<Family, Acc>()
        var total = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val d = mx - mn
            val v = mx
            val s = if (mx == 0.0) 0.0 else d / mx
            var h = 0.0
            if (d > 0) {
                h = when (mx) {
                    r -> 60 * (((g - b) / d) % 6)
                    g -> 60 * ((b - r) / d + 2)
                    else -> 60 * ((r - g) / d + 4)
                }
                if (h < 0) h += 360
            }
            val family = classify(h, s, v)
            val a = acc.getOrPut(family) { Acc() }
            a.count++
            a.sumS += s
            a.sumV += v
            total++
        }
        if (total == 0) return ColorReport(emptyList())
        val colors = acc.entries
            .map { (fam, a) -> NamedColor(name(fam, a.sumS / a.count, a.sumV / a.count), a.count.toDouble() / total) to a.count }
            .sortedByDescending { it.second }
            .map { it.first }
        // Одинаковые названия из разных семейств (например, два «серых») склеиваем.
        val merged = LinkedHashMap<String, Double>()
        for (c in colors) merged[c.name] = (merged[c.name] ?: 0.0) + c.share
        return ColorReport(merged.map { NamedColor(it.key, it.value) }.sortedByDescending { it.share })
    }

    private fun classify(h: Double, s: Double, v: Double): Family {
        if (v < 0.16) return Family.BLACK
        if (s < 0.10 || (s < 0.16 && v > 0.9)) {
            return when {
                v > 0.86 -> Family.WHITE
                v < 0.2 -> Family.BLACK
                else -> Family.GRAY
            }
        }
        // Бледные «грязные» оттенки с малой насыщенностью — серый, а не цветной.
        if (s < 0.2 && v < 0.75) return Family.GRAY
        return when {
            h < 12 || h >= 345 -> if (v < 0.5) (if (s >= 0.6) Family.RED else Family.BROWN) else if (s < 0.5 && v > 0.72) Family.PINK else Family.RED
            h < 42 -> when {
                v < 0.62 -> Family.BROWN
                s < 0.38 -> Family.BEIGE
                else -> Family.ORANGE
            }
            h < 66 -> when {
                v < 0.6 -> Family.OLIVE
                s < 0.3 -> Family.BEIGE
                else -> Family.YELLOW
            }
            h < 90 -> if (v < 0.65) Family.OLIVE else Family.GREEN
            h < 165 -> Family.GREEN
            h < 190 -> Family.TEAL
            h < 215 -> if (v > 0.55) Family.SKY else Family.TEAL
            h < 255 -> if (v > 0.78 && s < 0.55) Family.SKY else Family.BLUE
            h < 295 -> Family.PURPLE
            else -> Family.PINK
        }
    }

    private fun name(f: Family, s: Double, v: Double): String = when (f) {
        Family.BLACK -> "чёрный"
        Family.WHITE -> "белый"
        Family.GRAY -> if (v > 0.72) "светло-серый" else if (v < 0.4) "тёмно-серый" else "серый"
        Family.RED -> if (v < 0.5) "бордовый" else "красный"
        Family.ORANGE -> "оранжевый"
        Family.BROWN -> "коричневый"
        Family.BEIGE -> "бежевый"
        Family.YELLOW -> "жёлтый"
        Family.OLIVE -> "оливковый"
        Family.GREEN -> if (v < 0.4) "тёмно-зелёный" else if (v > 0.82 && s < 0.5) "светло-зелёный" else "зелёный"
        Family.TEAL -> "бирюзовый"
        Family.SKY -> "голубой"
        Family.BLUE -> if (v < 0.45) "тёмно-синий" else "синий"
        Family.PURPLE -> if (v > 0.82 && s < 0.5) "сиреневый" else if (v < 0.4) "тёмно-фиолетовый" else "фиолетовый"
        Family.PINK -> if (v > 0.7 && s < 0.65) "розовый" else "малиновый"
    }

    /** «Основной цвет — тёмно-синий, ещё есть белый». subject — «Рубашка», «В центре кадра» и т.п. */
    fun speak(report: ColorReport, subject: String?): String {
        val main = report.primary ?: return "Не удалось определить цвет."
        val extra = report.colors.drop(1).filter { it.share >= 0.2 }.take(2).map { it.name }
        val body = when {
            main.share >= 0.7 || extra.isEmpty() -> "цвет — ${main.name}"
            else -> "основной цвет — ${main.name}, ещё есть ${extra.joinToString(" и ")}"
        }
        return if (subject.isNullOrBlank()) body.replaceFirstChar { it.uppercase() } + "." else "$subject: $body."
    }

    /** Коротко для карточки предмета: «тёмно-синий» или «тёмно-синий, белый». */
    fun short(report: ColorReport): String? {
        val main = report.primary ?: return null
        val second = report.colors.getOrNull(1)?.takeIf { it.share >= 0.25 }
        return if (second != null) "${main.name}, ${second.name}" else main.name
    }
}
