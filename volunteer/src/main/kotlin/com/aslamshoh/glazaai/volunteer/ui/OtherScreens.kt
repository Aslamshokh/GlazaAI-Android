package com.aslamshoh.glazaai.volunteer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.volunteer.DutyService
import com.aslamshoh.glazaai.volunteer.OfferText
import com.aslamshoh.glazaai.volunteer.VolText
import com.aslamshoh.glazaai.volunteer.VolunteerRepo
import com.aslamshoh.glazaai.volunteer.net.ProfileBody
import com.aslamshoh.glazaai.volunteer.net.VolunteerApi
import com.aslamshoh.glazaai.volunteer.net.VolunteerProfile
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PageHost(page: Page) {
    val back = { MainNav.back(); Unit }
    when (page) {
        Page.Stats -> StatsPage(back)
        Page.Notices -> NoticesPage(back)
        Page.Settings -> SettingsPage(back)
        Page.Emergency -> EmergencyPage(back)
        Page.EditPersonal -> EditPersonalPage(back)
        Page.EditLanguages -> EditLanguagesPage(back)
        Page.EditSpecialties -> EditSpecialtiesPage(back)
        Page.About -> AboutPage(back)
        is Page.Info -> InfoPage(page, back)
        is Page.Chat -> ChatPage(page, back)
        is Page.Legal -> LegalPage(page.doc, back)
    }
}

@Composable
private fun PageFrame(title: String, onBack: () -> Unit, bottom: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopBar(title, onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content(); Gap(8.dp) }
        if (bottom != null) Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
    }
}

