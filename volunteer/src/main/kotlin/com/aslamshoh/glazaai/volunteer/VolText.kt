package com.aslamshoh.glazaai.volunteer

import com.google.gson.Gson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Country(val code: String, val name: String, val flag: String, val dial: String)

/** Уведомление внутри приложения (экран «Уведомления»). kind: sos | call | done | info. */
data class AppNotice(val id: Long, val kind: String, val title: String, val text: String, val at: Long)

/** Сообщение чата во время звонка. */
data class ChatMsg(val mine: Boolean, val text: String, val at: Long)

/** Запись в списке чатов. */
data class ChatSummary(val requestId: Int, val userName: String, val at: Long, val last: String)

/**
 * Чистая логика экранов приложения волонтёра (без Android) — чтобы проверять на обычной JVM.
 */
object VolText {
    val COUNTRIES = listOf(
        Country("TJ", "Таджикистан", "🇹🇯", "+992"),
        Country("RU", "Россия", "🇷🇺", "+7"),
        Country("UZ", "Узбекистан", "🇺🇿", "+998"),
        Country("KZ", "Казахстан", "🇰🇿", "+7"),
        Country("KG", "Кыргызстан", "🇰🇬", "+996"),
        Country("BY", "Беларусь", "🇧🇾", "+375"),
        Country("AM", "Армения", "🇦🇲", "+374"),
        Country("AZ", "Азербайджан", "🇦🇿", "+994"),
        Country("TR", "Турция", "🇹🇷", "+90"),
        Country("US", "США", "🇺🇸", "+1")
    )

    val SPECIALTIES = listOf(
        "online" to "Онлайн-помощь",
        "navigation" to "Навигация",
        "reading" to "Чтение текста",
        "shopping" to "Магазины",
        "documents" to "Документы"
    )

    fun specialtyName(code: String): String = SPECIALTIES.firstOrNull { it.first == code }?.second ?: code

    // ── телефон ──
    fun fullPhone(dial: String, national: String): String = dial + national.filter { it.isDigit() }

    fun phoneProblem(dial: String, national: String): String? {
        val n = fullPhone(dial, national).count { it.isDigit() }
        return if (n in 9..15) null else "Введите номер телефона полностью."
    }

    // ── дата рождения: ввод маской 10.07.2002 ──
    fun formatBirthInput(raw: String): String {
        val d = raw.filter { it.isDigit() }.take(8)
        return buildString {
            d.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('.')
                append(c)
            }
        }
    }

    private val BIRTH_FMT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)

    fun parseBirth(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), BIRTH_FMT).takeIf { it.year >= 1900 }
    } catch (e: Exception) { null }

    /** «2002-07-10» → «10.07.2002» (так приходит дата с сервера). */
    fun isoToBirth(iso: String?): String = try {
        if (iso.isNullOrBlank()) "" else LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
    } catch (e: Exception) { "" }

    fun ageOn(born: LocalDate, today: LocalDate): Int = java.time.Period.between(born, today).years

    fun birthProblem(text: String, today: LocalDate): String? {
        val born = parseBirth(text) ?: return "Укажите дату рождения полностью, например 10.07.2002."
        return if (ageOn(born, today) < 18) "Волонтёром может быть только человек старше 18 лет." else null
    }

    fun emailProblem(email: String): String? {
        val e = email.trim()
        if (e.isEmpty()) return null
        return if (Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(e)) null else "Электронная почта указана неверно."
    }

    fun nameProblem(name: String): String? = if (name.trim().length in 2..40) null else "Укажите имя (от 2 букв)."

    fun personalProblem(name: String, email: String, birth: String, today: LocalDate): String? =
        nameProblem(name) ?: emailProblem(email) ?: birthProblem(birth, today)

    // ── время ──
    fun waitedText(seconds: Int): String = when {
        seconds < 5 -> "только что"
        seconds < 60 -> "$seconds с назад"
        else -> "${seconds / 60} мин назад"
    }

    fun relativeTime(nowSec: Long, thenSec: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = nowSec - thenSec
        return when {
            d < 60 -> "только что"
            d < 3600 -> "${d / 60} мин назад"
            d < 86400 -> "${d / 3600} ч назад"
            else -> {
                val now = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
                val then = Instant.ofEpochSecond(thenSec).atZone(zone).toLocalDate()
                if (then == now.minusDays(1)) "Вчера" else then.format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
            }
        }
    }

    fun formatDateTime(sec: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochSecond(sec).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm"))

    /** «45 с», «12 мин», «6.5 ч». */
    fun formatDuration(sec: Long): String = when {
        sec < 60 -> "$sec с"
        sec < 3600 -> "${sec / 60} мин"
        else -> "${sec / 3600}.${(sec % 3600) * 10 / 3600} ч"
    }

    fun timer(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)

    fun statusLabel(status: String): String = when (status) {
        "completed" -> "Завершён"
        "cancelled" -> "Отменён пользователем"
        "active" -> "В процессе"
        else -> status
    }

    fun callTitle(urgent: Boolean): String = if (urgent) "Срочный вызов (SOS)" else "Помощь по видеосвязи"

    /** Высоты столбиков диаграммы 0..1; самый высокий день — 1. */
    fun barHeights(values: List<Int>): List<Float> {
        val max = values.maxOrNull() ?: 0
        return values.map { if (max <= 0) 0f else it.toFloat() / max }
    }

    val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    // ── местоположение для экстренных функций ──
    fun whereAmIText(name: String, lat: Double?, lon: Double?): String {
        val head = "${name.trim().ifEmpty { "Волонтёр" }}: мне нужна помощь."
        return if (lat == null || lon == null) "$head Позвоните мне, пожалуйста."
        else "$head Я здесь: https://maps.google.com/?q=${String.format(java.util.Locale.US, "%.5f,%.5f", lat, lon)}"
    }

    // ── чат во время звонка: формат сообщения в канале данных LiveKit (тот же в приложении «ИИ Глаз») ──
    private data class Wire(val t: String = "chat", val text: String = "")

    fun chatEncode(text: String): ByteArray = Gson().toJson(Wire("chat", text.trim().take(500))).toByteArray(Charsets.UTF_8)

    fun chatDecode(bytes: ByteArray): String? = try {
        val w = Gson().fromJson(String(bytes, Charsets.UTF_8), Wire::class.java)
        if (w != null && w.t == "chat" && w.text.isNotBlank()) w.text.trim().take(500) else null
    } catch (e: Exception) { null }

    // ── хранение списков одной строкой JSON ──
    fun noticesToJson(list: List<AppNotice>): String = Gson().toJson(list.take(50))
    fun noticesFromJson(s: String): List<AppNotice> = try {
        Gson().fromJson(s, Array<AppNotice>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun chatToJson(list: List<ChatMsg>): String = Gson().toJson(list.takeLast(200))
    fun chatFromJson(s: String): List<ChatMsg> = try {
        Gson().fromJson(s, Array<ChatMsg>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun summariesToJson(list: List<ChatSummary>): String = Gson().toJson(list.take(30))
    fun summariesFromJson(s: String): List<ChatSummary> = try {
        Gson().fromJson(s, Array<ChatSummary>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) { emptyList() }
}
