package com.aslamshoh.glazaai.volunteer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.volunteer.DutyService
import com.aslamshoh.glazaai.volunteer.OfferText
import com.aslamshoh.glazaai.volunteer.VolText
import com.aslamshoh.glazaai.volunteer.VolunteerRepo
import com.aslamshoh.glazaai.volunteer.net.ApiError
import com.aslamshoh.glazaai.volunteer.net.HistoryItem
import com.aslamshoh.glazaai.volunteer.net.Offer
import com.aslamshoh.glazaai.volunteer.net.VolunteerApi
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.launch

// ───────────────────────── корень приложения ─────────────────────────
@Composable
fun VolunteerRoot() {
    val loggedIn = VolunteerStore.isLoggedIn
    val profile by VolunteerRepo.profile.collectAsState()
    val call by VolunteerRepo.call.collectAsState()
    var retry by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, retry) {
        if (loggedIn && VolunteerRepo.profile.value == null) loadFailed = !VolunteerRepo.refreshProfile()
    }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) { AuthNav.reset(AuthStep.Welcome); MainNav.reset(); RegState.resetAll() }
    }
    // регистрация не закончена (например, закрыли приложение после кода) — продолжаем с анкеты
    LaunchedEffect(profile?.profileComplete) {
        if (loggedIn && profile?.profileComplete == false && AuthNav.current is AuthStep.Welcome) AuthNav.reset(AuthStep.Personal)
    }

    val inAuth = !loggedIn || (profile != null && (profile?.profileComplete != true || RegState.inSuccess))
    val inCall = !inAuth && loggedIn && profile != null && call != null
    val inMain = !inAuth && !inCall && loggedIn && profile != null

    BackHandler(enabled = inAuth && AuthNav.stack.size > 1) { AuthNav.pop() }
    BackHandler(enabled = inMain && (MainNav.stack.isNotEmpty() || MainNav.tab != Tab.Home)) { MainNav.back() }
    BackHandler(enabled = inCall) { /* случайный жест «назад» не должен обрывать звонок */ }

    Box(modifier = Modifier.fillMaxSize().background(VColors.background).statusBarsPadding().navigationBarsPadding()) {
        when {
            inAuth -> AuthHost()
            loggedIn && profile == null -> LoadingScreen(loadFailed, { loadFailed = false; retry++ }) { logout() }
            inCall -> CallHost(call!!)
            else -> MainHost()
        }
    }
}

fun logout() {
    val context = com.aslamshoh.glazaai.volunteer.VolunteerApp.instance
    if (context != null) DutyService.stop(context)
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try { VolunteerRepo.setOnline(false) } catch (_: Exception) {}
        VolunteerRepo.logout()
    }
}

@Composable
private fun LoadingScreen(failed: Boolean, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        if (!failed) {
            CircularProgressIndicator(color = VColors.accent)
            Text("Загружаю профиль…", color = VColors.textSecondary, fontSize = 15.sp)
        } else {
            Text("Нет связи с сервером. Проверьте интернет и адрес сервера.", color = VColors.warning, fontSize = 15.sp, textAlign = TextAlign.Center)
            PrimaryButton("Повторить", onClick = onRetry)
            GhostButton("Выйти из аккаунта", onLogout)
        }
    }
}

// ───────────────────────── каркас с нижним меню ─────────────────────────
@Composable
private fun MainHost() {
    val profile by VolunteerRepo.profile.collectAsState()
    val offers by VolunteerRepo.offers.collectAsState()
    var snoozed by remember { mutableStateOf(setOf<Int>()) }
    val first = if (profile?.online == true) offers.firstOrNull { it.requestId !in snoozed } else null
    val context = LocalContext.current
    // «На связи» на сервере, а служба опроса не работает (телефон перезагружали) — запускаем её снова
    LaunchedEffect(profile?.online) { if (profile?.online == true) DutyService.start(context) }

    if (first != null) {
        IncomingScreen(first) { snoozed = snoozed + first.requestId }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            val page = MainNav.stack.lastOrNull()
            if (page != null) PageHost(page) else when (MainNav.tab) {
                Tab.Home -> HomeScreen()
                Tab.Requests -> RequestsScreen()
                Tab.History -> HistoryScreen()
                Tab.Chats -> ChatsScreen()
                Tab.Profile -> ProfileScreen()
            }
        }
        if (MainNav.stack.isEmpty()) BottomBar()
    }
}

