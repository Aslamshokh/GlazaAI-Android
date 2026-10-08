package com.aslamshoh.glazaai.util

/** Что внутри кода: ссылка, Wi-Fi, телефон, обычный текст и т.д. */
enum class QrKind { URL, WIFI, PHONE, EMAIL, SMS, GEO, CONTACT, TEXT }

/**
 * Разобранный QR-код: [spoken] — что сказать вслух (коротко и по-человечески, без чтения
 * длинных адресов по буквам), [openUrl] — что открыть кнопкой «Открыть» (если есть),
 * [copy] — что копировать.
 */
data class QrInfo(
    val kind: QrKind,
    val title: String,
    val spoken: String,
    val copy: String,
    val openUrl: String? = null
)

/**
 * Разбор содержимого QR/двумерных кодов — чистая логика без Android, проверяется тестами.
 * Обычные товарные штрихкоды (только цифры) сюда не попадают: их ищем в базе товаров.
 */
object QrText {
    private val productCode = Regex("^\\d{6,14}$")

    fun isProductCode(raw: String): Boolean = productCode.matches(raw.trim())

    fun describe(rawInput: String): QrInfo {
        val raw = rawInput.trim()
        val lower = raw.lowercase()
        return when {
            lower.startsWith("wifi:") -> wifi(raw)
            lower.startsWith("http://") || lower.startsWith("https://") -> url(raw, raw)
            lower.startsWith("www.") && !raw.contains(' ') -> url(raw, "https://$raw")
            lower.startsWith("tel:") -> {
                val number = raw.substring(4).trim()
                QrInfo(QrKind.PHONE, "Телефон", "QR-код с номером телефона: ${digitsSpaced(number)}.", number)
            }
            lower.startsWith("mailto:") -> {
                val address = raw.substring(7).substringBefore('?').trim()
                QrInfo(QrKind.EMAIL, "Электронная почта", "QR-код с адресом электронной почты: ${mailSpoken(address)}.", address, raw)
            }
            lower.startsWith("smsto:") || lower.startsWith("sms:") -> {
                val parts = raw.substringAfter(':').split(':', limit = 2)
                val number = parts[0].trim()
                val text = parts.getOrNull(1)?.trim().orEmpty()
                val spoken = "QR-код с сообщением для номера ${digitsSpaced(number)}" + if (text.isNotEmpty()) ": $text." else "."
                QrInfo(QrKind.SMS, "Сообщение", spoken, raw)
            }
            lower.startsWith("geo:") -> {
                val coords = raw.substring(4).substringBefore('?').trim()
                QrInfo(
                    QrKind.GEO, "Место на карте", "QR-код с координатами места на карте.", coords,
                    "https://www.google.com/maps/search/?api=1&query=" + coords.replace(" ", "")
                )
            }
            lower.startsWith("begin:vcard") -> contact(raw)
            else -> {
                val spoken = if (raw.isEmpty()) "QR-код пустой." else "QR-код с текстом: ${limit(raw, 400)}"
                QrInfo(QrKind.TEXT, "Текст из QR-кода", spoken, raw)
            }
        }
    }

    private fun url(display: String, openUrl: String): QrInfo {
        val host = display.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .removePrefix("www.")
        val hasPath = display.substringAfter("://").let { it.contains('/') && it.substringAfter('/').isNotBlank() }
        val spoken = "QR-код со ссылкой на сайт $host" + (if (hasPath) ", на конкретную страницу" else "") +
            ". Нажмите «Открыть», чтобы перейти. Платите или вводите данные только если доверяете этому сайту."
        return QrInfo(QrKind.URL, "Ссылка", spoken, display, openUrl)
    }

