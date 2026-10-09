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

    // ── настройки и локальные данные (всё хранится только на телефоне) ──
    /** Фото профиля: файл во внутренней папке приложения, на сервер не отправляется. */
    var avatarPath by mutableStateOf("")
        private set
    var notificationsOn by mutableStateOf(true)
        private set
    var soundOn by mutableStateOf(true)
        private set
    var emergencyNumber by mutableStateOf("112")
        private set
    var trustedName by mutableStateOf("")
        private set
    var trustedPhone by mutableStateOf("")
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("volunteer", Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_URL, "") ?: ""
        token = prefs.getString(KEY_TOKEN, "") ?: ""
        name = prefs.getString(KEY_NAME, "") ?: ""
        avatarPath = prefs.getString("avatar", "") ?: ""
        notificationsOn = prefs.getBoolean("notifOn", true)
        soundOn = prefs.getBoolean("soundOn", true)
        emergencyNumber = prefs.getString("emergency", "112") ?: "112"
        trustedName = prefs.getString("trustedName", "") ?: ""
        trustedPhone = prefs.getString("trustedPhone", "") ?: ""
    }

    fun setAvatar(path: String) { avatarPath = path; prefs.edit().putString("avatar", path).apply() }
    fun changeNotificationsOn(v: Boolean) { notificationsOn = v; prefs.edit().putBoolean("notifOn", v).apply() }
    fun changeSoundOn(v: Boolean) { soundOn = v; prefs.edit().putBoolean("soundOn", v).apply() }
    fun changeEmergencyNumber(v: String) { emergencyNumber = v.filter { it.isDigit() || it == '+' }.take(6); prefs.edit().putString("emergency", emergencyNumber).apply() }
    fun setTrusted(name: String, phone: String) {
        trustedName = name.trim().take(30)
        trustedPhone = phone.filter { it.isDigit() || it == '+' }.take(16)
        prefs.edit().putString("trustedName", trustedName).putString("trustedPhone", trustedPhone).apply()
    }

    // списки хранятся одной строкой JSON (формат — в VolText)
    fun getString(key: String): String = prefs.getString(key, "") ?: ""
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun remove(key: String) { prefs.edit().remove(key).apply() }

    fun updateServerUrl(value: String) {
        serverUrl = value.trim()
        prefs.edit().putString(KEY_URL, serverUrl).apply()
    }

    fun saveLogin(token: String, name: String) {
        this.token = token
        this.name = name
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_NAME, name).apply()
    }

    /** Выход из аккаунта: токен и личные локальные данные (фото, уведомления, чаты, контакт) стираются. */
    fun clearLogin() {
        token = ""
        name = ""
        avatarPath = ""
        trustedName = ""
        trustedPhone = ""
        val keep = serverUrl
        prefs.edit().clear().putString(KEY_URL, keep).apply()
    }

    val isLoggedIn: Boolean get() = token.isNotEmpty()
}
