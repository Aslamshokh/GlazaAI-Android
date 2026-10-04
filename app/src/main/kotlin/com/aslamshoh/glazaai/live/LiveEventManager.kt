package com.aslamshoh.glazaai.live

import com.aslamshoh.glazaai.network.LiveTrack
import kotlin.math.roundToInt

/** Что именно сказать вслух и нужно ли прервать текущую речь (для критической опасности). */
data class Announcement(val text: String, val priority: String, val interrupt: Boolean)

/**
 * Менеджер событий и приоритетов (раздел 10 ТЗ): решает, о чём из найденного озвучить и когда,
 * чтобы не перегружать пользователя. Правила:
 *  — критическая опасность (машина рядом, лестница рядом) озвучивается сразу, прерывает любую
 *    речь и повторяется каждые ~3,5 с, пока опасность сохраняется;
 *  — высокая — один раз при появлении, повтор-напоминание не чаще раза в ~9 с;
 *  — средняя — один раз при появлении, повтор не чаще раза в ~20 с;
 *  — низкая автоматически не озвучивается вообще (только по кнопке «Что вокруг?»);
 *  — повтор того же события блокируется, пока ситуация не изменилась (предмет стал ближе,
 *    начал приближаться, поднялся приоритет, сменил сторону, сменился сигнал светофора);
 *  — за раз озвучивается не больше 3 объектов (при критической опасности — не больше 2).
 *
 * Чистая логика без Android-зависимостей: время и «говорит ли сейчас синтезатор» передаются
 * параметрами — так её можно проверять обычными тестами.
 */
class LiveEventManager {
    private class Memory(
        var lastSpokenMs: Long,
        var zoneRank: Int,
        var priorityRank: Int,
        var approaching: Boolean,
        var direction: String,
        var lightState: String?
    )

    private val memory = HashMap<Int, Memory>()
    private val lastByLabelKey = HashMap<String, Long>()
    private var lastAnySpokenMs = 0L

    fun reset() {
        memory.clear()
        lastByLabelKey.clear()
        lastAnySpokenMs = 0L
    }

    fun process(tracks: List<LiveTrack>, nowMs: Long, isSpeaking: Boolean): Announcement? {
        // Забываем треки, которых давно нет в кадре, чтобы память не росла.
        val aliveIds = tracks.map { it.trackId }.toSet()
        memory.keys.retainAll { it in aliveIds || nowMs - (memory[it]?.lastSpokenMs ?: 0L) < FORGET_AFTER_MS }

        val candidates = tracks.filter { shouldSpeak(it, nowMs) }
        if (candidates.isEmpty()) return null

        val sorted = candidates.sortedWith(
            compareBy<LiveTrack> { priorityRank(it.priority) }.thenBy { zoneRank(it.zone) }
        )
        val hasCritical = sorted.first().priority == "critical"

        // Пока синтезатор говорит, не копим очередь устаревших фраз: некритичное просто
        // подождёт (память не обновляется — на следующем кадре событие вернётся в кандидаты).
        if (isSpeaking && !hasCritical) return null
        val minGap = if (hasCritical) MIN_GAP_CRITICAL_MS else MIN_GAP_MS
        if (nowMs - lastAnySpokenMs < minGap) return null

        val limit = if (hasCritical) 2 else 3
        val chosen = (if (hasCritical) sorted.filter { it.priority == "critical" } else sorted).take(limit)

        for (t in chosen) {
            memory[t.trackId] = Memory(
                lastSpokenMs = nowMs,
                zoneRank = zoneRank(t.zone),
                priorityRank = priorityRank(t.priority),
                approaching = t.approaching,
                direction = t.direction,
                lightState = t.trafficLightState
            )
            lastByLabelKey[labelKey(t)] = nowMs
        }
        lastAnySpokenMs = nowMs

        val text = chosen.joinToString(" ") { phrase(it, hasCritical) }
        return Announcement(text = text, priority = chosen.first().priority, interrupt = hasCritical)
    }

