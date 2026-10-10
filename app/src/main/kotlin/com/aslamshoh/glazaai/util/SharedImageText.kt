package com.aslamshoh.glazaai.util

/**
 * Что сказать про картинку, которой поделились из другого приложения (мессенджер, галерея, браузер):
 * сначала описание того, что на ней, потом — текст, если он есть. Чистая логика — проверяется тестами.
 */
object SharedImageText {
    /** Считаем, что на картинке есть осмысленный текст, если в нём хотя бы 12 букв. */
    fun hasText(ocr: String?): Boolean = ocr.orEmpty().count { it.isLetter() } >= 12

    fun compose(scene: String?, ocr: String?): String {
        val parts = ArrayList<String>()
        val s = scene?.trim().orEmpty()
        if (s.isNotEmpty()) parts += s
        if (hasText(ocr)) parts += "На картинке есть текст: " + ocr!!.trim().replace(Regex("\\s*\\n\\s*"), ". ")
        else if (s.isEmpty()) parts += "Не удалось разобрать картинку. Попробуйте поделиться другой."
        return parts.joinToString(" ")
    }
}
