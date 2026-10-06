package com.aslamshoh.glazaai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Озвучивание результатов через системный Android TextToSpeech — аналог AVSpeechSynthesizer
 * на iOS (см. GlazaAI-iOS/GlazaAI/Stores/SpeechSynthesizer.swift). init(context) вызывается
 * один раз из GlazaApplication.onCreate().
 *
 * Результаты (speak) по возможности озвучивает естественный нейроголос: текст отправляется на
 * backend (/tts), тот возвращает mp3, телефон его проигрывает; готовые озвучки кэшируются на
 * телефоне. Если backend недоступен, медленно отвечает или голос выключен в настройках —
 * говорит встроенный голос телефона. Срочные подсказки живого режима и навигации (speakQueued)
 * по-прежнему идут встроенным голосом: он отвечает мгновенно.
 */
object SpeechSynthesizer {
    private var tts: TextToSpeech? = null
    private val russian = Locale("ru", "RU")
    private var currentLocale: Locale = russian

    var isSpeaking by mutableStateOf(false)
        private set

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var neuralJob: Job? = null
    private var player: MediaPlayer? = null
    private var requestId = 0

    fun init(context: Context) {
        appContext = context.applicationContext
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
        if (text.isBlank()) return
        if (SettingsStore.neuralVoice && SettingsStore.isBackendConfigured && appContext != null) {
            speakNeural(text, rate, locale)
        } else {
            speakSystem(text, rate, locale)
        }
    }

    /** Встроенный голос телефона. */
    private fun speakSystem(text: String, rate: Float, locale: Locale?) {
        val engine = tts ?: return
        if (text.isBlank()) return
        applyLocale(engine, locale ?: russian)
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "glazaai_${System.currentTimeMillis()}")
    }

    /** Естественный голос: ждём озвучку от backend (до ~12 с), при любой неудаче — встроенный голос. */
    private fun speakNeural(text: String, rate: Float, locale: Locale?) {
        stop()
        val id = requestId
        isSpeaking = true
        neuralJob = scope.launch {
            val file = try {
                fetchAudio(text, rate, locale)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (id != requestId) return@launch
            if (file == null) {
                speakSystem(text, rate, locale)
            } else {
                play(file, id) { speakSystem(text, rate, locale) }
            }
        }
    }

    private suspend fun fetchAudio(text: String, rate: Float, locale: Locale?): File? {
        val context = appContext ?: return null
        val lang = if (locale?.language == "en") "en" else "ru"
        val gender = SettingsStore.voiceGender
        val rateKey = Math.round(rate * 10)
        return withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "tts").apply { mkdirs() }
            val digest = MessageDigest.getInstance("SHA-256").digest("$lang|$gender|$rateKey|$text".toByteArray())
            val name = digest.joinToString("") { "%02x".format(it) }.take(40)
            val file = File(folder, "$name.mp3")
            if (file.exists() && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis())
                return@withContext file
            }
            val bytes = ApiClient.postForBytes("/tts", TtsRequestBody(text, lang, gender, rate))
            if (bytes.isEmpty()) return@withContext null
            file.writeBytes(bytes)
            // Кэш на телефоне небольшой: храним последние 60 озвучек.
            val files = folder.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            files.take((files.size - 60).coerceAtLeast(0)).forEach { it.delete() }
            file
        }
    }

    private fun play(file: File, id: Int, fallback: () -> Unit) {
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                if (id == requestId) {
                    isSpeaking = false
                    releasePlayer()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                if (id == requestId) {
                    releasePlayer()
                    file.delete()
                    fallback()
                }
                true
            }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            file.delete()
            fallback()
        }
    }

    private fun releasePlayer() {
        player?.let {
            try {
                it.release()
            } catch (e: Exception) {
                // уже освобождён
            }
        }
        player = null
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
        requestId++
        neuralJob?.cancel()
        neuralJob = null
        releasePlayer()
        tts?.stop()
        isSpeaking = false
    }
}

/** Тело запроса к backend /tts (имена полей — как в app/routers/tts.py). */
data class TtsRequestBody(val text: String, val lang: String, val gender: String, val rate: Float)
