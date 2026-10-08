package com.aslamshoh.glazaai.util

import java.nio.ByteBuffer
import kotlin.math.abs

/** Показатели одного кадра: чёткость, яркость, насколько кадр отличается от прошлого. */
data class FrameStats(val sharpness: Double, val luma: Double, val motion: Double)

/**
 * Оценка кадра по яркостной плоскости (Y) прямо с камеры — без сервера и без создания Bitmap,
 * поэтому укладывается в пару миллисекунд. Чистая логика без Android — проверяется тестами.
 *
 * Чёткость — дисперсия лапласиана по центральной части кадра: на чётком тексте она на порядок
 * выше, чем на смазанном. Движение — средняя разница маленьких миниатюр соседних кадров.
 */
class FrameQuality {
    private var previous: IntArray? = null

    fun reset() {
        previous = null
    }

    /** y — плоскость яркости, rowStride — шаг строки в байтах, pixelStride — шаг пикселя (для Y обычно 1). */
    fun measure(y: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int = 1): FrameStats {
        if (width < 16 || height < 16) return FrameStats(0.0, 0.0, 0.0)
        fun px(x: Int, yy: Int): Int = y.get(yy * rowStride + x * pixelStride).toInt() and 0xFF

        // Центральные 70 % кадра: по краям чаще стол, рука и фон.
        val x0 = (width * 0.15).toInt().coerceAtLeast(1)
        val x1 = (width * 0.85).toInt().coerceAtMost(width - 2)
        val y0 = (height * 0.15).toInt().coerceAtLeast(1)
        val y1 = (height * 0.85).toInt().coerceAtMost(height - 2)
        val step = (width / 320).coerceAtLeast(1)

        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        var lumaSum = 0.0
        var yy = y0
        while (yy < y1) {
            var xx = x0
            while (xx < x1) {
                val c = px(xx, yy)
                val lap = 4 * c - px(xx - 1, yy) - px(xx + 1, yy) - px(xx, yy - 1) - px(xx, yy + 1)
                sum += lap
                sumSq += lap.toDouble() * lap
                lumaSum += c
                n++
                xx += step
            }
            yy += step
        }
        if (n == 0) return FrameStats(0.0, 0.0, 0.0)
        val mean = sum / n
        val sharpness = sumSq / n - mean * mean
        val luma = lumaSum / n

        // Миниатюра 16×12 по всему кадру — для сравнения с прошлым.
        val tw = 16
        val th = 12
        val thumb = IntArray(tw * th)
        for (j in 0 until th) {
            for (i in 0 until tw) {
                thumb[j * tw + i] = px((i * 2 + 1) * width / (tw * 2), (j * 2 + 1) * height / (th * 2))
            }
        }
        val prev = previous
        previous = thumb
        val motion = if (prev == null || prev.size != thumb.size) 99.0 else {
            var d = 0L
            for (k in thumb.indices) d += abs(thumb[k] - prev[k])
            d.toDouble() / thumb.size
        }
        return FrameStats(sharpness, luma, motion)
    }
}

/** Что сказать пользователю про текущий кадр. */
enum class FrameHint(val spoken: String?) {
    DARK("Слишком темно. Включите свет или фонарик."),
    NO_CONTENT("Не вижу текста. Наведите камеру на надпись."),
    BLURRY("Нечётко. Держите телефон ровно и чуть отодвиньте."),
    MOVING("Держите ровно."),
    GOOD(null)
}

class GateDecision(val hint: FrameHint, val fire: Boolean)

/**
 * Решает, когда снимать автоматически: кадр чёткий, светлый и неподвижный непрерывно [holdMs].
 * Если текст в кадре есть и камера неподвижна, но резкость так и не дотянула до порога
 * (например, глянцевая бумага) — снимаем всё равно через [patienceMs], чтобы человек не ждал вечно.
 */
class AutoCaptureGate(
    private val holdMs: Long = 700,
    private val patienceMs: Long = 3500,
    private val sharpMin: Double = 90.0,
    private val contentMin: Double = 12.0,
    private val darkLuma: Double = 40.0,
    private val motionMax: Double = 5.0
) {
    private var goodSince = -1L
    private var steadySince = -1L

    fun reset() {
        goodSince = -1L
        steadySince = -1L
    }

    fun classify(s: FrameStats): FrameHint = when {
        s.luma < darkLuma -> FrameHint.DARK
        s.sharpness < contentMin -> FrameHint.NO_CONTENT
        s.motion > motionMax -> FrameHint.MOVING
        s.sharpness < sharpMin -> FrameHint.BLURRY
        else -> FrameHint.GOOD
    }

    fun update(s: FrameStats, nowMs: Long): GateDecision {
        val hint = classify(s)
        if (hint == FrameHint.GOOD) {
            if (goodSince < 0) goodSince = nowMs
        } else {
            goodSince = -1L
        }
        // «Устойчиво, но не резко» — отдельный таймер терпения.
        if (hint == FrameHint.GOOD || hint == FrameHint.BLURRY) {
            if (steadySince < 0) steadySince = nowMs
        } else {
            steadySince = -1L
        }
        val fire = (goodSince >= 0 && nowMs - goodSince >= holdMs) ||
            (steadySince >= 0 && nowMs - steadySince >= patienceMs)
        if (fire) reset()
        return GateDecision(hint, fire)
    }
}

/** Как часто и что говорить вслух про кадр, чтобы не заваливать человека одинаковыми фразами. */
class HintSpeaker(private val repeatMs: Long = 6000, private val firstDelayMs: Long = 2500) {
    private var lastHint: FrameHint? = null
    private var lastSpokenAt = NEVER
    private var badSince = -1L

    private companion object {
        const val NEVER = -1_000_000L
    }

    fun reset() {
        lastHint = null
        lastSpokenAt = NEVER
        badSince = -1L
    }

    /** Возвращает фразу, которую пора сказать, или null. */
    fun next(hint: FrameHint, nowMs: Long): String? {
        if (hint == FrameHint.GOOD) {
            badSince = -1L
            lastHint = hint
            return null
        }
        if (badSince < 0) badSince = nowMs
        val sameAsBefore = lastHint == hint
        lastHint = hint
        if (nowMs - badSince < firstDelayMs) return null
        val gap = if (sameAsBefore) repeatMs else 2000L
        if (nowMs - lastSpokenAt < gap) return null
        lastSpokenAt = nowMs
        return hint.spoken
    }
}