    private fun wifi(raw: String): QrInfo {
        val fields = HashMap<String, String>()
        // Формат WIFI:T:WPA;S:имя;P:пароль;H:false;; — спецсимволы экранируются обратной косой чертой.
        val body = raw.substring(5)
        val current = StringBuilder()
        val parts = ArrayList<String>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                current.append(body[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                parts.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        for (p in parts) {
            val idx = p.indexOf(':')
            if (idx > 0) fields[p.substring(0, idx).uppercase()] = p.substring(idx + 1)
        }
        val name = fields["S"].orEmpty()
        val password = fields["P"].orEmpty()
        val security = fields["T"].orEmpty().uppercase()
        val sb = StringBuilder("QR-код для подключения к Wi-Fi")
        if (name.isNotEmpty()) sb.append(". Сеть: $name")
        if (security == "NOPASS" || (password.isEmpty() && security.isEmpty())) {
            sb.append(". Пароля нет")
        } else if (password.isNotEmpty()) {
            sb.append(". Пароль: ${spellPassword(password)}")
        }
        sb.append(".")
        return QrInfo(QrKind.WIFI, "Сеть Wi-Fi", sb.toString(), if (password.isNotEmpty()) password else name)
    }

    /** Пароль читаем с паузами по символам, чтобы его можно было на слух ввести. */
    private fun spellPassword(p: String): String =
        if (p.length <= 20) p.map { ch -> charName(ch) }.joinToString(", ") else p

    private fun charName(c: Char): String = when {
        c.isUpperCase() && c.code < 128 -> "заглавная $c"
        c == '-' -> "минус"
        c == '_' -> "подчёркивание"
        c == '.' -> "точка"
        c == '@' -> "собака"
        c == '#' -> "решётка"
        c == '!' -> "восклицательный знак"
        c == ' ' -> "пробел"
        else -> c.toString()
    }

    private fun contact(raw: String): QrInfo {
        fun field(vararg keys: String): String? {
            for (line in raw.lines()) {
                val head = line.substringBefore(':').uppercase().substringBefore(';')
                if (head in keys) return line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            }
            return null
        }
        val name = field("FN") ?: field("N")?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ")
        val tel = field("TEL")
        val mail = field("EMAIL")
        val org = field("ORG")
        val sb = StringBuilder("QR-код с визиткой")
        if (name != null) sb.append(". Имя: $name")
        if (org != null) sb.append(". Организация: $org")
        if (tel != null) sb.append(". Телефон: ${digitsSpaced(tel)}")
        if (mail != null) sb.append(". Почта: ${mailSpoken(mail)}")
        sb.append(".")
        return QrInfo(QrKind.CONTACT, "Визитка", sb.toString(), raw)
    }

    /** «+992 93 123 45 67» → цифры группами, чтобы синтезатор не читал одно огромное число. */
    fun digitsSpaced(number: String): String {
        val trimmed = number.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 5) return trimmed
        val plus = if (trimmed.startsWith("+")) "плюс " else ""
        return plus + digits.chunked(2).joinToString(" ")
    }

    private fun mailSpoken(address: String): String = address.replace("@", " собака ").replace(".", " точка ")

    private fun limit(s: String, max: Int) = if (s.length <= max) s else s.take(max).trimEnd() + "… и далее"
}

/** Подсказки, если код долго не находится: что поправить, не нажимая ничего на экране. */
object ScanHints {
    /** Возвращает (индекс этапа, фраза) для самой поздней ещё не сказанной подсказки или null. */
    fun next(elapsedMs: Long, doneStages: Int, torchAvailable: Boolean, torchOn: Boolean): Pair<Int, String>? {
        val stages = listOf(
            7_000L to "Код пока не найден. Держите телефон на расстоянии ладони от кода и медленно двигайте.",
            15_000L to if (torchAvailable && !torchOn) "Если темно, включите фонарик кнопкой на экране. Избегайте бликов на упаковке."
            else "Попробуйте другой угол, чтобы не было бликов и теней.",
            26_000L to "Если на упаковке нет штрихкода, перейдите в режим «Текст» и прочитайте название."
        )
        for ((index, stage) in stages.withIndex()) {
            if (index >= doneStages && elapsedMs >= stage.first) return index to stage.second
        }
        return null
    }
}
