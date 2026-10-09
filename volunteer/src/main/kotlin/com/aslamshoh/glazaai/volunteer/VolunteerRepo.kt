package com.aslamshoh.glazaai.volunteer

import com.aslamshoh.glazaai.volunteer.net.AcceptResponse
import com.aslamshoh.glazaai.volunteer.net.ApiError
import com.aslamshoh.glazaai.volunteer.net.LiveKitInfo
import com.aslamshoh.glazaai.volunteer.net.Offer
import com.aslamshoh.glazaai.volunteer.net.ProfileBody
import com.aslamshoh.glazaai.volunteer.net.VolunteerApi
import com.aslamshoh.glazaai.volunteer.net.VolunteerProfile
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Принятый вызов, который сейчас идёт. */
data class ActiveCall(
    val requestId: Int, val roomUrl: String, val userName: String, val language: String, val urgent: Boolean,
    val lat: Double?, val lon: Double?, val livekit: LiveKitInfo? = null
)

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

    private val _notices = MutableStateFlow<List<AppNotice>>(emptyList())
    val notices: StateFlow<List<AppNotice>> = _notices

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun showMessage(text: String?) { _message.value = text }

    /** Загружает локальные уведомления и чаты (вызывается один раз при старте приложения). */
    fun loadLocal() {
        _notices.value = VolText.noticesFromJson(VolunteerStore.getString("notices"))
        _chats.value = VolText.summariesFromJson(VolunteerStore.getString("chats"))
    }

    fun addNotice(kind: String, title: String, text: String) {
        val n = AppNotice(System.currentTimeMillis(), kind, title, text, System.currentTimeMillis() / 1000)
        _notices.value = (listOf(n) + _notices.value).take(50)
        VolunteerStore.putString("notices", VolText.noticesToJson(_notices.value))
    }

    fun chatMessages(requestId: Int): List<ChatMsg> = VolText.chatFromJson(VolunteerStore.getString("chat.$requestId"))

    /** Сохраняет сообщение чата локально (на сервере чат не хранится). */
    fun appendChat(requestId: Int, userName: String, msg: ChatMsg) {
        val list = chatMessages(requestId) + msg
        VolunteerStore.putString("chat.$requestId", VolText.chatToJson(list))
        val all = listOf(ChatSummary(requestId, userName, msg.at, msg.text.take(80))) + _chats.value.filter { it.requestId != requestId }
        // храним 30 последних чатов; переписку остальных удаляем
        all.drop(30).forEach { VolunteerStore.remove("chat.${it.requestId}") }
        _chats.value = all.take(30)
        VolunteerStore.putString("chats", VolText.summariesToJson(_chats.value))
    }

    /** Возвращает тестовый код, если сервер в режиме проверки (HELP_DEV_OTP). */
    suspend fun requestCode(phone: String): String? = VolunteerApi.requestCode(phone).devCode

    /** Проверяет код; токен сохраняется сразу. Если анкета не заполнена — приложение продолжит регистрацию. */
    suspend fun verifyCode(phone: String, code: String): VolunteerProfile {
        val r = VolunteerApi.verify(phone, code)
        val p = r.profile
        VolunteerStore.saveLogin(r.token, p?.name ?: "")
        _profile.value = p
        return p ?: VolunteerProfile()
    }

    suspend fun saveProfile(body: ProfileBody): VolunteerProfile {
        try {
            val p = VolunteerApi.updateProfile(body)
            _profile.value = p
            VolunteerStore.saveLogin(VolunteerStore.token, p.name)
            return p
        } catch (e: ApiError) {
            handleAuth(e)
            throw e
        }
    }

    /** Возвращает false, если профиль загрузить не удалось (нет связи). */
    suspend fun refreshProfile(): Boolean {
        return try {
            _profile.value = VolunteerApi.me()
            true
        } catch (e: ApiError) {
            handleAuth(e)
            false
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
                addNotice("done", "Вызов завершён", "Пользователь завершил звонок.")
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
            _call.value = ActiveCall(a.requestId, a.roomUrl, a.userName, a.language, a.urgent, a.location?.lat, a.location?.lon, a.livekit)
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
        addNotice("done", "Вызов завершён", "Спасибо за помощь!")
        try { VolunteerApi.finish(c.requestId) } catch (_: ApiError) {}
    }

    suspend fun reportAndFinish(reason: String) {
        val c = _call.value ?: return
        try { VolunteerApi.report(c.requestId, reason) } catch (_: ApiError) {}
        finishCall()
    }

    fun logout() {
        VolunteerStore.clearLogin()
        _notices.value = emptyList()
        _chats.value = emptyList()
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
