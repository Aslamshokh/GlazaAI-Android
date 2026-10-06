package com.aslamshoh.glazaai.store

/**
 * Запомненная вещь: «ключи — вот здесь». Чистая модель без Android — её же проверяют юнит-тесты.
 *
 * lat/lon/accuracyM — положение по GPS в момент запоминания (null, если GPS не поймался: дома
 * он часто молчит — тогда остаются описание и фото). place — то, что человек сам сказал про
 * место («на кухонном столе»). around — что камера увидела рядом («стол по центру, чашка слева»).
 */
data class MemoryItem(
    val id: String,
    val name: String,
    val savedAtMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val place: String = "",
    val around: String = "",
    /** Имя файла снимка в папке memory_thumbs (null — снимка нет). */
    val thumb: String? = null
)
