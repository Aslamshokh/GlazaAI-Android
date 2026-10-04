package com.aslamshoh.glazaai.nav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Голосовой ввод через системный SpeechRecognizer (русский). Вызывать только с главного потока.
 * Если в телефоне нет службы распознавания речи (бывает на телефонах без сервисов Google),
 * isAvailable == false — экран тогда предлагает ввод текстом.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** onResult получает лучший вариант фразы или null (ничего не сказали / не распознали). */
    fun listen(onResult: (String?) -> Unit) {
        cancel()
        if (!isAvailable) {
            onResult(null)
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        partial = ""
        var delivered = false
        fun deliver(text: String?) {
            if (delivered) return
            delivered = true
            listening = false
            onResult(text?.trim()?.takeIf { it.isNotEmpty() })
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { deliver(null) }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                deliver(list?.firstOrNull())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        r.startListening(intent)
    }

    fun cancel() {
        listening = false
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
                // уже уничтожен — ничего страшного
            }
        }
        recognizer = null
    }
}
