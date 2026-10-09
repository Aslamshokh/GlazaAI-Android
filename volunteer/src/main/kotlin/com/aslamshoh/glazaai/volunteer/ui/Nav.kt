package com.aslamshoh.glazaai.volunteer.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.volunteer.Country
import com.aslamshoh.glazaai.volunteer.VolText
import com.aslamshoh.glazaai.volunteer.net.HistoryItem

/** Юридические тексты, которые открываются из регистрации и настроек. */
enum class Doc(val title: String) {
    AGREEMENT("Пользовательское соглашение"),
    PRIVACY("Политика конфиденциальности"),
    RULES("Правила для волонтёров"),
    SAFETY("Правила безопасности"),
    SUPPORT("Помощь и поддержка")
}

/** Шаги регистрации и входа (до главного экрана). */
sealed interface AuthStep {
    object Welcome : AuthStep
    object Phone : AuthStep
    object Code : AuthStep
    object Personal : AuthStep
    object ProfileSetup : AuthStep
    object VerifyMenu : AuthStep
    object DocPhoto : AuthStep
    object Selfie : AuthStep
    object FaceCheck : AuthStep
    object Terms : AuthStep
    object Success : AuthStep
    data class Legal(val doc: Doc) : AuthStep
}

object AuthNav {
    val stack = mutableStateListOf<AuthStep>(AuthStep.Welcome)
    val current: AuthStep get() = stack.last()
    fun push(step: AuthStep) { stack.add(step) }
    /** Возвращает true, если шаг назад был сделан. */
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.size - 1); true } else false
    fun reset(step: AuthStep) { stack.clear(); stack.add(step) }
}

/** Всё, что человек вводит при регистрации. Живёт, пока приложение открыто; фото для проверки на сервер не уходят. */
object RegState {
    var loginMode by mutableStateOf(false)
    var country by mutableStateOf<Country>(VolText.COUNTRIES.first())
    var national by mutableStateOf("")
    var devCode by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var birth by mutableStateOf("")
    var countryName by mutableStateOf(VolText.COUNTRIES.first().name)
    var city by mutableStateOf("")
    var languages by mutableStateOf(setOf("ru"))
    var specialties by mutableStateOf(setOf<String>())
    var docPhoto by mutableStateOf<Bitmap?>(null)
    var selfie by mutableStateOf<Bitmap?>(null)
    var faceShot by mutableStateOf<Bitmap?>(null)
    var acceptTerms by mutableStateOf(false)
    var acceptData by mutableStateOf(false)
    /** Пока true, после создания аккаунта показывается экран «Аккаунт создан». */
    var inSuccess by mutableStateOf(false)

    val phone: String get() = VolText.fullPhone(country.dial, national)

    /** Полный сброс после выхода из аккаунта. */
    fun resetAll() {
        clearSensitive()
        loginMode = false; name = ""; email = ""; birth = ""; city = ""
        countryName = country.name; languages = setOf("ru"); specialties = emptySet()
        acceptTerms = false; acceptData = false; inSuccess = false
    }

    fun clearSensitive() {
        docPhoto = null; selfie = null; faceShot = null
        national = ""; devCode = null
    }
}

enum class Tab { Home, Requests, History, Chats, Profile }

/** Страницы, открываемые поверх вкладок. */
sealed interface Page {
    object Stats : Page
    object Notices : Page
    object Settings : Page
    object Emergency : Page
    object EditPersonal : Page
    object EditLanguages : Page
    object EditSpecialties : Page
    object About : Page
    data class Info(val item: HistoryItem) : Page
    data class Chat(val requestId: Int, val userName: String) : Page
    data class Legal(val doc: Doc) : Page
}

object MainNav {
    var tab by mutableStateOf(Tab.Home)
    val stack = mutableStateListOf<Page>()
    fun open(page: Page) { stack.add(page) }
    fun back(): Boolean = when {
        stack.isNotEmpty() -> { stack.removeAt(stack.size - 1); true }
        tab != Tab.Home -> { tab = Tab.Home; true }
        else -> false
    }
    fun reset() { stack.clear(); tab = Tab.Home }
}
