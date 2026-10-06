package com.aslamshoh.glazaai.store

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.UUID

data class HistoryEntry(
    val id: String,
    val module: String,
    val title: String,
    val description: String,
    val timestampMs: Long,
    /** Имя файла миниатюры в папке history_thumbs (null — снимка нет). */
    val thumb: String? = null
)

/**
 * Локальная история распознаваний — хранится на устройстве (SharedPreferences, как JSON, плюс
 * маленькие JPEG-миниатюры в папке приложения), никуда не отправляется. Зеркалит
 * GlazaAI-iOS/GlazaAI/Stores/HistoryStore.swift. init(context) вызывается один раз из
 * GlazaApplication.onCreate().
 */
object HistoryStore {
    private lateinit var prefs: SharedPreferences
    private var thumbDir: File? = null
    private val gson = Gson()
    private const val KEY_ENTRIES = "glazaai.history"
    private const val MAX_ENTRIES = 200
    private const val THUMB_MAX_SIDE = 192

    private val thumbCache = HashMap<String, Bitmap>()

    /** mutableStateListOf — экраны Compose, читающие entries, перерисовываются сами при
     * добавлении/очистке записей. Новые записи вставляются в начало (самые свежие сверху). */
    val entries = mutableStateListOf<HistoryEntry>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("glazaai_history", Context.MODE_PRIVATE)
        thumbDir = File(context.applicationContext.filesDir, "history_thumbs").also { it.mkdirs() }
        val json = prefs.getString(KEY_ENTRIES, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<HistoryEntry>>() {}.type
                val loaded: List<HistoryEntry> = gson.fromJson(json, type) ?: emptyList()
                entries.addAll(loaded)
            } catch (e: Exception) {
                // Повреждённая история не должна ломать запуск приложения — просто начинаем с пустой.
            }
        }
    }

    fun addEntry(module: String, title: String, description: String, thumbnail: Bitmap? = null) {
        val id = UUID.randomUUID().toString()
        val entry = HistoryEntry(
            id = id,
            module = module,
            title = title,
            description = description,
            timestampMs = System.currentTimeMillis(),
            thumb = thumbnail?.let { saveThumb(id, it) }
        )
        entries.add(0, entry)
        while (entries.size > MAX_ENTRIES) {
            deleteThumb(entries.removeAt(entries.size - 1))
        }
        persist()
    }

    fun remove(id: String) {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        deleteThumb(entries.removeAt(index))
        persist()
    }

    fun clear() {
        entries.forEach { deleteThumb(it) }
        entries.clear()
        persist()
    }

    /** Миниатюра записи (или null). Небольшие JPEG читаются с диска один раз и кэшируются. */
    fun loadThumb(entry: HistoryEntry): Bitmap? {
        val name = entry.thumb ?: return null
        thumbCache[name]?.let { return it }
        val dir = thumbDir ?: return null
        val file = File(dir, name)
        if (!file.exists()) return null
        val bmp = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
        if (bmp != null) thumbCache[name] = bmp
        return bmp
    }

    /** Сколько записей сделано сегодня (для счётчика на экране профиля). */
    fun countToday(): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return entries.count { it.timestampMs >= start }
    }

    private fun saveThumb(id: String, source: Bitmap): String? {
        val dir = thumbDir ?: return null
        return try {
            val scale = THUMB_MAX_SIDE.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }
            val name = "$id.jpg"
            FileOutputStream(File(dir, name)).use { out ->
                small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            thumbCache[name] = small
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteThumb(entry: HistoryEntry) {
        val name = entry.thumb ?: return
        thumbCache.remove(name)
        val dir = thumbDir ?: return
        try {
            File(dir, name).delete()
        } catch (e: Exception) {
            // не критично
        }
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_ENTRIES, gson.toJson(entries.toList())).apply()
    }
}
