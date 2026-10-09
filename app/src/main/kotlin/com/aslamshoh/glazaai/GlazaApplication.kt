package com.aslamshoh.glazaai

import android.app.Application
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HelpStore
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.MemoryStore
import com.aslamshoh.glazaai.store.SettingsStore

/** Инициализация синглтонов (настройки/история на SharedPreferences, синтез речи) до того,
 * как их впервые прочитает любой экран Compose. */
class GlazaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        HistoryStore.init(this)
        MemoryStore.init(this)
        HelpStore.init(this)
        SpeechSynthesizer.init(this)
    }
}
