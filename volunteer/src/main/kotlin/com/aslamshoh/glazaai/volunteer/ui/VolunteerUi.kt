package com.aslamshoh.glazaai.volunteer.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.volunteer.ActiveCall
import com.aslamshoh.glazaai.volunteer.DutyService
import com.aslamshoh.glazaai.volunteer.OfferText
import com.aslamshoh.glazaai.volunteer.VolunteerRepo
import com.aslamshoh.glazaai.volunteer.net.ApiError
import com.aslamshoh.glazaai.volunteer.net.Offer
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.launch

object VColors {
    val background = Color(0xFF070C1A)
    val surface = Color(0xFF111A2E)
    val surfaceAlt = Color(0xFF1A2540)
    val accent = Color(0xFF2E7BFF)
    val text = Color.White
    val textSecondary = Color(0xFF9AA6BF)
    val success = Color(0xFF34C77B)
    val warning = Color(0xFFF5A623)
    val critical = Color(0xFFFF5C5C)
}

@Composable
fun VolunteerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VColors.accent, background = VColors.background, surface = VColors.surface,
            onPrimary = Color.White, onBackground = VColors.text, onSurface = VColors.text
        ),
        content = content
    )
}

private fun messageOf(e: Exception): String = e.message ?: "Неизвестная ошибка."

@Composable
fun VolunteerRoot() {
    val loggedIn = VolunteerStore.isLoggedIn
    val call by VolunteerRepo.call.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(VColors.background).statusBarsPadding()) {
        when {
            !loggedIn -> LoginScreen()
            call != null -> CallScreen(call!!)
            else -> HomeScreen()
        }
    }
}

@Composable
private fun BigButton(label: String, color: Color = VColors.accent, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else VColors.surfaceAlt)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedContainerColor = VColors.surface, unfocusedContainerColor = VColors.surface,
    focusedIndicatorColor = VColors.accent, unfocusedIndicatorColor = Color(0x14FFFFFF), cursorColor = VColors.accent
)

@Composable
private fun MessageLine(message: String?) {
    if (message != null) Text(message, color = VColors.warning, fontSize = 14.sp)
}

// ───────────────────────────── вход ─────────────────────────────
@Composable
private fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(VolunteerStore.serverUrl) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(VolunteerStore.name) }
    var languages by remember { mutableStateOf(setOf("ru")) }
    var accepted by remember { mutableStateOf(false) }
    var codeSent by remember { mutableStateOf(false) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val notice by VolunteerRepo.message.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ИИ Глаз Помощь", color = VColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Помогайте незрячим людям по видеосвязи: подскажите, что перед ними. Звонок можно принять в любой момент и так же легко завершить.",
            color = VColors.textSecondary, fontSize = 14.sp
        )
        MessageLine(notice)
        OutlinedTextField(
            value = server, onValueChange = { server = it; VolunteerStore.updateServerUrl(it) },
            label = { Text("Адрес сервера, например https://….") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it }, label = { Text("Телефон, например +992…") },
            singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Ваше имя (его увидит человек)") },
            singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        Text("На каких языках вы можете помогать?", color = VColors.textSecondary, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OfferText.LANGUAGES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (c, label) ->
                        val on = c in languages
                        Box(
                            modifier = Modifier
                                .weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) VColors.accent else VColors.surfaceAlt)
                                .clickable(onClickLabel = label, role = Role.Checkbox) { languages = if (on) languages - c else languages + c },
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        if (codeSent) {
            devCode?.let { Text("Тестовый режим: код $it", color = VColors.warning, fontSize = 14.sp) }
            OutlinedTextField(
                value = code, onValueChange = { code = it }, label = { Text("Код из сообщения") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
        }
        Text("Правила волонтёра", color = VColors.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        OfferText.RULES.forEach { Text("• $it", color = VColors.textSecondary, fontSize = 14.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text("Я принимаю правила", color = VColors.text, fontSize = 15.sp)
        }
        MessageLine(error)
        if (!codeSent) {
            BigButton("Получить код", enabled = !busy) {
                if (!OfferText.validPhone(phone)) { error = "Введите номер телефона в международном формате."; return@BigButton }
                busy = true; error = null
                scope.launch {
                    try {
                        devCode = VolunteerRepo.requestCode(phone)
                        codeSent = true
                    } catch (e: Exception) { error = messageOf(e) } finally { busy = false }
                }
            }
        } else {
            BigButton("Войти", enabled = !busy) {
                val problem = OfferText.loginProblem(phone, code, name, languages, accepted)
                if (problem != null) { error = problem; return@BigButton }
                busy = true; error = null
                scope.launch {
                    try {
                        VolunteerRepo.login(phone, code, name, languages.toList())
                        VolunteerRepo.showMessage(null)
                    } catch (e: Exception) { error = messageOf(e) } finally { busy = false }
                }
            }
            BigButton("Получить код заново", color = VColors.surfaceAlt) { codeSent = false; code = "" }
        }
    }
}

// ───────────────────────────── главный ─────────────────────────────
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by VolunteerRepo.profile.collectAsState()
    val offers by VolunteerRepo.offers.collectAsState()
    val notice by VolunteerRepo.message.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { VolunteerRepo.refreshProfile() }

    fun goOnline(on: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                VolunteerRepo.setOnline(on)
                if (on) DutyService.start(context) else DutyService.stop(context)
            } catch (e: Exception) { error = messageOf(e) } finally { busy = false }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { goOnline(true) }

    fun toggle(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else goOnline(on)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Здравствуйте, ${profile?.name ?: VolunteerStore.name}", color = VColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        profile?.let { p ->
            val rating = p.rating?.let { "оценка $it" } ?: "оценок пока нет"
            Text("Помогли за 30 дней: ${p.helpedLast30Days} · $rating", color = VColors.textSecondary, fontSize = 14.sp)
            if (p.isNewbie) Text("Вы новичок: первые 7 дней срочные вызовы вам не приходят.", color = VColors.textSecondary, fontSize = 13.sp)
            if (p.underReview) Text("Ваш профиль на проверке у администратора, вызовы не приходят.", color = VColors.warning, fontSize = 14.sp)
        }
        MessageLine(notice)
        MessageLine(error)

        val online = profile?.online == true
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VColors.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (online) "Вы на связи" else "Вы не на связи", color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (online) "Вызовы приходят сюда и в уведомления." else "Включите, когда готовы помогать.",
                    color = VColors.textSecondary, fontSize = 13.sp
                )
            }
            Switch(checked = online, enabled = !busy && profile?.underReview != true, onCheckedChange = { toggle(it) })
        }

        if (online && offers.isEmpty()) {
            Text("Пока никто не просит помощи. Оставайтесь на связи.", color = VColors.textSecondary, fontSize = 15.sp)
        }
        offers.forEach { offer -> OfferCard(offer) { error = it } }

        BigButton("Выйти из аккаунта", color = VColors.surfaceAlt) {
            DutyService.stop(context)
            scope.launch { try { VolunteerRepo.setOnline(false) } catch (_: Exception) {} ; VolunteerRepo.logout() }
        }
    }
}

