package com.aslamshoh.glazaai.util

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Перевод RU ⇄ EN прямо на телефоне (Google ML Kit On-Device Translation): бесплатно, без
 * нашего сервера. Языковые модели (~30 МБ каждая) скачиваются один раз — после этого перевод
 * работает без интернета. Скачать заранее можно в Профиль → «Скачать офлайн-модели».
 */
object Translator {
    const val RU = "ru"
    const val EN = "en"

    fun isCyrillic(text: String): Boolean {
        var cyr = 0
        var lat = 0
        for (ch in text) {
            if (ch in 'А'..'я' || ch == 'ё' || ch == 'Ё') cyr++
            else if (ch in 'A'..'Z' || ch in 'a'..'z') lat++
        }
        return cyr >= lat && cyr > 0
    }

    private fun mlKitCode(lang: String): String =
        if (lang == RU) TranslateLanguage.RUSSIAN else TranslateLanguage.ENGLISH

    /** Переводит text с языка from на to (коды "ru"/"en"). Бросает исключение, если модели нет
     * и нет интернета для её первой загрузки. */
    suspend fun translate(text: String, from: String, to: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlKitCode(from))
            .setTargetLanguage(mlKitCode(to))
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    /** Скачана ли языковая модель. */
    suspend fun isModelDownloaded(lang: String): Boolean {
        val model = TranslateRemoteModel.Builder(mlKitCode(lang)).build()
        return RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }

    suspend fun downloadModel(lang: String) {
        val model = TranslateRemoteModel.Builder(mlKitCode(lang)).build()
        RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build()).await()
    }

    suspend fun deleteModel(lang: String) {
        val model = TranslateRemoteModel.Builder(mlKitCode(lang)).build()
        RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
    }
}

/** Ждёт результата Task из Google Play Services / ML Kit без дополнительных библиотек. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
}