@Composable
private fun BottomBar() {
    Row(modifier = Modifier.fillMaxWidth().background(VColors.surface).padding(vertical = 4.dp)) {
        BottomItem(Tab.Home, "Главная", Icons.Filled.Home)
        BottomItem(Tab.Requests, "Обращения", Icons.Filled.List)
        BottomItem(Tab.History, "Вызовы", Icons.Filled.Call)
        BottomItem(Tab.Chats, "Чаты", Icons.Filled.Chat)
        BottomItem(Tab.Profile, "Профиль", Icons.Filled.Person)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(tab: Tab, label: String, icon: ImageVector) {
    val on = MainNav.tab == tab
    Column(
        modifier = Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Tab, onClickLabel = label) { MainNav.tab = tab }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (on) VColors.accent else VColors.textSecondary)
        Text(label, color = if (on) VColors.accent else VColors.textSecondary, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ───────────────────────── действия ─────────────────────────
/** Принять вызов; возвращает текст ошибки или null. */
private suspend fun acceptOffer(context: android.content.Context, offer: Offer): String? = try {
    val a = VolunteerRepo.accept(offer)
    // сервер без LiveKit — запасной вариант через ссылку Jitsi
    if (a.livekit == null) openUrl(context, a.roomUrl)
    null
} catch (e: ApiError) { e.message ?: "Не удалось принять вызов." }

private fun offerTitle(o: Offer) = VolText.callTitle(o.urgent)

// ───────────────────────── 1. Главная ─────────────────────────
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by VolunteerRepo.profile.collectAsState()
    val offers by VolunteerRepo.offers.collectAsState()
    val notices by VolunteerRepo.notices.collectAsState()
    val notice by VolunteerRepo.message.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val history = rememberLoad(MainNav.tab) { VolunteerApi.history() }

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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFF8A82FF))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Eyes AI", color = VColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Volunteer", color = VColors.textSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = { MainNav.open(Page.Notices) }) {
                Box {
                    Icon(Icons.Filled.Notifications, contentDescription = "Уведомления", tint = VColors.text)
                    if (notices.isNotEmpty()) Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(VColors.critical).align(Alignment.TopEnd))
                }
            }
        }
        ErrorText(notice)
        ErrorText(error)
        profile?.let { p ->
            if (p.underReview) Text("Ваш профиль на проверке у администратора, вызовы не приходят.", color = VColors.warning, fontSize = 14.sp)
            if (p.isNewbie) Text("Вы новичок: первые 7 дней срочные вызовы (SOS) вам не приходят.", color = VColors.textSecondary, fontSize = 13.sp)
        }

        val online = profile?.online == true
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VColors.surface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (online) "На связи" else "Не на связи", color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (online) "Вызовы приходят сюда и в уведомления." else "Включите, когда готовы помогать.",
                    color = VColors.textSecondary, fontSize = 13.sp
                )
            }
            Switch(checked = online, enabled = !busy && profile?.underReview != true, onCheckedChange = { toggle(it) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(Modifier.weight(1f), offers.size.toString(), "Новых обращений", VColors.critical)
            StatTile(Modifier.weight(1f), offers.count { it.urgent }.toString(), "Срочных SOS", VColors.accent)
        }

        if (online && offers.isEmpty()) {
            Text("Пока никто не просит помощи. Оставайтесь на связи.", color = VColors.textSecondary, fontSize = 15.sp)
        }
        if (offers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Ближайшие обращения")
                Spacer(Modifier.weight(1f))
                Text("Все", color = VColors.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { MainNav.tab = Tab.Requests }.padding(8.dp))
            }
            offers.take(3).forEach { o -> OfferRow(o) { scope.launch { busy = true; error = acceptOffer(context, o); busy = false } } }
        }

        val recent = history.data?.take(3) ?: emptyList()
        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Последние вызовы")
                Spacer(Modifier.weight(1f))
                Text("Все", color = VColors.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { MainNav.tab = Tab.History }.padding(8.dp))
            }
            recent.forEach { HistoryRow(it) }
        }
        PrimaryButton("Все обращения") { MainNav.tab = Tab.Requests }
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: String, label: String, color: Color) {
    Row(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(VColors.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = VColors.text, fontSize = 14.sp)
    }
}

