package com.aslamshoh.glazaai.util

import java.nio.ByteBuffer
import kotlin.math.abs

/** Показатели одного кадра: чёткость, яркость, насколько кадр отличается от прошлого и где
 * в кадре то, что нужно прочитать ([content] — рамка в долях 0..1 уже в ориентации экрана). */
data class FrameStats(
    val sharpness: Double,
    val luma: Double,
    val motion: Double,
    val content: ContentBox? = null
)

/** Где в кадре текст / купюра / документ: рамка (доли 0..1) и касается ли она краёв кадра. */
data class ContentBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val cutLeft: Boolean,
    val cutTop: Boolean,
    val cutRight: Boolean,
    val cutBottom: Boolean
) {
    val cx: Double get() = (left + right) / 2
    val cy: Double get() = (top + bottom) / 2
    val area: Double get() = (right - left) * (bottom - top)

    /** Поворот кадра камеры к ориентации экрана (rotationDegrees из CameraX, по часовой). */
    fun rotated(deg: Int): ContentBox = when (((deg % 360) + 360) % 360) {
        90 -> ContentBox(1 - bottom, left, 1 - top, right, cutBottom, cutLeft, cutTop, cutRight)
        180 -> ContentBox(1 - right, 1 - bottom, 1 - left, 1 - top, cutRight, cutBottom, cutLeft, cutTop)
        270 -> ContentBox(top, 1 - right, bottom, 1 - left, cutTop, cutRight, cutBottom, cutLeft)
        else -> this
    }
}

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

    /** y — плоскость яркости, rowStride — шаг строки в байтах, pixelStride — шаг пикселя (для Y обычно 1),
     * rotation — rotationDegrees кадра (чтобы «левее/выше» совпадали с тем, как держат телефон). */
    fun measure(y: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int = 1, rotation: Int = 0): FrameStats {
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
        val content = findContent(::px, width, height)?.rotated(rotation)
        return FrameStats(sharpness, luma, motion, content)
    }

    /**
     * Где в кадре «содержимое» (буквы, цифры, рисунок купюры): сетка 24×18 клеток, в каждой —
     * средняя сила перепадов яркости. Клетки заметно «живее» фона — содержимое; его рамка и
     * касание краёв говорят, куда сдвинуть телефон. Пару миллисекунд, без сервера.
     */
    private fun findContent(px: (Int, Int) -> Int, width: Int, height: Int): ContentBox? {
        val gw = 24
        val gh = 18
        val cell = DoubleArray(gw * gh)
        val step = (minOf(width, height) / 180).coerceAtLeast(1)
        for (j in 0 until gh) {
            val ya = j * height / gh
            val yb = ((j + 1) * height / gh).coerceAtMost(height - 1)
            for (i in 0 until gw) {
                val xa = i * width / gw
                val xb = ((i + 1) * width / gw).coerceAtMost(width - 1)
                var sum = 0L
                var n = 0
                var yy = ya
                while (yy < yb) {
                    var xx = xa
                    while (xx < xb) {
                        sum += abs(px(xx + 1, yy) - px(xx, yy)) + abs(px(xx, yy + 1) - px(xx, yy))
                        n++
                        xx += step
                    }
                    yy += step
                }
                cell[j * gw + i] = if (n == 0) 0.0 else sum.toDouble() / n
            }
        }
        val sorted = cell.sorted()
        val background = sorted[sorted.size / 4] // спокойная четверть кадра — это фон
        val peak = sorted[sorted.size * 19 / 20]
        if (peak < 10.0 || peak < background * 1.8) return null // ровный кадр — содержимого нет
        val threshold = maxOf(background + (peak - background) * 0.35, 8.0)
        var minI = gw
        var maxI = -1
        var minJ = gh
        var maxJ = -1
        var count = 0
        for (j in 0 until gh) for (i in 0 until gw) {
            if (cell[j * gw + i] >= threshold) {
                // одиночная «живая» клетка без соседей — шум, а не текст
                val neighbours = listOf(i - 1 to j, i + 1 to j, i to j - 1, i to j + 1).count { (a, b) ->
                    a in 0 until gw && b in 0 until gh && cell[b * gw + a] >= threshold
                }
                if (neighbours == 0) continue
                count++
                if (i < minI) minI = i
                if (i > maxI) maxI = i
                if (j < minJ) minJ = j
                if (j > maxJ) maxJ = j
            }
        }
        if (count < 4) return null
        return ContentBox(
            left = minI.toDouble() / gw,
            top = minJ.toDouble() / gh,
            right = (maxI + 1).toDouble() / gw,
            bottom = (maxJ + 1).toDouble() / gh,
            cutLeft = minI == 0,
            cutTop = minJ == 0,
            cutRight = maxI == gw - 1,
            cutBottom = maxJ == gh - 1
        )
    }
}

