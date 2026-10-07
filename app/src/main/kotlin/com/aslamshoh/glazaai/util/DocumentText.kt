package com.aslamshoh.glazaai.util

import com.aslamshoh.glazaai.network.DocumentResult

/** Что произнести для разных кнопок экрана «Чек / Документ». Чистая логика — проверяется тестами. */
object DocumentText {
    fun total(r: DocumentResult): String {
        val field = r.fields.orEmpty().firstOrNull { it.label.equals("Итого", true) || it.label.equals("Сумма", true) }
        return when {
            field != null -> "${field.label}: ${field.value}."
            r.kind == "receipt" -> "Итог на чеке найти не удалось. Снимите ближе, чтобы была видна нижняя часть чека."
            else -> "Сумма в документе не найдена."
        }
    }

    fun items(r: DocumentResult): String {
        val items = r.items.orEmpty()
        if (items.isEmpty()) return "Позиции прочитать не удалось."
        val head = "Позиций: ${items.size}. "
        return head + items.mapIndexed { i, it -> "${i + 1}. ${it.spoken.ifBlank { it.name }}" }.joinToString(". ") + "."
    }

    fun fields(r: DocumentResult): String {
        val f = r.fields.orEmpty()
        if (f.isEmpty()) return r.description
        return f.joinToString(". ") { "${it.label}: ${it.value}" } + "."
    }

    /** Полный текст для озвучки: сводка + позиции (чек) либо поля (документ) + предупреждения. */
    fun readAll(r: DocumentResult): String {
        val parts = ArrayList<String>()
        parts += r.description
        when {
            r.kind == "receipt" && !r.items.isNullOrEmpty() -> parts += items(r)
            r.kind == "document" && !r.fields.isNullOrEmpty() -> parts += fields(r)
        }
        r.warnings.orEmpty().drop(if (r.kind == "receipt") 1 else 0).forEach { parts += it }
        return parts.joinToString(" ")
    }

    /** Что сказать сразу после съёмки: короткая сводка и подсказка, что можно спросить ещё. */
    fun afterCapture(r: DocumentResult): String {
        val hint = when {
            r.kind == "receipt" && !r.items.isNullOrEmpty() -> " Нажмите «Позиции», чтобы услышать список покупок."
            r.kind == "document" && !r.fields.isNullOrEmpty() -> " Нажмите «Все поля», чтобы услышать остальное."
            else -> ""
        }
        return r.description + hint
    }
}