    private fun shouldSpeak(t: LiveTrack, nowMs: Long): Boolean {
        if (t.priority == "low") return false
        val mem = memory[t.trackId]
        val key = labelKey(t)

        if (mem == null) {
            // Новый объект. Если только что говорили о таком же (тот же класс и сторона) —
            // скорее всего это тот же предмет с «перескочившим» id, повторять не нужно.
            if (t.priority != "critical") {
                val last = lastByLabelKey[key]
                val window = if (t.priority == "high") SAME_LABEL_HIGH_MS else SAME_LABEL_MEDIUM_MS
                if (last != null && nowMs - last < window) return false
            }
            return true
        }

        val elapsed = nowMs - mem.lastSpokenMs
        val zoneWorse = zoneRank(t.zone) < mem.zoneRank
        val priorityUp = priorityRank(t.priority) < mem.priorityRank
        val startedApproaching = t.approaching && !mem.approaching
        val lightChanged = t.labelEn == "traffic light" &&
            t.trafficLightState != null && t.trafficLightState != "unknown" &&
            t.trafficLightState != mem.lightState
        val directionChanged = t.direction != mem.direction

        if (lightChanged && elapsed >= 1500) return true
        if ((zoneWorse || priorityUp || startedApproaching) && elapsed >= CHANGE_COOLDOWN_MS) return true
        if (directionChanged && elapsed >= DIRECTION_COOLDOWN_MS && t.priority != "medium") return true

        val repeatAfter = when (t.priority) {
            "critical" -> REPEAT_CRITICAL_MS
            "high" -> REPEAT_HIGH_MS
            else -> REPEAT_MEDIUM_MS
        }
        return elapsed >= repeatAfter
    }

    /** Краткое описание всего, что сейчас видно, — по кнопке «Что вокруг?». Включает и
     * низкоприоритетные предметы (в автоматическом режиме они не озвучиваются). */
    fun describeAll(tracks: List<LiveTrack>): String {
        if (tracks.isEmpty()) {
            return "Ничего не вижу. Проверьте, что камера направлена вперёд и достаточно света."
        }
        val top = tracks
            .sortedWith(compareBy<LiveTrack> { priorityRank(it.priority) }.thenBy { zoneRank(it.zone) })
            .take(6)
        return "Вижу: " + top.joinToString(". ") { phrase(it, false) } + "."
    }

    companion object {
        private const val MIN_GAP_MS = 900L
        private const val MIN_GAP_CRITICAL_MS = 500L
        private const val CHANGE_COOLDOWN_MS = 2000L
        private const val DIRECTION_COOLDOWN_MS = 6000L
        private const val REPEAT_CRITICAL_MS = 3500L
        private const val REPEAT_HIGH_MS = 9000L
        private const val REPEAT_MEDIUM_MS = 20000L
        private const val SAME_LABEL_HIGH_MS = 3000L
        private const val SAME_LABEL_MEDIUM_MS = 6000L
        private const val FORGET_AFTER_MS = 5000L

        fun priorityRank(p: String): Int = when (p) {
            "critical" -> 0
            "high" -> 1
            "medium" -> 2
            else -> 3
        }

        /** Меньше число — ближе предмет. */
        fun zoneRank(z: String): Int = when (z) {
            "very_close" -> 0
            "close" -> 1
            "medium" -> 2
            else -> 3
        }

        private fun labelKey(t: LiveTrack) = "${t.labelEn}|${t.direction}"

        /** «1 метр», «2 метра», «5 метров», «11 метров», «21 метр» ... */
        fun metersWord(n: Int): String {
            val mod100 = n % 100
            val mod10 = n % 10
            return when {
                mod100 in 11..14 -> "метров"
                mod10 == 1 -> "метр"
                mod10 in 2..4 -> "метра"
                else -> "метров"
            }
        }

        fun distancePhrase(t: LiveTrack): String {
            val d = t.distanceM
            if (d != null) {
                if (d < 1.0) return "менее метра"
                val n = d.roundToInt().coerceAtLeast(1)
                return "$n ${metersWord(n)}"
            }
            return when (t.zone) {
                "very_close" -> "совсем рядом"
                "close" -> "близко"
                "medium" -> "в нескольких метрах"
                else -> "далеко"
            }
        }

        fun phrase(t: LiveTrack, withWarning: Boolean): String {
            val name = t.label.replaceFirstChar { it.uppercase() }
            val sb = StringBuilder()
            if (withWarning && t.priority == "critical") sb.append("Осторожно! ")
            sb.append(name).append(' ').append(t.direction).append(", ").append(distancePhrase(t))
            if (t.labelEn == "traffic light") {
                when (t.trafficLightState) {
                    "red" -> sb.append(", красный сигнал")
                    "green" -> sb.append(", зелёный сигнал")
                    "yellow" -> sb.append(", жёлтый сигнал")
                    else -> Unit
                }
            }
            if (t.approaching) sb.append(", приближается")
            sb.append('.')
            return sb.toString()
        }
    }
}
