package com.aslamshoh.glazaai.util

import com.aslamshoh.glazaai.nav.NavGeo
import com.aslamshoh.glazaai.nav.NavPoint
import com.aslamshoh.glazaai.store.MemoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Предмет рядом, увиденный камерой в момент запоминания (из живого потока). */
data class NearObject(val label: String, val direction: String, val distanceM: Double?)

/**
 * Вся «речь» функции «Память вещей» и сопоставление названий: что сказать, когда человек просит
 * запомнить или спрашивает «где мои ключи?». Чистая логика без Android — проверяется тестами.
 */
object MemoryText {
    /** Слова, которые не часть названия вещи: «мои ключи» = «ключи». */
    private val noise = setOf(
        "мои", "мой", "моя", "мое", "мою", "моих", "свои", "свой", "свою", "свое", "наши", "наш", "нашу",
        "мне", "ии", "глаз", "ай", "айс", "айз", "eyes", "эй", "ai", "пожалуйста", "я", "ты", "что", "где", "куда", "это", "здесь", "тут",
        "вот", "сюда", "там", "же", "ли", "то", "опять", "снова", "напомни", "вспомни", "скажи", "подскажи"
    )

    private val vowelsEnd = charArrayOf('а', 'е', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я', 'й', 'ь', 'ъ')

    /** Нижний регистр, без знаков и лишних слов: «Мои ключи!» -> «ключи». */
    fun cleanItem(raw: String): String =
        raw.lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("[^а-яa-z0-9 ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() && it !in noise }
            .joinToString(" ")

    /** Грубая основа слова: «ключи/ключей/ключами» -> «ключ…». Без словарей — хватает для вещей. */
    fun stem(word: String): String {
        val cut = word.trimEnd(*vowelsEnd)
        return if (cut.length >= 3) cut else word
    }

    private fun wordMatch(a: String, b: String): Boolean {
        val sa = stem(a)
        val sb = stem(b)
        if (sa == sb) return true
        var cp = 0
        while (cp < sa.length && cp < sb.length && sa[cp] == sb[cp]) cp++
        return cp >= max(3, min(sa.length, sb.length) - 2)
    }

    /** Одна и та же вещь? «ключи» ~ «ключей», «кошелёк» ~ «кошелька», «ключи от дома» ⊇ «ключи». */
    fun sameItem(a: String, b: String): Boolean {
        val wa = cleanItem(a).split(" ").filter { it.isNotBlank() }
        val wb = cleanItem(b).split(" ").filter { it.isNotBlank() }
        if (wa.isEmpty() || wb.isEmpty()) return false
        val (short, long) = if (wa.size <= wb.size) wa to wb else wb to wa
        return short.all { s -> long.any { l -> wordMatch(s, l) } }
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> many
            n10 == 1 -> one
            n10 in 2..4 -> few
            else -> many
        }
    }

    /** «только что», «15 минут назад», «сегодня в 14:20», «вчера в 09:05», «3 октября в 18:30». */
    fun whenPhrase(savedMs: Long, nowMs: Long): String {
        val diffSec = ((nowMs - savedMs) / 1000).coerceAtLeast(0)
        if (diffSec < 90) return "только что"
        val minutes = (diffSec / 60).toInt()
        if (minutes < 60) return "$minutes ${plural(minutes, "минуту", "минуты", "минут")} назад"
        val timeText = SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(savedMs))
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val then = Calendar.getInstance().apply { timeInMillis = savedMs }
        val dayDiff = dayNumber(now) - dayNumber(then)
        return when {
            dayDiff == 0 -> {
                val hours = (minutes / 60.0).roundToInt().coerceAtLeast(1)
                "$hours ${plural(hours, "час", "часа", "часов")} назад, в $timeText"
            }
            dayDiff == 1 -> "вчера в $timeText"
            dayDiff in 2..6 -> "$dayDiff ${plural(dayDiff, "день", "дня", "дней")} назад, в $timeText"
            else -> SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru")).format(Date(savedMs)) + " в $timeText"
        }
    }

