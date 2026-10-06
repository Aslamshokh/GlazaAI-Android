package com.aslamshoh.glazaai.util

/**
 * Сопоставление запроса «ключи» / «мою кружку» с названием найденного предмета («кружка»).
 * Русские слова меняются по падежам, поэтому сравниваем по основе — началу слова без
 * последних двух букв (но не короче трёх). Чисто текстовая логика, без Android.
 */
object FindMatch {
    private val skip = setOf("мой", "моя", "мои", "моё", "мою", "моего", "мне", "найди", "найти", "где", "the", "my")

    fun stems(query: String): List<String> =
        query.lowercase().replace('ё', 'е')
            .split(Regex("[^а-яa-z0-9]+"))
            .filter { it.length >= 2 && it !in skip }
            .map { it.take(maxOf(3, it.length - 2)) }

    /** Подходит ли предмет с русским названием label (и английским labelEn) под запрос. */
    fun matches(query: String, label: String, labelEn: String = ""): Boolean {
        val stems = stems(query)
        if (stems.isEmpty()) return false
        val ru = label.lowercase().replace('ё', 'е')
        val en = labelEn.lowercase()
        return stems.any { s -> ru.contains(s) || en.contains(s) }
    }
}