@Composable
private fun OfferRow(o: Offer, onAccept: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (o.urgent) SosBadge() else Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color(0xFF9D96FF))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(offerTitle(o), color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Язык: ${OfferText.languageName(o.language)} · осталось ${o.leftSeconds} с", color = VColors.textSecondary, fontSize = 12.sp)
        }
        Text(VolText.waitedText(o.waitedSeconds), color = VColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(VColors.success).clickable(role = Role.Button, onClickLabel = "Принять вызов", onClick = onAccept),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface)
            .clickable(role = Role.Button) { MainNav.open(Page.Info(item)) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ok = item.status == "completed"
        if (item.urgent && !ok) SosBadge() else Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background((if (ok) VColors.success else VColors.critical).copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(if (ok) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = if (ok) VColors.success else VColors.critical) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(VolText.callTitle(item.urgent), color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(VolText.statusLabel(item.status), color = if (ok) VColors.success else VColors.textSecondary, fontSize = 12.sp)
            Text(VolText.formatDateTime(item.acceptedAt), color = VColors.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = VColors.textSecondary)
    }
}

// ───────────────────────── 2. Обращения ─────────────────────────
@Composable
private fun RequestsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by VolunteerRepo.profile.collectAsState()
    val offers by VolunteerRepo.offers.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var error by remember { mutableStateOf<String?>(null) }
    val myLangs = profile?.languages ?: emptyList()
    val shown = offers.filter { when (filter) { "sos" -> it.urgent; "lang" -> it.language in myLangs; else -> true } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Обращения")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            SelectChip("Все", filter == "all") { filter = "all" }
            SelectChip("SOS", filter == "sos") { filter = "sos" }
            SelectChip("Мой язык", filter == "lang") { filter = "lang" }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ErrorText(error)
            if (profile?.online != true) EmptyState("Вы не на связи. Включите статус «На связи» на главном экране, чтобы получать обращения.")
            else if (shown.isEmpty()) EmptyState("Пока обращений нет. Оставайтесь на связи: вызов появится здесь и придёт уведомлением.")
            shown.forEach { o ->
                Card(color = if (o.urgent) VColors.sosBackground else VColors.surface) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (o.urgent) SosBadge(36.dp)
                            if (o.urgent) Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(offerTitle(o), color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("Язык: ${OfferText.languageName(o.language)} · ${VolText.waitedText(o.waitedSeconds)}", color = VColors.textSecondary, fontSize = 12.sp)
                            }
                            Text("${o.leftSeconds} с", color = VColors.textSecondary, fontSize = 13.sp)
                        }
                        if (!o.note.isNullOrBlank()) Text("Причина: ${o.note}", color = VColors.textSecondary, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { PrimaryButton("Отклонить", color = VColors.surfaceAlt) { scope.launch { VolunteerRepo.decline(o) } } }
                            Box(Modifier.weight(1f)) { PrimaryButton("Принять", color = VColors.success) { scope.launch { error = acceptOffer(context, o) } } }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 3. Входящий вызов ─────────────────────────
@Composable
private fun IncomingScreen(o: Offer, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().background(if (o.urgent) VColors.sosBackground else VColors.background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.4f))
        if (o.urgent) SosBadge(96.dp) else Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color(0xFF9D96FF), modifier = Modifier.size(52.dp))
        }
        Gap(16.dp)
        Text("Новое обращение", color = VColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (o.urgent) "Экстренная помощь" else "Помощь по видеосвязи", color = VColors.textSecondary, fontSize = 16.sp)
        Gap(24.dp)
        Card(color = VColors.surface.copy(alpha = 0.7f)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoLine(Icons.Filled.Language, "Язык", OfferText.languageName(o.language))
                InfoLine(Icons.Filled.Notifications, "Время", VolText.waitedText(o.waitedSeconds))
                InfoLine(Icons.Filled.Warning, "Причина", o.note?.takeIf { it.isNotBlank() } ?: "Нужна помощь по видеосвязи")
            }
        }
        Gap(10.dp)
        Text("Осталось ${o.leftSeconds} с", color = VColors.textSecondary, fontSize = 14.sp)
        ErrorText(error)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { PrimaryButton("Отклонить", enabled = !busy, color = VColors.critical) { scope.launch { VolunteerRepo.decline(o) } } }
            Box(Modifier.weight(1f)) {
                PrimaryButton("Принять", enabled = !busy, color = VColors.success) {
                    busy = true; error = null
                    scope.launch { error = acceptOffer(context, o); busy = false }
                }
            }
        }
        Text(
            "Показать в списке", color = VColors.textSecondary, fontSize = 14.sp,
            modifier = Modifier.clickable(role = Role.Button, onClick = onSnooze).padding(14.dp)
        )
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = VColors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = VColors.textSecondary, fontSize = 12.sp)
            Text(value, color = VColors.text, fontSize = 15.sp)
        }
    }
}

