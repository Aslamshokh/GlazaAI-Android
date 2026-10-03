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

    fun speak(text: String, rate: Float) {
        val engine = tts ?: return
        if (text.isBlank()) return
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "glazaai_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }
}
