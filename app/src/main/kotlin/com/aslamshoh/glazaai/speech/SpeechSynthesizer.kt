package com.aslamshoh.glazaai.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Озвучивание результатов через системный Android TextToSpeech — аналог AVSpeechSynthesizer
 * на iOS (см. GlazaAI-iOS/GlazaAI/Stores/SpeechSynthesizer.swift). init(context) вызывается
 * один раз из GlazaApplication.onCreate().
 */
object SpeechSynthesizer {
    private var tts: TextToSpeech? = null
    private val russian = Locale("ru", "RU")
    private var currentLocale: Locale = russian

    var isSpeaking by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
        tts = engine
    }

    /** Переключает язык синтезатора только при необходимости (английский текст — английским голосом). */
    private fun applyLocale(engine: TextToSpeech, target: Locale) {
        if (currentLocale != target) {
            engine.language = target
            currentLocale = target
        }
    }

    fun speak(text: String, rate: Float, locale: Locale? = null) {
        val engine = tts ?: return
        if (text.isBlank()) return
        applyLocale(engine, locale ?: russian)
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "glazaai_${System.currentTimeMillis()}")
    }

    /** Для живого режима: interrupt=true — прервать текущую речь (критическая опасность),
     * false — дождаться окончания текущей фразы (QUEUE_ADD). */
    fun speakQueued(text: String, rate: Float, interrupt: Boolean) {
        val engine = tts ?: return
        if (text.isBlank()) return
        applyLocale(engine, russian)
        engine.setSpeechRate(rate)
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(text, mode, null, "glazaai_live_${System.currentTimeMillis()}")
        // Если синтезатор не принял фразу (например, ещё не готов), onStart/onError не придут —
        // не оставляем isSpeaking «залипшим» в true, иначе живой режим замолчит насовсем.
        isSpeaking = (result == TextToSpeech.SUCCESS)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }
}
