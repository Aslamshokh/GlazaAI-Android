package com.aslamshoh.glazaai.volunteer.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Локальные данные волонтёра: адрес сервера и токен входа. Номер телефона на устройстве не хранится. */
object VolunteerStore {
    private lateinit var prefs: SharedPreferences
    private const val KEY_URL = "volunteer.serverUrl"
    private const val KEY_TOKEN = "volunteer.token"
    private const val KEY_NAME = "volunteer.name"

    var serverUrl by mutableStateOf("")
        private set
    var token by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("volunteer", Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_URL, "") ?: ""
        token = prefs.getString(KEY_TOKEN, "") ?: ""
        name = prefs.getString(KEY_NAME, "") ?: ""
    }

    fun updateServerUrl(value: String) {
        serverUrl = value.trim()
        prefs.edit().putString(KEY_URL, serverUrl).apply()
    }

    fun saveLogin(token: String, name: String) {
        this.token = token
        this.name = name
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_NAME, name).apply()
    }

    fun clearLogin() {
        token = ""
        prefs.edit().putString(KEY_TOKEN, "").apply()
    }

    val isLoggedIn: Boolean get() = token.isNotEmpty()
}