// ───────────────────────── Документы ─────────────────────────
@Composable
fun LegalPage(doc: Doc, onBack: () -> Unit) {
    PageFrame(doc.title, onBack) {
        LegalTexts.body(doc).forEach { (h, t) ->
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(h, color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(t, color = VColors.textSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────── 6. Информация о вызове ─────────────────────────
@Composable
private fun InfoPage(page: Page.Info, onBack: () -> Unit) {
    val i = page.item
    val ok = i.status == "completed"
    PageFrame("Информация", onBack) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i.urgent) SosBadge(52.dp) else Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = Color(0xFF9D96FF))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(VolText.callTitle(i.urgent), color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Gap(4.dp)
                    Pill(VolText.statusLabel(i.status), if (ok) VColors.success else if (i.status == "active") VColors.accent else VColors.critical)
                }
            }
        }
        InfoCard(Icons.Filled.Schedule, "Время", VolText.formatDateTime(i.acceptedAt))
        InfoCard(Icons.Filled.Schedule, "Длительность", VolText.formatDuration(i.durationSeconds))
        InfoCard(Icons.Filled.Person, "Язык", OfferText.languageName(i.language))
        InfoCard(Icons.Filled.Warning, "Причина", i.note?.takeIf { it.isNotBlank() } ?: "Помощь по видеосвязи")
        InfoCard(Icons.Filled.Star, "Оценка человека", i.rating?.let { "$it из 5" } ?: "Не поставлена")
        Text("Место человека хранится только во время срочного вызова и после него не сохраняется.", color = VColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun InfoCard(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = VColors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = VColors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = VColors.text, fontSize = 14.sp)
    }
}

// ───────────────────────── 8. Статистика ─────────────────────────
@Composable
private fun StatsPage(onBack: () -> Unit) {
    var period by remember { mutableStateOf("month") }
    val tz = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
    val data = rememberLoad(period) { VolunteerApi.stats(period, tz) }
    val s = data.data
    PageFrame("Статистика", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Все", period == "all") { period = "all" }
            SelectChip("Месяц", period == "month") { period = "month" }
            SelectChip("Год", period == "year") { period = "year" }
        }
        ErrorText(data.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BigStat(Modifier.weight(1f), Icons.Filled.Check, VColors.success, (s?.completed ?: 0).toString(), "Завершённые")
            BigStat(Modifier.weight(1f), Icons.Filled.Close, VColors.critical, (s?.cancelled ?: 0).toString(), "Отменённые")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BigStat(Modifier.weight(1f), Icons.Filled.Star, VColors.warning, s?.avgRating?.toString() ?: "—", "Средняя оценка")
            BigStat(Modifier.weight(1f), Icons.Filled.Schedule, VColors.accent, VolText.formatDuration(s?.helpSeconds ?: 0), "Время помощи")
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Звонки по дням недели", color = VColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val counts = s?.byWeekday ?: List(7) { 0 }
                val heights = VolText.barHeights(counts)
                Row(modifier = Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    heights.forEachIndexed { idx, h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(counts.getOrElse(idx) { 0 }.toString(), color = VColors.textSecondary, fontSize = 11.sp)
                            Box(modifier = Modifier.width(22.dp).height((8 + 90 * h).dp).clip(RoundedCornerShape(6.dp)).background(if (h > 0f) VColors.accent else VColors.divider))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VolText.WEEKDAYS.forEach { Text(it, color = VColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(22.dp)) }
                }
            }
        }
        Text("Статистика считается по звонкам за последние 90 дней: старые записи удаляются.", color = VColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun BigStat(modifier: Modifier, icon: ImageVector, color: Color, value: String, label: String) {
    Row(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(VColors.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, color = VColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = VColors.textSecondary, fontSize = 12.sp)
        }
    }
}

// ───────────────────────── 9. Уведомления ─────────────────────────
@Composable
private fun NoticesPage(onBack: () -> Unit) {
    val notices by VolunteerRepo.notices.collectAsState()
    val now = System.currentTimeMillis() / 1000
    PageFrame("Уведомления", onBack) {
        if (notices.isEmpty()) EmptyState("Пока уведомлений нет.")
        notices.forEach { n ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (n.kind) {
                    "sos" -> SosBadge(40.dp)
                    else -> Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
                        Icon(if (n.kind == "done") Icons.Filled.Check else Icons.Filled.Notifications, contentDescription = null, tint = Color(0xFF9D96FF))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(n.title, color = VColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(n.text, color = VColors.textSecondary, fontSize = 13.sp)
                }
                Text(VolText.relativeTime(now, n.at), color = VColors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ───────────────────────── 11. Настройки ─────────────────────────
@Composable
private fun SettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by VolunteerRepo.profile.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    PageFrame("Настройки", onBack) {
        ErrorText(error)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = VColors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Статус", color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(if (profile?.online == true) "На связи" else "Не на связи", color = VColors.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = profile?.online == true, enabled = profile?.underReview != true, onCheckedChange = { on ->
                scope.launch {
                    try {
                        VolunteerRepo.setOnline(on)
                        if (on) DutyService.start(context) else DutyService.stop(context)
                    } catch (e: Exception) { error = messageOf(e) }
                }
            })
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = VColors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text("Уведомления о вызовах", color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = VolunteerStore.notificationsOn, onCheckedChange = { VolunteerStore.changeNotificationsOn(it) })
        }
        MenuRow(Icons.Filled.VolumeUp, "Звук вызова", trailing = if (VolunteerStore.soundOn) "По умолчанию" else "Без звука") {
            VolunteerStore.changeSoundOn(!VolunteerStore.soundOn)
        }
        MenuRow(Icons.Filled.Palette, "Тема", trailing = "Тёмная", enabled = false)
        MenuRow(Icons.Filled.Lock, "Конфиденциальность") { MainNav.open(Page.Legal(Doc.PRIVACY)) }
        MenuRow(Icons.Filled.Shield, "Безопасность") { MainNav.open(Page.Emergency) }
        MenuRow(Icons.Filled.HelpOutline, "Помощь и поддержка") { MainNav.open(Page.Legal(Doc.SUPPORT)) }
        MenuRow(Icons.Filled.Info, "О приложении") { MainNav.open(Page.About) }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    var server by remember { mutableStateOf(VolunteerStore.serverUrl) }
    PageFrame("О приложении", onBack) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Eyes AI Volunteer", color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Версия 0.2.0", color = VColors.textSecondary, fontSize = 13.sp)
                Text("Помогайте незрячим и слабовидящим людям по видеосвязи.", color = VColors.textSecondary, fontSize = 14.sp)
            }
        }
        VField(server, { server = it; VolunteerStore.updateServerUrl(it) }, "Адрес сервера", keyboardType = KeyboardType.Uri)
        MenuRow(Icons.Filled.Info, Doc.AGREEMENT.title) { MainNav.open(Page.Legal(Doc.AGREEMENT)) }
        MenuRow(Icons.Filled.Shield, Doc.PRIVACY.title) { MainNav.open(Page.Legal(Doc.PRIVACY)) }
        MenuRow(Icons.Filled.Person, Doc.RULES.title) { MainNav.open(Page.Legal(Doc.RULES)) }
    }
}

