package com.aslamshoh.glazaai.network

import com.aslamshoh.glazaai.store.SettingsStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ошибки сети/backend — формулировки зеркалят APIError из iOS-версии, чтобы поведение
 * и тексты совпадали на обеих платформах. */
sealed class ApiException(message: String) : Exception(message) {
    object NotConfigured : ApiException("Адрес backend не настроен. Откройте Настройки и укажите адрес сервера.")
    object Network : ApiException("Не удалось связаться с backend. Проверьте, что сервер запущен и доступен.")
    class Server(val status: Int, val msg: String) : ApiException(msg)
    object Decoding : ApiException("Backend вернул неожиданный ответ.")
    object InvalidUrl : ApiException("Некорректный адрес backend в Настройках.")
}

/**
 * Простой клиент к собственному backend GLAZA AI (тот же backend, что использует web- и
 * iOS-версии — glaza-ai-backend/). Секретные ключи ИИ-провайдеров здесь не хранятся: только
 * адрес backend и общий X-API-Key (см. BACKEND_API_KEY в .env backend).
 */
object ApiClient {
    // Не private: обращаются из public inline-функций execute()/post()/get() ниже — Kotlin
    // запрещает public inline-функциям обращаться к private-членам (нарушение "public API inline").
    val gson = Gson()
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Тоже не private — см. комментарий у execute() выше.
    fun buildUrl(path: String): String {
        val base = SettingsStore.backendBaseUrl.trim()
        if (base.isEmpty()) throw ApiException.NotConfigured
        val normalized = if (base.endsWith("/")) base.dropLast(1) else base
        val url = normalized + path
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw ApiException.InvalidUrl
        return url
    }

    fun newRequestBuilder(path: String): Request.Builder {
        val builder = Request.Builder().url(buildUrl(path))
        val apiKey = SettingsStore.backendApiKey.trim()
        if (apiKey.isNotEmpty()) {
            builder.addHeader("X-API-Key", apiKey)
        }
        return builder
    }

    // Не private: вызывается из public inline-функций post()/get() ниже — Kotlin запрещает
    // public inline-функциям обращаться к private-членам (нарушение "public API inline").
    inline fun <reified T> execute(request: Request): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException.Network
        }

        response.use { resp ->
            val bodyString = try {
                resp.body?.string() ?: ""
            } catch (e: IOException) {
                throw ApiException.Network
            }

            if (!resp.isSuccessful) {
                val detail = try {
                    gson.fromJson(bodyString, ErrorResponse::class.java)?.detail
                } catch (e: Exception) {
                    null
                }
                throw ApiException.Server(resp.code, detail ?: "Ошибка сервера: ${resp.code}")
            }

            return try {
                gson.fromJson(bodyString, T::class.java) ?: throw ApiException.Decoding
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException.Decoding
            }
        }
    }

    suspend inline fun <reified T> post(path: String, body: Any): T = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder(path).post(requestBody).build()
        execute<T>(request)
    }

    suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = newRequestBuilder(path).get().build()
        execute<T>(request)
    }

    /** Текст ошибки для показа пользователю и озвучивания — единая точка форматирования. */
    fun messageFor(error: Throwable): String = when (error) {
        is ApiException -> error.message ?: "Неизвестная ошибка."
        else -> error.message ?: "Неизвестная ошибка."
    }
}
