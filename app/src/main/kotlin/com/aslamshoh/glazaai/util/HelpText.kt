package com.aslamshoh.glazaai.util

/** Доверенный человек, которому можно позвонить одним нажатием. */
data class TrustedContact(val name: String, val phone: String)

/**
 * Тексты и мелкая логика экрана «Помощь» — отдельно от Compose, чтобы проверять на обычной JVM.
 * Все фразы короткие и спокойные: человек может быть напуган или находиться на улице.
 */
object HelpText {
    const val MAX_CONTACTS = 5
    const val DEFAULT_EMERGENCY = "112"

    val DISCLAIMER =
        "Волонтёр — это человек, который готов помочь советом по видеосвязи. Это не служба спасения. " +
            "Если есть угроза жизни, звоните в экстренную службу."

    val CONSENT =
        "Когда вы позовёте волонтёра, он увидит то, что снимает камера, и услышит вас. " +
            "Запись не ведётся. Волонтёр не видит ваш номер и адрес."

    fun waiting(volunteersOnline: Int?, waitedSeconds: Int): String? {
        if (waitedSeconds <= 1) {
            return when {
                volunteersOnline == null -> "Ищу волонтёра."
                volunteersOnline <= 0 -> "Сейчас на связи никого нет, но я продолжаю искать. Можно отменить и позвонить близкому."
                volunteersOnline == 1 -> "Ищу волонтёра. Сейчас на связи один."
                else -> "Ищу волонтёра. Сейчас на связи $volunteersOnline."
            }
        }
        // дальше напоминаем раз в 10 секунд, чтобы не тараторить
        return if (waitedSeconds % 10 in 0..1 && waitedSeconds >= 10) "Всё ещё ищу. Прошло $waitedSeconds ${seconds(waitedSeconds)}." else null
    }

    fun seconds(n: Int): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> "секунд"
            m10 == 1 -> "секунда"
            m10 in 2..4 -> "секунды"
            else -> "секунд"
        }
    }

    fun accepted(volunteerName: String?): String {
        val who = volunteerName?.trim().orEmpty()
        return if (who.isEmpty()) "Волонтёр ответил. Открываю видеозвонок."
        else "Вам отвечает $who. Открываю видеозвонок."
    }

    fun expired(hasContacts: Boolean, urgent: Boolean): String {
        val base = "Никто не ответил."
        val next = if (hasContacts) "Позвоните близкому или попробуйте ещё раз." else "Попробуйте ещё раз или добавьте близкого человека, чтобы звонить ему."
        return if (urgent) "$base При угрозе жизни звоните в экстренную службу. $next" else "$base $next"
    }

    fun rated(n: Int): String = if (n >= 4) "Спасибо за оценку!" else "Спасибо. Мы учтём ваш отзыв."

    /** Оставляет цифры и ведущий «+»; пустая строка, если цифр слишком мало, чтобы быть номером. */
    fun cleanPhone(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 3) return ""
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    fun dialUri(phone: String): String? {
        val p = cleanPhone(phone)
        return if (p.isEmpty()) null else "tel:$p"
    }

    fun smsUri(phone: String): String? {
        val p = cleanPhone(phone)
        return if (p.isEmpty()) null else "smsto:$p"
    }

    fun whereAmIText(name: String, lat: Double?, lon: Double?): String {
        val who = name.trim().ifEmpty { "Мне" }
        val head = if (who == "Мне") "Мне нужна помощь." else "$who просит помощи."
        return if (lat == null || lon == null) "$head Позвони мне, пожалуйста."
        else "$head Я здесь: https://maps.google.com/?q=${fmt(lat)},${fmt(lon)}"
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.5f", v)

    // ── хранение контактов одной строкой: «имя\tтелефон» на строку ──
    fun encodeContacts(list: List<TrustedContact>): String =
        list.joinToString("\n") { "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.phone.replace('\t', ' ').replace('\n', ' ')}" }

    fun decodeContacts(s: String): List<TrustedContact> =
        s.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            val phone = cleanPhone(parts[1])
            val name = parts[0].trim()
            if (name.isEmpty() || phone.isEmpty()) null else TrustedContact(name, phone)
        }.take(MAX_CONTACTS)

    fun addContact(list: List<TrustedContact>, name: String, phone: String): Pair<List<TrustedContact>, String?> {
        val n = name.trim().take(30)
        val p = cleanPhone(phone)
        if (n.isEmpty()) return list to "Укажите имя."
        if (p.isEmpty()) return list to "Укажите номер телефона."
        if (list.any { it.phone == p }) return list to "Этот номер уже есть в списке."
        if (list.size >= MAX_CONTACTS) return list to "Можно добавить не больше $MAX_CONTACTS человек."
        return (list + TrustedContact(n, p)) to null
    }
}