@Composable
private fun OfferCard(offer: Offer, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (offer.urgent) VColors.critical.copy(alpha = 0.18f) else VColors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(OfferText.title(offer.userName, offer.urgent), color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(OfferText.subtitle(offer.language, offer.leftSeconds), color = VColors.textSecondary, fontSize = 14.sp)
        BigButton("Принять и позвонить", color = VColors.success, enabled = !busy) {
            busy = true
            scope.launch {
                try {
                    val a = VolunteerRepo.accept(offer)
                    openUrl(context, a.roomUrl)
                } catch (e: ApiError) { onError(e.message ?: "Не удалось принять вызов.") } finally { busy = false }
            }
        }
        BigButton("Отклонить", color = VColors.surfaceAlt, enabled = !busy) { scope.launch { VolunteerRepo.decline(offer) } }
    }
}

// ───────────────────────────── звонок ─────────────────────────────
@Composable
private fun CallScreen(call: ActiveCall) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Идёт вызов", color = VColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(OfferText.title(call.userName, call.urgent), color = VColors.text, fontSize = 18.sp)
        Text("Язык: ${OfferText.languageName(call.language)}", color = VColors.textSecondary, fontSize = 14.sp)
        Text(
            "Видеозвонок открывается в программе Jitsi Meet. Если она закрылась, нажмите «Открыть видеозвонок». " +
                "Не записывайте звонок и не просите личные данные.",
            color = VColors.textSecondary, fontSize = 14.sp
        )
        BigButton("Открыть видеозвонок") { openUrl(context, call.roomUrl) }
        if (call.lat != null && call.lon != null) {
            Text("Человек отправил своё положение (срочный вызов).", color = VColors.warning, fontSize = 14.sp)
            BigButton("Показать место на карте", color = VColors.surfaceAlt) {
                openUrl(context, "geo:${call.lat},${call.lon}?q=${call.lat},${call.lon}")
            }
        }
        BigButton("Завершить звонок", color = VColors.surfaceAlt) { scope.launch { VolunteerRepo.finishCall() } }
        BigButton("Пожаловаться и завершить", color = VColors.critical) {
            scope.launch { VolunteerRepo.reportAndFinish("Жалоба волонтёра") }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        VolunteerRepo.showMessage("Не найдена программа для видеозвонка. Установите бесплатное приложение Jitsi Meet.")
    }
}
