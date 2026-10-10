package com.aslamshoh.glazaai.util

import com.aslamshoh.glazaai.network.DocumentItem
import com.aslamshoh.glazaai.network.DocumentResult
import java.util.Locale

/**
 * «Чат с документом»: вопрос обычными словами → короткий ответ по уже распознанному чеку или документу.
 * Работает прямо на телефоне, без интернета и без облачного ИИ: ищет в полях, позициях и тексте.
 * Умеет: итог, дату, магазин, число позиций, самое дорогое и дешёвое, «сколько стоит молоко»,
 * «есть ли хлеб», сдачу/оплату/НДС, любое поле документа («номер», «срок действия») и поиск слова в тексте.
 * Чистая логика — проверяется тестами без Android.
 */
object DocQa {
    private val stop = setOf(
        "что", "это", "как", "где", "какой", "какая", "какое", "какие", "мне", "мой", "моя", "есть", "ли", "и", "в", "на",
        "по", "про", "для", "от", "из", "за", "с", "со", "у", "а", "то", "же", "ну", "пожалуйста", "скажи", "скажите",
        "покажи", "найди", "сколько", "стоит", "цена", "цены", "стоят", "написано", "документе", "документ", "чеке", "чек",
        "здесь", "там", "всего", "ещё", "еще"
    )

    private fun norm(s: String): String =
        s.lowercase(Locale("ru")).replace('ё', 'е').replace(Regex("[^а-яa-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun words(q: String): List<String> = norm(q).split(' ').filter { it.length >= 3 && it !in stop }

    /** Слово из вопроса «совпадает» со словом из текста, если у них общее начало (молоко ~ молока, хлеб ~ хлеба). */
    private fun matches(word: String, text: String): Boolean {
        val stem = word.take(maxOf(3, word.length - 2))
        return norm(text).split(' ').any { it.startsWith(stem) }
    }

    private fun money(v: Double): String {
        val s = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
        return s
    }

    private fun hasAny(q: String, vararg keys: String) = keys.any { q.contains(it) }

    fun hint(r: DocumentResult): String =
        if (r.kind == "receipt")
            "Можно спросить: итог, дата, магазин, сколько позиций, самое дорогое, сколько стоит молоко, есть ли хлеб."
        else
            "Можно спросить: любое поле документа, например номер или срок действия, или найти слово в тексте."

    fun answer(r: DocumentResult, question: String): String {
        val q = norm(question)
        if (q.isEmpty()) return "Я не расслышала вопрос. " + hint(r)
        val items = r.items.orEmpty()
        val fields = r.fields.orEmpty()

        // 1. общие просьбы
        if (hasAny(q, "прочитай", "перескажи", "что в документе", "что в чеке", "о чем", "кратко", "что написано")) {
            return if (hasAny(q, "все", "полностью", "целиком")) DocumentText.readAll(r) else shortSummary(r)
        }
        // 2. итог / сумма / «сколько с меня»
        if (hasAny(q, "итог", "сумма", "всего", "к оплате", "с меня", "заплатить", "платить", "сколько я")) {
            return DocumentText.total(r)
        }
        // 3. дата
        if (hasAny(q, "дата", "когда", "какого числа", "число")) {
            val d = r.date ?: fields.firstOrNull { it.label.contains("дат", true) }?.value
            return d?.let { "Дата: $it." } ?: "Дату на документе найти не удалось."
        }
        // 4. магазин / организация
        if (hasAny(q, "магазин", "где купил", "откуда", "название", "организация", "продавец")) {
            val s = r.store ?: fields.firstOrNull { it.label.contains("магазин", true) || it.label.contains("организац", true) }?.value
            return s?.let { "Магазин: $it." } ?: "Название магазина найти не удалось."
        }
        // 5. количество позиций, самое дорогое / дешёвое
        if (items.isNotEmpty()) {
            if (hasAny(q, "сколько позиций", "сколько товаров", "сколько покупок", "сколько штук")) {
                return "Позиций: ${items.size}."
            }
            val priced = items.filter { it.amount != null }
            if (priced.isNotEmpty() && hasAny(q, "дорог")) {
                val top = priced.maxByOrNull { it.amount ?: 0.0 }!!
                return "Самая дорогая позиция: " + describe(top) + "."
            }
            if (priced.isNotEmpty() && hasAny(q, "дешев", "дешёв")) {
                val low = priced.minByOrNull { it.amount ?: 0.0 }!!
                return "Самая дешёвая позиция: " + describe(low) + "."
            }
        }
        // 6. сдача, оплата, НДС и прочие подписанные поля
        val keyed = listOf(
            "сдач" to "сдач", "оплат" to "оплат", "карт" to "карт", "налич" to "налич",
            "ндс" to "ндс", "налог" to "налог", "скидк" to "скидк"
        )
        for ((askStem, labelStem) in keyed) {
            if (q.contains(askStem)) {
                val f = fields.firstOrNull { it.label.lowercase(Locale("ru")).contains(labelStem) }
                if (f != null) return "${f.label}: ${f.value}."
                val line = r.recognizedText.orEmpty().lines().firstOrNull { norm(it).contains(askStem) }
                if (line != null) return "На документе написано: ${line.trim()}."
                return "Про это на документе ничего не нашлось."
            }
        }

        val ws = words(q)
        // 7. «сколько стоит <товар>» / «есть ли <товар>» — по позициям чека
        if (items.isNotEmpty() && ws.isNotEmpty()) {
            val found = items.filter { item -> ws.any { w -> matches(w, item.name) } }
            if (found.isNotEmpty()) {
                return found.take(3).joinToString(". ") { describe(it) } + "."
            }
        }
        // 8. любое поле документа по подписи
        if (fields.isNotEmpty() && ws.isNotEmpty()) {
            val byLabel = fields.filter { f -> ws.any { w -> matches(w, f.label) } }
            if (byLabel.isNotEmpty()) return byLabel.take(3).joinToString(". ") { "${it.label}: ${it.value}" } + "."
        }
        // 9. поиск слова во всём распознанном тексте
        if (ws.isNotEmpty()) {
            val lines = r.recognizedText.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            val found = lines.filter { line -> ws.any { w -> matches(w, line) } }
            if (found.isNotEmpty()) {
                return "Нашла: " + found.take(3).joinToString(". ") + "."
            }
            return "«${ws.joinToString(" ")}» на документе не нашлось. " + hint(r)
        }
        return "Не поняла вопрос. " + hint(r)
    }

    private fun describe(it: DocumentItem): String = it.spoken.ifBlank {
        it.name + (it.amount?.let { a -> ", ${money(a)}" } ?: "")
    }

    private fun shortSummary(r: DocumentResult): String {
        val parts = ArrayList<String>()
        parts += r.description
        val items = r.items.orEmpty()
        if (r.kind == "receipt" && items.isNotEmpty()) {
            parts += "Позиций: ${items.size}. Первые: " + items.take(3).joinToString(", ") { it.name } + "."
        } else {
            val f = r.fields.orEmpty().take(3)
            if (f.isNotEmpty()) parts += f.joinToString(". ") { "${it.label}: ${it.value}" } + "."
        }
        return parts.joinToString(" ")
    }
}
