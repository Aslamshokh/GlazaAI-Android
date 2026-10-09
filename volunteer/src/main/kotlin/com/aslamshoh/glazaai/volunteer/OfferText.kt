package com.aslamshoh.glazaai.volunteer

/** Тексты и проверки приложения волонтёра — без Android, чтобы тестировать на обычной JVM. */
object OfferText {
    val LANGUAGES = listOf("ru" to "Русский", "tg" to "Тоҷикӣ", "en" to "English", "uz" to "Oʻzbek", "kk" to "Қазақша", "ky" to "Кыргызча")

    fun languageName(code: String?): String = LANGUAGES.firstOrNull { it.first == code?.lowercase() }?.second ?: (code ?: "")

    fun title(userName: String, urgent: Boolean): String =
        (if (urgent) "СРОЧНО: " else "") + (userName.trim().ifEmpty { "Пользователь" }) + " просит помощи"

    fun subtitle(language: String, leftSeconds: Int): String =
        "Язык: ${languageName(language)} · осталось $leftSeconds с"

    /** Допустимый номер: 9–15 цифр (минимум без «+», как принимает сервер). */
    fun validPhone(raw: String): Boolean = raw.count { it.isDigit() } in 9..15

    fun validCode(raw: String): Boolean = raw.trim().length in 4..8 && raw.trim().all { it.isDigit() }

    fun validName(raw: String): Boolean = raw.trim().length in 2..40

    fun loginProblem(phone: String, code: String, name: String, languages: Set<String>, acceptedRules: Boolean): String? = when {
        !validPhone(phone) -> "Введите номер телефона в международном формате, например +992…"
        !validName(name) -> "Укажите имя (от 2 букв)."
        languages.isEmpty() -> "Выберите хотя бы один язык."
        !validCode(code) -> "Введите код из сообщения."
        !acceptedRules -> "Подтвердите правила волонтёра."
        else -> null
    }

    /** Нужно ли показать новое уведомление: только для вызовов, о которых ещё не сообщали. */
    fun newOfferIds(current: List<Int>, alreadyNotified: Set<Int>): List<Int> = current.filter { it !in alreadyNotified }

    val RULES = listOf(
        "Я не прошу у людей деньги, паспортные и банковские данные.",
        "Я не записываю видеозвонок и не делаю снимков экрана.",
        "Я помогаю советом и не принимаю решений за человека. При угрозе жизни советую звонить в экстренную службу.",
        "Я отношусь к людям уважительно. За нарушение правил мой доступ могут закрыть."
    )
}