    private fun dayNumber(c: Calendar): Int = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)

    private val compass = listOf(
        "на север", "на северо-восток", "на восток", "на юго-восток",
        "на юг", "на юго-запад", "на запад", "на северо-запад"
    )

    fun compassPhrase(bearingDeg: Double): String {
        val i = (((bearingDeg % 360 + 360) % 360 + 22.5) / 45.0).toInt() % 8
        return compass[i]
    }

    /** Куда это относительно того, куда смотрит человек (delta = азимут цели − азимут взгляда). */
    fun relativePhrase(deltaDeg: Double): String {
        var d = deltaDeg % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return when {
            abs(d) <= 30 -> "прямо перед вами"
            d in 30.0..100.0 -> "справа"
            d in 100.0..165.0 -> "сзади справа"
            d in -100.0..-30.0 -> "слева"
            d in -165.0..-100.0 -> "сзади слева"
            else -> "позади вас"
        }
    }

    fun distancePhrase(meters: Double): String {
        if (meters >= 1000) {
            val km = String.format(Locale.forLanguageTag("ru"), "%.1f", meters / 1000.0)
            return "примерно $km км"
        }
        val rounded = when {
            meters < 20 -> meters.roundToInt().coerceAtLeast(1)
            meters < 100 -> (meters / 5).roundToInt() * 5
            else -> (meters / 10).roundToInt() * 10
        }
        return "примерно $rounded ${plural(rounded, "метр", "метра", "метров")}"
    }

    /** «стол по центру, чашка слева» из того, что камера видела рядом (без самой вещи и людей). */
    fun aroundPhrase(near: List<NearObject>, itemName: String, limit: Int = 4): String {
        val seen = HashSet<String>()
        return near
            .filter { it.label.isNotBlank() && it.label != "человек" && !sameItem(it.label, itemName) }
            .sortedBy { it.distanceM ?: Double.MAX_VALUE }
            .filter { seen.add(it.label) }
            .take(limit)
            .joinToString(", ") { if (it.direction.isBlank()) it.label else "${it.label} ${it.direction}" }
    }

    /** Ответ сервера «Распознано: стол, чашка.» -> «стол, чашка». Живой текст облачной модели — как есть. */
    fun normalizeScene(description: String): String {
        var t = description.trim()
        if (t.startsWith("Распознано:", ignoreCase = true)) t = t.substring("Распознано:".length)
        return t.trim().trimEnd('.').take(220)
    }

    /** Что сказать сразу после «запомни ключи здесь». */
    fun rememberedAnswer(name: String, place: String, around: String, hasGps: Boolean): String {
        val parts = ArrayList<String>()
        parts += "Запомнила: $name."
        if (place.isNotBlank()) parts += "Место: $place."
        if (around.isNotBlank()) parts += "Рядом: $around."
        parts += if (hasGps) {
            "Положение по GPS тоже сохранила."
        } else {
            "Положение по GPS определить не удалось, поэтому запомнила по описанию и фото."
        }
        return parts.joinToString(" ")
    }

    /** Что сказать в ответ на «где мои ключи?». here/headingDeg — где человек сейчас (могут быть null). */
    fun recallAnswer(
        item: MemoryItem,
        nowMs: Long,
        here: NavPoint?,
        hereAccuracyM: Float?,
        headingDeg: Float?
    ): String {
        val parts = ArrayList<String>()
        parts += "${item.name.replaceFirstChar { it.uppercase() }}. Вы запомнили это место ${whenPhrase(item.savedAtMs, nowMs)}."
        if (item.place.isNotBlank()) parts += "Вы сказали: ${item.place}."
        if (item.around.isNotBlank()) parts += "Рядом было: ${item.around}."

        val lat = item.lat
        val lon = item.lon
        if (lat != null && lon != null) {
            if (here == null) {
                parts += "Ваше положение сейчас определить не удалось — ориентируйтесь по описанию."
            } else {
                val target = NavPoint(lat, lon)
                val meters = NavGeo.distanceM(here, target)
                val gpsError = max(item.accuracyM ?: 25f, hereAccuracyM ?: 25f)
                if (meters <= max(8.0, gpsError.toDouble())) {
                    parts += "Вы сейчас совсем рядом с этим местом."
                } else {
                    val bearing = NavGeo.bearingDeg(here, target)
                    var s = "От вас это ${distancePhrase(meters)}, ${compassPhrase(bearing)}"
                    if (headingDeg != null) s += ", сейчас ${relativePhrase(bearing - headingDeg)}"
                    parts += "$s."
                }
                if (gpsError > 40f) parts += "GPS здесь неточный, поэтому расстояние приблизительное."
            }
        }
        return parts.joinToString(" ")
    }

    fun listAnswer(items: List<MemoryItem>, nowMs: Long): String {
        if (items.isEmpty()) return "Пока ничего не запомнено. Скажите, например: запомни ключи здесь."
        val head = "Я помню ${items.size} ${plural(items.size, "вещь", "вещи", "вещей")}: "
        return head + items.take(8).joinToString("; ") { "${it.name} — ${whenPhrase(it.savedAtMs, nowMs)}" } + "."
    }
}