// ───────────────────────── 12. Экстренные функции ─────────────────────────
@Composable
private fun EmergencyPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf("") }
    var confirmAlarm by remember { mutableStateOf(false) }
    var trustedName by remember { mutableStateOf(VolunteerStore.trustedName) }
    var trustedPhone by remember { mutableStateOf(VolunteerStore.trustedPhone) }
    var emergency by remember { mutableStateOf(VolunteerStore.emergencyNumber) }
    val myName = VolunteerStore.name

    fun lastLocation(): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) { null }
    }

    fun shareLocation() {
        val loc = lastLocation()
        val text = VolText.whereAmIText(myName, loc?.first, loc?.second)
        if (loc == null) status = "Не удалось определить место — отправляю сообщение без него."
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Отправить моё местоположение")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sendAlarm() {
        val phone = VolunteerStore.trustedPhone
        if (phone.isBlank()) { status = "Сначала укажите доверенного человека ниже."; return }
        val loc = lastLocation()
        val text = VolText.whereAmIText(myName, loc?.first, loc?.second)
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        status = "Открыто сообщение для ${VolunteerStore.trustedName.ifBlank { "доверенного человека" }}. Нажмите «Отправить»."
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (pendingAction == "share") shareLocation() else if (pendingAction == "alarm") sendAlarm()
        pendingAction = ""
    }
    fun withLocationPermission(action: String) {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (has) { if (action == "share") shareLocation() else sendAlarm() }
        else { pendingAction = action; permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }

    PageFrame("Экстренные функции", onBack) {
        ErrorText(status)
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VColors.critical)
                .clickable(role = Role.Button) {
                    // ACTION_DIAL только открывает набор номера: человек сам нажимает «позвонить»
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${VolunteerStore.emergencyNumber}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Вызвать ${VolunteerStore.emergencyNumber}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Экстренные службы", color = Color.White, fontSize = 13.sp)
            }
        }
        MenuRow(Icons.Filled.LocationOn, "Отправить моё местоположение", "В случае опасности", iconColor = VColors.critical) { withLocationPermission("share") }
        if (!confirmAlarm) {
            MenuRow(Icons.Filled.Warning, "Тревожная кнопка", "Сообщить доверенному человеку об опасной ситуации", iconColor = VColors.critical) { confirmAlarm = true }
        } else {
            Card(color = VColors.sosBackground) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Отправить сообщение «мне нужна помощь» с вашим местоположением?", color = VColors.text, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { PrimaryButton("Нет", color = VColors.surfaceAlt) { confirmAlarm = false } }
                        Box(Modifier.weight(1f)) { PrimaryButton("Да, отправить", color = VColors.critical) { confirmAlarm = false; withLocationPermission("alarm") } }
                    }
                }
            }
        }
        MenuRow(Icons.Filled.Shield, "Правила безопасности", "Рекомендации по работе с пользователями") { MainNav.open(Page.Legal(Doc.SAFETY)) }

        SectionLabel("Доверенный человек")
        Text("Ему уйдёт сообщение при нажатии тревожной кнопки. Номер хранится только на вашем телефоне.", color = VColors.textSecondary, fontSize = 13.sp)
        VField(trustedName, { trustedName = it; VolunteerStore.setTrusted(it, trustedPhone) }, "Имя", Icons.Filled.Person)
        VField(trustedPhone, { trustedPhone = it; VolunteerStore.setTrusted(trustedName, it) }, "Телефон", Icons.Filled.Call, KeyboardType.Phone)
        SectionLabel("Номер экстренной службы")
        Text("Он зависит от страны. Проверьте и при необходимости измените.", color = VColors.textSecondary, fontSize = 13.sp)
        VField(emergency, { emergency = it.filter { c -> c.isDigit() || c == '+' }.take(6); VolunteerStore.changeEmergencyNumber(emergency) }, "Номер", keyboardType = KeyboardType.Phone)
    }
}

