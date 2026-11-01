package com.aslamshoh.glazaai.util

import com.aslamshoh.glazaai.network.ReadBlock

/**
 * Озвучка текста со структурой — та же логика, что у backend (app/services/layout.py), чтобы
 * переключать «по строкам / по столбцам» мгновенно, без повторной отправки фото.
 *
 * Таблица по строкам (по умолчанию): сначала названия столбцов, потом
 * «Строка 1: Имя — Иван, Возраст — 12». По столбцам: «Столбец Возраст: 12, 14».
 */
object ReadSpeech {
    private val paren = Regex("\\(([^()]{1,200})\\)")
    private val bracket = Regex("\\[([^\\[\\]]{1,200})]")

    /** Делает скобки слышимыми: «вода (500 мл)» → «вода, в скобках: 500 мл». */
    fun punct(text: String): String {
        var t = paren.replace(text) { ", в скобках: ${it.groupValues[1].trim()}," }
        t = bracket.replace(t) { ", в квадратных скобках: ${it.groupValues[1].trim()}," }
        t = t.replace(Regex(",\\s*,"), ",").replace(Regex(",\\s*([.!?])"), "$1")
        return t.trim(' ', ',')
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }

    fun table(headers: List<String>, rows: List<List<String>>, mode: String): String {
        val parts = mutableListOf(
            "Таблица: ${headers.size} ${plural(headers.size, "столбец", "столбца", "столбцов")}, " +
                "${rows.size} ${plural(rows.size, "строка", "строки", "строк")}.",
            "Столбцы: " + headers.joinToString(", ") + "."
        )
        if (mode == "columns") {
            headers.forEachIndexed { i, h ->
                val values = rows.map { r -> r.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "пусто" }
                parts += "Столбец $h: " + values.joinToString(", ") + "."
            }
        } else {
            rows.forEachIndexed { n, r ->
                val cells = headers.mapIndexed { i, h -> "$h — " + (r.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "пусто") }
                parts += "Строка ${n + 1}: " + cells.joinToString(", ") + "."
            }
        }
        return parts.joinToString(" ") { punct(it) }
    }

    fun block(b: ReadBlock, mode: String): String = when (b.type) {
        "table" -> table(b.headers.orEmpty(), b.rows.orEmpty(), mode)
        "list" -> {
            val items = b.items.orEmpty()
            val top = items.count { (it.level ?: 1) == 1 }.takeIf { it > 0 } ?: items.size
            val out = mutableListOf("Список из $top ${plural(top, "пункта", "пунктов", "пунктов")}.")
            var k = 0
            items.forEach {
                if ((it.level ?: 1) == 1) {
                    k++
                    out += "Пункт $k: ${punct(it.text)}."
                } else {
                    out += "Подпункт: ${punct(it.text)}."
                }
            }
            out.joinToString(" ")
        }
        "heading" -> "Заголовок: ${punct(b.text)}."
        else -> punct(b.text)
    }

    fun all(blocks: List<ReadBlock>, mode: String): String = blocks.joinToString("\n") { block(it, mode) }

    /** Короткое название блока для кнопок «Дальше / Назад». */
    fun kind(b: ReadBlock): String = when (b.type) {
        "table" -> "таблица"
        "list" -> "список"
        "heading" -> "заголовок"
        else -> "абзац"
    }
}