/**
 * Куда сдвинуть телефон, чтобы текст / чек / купюра целиком попали в кадр и оказались по центру.
 * «Левее» — значит телефон надо сдвинуть влево (содержимое видно у левого края или уходит за него).
 * null — с положением всё хорошо.
 */
fun framingHint(c: ContentBox?): FrameHint? {
    if (c == null) return null
    val cutH = c.cutLeft && c.cutRight
    val cutV = c.cutTop && c.cutBottom
    return when {
        cutH && cutV -> FrameHint.FARTHER
        cutH -> if (c.cutTop) FrameHint.UP else if (c.cutBottom) FrameHint.DOWN else FrameHint.FARTHER
        cutV -> if (c.cutLeft) FrameHint.LEFT else if (c.cutRight) FrameHint.RIGHT else FrameHint.FARTHER
        c.cutLeft -> FrameHint.LEFT
        c.cutRight -> FrameHint.RIGHT
        c.cutTop -> FrameHint.UP
        c.cutBottom -> FrameHint.DOWN
        c.area < 0.10 -> FrameHint.CLOSER
        c.cx < 0.36 -> FrameHint.LEFT
        c.cx > 0.64 -> FrameHint.RIGHT
        c.cy < 0.36 -> FrameHint.UP
        c.cy > 0.64 -> FrameHint.DOWN
        else -> null
    }
}

/** Что сказать пользователю про текущий кадр. */
enum class FrameHint(val spoken: String?) {
    DARK("Слишком темно. Включите свет или фонарик."),
    NO_CONTENT("Ничего не вижу. Наведите камеру на текст, чек или купюру."),
    LEFT("Левее."),
    RIGHT("Правее."),
    UP("Чуть выше."),
    DOWN("Чуть ниже."),
    CLOSER("Ближе."),
    FARTHER("Чуть дальше."),
    BLURRY("Нечётко. Держите телефон ровно и чуть отодвиньте."),
    MOVING("Держите ровно."),
    GOOD(null)
}

val FrameHint.isFraming: Boolean
    get() = this == FrameHint.LEFT || this == FrameHint.RIGHT || this == FrameHint.UP ||
        this == FrameHint.DOWN || this == FrameHint.CLOSER || this == FrameHint.FARTHER

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
        else -> framingHint(s.content) ?: if (s.sharpness < sharpMin) FrameHint.BLURRY else FrameHint.GOOD
    }

    fun update(s: FrameStats, nowMs: Long): GateDecision {
        val hint = classify(s)
        if (hint == FrameHint.GOOD) {
            if (goodSince < 0) goodSince = nowMs
        } else {
            goodSince = -1L
        }
        // «Устойчиво, но не резко / не идеально по центру» — отдельный таймер терпения: подсказка
        // «левее» может ошибаться на пёстром фоне, и человек не должен ждать вечно.
        if (hint == FrameHint.GOOD || hint == FrameHint.BLURRY || hint.isFraming) {
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
        // Подсказки направления («левее», «выше») нужны быстро и чаще — человек двигает телефон по ним.
        val first = if (hint.isFraming) 900L else firstDelayMs
        if (nowMs - badSince < first) return null
        val gap = when {
            hint.isFraming && sameAsBefore -> 2500L
            hint.isFraming -> 1200L
            sameAsBefore -> repeatMs
            else -> 2000L
        }
        if (nowMs - lastSpokenAt < gap) return null
        lastSpokenAt = nowMs
        return hint.spoken
    }
}
