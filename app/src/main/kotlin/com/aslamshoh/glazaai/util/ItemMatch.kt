package com.aslamshoh.glazaai.util

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Сопоставление названий вещей: «ключи» ~ «ключей», «мою рубашку» -> «рубашку». */
object ItemMatch {
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
}
