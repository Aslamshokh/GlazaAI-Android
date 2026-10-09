package com.aslamshoh.glazaai.volunteer.net

import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, message: String) : Exception(message)

private data class ErrorBody(val detail: String? = null)
private data class OtpBody(val phone: String)
private data class VerifyBody(val phone: String, val code: String, val name: String, val languages: List<String>)
private data class OnlineBody(val online: Boolean)
private data class ReasonBody(val reason: String)
private object Empty

/** Минимальный клиент к серверу помощи. Все вызовы — suspend, выполняются в IO. */
object VolunteerApi {
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String {
        val base = VolunteerStore.serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw ApiError(0, "Укажите адрес сервера.")
        if (!base.startsWith("http://") && !base.startsWith("https://")) throw ApiError(0, "Адрес сервера должен начинаться с http:// или https://")
        return base + path
    }

    private fun <T> run(method: String, path: String, body: Any?, auth: Boolean, cls: Class<T>): T {
        val builder = Request.Builder().url(url(path))
        if (auth) builder.addHeader("Authorization", "Bearer " + VolunteerStore.token)
        if (method == "GET") builder.get() else builder.post(gson.toJson(body ?: Empty).toRequestBody(json))
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiError(-1, "Нет связи с сервером. Проверьте интернет и адрес сервера.")
        }
        response.use { r ->
            val text = try { r.body?.string() ?: "" } catch (e: IOException) { throw ApiError(-1, "Нет связи с сервером.") }
            if (!r.isSuccessful) {
                val detail = try { gson.fromJson(text, ErrorBody::class.java)?.detail } catch (e: Exception) { null }
                throw ApiError(r.code, detail ?: "Ошибка сервера: ${r.code}")
            }
            return gson.fromJson(text, cls) ?: throw ApiError(-2, "Сервер вернул неожиданный ответ.")
        }
    }

    suspend fun requestCode(phone: String): OtpResponse =
        withContext(Dispatchers.IO) { run("POST", "/help/volunteers/otp", OtpBody(phone), false, OtpResponse::class.java) }

    suspend fun verify(phone: String, code: String, name: String, languages: List<String>): VerifyResponse =
        withContext(Dispatchers.IO) {
            run("POST", "/help/volunteers/verify", VerifyBody(phone, code.trim(), name.trim(), languages), false, VerifyResponse::class.java)
        }

    suspend fun me(): VolunteerProfile =
        withContext(Dispatchers.IO) { run("GET", "/help/volunteers/me", null, true, VolunteerProfile::class.java) }

    suspend fun setOnline(online: Boolean): VolunteerProfile =
        withContext(Dispatchers.IO) { run("POST", "/help/volunteers/me/online", OnlineBody(online), true, VolunteerProfile::class.java) }

    suspend fun incoming(): IncomingResponse =
        withContext(Dispatchers.IO) { run("GET", "/help/volunteers/me/incoming", null, true, IncomingResponse::class.java) }

    suspend fun accept(requestId: Int): AcceptResponse =
        withContext(Dispatchers.IO) { run("POST", "/help/requests/$requestId/accept", null, true, AcceptResponse::class.java) }

    suspend fun decline(requestId: Int): OkResponse =
        withContext(Dispatchers.IO) { run("POST", "/help/requests/$requestId/decline", null, true, OkResponse::class.java) }

    suspend fun finish(requestId: Int): OkResponse =
        withContext(Dispatchers.IO) { run("POST", "/help/requests/$requestId/finish", null, true, OkResponse::class.java) }

    suspend fun report(requestId: Int, reason: String): OkResponse =
        withContext(Dispatchers.IO) { run("POST", "/help/requests/$requestId/report-by-volunteer", ReasonBody(reason), true, OkResponse::class.java) }
}
