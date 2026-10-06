package com.aslamshoh.glazaai.store

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import com.aslamshoh.glazaai.nav.NavPoint
import com.aslamshoh.glazaai.util.MemoryText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * «Память вещей»: где человек оставил ключи, очки, телефон. Хранится только на телефоне
 * (SharedPreferences как JSON + небольшие JPEG в папке приложения) и никуда не отправляется.
 * Одна вещь — одна запись: если сказать «запомни ключи здесь» второй раз, старое место заменяется.
 * init(context) вызывается из GlazaApplication.onCreate().
 */
object MemoryStore {
    private lateinit var prefs: SharedPreferences
    private var thumbDir: File? = null
    private val gson = Gson()
    private const val KEY_ITEMS = "glazaai.memory"
    private const val MAX_ITEMS = 100
    private const val THUMB_MAX_SIDE = 480

    private val thumbCache = HashMap<String, Bitmap>()

    /** Самые свежие — сверху; Compose перерисовывается сам при изменениях. */
    val items = mutableStateListOf<MemoryItem>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("glazaai_memory", Context.MODE_PRIVATE)
        thumbDir = File(context.applicationContext.filesDir, "memory_thumbs").also { it.mkdirs() }
        val json = prefs.getString(KEY_ITEMS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<MemoryItem>>() {}.type
                val loaded: List<MemoryItem> = gson.fromJson(json, type) ?: emptyList()
                items.addAll(loaded)
            } catch (e: Exception) {
                // Повреждённые данные не должны ломать запуск — начинаем с пустого списка.
            }
        }
    }

    /** Запоминает вещь (заменяя прежнюю запись о ней) и возвращает созданную запись. */
    fun remember(
        name: String,
        place: String,
        around: String,
        point: NavPoint?,
        accuracyM: Float?,
        photo: Bitmap?
    ): MemoryItem {
        items.filter { MemoryText.sameItem(it.name, name) }.forEach { deleteThumb(it) }
        items.removeAll { MemoryText.sameItem(it.name, name) }

        val id = UUID.randomUUID().toString()
        val item = MemoryItem(
            id = id,
            name = name,
            savedAtMs = System.currentTimeMillis(),
            lat = point?.lat,
            lon = point?.lon,
            accuracyM = if (point != null) accuracyM else null,
            place = place,
            around = around,
            thumb = photo?.let { saveThumb(id, it) }
        )
        items.add(0, item)
        while (items.size > MAX_ITEMS) deleteThumb(items.removeAt(items.size - 1))
        persist()
        return item
    }

    /** Запись о вещи по тому, как её назвал человек («ключей», «мои ключи»), либо null. */
    fun find(query: String): MemoryItem? = items.firstOrNull { MemoryText.sameItem(it.name, query) }

    fun remove(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        deleteThumb(items.removeAt(index))
        persist()
    }

    fun loadThumb(item: MemoryItem): Bitmap? {
        val name = item.thumb ?: return null
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
            FileOutputStream(File(dir, name)).use { out -> small.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            thumbCache[name] = small
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteThumb(item: MemoryItem) {
        val name = item.thumb ?: return
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
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items.toList())).apply()
    }
}
