package com.aslamshoh.glazaai.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class HistoryEntry(
    val id: String,
    val module: String,
    val title: String,
    val description: String,
    val timestampMs: Long
)

/**
 * Локальная история распознаваний — хранится на устройстве (SharedPreferences, как JSON),
 * никуда не отправляется. Зеркалит GlazaAI-iOS/GlazaAI/Stores/HistoryStore.swift.
 * init(context) вызывается один раз из GlazaApplication.onCreate().
 */
object HistoryStore {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val KEY_ENTRIES = "glazaai.history"
    private const val MAX_ENTRIES = 200

    /** mutableStateListOf — экраны Compose, читающие entries, перерисовываются сами при
     * добавлении/очистке записей. Новые записи вставляются в начало (самые свежие сверху). */
    val entries = mutableStateListOf<HistoryEntry>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("glazaai_history", Context.MODE_PRIVATE)
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

    fun addEntry(module: String, title: String, description: String) {
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            module = module,
            title = title,
            description = description,
            timestampMs = System.currentTimeMillis()
        )
        entries.add(0, entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }
        persist()
    }

    fun clear() {
        entries.clear()
        persist()
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_ENTRIES, gson.toJson(entries.toList())).apply()
    }
}
