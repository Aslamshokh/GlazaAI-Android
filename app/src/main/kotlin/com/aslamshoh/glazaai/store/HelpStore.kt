package com.aslamshoh.glazaai.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.util.HelpText
import com.aslamshoh.glazaai.util.TrustedContact
import java.util.UUID

/**
 * Данные раздела «Помощь», только на телефоне: анонимный номер устройства (для запросов к серверу),
 * согласие, доверенные контакты и номер экстренной службы. Номера контактов на сервер не уходят.
 */
object HelpStore {
    private lateinit var prefs: SharedPreferences

    private const val KEY_DEVICE = "help.deviceId"
    private const val KEY_CONSENT = "help.consent"
    private const val KEY_CONTACTS = "help.contacts"
    private const val KEY_EMERGENCY = "help.emergency"

    var deviceId by mutableStateOf("")
        private set
    var consent by mutableStateOf(false)
        private set
    var contacts by mutableStateOf<List<TrustedContact>>(emptyList())
        private set
    var emergencyNumber by mutableStateOf(HelpText.DEFAULT_EMERGENCY)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("glazaai_help", Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE, null)
        if (id.isNullOrBlank()) {
            id = "dev-" + UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE, id).apply()
        }
        deviceId = id
        consent = prefs.getBoolean(KEY_CONSENT, false)
        contacts = HelpText.decodeContacts(prefs.getString(KEY_CONTACTS, "") ?: "")
        emergencyNumber = prefs.getString(KEY_EMERGENCY, HelpText.DEFAULT_EMERGENCY) ?: HelpText.DEFAULT_EMERGENCY
    }

    fun giveConsent() {
        consent = true
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
    }

    /** Возвращает текст ошибки или null, если контакт добавлен. */
    fun addContact(name: String, phone: String): String? {
        val (list, error) = HelpText.addContact(contacts, name, phone)
        if (error == null) saveContacts(list)
        return error
    }

    fun removeContact(contact: TrustedContact) {
        saveContacts(contacts.filter { it != contact })
    }

    private fun saveContacts(list: List<TrustedContact>) {
        contacts = list
        prefs.edit().putString(KEY_CONTACTS, HelpText.encodeContacts(list)).apply()
    }

    fun updateEmergencyNumber(value: String) {
        val clean = HelpText.cleanPhone(value).ifEmpty { HelpText.DEFAULT_EMERGENCY }
        emergencyNumber = clean
        prefs.edit().putString(KEY_EMERGENCY, clean).apply()
    }
}
