package com.aslamshoh.glazaai.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Настройки приложения — хранятся локально на устройстве (SharedPreferences), не уходят ни на
 * какой сервер. Зеркалит GlazaAI-iOS/GlazaAI/Stores/AppSettingsStore.swift: адрес backend и
 * X-API-Key пользователь вводит один раз в Настройках, так как у каждого свой self-hosted сервер.
 *
 * init(context) вызывается один раз из GlazaApplication.onCreate(), дальше объект используется
 * как синглтон (аналог "AppSettingsStore.shared" на iOS) — состояния объявлены через
 * mutableStateOf, поэтому экраны Compose, читающие их, автоматически перерисовываются.
 */
object SettingsStore {
    private lateinit var prefs: SharedPreferences

    private const val KEY_BACKEND_URL = "glazaai.backendBaseURL"
    private const val KEY_API_KEY = "glazaai.backendAPIKey"
    private const val KEY_SPEECH_RATE = "glazaai.speechRate"
    private const val KEY_HAPTICS = "glazaai.hapticsEnabled"
    private const val KEY_HIGH_CONTRAST = "glazaai.highContrast"

    var backendBaseUrl by mutableStateOf("")
        private set
    var backendApiKey by mutableStateOf("")
        private set
    var speechRate by mutableFloatStateOf(1.0f)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    var highContrast by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("glazaai_settings", Context.MODE_PRIVATE)
        backendBaseUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        backendApiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    }

    fun setBackendBaseUrl(value: String) {
        backendBaseUrl = value
        prefs.edit().putString(KEY_BACKEND_URL, value).apply()
    }

    fun setBackendApiKey(value: String) {
        backendApiKey = value
        prefs.edit().putString(KEY_API_KEY, value).apply()
    }

    fun setSpeechRate(value: Float) {
        speechRate = value
        prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()
    }

    fun setHapticsEnabled(value: Boolean) {
        hapticsEnabled = value
        prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    fun setHighContrast(value: Boolean) {
        highContrast = value
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()
    }

    val isBackendConfigured: Boolean
        get() = backendBaseUrl.trim().isNotEmpty()
}
