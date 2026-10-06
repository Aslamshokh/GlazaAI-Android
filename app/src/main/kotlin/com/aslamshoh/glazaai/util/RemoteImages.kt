package com.aslamshoh.glazaai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Загрузка маленькой картинки по ссылке (фото товара). Без сторонних библиотек. */
object RemoteImages {
    suspend fun load(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 8000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Пояснения к самым частым табличкам и надписям — для кнопки «Объяснить» на экране «Текст».
 * Это встроенный справочник (не нейросеть): если надписи в нём нет, приложение так и говорит.
 */
object SignGlossary {
    private val entries = listOf(
        listOf("выход", "exit") to "Выход из здания. При пожаре двигайтесь к таким указателям.",
        listOf("вход", "entrance", "entry") to "Вход в здание или помещение.",
        listOf("от себя", "push") to "Дверь открывается от себя — толкните её.",
        listOf("на себя", "pull") to "Дверь открывается на себя — потяните её.",
        listOf("стоп", "stop") to "Требование остановиться. Дальше проходить нельзя.",
        listOf("осторожно", "caution", "warning", "attention") to "Предупреждение об опасности рядом. Двигайтесь медленно.",
        listOf("опасно", "danger") to "Опасная зона. Лучше не заходить.",
        listOf("не входить", "no entry", "do not enter") to "Входить запрещено.",
        listOf("посторонним вход воспрещён", "staff only", "authorized personnel only") to "Только для сотрудников.",
        listOf("аптека", "pharmacy") to "Аптека — здесь продают лекарства.",
        listOf("туалет", "wc", "toilet", "restroom") to "Туалет.",
        listOf("касса", "cashier", "checkout") to "Касса — здесь платят за покупки.",
        listOf("информация", "information") to "Место, где можно получить справку.",
        listOf("лифт", "elevator", "lift") to "Лифт.",
        listOf("лестница", "stairs") to "Лестница. Будьте осторожны на ступенях.",
        listOf("открыто", "open") to "Заведение открыто.",
        listOf("закрыто", "closed") to "Заведение закрыто.",
        listOf("скидка", "sale") to "Товары продаются дешевле обычного.",
        listOf("не курить", "no smoking") to "Курить запрещено.",
        listOf("пожарный выход", "fire exit") to "Запасной выход на случай пожара."
    )

    fun explain(text: String): String? {
        val t = text.lowercase().replace('ё', 'е')
        return entries.firstOrNull { (keys, _) -> keys.any { t.contains(it) } }?.second
    }
}