// ───────────────────────── 7. История вызовов ─────────────────────────
@Composable
private fun HistoryScreen() {
    var kind by remember { mutableStateOf("all") }
    val data = rememberLoad(kind) { VolunteerApi.history(kind) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("История вызовов", trailing = {
            IconButton(onClick = { MainNav.open(Page.Stats) }) { Icon(Icons.Filled.BarChart, contentDescription = "Статистика", tint = VColors.text) }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            SelectChip("Все", kind == "all") { kind = "all" }
            SelectChip("Завершённые", kind == "completed") { kind = "completed" }
            SelectChip("Отменённые", kind == "cancelled") { kind = "cancelled" }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.loading && data.data == null) EmptyState("Загружаю…")
            ErrorText(data.error)
            val list = data.data ?: emptyList()
            if (!data.loading && data.error == null && list.isEmpty()) EmptyState("Здесь появятся ваши звонки. История хранится 90 дней.")
            list.forEach { HistoryRow(it) }
        }
    }
}

// ───────────────────────── 5. Чаты ─────────────────────────
@Composable
private fun ChatsScreen() {
    val chats by VolunteerRepo.chats.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Чаты")
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Переписка во время звонка хранится только на вашем телефоне.", color = VColors.textSecondary, fontSize = 13.sp)
            if (chats.isEmpty()) EmptyState("Чаты появятся после звонков, в которых вы переписывались. Чат открывается кнопкой «Чат» во время звонка.")
            val now = System.currentTimeMillis() / 1000
            chats.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface)
                        .clickable(role = Role.Button) { MainNav.open(Page.Chat(c.requestId, c.userName)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
                        Text(c.userName.take(1).uppercase(), color = VColors.text, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.userName.ifBlank { "Пользователь" }, color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.last, color = VColors.textSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    Text(VolText.relativeTime(now, c.at), color = VColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ───────────────────────── 10. Профиль ─────────────────────────
@Composable
private fun ProfileScreen() {
    val profile by VolunteerRepo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { VolunteerRepo.refreshProfile() }
    val p = profile
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Профиль", trailing = {
            IconButton(onClick = { MainNav.open(Page.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = VColors.text) }
        })
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(VolunteerStore.avatarPath, 96.dp)
            Text(p?.name ?: VolunteerStore.name, color = VColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Волонтёр", color = VColors.textSecondary, fontSize = 14.sp)
            if (p?.online == true) Pill("На связи", VColors.success) else Pill("Не на связи", VColors.textSecondary)
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VColors.surface).padding(vertical = 14.dp)) {
                ProfileStat(Modifier.weight(1f), (p?.helpedTotal ?: 0).toString(), "Вызова")
                ProfileStat(Modifier.weight(1f), p?.rating?.toString() ?: "—", "Оценка")
                ProfileStat(Modifier.weight(1f), VolText.formatDuration(p?.helpedSecondsTotal ?: 0), "Помогал")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MenuRow(Icons.Filled.Person, "Личные данные") { MainNav.open(Page.EditPersonal) }
                MenuRow(Icons.Filled.Translate, "Мои языки", p?.languages?.joinToString(", ") { OfferText.languageName(it) }) { MainNav.open(Page.EditLanguages) }
                MenuRow(Icons.Filled.SupportAgent, "Специализация", p?.specialties?.joinToString(", ") { VolText.specialtyName(it) }?.ifBlank { "Не выбрана" }) { MainNav.open(Page.EditSpecialties) }
                MenuRow(Icons.Filled.BarChart, "Статистика") { MainNav.open(Page.Stats) }
                MenuRow(Icons.Filled.Warning, "Экстренные функции", iconColor = VColors.critical) { MainNav.open(Page.Emergency) }
                MenuRow(Icons.Filled.School, "Обучение и сертификаты", "Скоро", enabled = false)
            }
            PrimaryButton("Выйти из аккаунта", color = VColors.surfaceAlt) { logout() }
            Gap(8.dp)
        }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = VColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = VColors.textSecondary, fontSize = 12.sp)
    }
}