// ───────────────────────── Чат (просмотр) ─────────────────────────
@Composable
private fun ChatPage(page: Page.Chat, onBack: () -> Unit) {
    val msgs = remember(page.requestId) { VolunteerRepo.chatMessages(page.requestId) }
    PageFrame(page.userName.ifBlank { "Чат" }, onBack) {
        if (msgs.isEmpty()) EmptyState("Сообщений нет.")
        ChatBubbles(msgs)
        Text("Чат работает во время звонка. После звонка переписка доступна только для чтения.", color = VColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun ChatBubbles(msgs: List<com.aslamshoh.glazaai.volunteer.ChatMsg>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        msgs.forEach { m ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (m.mine) VColors.accent else VColors.surfaceAlt).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(if (m.mine) "Вы" else "Пользователь", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(m.text, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

// ───────────────────────── Редактирование профиля ─────────────────────────
private suspend fun save(p: VolunteerProfile, mutate: (ProfileBody) -> ProfileBody): String? = try {
    val base = ProfileBody(
        name = p.name, birthDate = VolText.isoToBirth(p.birthDate), languages = p.languages ?: listOf("ru"),
        acceptTerms = true, email = p.email, country = p.country, city = p.city, specialties = p.specialties ?: emptyList()
    )
    VolunteerRepo.saveProfile(mutate(base))
    null
} catch (e: Exception) { messageOf(e) }

@Composable
private fun EditPersonalPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = VolunteerRepo.profile.collectAsState().value ?: return
    var name by remember { mutableStateOf(p.name) }
    var email by remember { mutableStateOf(p.email ?: "") }
    var birth by remember { mutableStateOf(VolText.isoToBirth(p.birthDate)) }
    var country by remember { mutableStateOf(p.country ?: VolText.COUNTRIES.first().name) }
    var city by remember { mutableStateOf(p.city ?: "") }
    var pickCountry by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !saveAvatarFromUri(context, uri)) error = "Не удалось открыть фото."
    }
    PageFrame("Личные данные", onBack, bottom = {
        ErrorText(error)
        PrimaryButton("Сохранить", enabled = !busy) {
            val problem = VolText.personalProblem(name, email, birth, LocalDate.now())
            if (problem != null) { error = problem; return@PrimaryButton }
            busy = true; error = null
            scope.launch {
                error = save(p) { it.copy(name = name.trim(), email = email.trim().ifEmpty { null }, birthDate = birth, country = country, city = city.trim().ifEmpty { null }) }
                busy = false
                if (error == null) onBack()
            }
        }
    }) {
        Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Сменить фото") { picker.launch("image/*") }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(VolunteerStore.avatarPath, 96.dp)
                Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(VColors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Text("Фото хранится только на телефоне", color = VColors.textSecondary, fontSize = 12.sp)
        }
        VField(name, { name = it.take(40) }, "Имя", Icons.Filled.Person)
        VField(email, { email = it.take(80) }, "Электронная почта", Icons.Filled.Email, KeyboardType.Email)
        VField(birth, { birth = VolText.formatBirthInput(it) }, "Дата рождения", Icons.Filled.CalendarMonth, KeyboardType.Number)
        SectionLabelSmall("Страна")
        CountryField(country, pickCountry, { pickCountry = !pickCountry }) { country = it; pickCountry = false }
        VField(city, { city = it.take(60) }, "Город")
    }
}

@Composable
private fun EditLanguagesPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val p = VolunteerRepo.profile.collectAsState().value ?: return
    var langs by remember { mutableStateOf((p.languages ?: listOf("ru")).toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    PageFrame("Мои языки", onBack, bottom = {
        ErrorText(error)
        PrimaryButton("Сохранить") {
            if (langs.isEmpty()) { error = "Выберите хотя бы один язык."; return@PrimaryButton }
            scope.launch { error = save(p) { it.copy(languages = langs.toList()) }; if (error == null) onBack() }
        }
    }) {
        Text("На каких языках вы можете помогать? Вызовы приходят в первую очередь на ваших языках.", color = VColors.textSecondary, fontSize = 14.sp)
        ChipGrid(OfferText.LANGUAGES, langs, perRow = 2) { c -> langs = if (c in langs) langs - c else langs + c }
    }
}

@Composable
private fun EditSpecialtiesPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val p = VolunteerRepo.profile.collectAsState().value ?: return
    var specs by remember { mutableStateOf((p.specialties ?: emptyList()).toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    PageFrame("Специализация", onBack, bottom = {
        ErrorText(error)
        PrimaryButton("Сохранить") { scope.launch { error = save(p) { it.copy(specialties = specs.toList()) }; if (error == null) onBack() } }
    }) {
        Text("В чём вам проще всего помогать?", color = VColors.textSecondary, fontSize = 14.sp)
        ChipGrid(VolText.SPECIALTIES, specs, perRow = 2) { c -> specs = if (c in specs) specs - c else specs + c }
    }
}
