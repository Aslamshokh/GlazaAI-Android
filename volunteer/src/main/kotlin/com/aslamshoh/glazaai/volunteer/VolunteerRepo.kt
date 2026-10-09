package com.aslamshoh.glazaai.volunteer

import com.aslamshoh.glazaai.volunteer.net.AcceptResponse
import com.aslamshoh.glazaai.volunteer.net.ApiError
import com.aslamshoh.glazaai.volunteer.net.Offer
import com.aslamshoh.glazaai.volunteer.net.VolunteerApi
import com.aslamshoh.glazaai.volunteer.net.VolunteerProfile
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Принятый вызов, который сейчас идёт. */
data class ActiveCall(val requestId: Int, val roomUrl: String, val userName: String, val language: String, val urgent: Boolean, val lat: Double?, val lon: Double?)

/**
 * Общее состояние приложения волонтёра. Экран и служба «на связи» читают и меняют одно и то же,
 * поэтому всё лежит в одном синглтоне со StateFlow.
 */
object VolunteerRepo {
    private val _profile = MutableStateFlow<VolunteerProfile?>(null)
    val profile: StateFlow<VolunteerProfile?> = _profile

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers: StateFlow<List<Offer>> = _offers

    private val _call = MutableStateFlow<ActiveCall?>(null)
    val call: StateFlow<ActiveCall?> = _call

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun showMessage(text: String?) { _message.value = text }

    /** Возвращает тестовый код, если сервер в режиме проверки (HELP_DEV_OTP). */
    suspend fun requestCode(phone: String): String? = VolunteerApi.requestCode(phone).devCode

    suspend fun login(phone: String, code: String, name: String, languages: List<String>) {
        val r = VolunteerApi.verify(phone, code, name, languages)
        VolunteerStore.saveLogin(r.token, r.profile?.name ?: name)
        _profile.value = r.profile
    }

    suspend fun refreshProfile() {
        try {
            _profile.value = VolunteerApi.me()
        } catch (e: ApiError) {
            handleAuth(e)
        }
    }

    suspend fun setOnline(on: Boolean) {
        try {
            _profile.value = VolunteerApi.setOnline(on)
            if (!on) _offers.value = emptyList()
        } catch (e: ApiError) {
            handleAuth(e)
            throw e
        }
    }

    /** Один опрос сервера (его делает служба каждые ~3 секунды; он же «пульс» «на связи»). */
    suspend fun poll(): List<Offer> {
        try {
            val r = VolunteerApi.incoming()
            val list = r.offers ?: emptyList()
            _offers.value = list
            // Пользователь сам положил трубку — закрываем экран вызова.
            if (_call.value != null && r.activeRequestId == null) {
                _call.value = null
                _message.value = "Пользователь завершил звонок."
            }
            val p = _profile.value
            if (p != null && p.online != r.online) _profile.value = p.copy(online = r.online)
            return list
        } catch (e: ApiError) {
            handleAuth(e)
            throw e
        }
    }

    suspend fun accept(offer: Offer): AcceptResponse {
        try {
            val a = VolunteerApi.accept(offer.requestId)
            _call.value = ActiveCall(a.requestId, a.roomUrl, a.userName, a.language, a.urgent, a.location?.lat, a.location?.lon)
            _offers.value = emptyList()
            return a
        } catch (e: ApiError) {
            if (e.status == 409) _offers.value = _offers.value.filter { it.requestId != offer.requestId }
            handleAuth(e)
            throw e
        }
    }

    suspend fun decline(offer: Offer) {
        _offers.value = _offers.value.filter { it.requestId != offer.requestId }
        try { VolunteerApi.decline(offer.requestId) } catch (_: ApiError) {}
    }

    suspend fun finishCall() {
        val c = _call.value ?: return
        _call.value = null
        try { VolunteerApi.finish(c.requestId) } catch (_: ApiError) {}
    }

    suspend fun reportAndFinish(reason: String) {
        val c = _call.value ?: return
        try { VolunteerApi.report(c.requestId, reason) } catch (_: ApiError) {}
        finishCall()
    }

    fun logout() {
        VolunteerStore.clearLogin()
        _profile.value = null
        _offers.value = emptyList()
        _call.value = null
    }

    private fun handleAuth(e: ApiError) {
        if (e.status == 401 || e.status == 403) {
            logout()
            _message.value = e.message
        }
    }
}
