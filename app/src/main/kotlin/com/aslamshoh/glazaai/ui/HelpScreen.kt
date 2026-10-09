package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.aslamshoh.glazaai.nav.LocationProvider
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.network.HelpRequestBody
import com.aslamshoh.glazaai.network.HelpService
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.HelpStore
import com.aslamshoh.glazaai.store.SettingsStore
import com.aslamshoh.glazaai.util.HelpText
import com.aslamshoh.glazaai.util.TrustedContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Что сейчас показано на экране «Помощь». */
private sealed interface HelpPhase {
    object Menu : HelpPhase
    data class Consent(val urgent: Boolean) : HelpPhase
    data class Waiting(val id: Int, val urgent: Boolean) : HelpPhase
    data class Connected(val id: Int, val url: String, val name: String?) : HelpPhase
    data class Rate(val id: Int, val name: String?) : HelpPhase
    data class Notice(val text: String, val urgent: Boolean) : HelpPhase
}

/**
 * «Помощь»: позвать волонтёра по видео, позвонить близкому, сообщить близкому, где вы, или набрать
 * экстренную службу. Видеозвонок открывается в приложении Jitsi Meet (бесплатное, ссылка на комнату
 * секретная и одноразовая). Номер экстренной службы можно поменять — он зависит от страны.
 */
@Composable
fun HelpScreen(autoStart: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location = remember { LocationProvider(context) }

    var phase by remember { mutableStateOf<HelpPhase>(HelpPhase.Menu) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun say(text: String) = SpeechSynthesizer.speak(text, SettingsStore.speechRate)

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasLocation by remember {
        mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result.values.any { it }
        if (hasLocation) location.start()
    }

    // Если человек ушёл с экрана, пока шёл поиск, — снимаем запрос, чтобы волонтёру не звонили впустую.
    val waitingId = remember { intArrayOf(-1) }
    DisposableEffect(Unit) {
        onDispose {
            location.stop()
            if (waitingId[0] >= 0) {
                val id = waitingId[0]
                CoroutineScope(Dispatchers.IO).launch {
                    try { HelpService.cancel(id, HelpStore.deviceId) } catch (_: Exception) {}
                }
            }
        }
    }

    fun dial(number: String) {
        val uri = HelpText.dialUri(number)
        if (uri == null) {
            say("Не удалось набрать номер.")
            return
        }
        // ACTION_DIAL только открывает набор номера: разрешение на звонки не нужно, и человек сам нажимает «позвонить».
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openRoom(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            say("Не нашла программу для видеозвонка. Установите бесплатное приложение Jitsi Meet и повторите.")
        }
    }

    fun tellWhereIAm(contact: TrustedContact) {
        if (hasLocation) location.start()
        val p = location.point
        val text = HelpText.whereAmIText(SettingsStore.testerName, p?.lat, p?.lon)
        val uri = HelpText.smsUri(contact.phone) ?: return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            if (p == null) say("Открываю сообщение. Положение определить не удалось, в тексте только просьба позвонить.")
            else say("Открываю сообщение с вашим положением. Нажмите «отправить».")
        } catch (e: ActivityNotFoundException) {
            say("Не нашла программу для сообщений.")
        }
    }

    fun startRequest(urgent: Boolean) {
        if (busy) return
        if (!HelpStore.consent) {
            phase = HelpPhase.Consent(urgent)
            say(HelpText.CONSENT + " Нажмите «Согласен», чтобы продолжить.")
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                var lat: Double? = null
                var lon: Double? = null
                if (urgent && hasLocation) {
                    location.start()
                    var waited = 0
                    while (location.point == null && waited < 3000) {
                        delay(250)
                        waited += 250
                    }
                    lat = location.point?.lat
                    lon = location.point?.lon
                }
                val name = SettingsStore.testerName.trim().ifEmpty { "Пользователь" }
                val st = HelpService.request(
                    HelpRequestBody(HelpStore.deviceId, name, SettingsStore.appLanguage, urgent, lat, lon)
                )
                if (st.status == "accepted" && st.roomUrl != null) {
                    phase = HelpPhase.Connected(st.requestId, st.roomUrl, st.volunteerName)
                    say(HelpText.accepted(st.volunteerName))
                    openRoom(st.roomUrl)
                } else {
                    waitingId[0] = st.requestId
                    phase = HelpPhase.Waiting(st.requestId, urgent)
                    HelpText.waiting(st.volunteersOnline, 0)?.let { say(it) }
                }
            } catch (e: Exception) {
                val m = ApiClient.messageFor(e)
                error = m
                say("Не получилось позвать волонтёра. $m")
            } finally {
                busy = false
            }
        }
    }

    // ── опрос состояния вызова ──
    val current = phase
    if (current is HelpPhase.Waiting) {
        LaunchedEffect(current.id) {
            var failures = 0
            while (true) {
                delay(2000)
                val st = try {
                    HelpService.status(current.id, HelpStore.deviceId).also { failures = 0 }
                } catch (e: Exception) {
                    failures++
                    if (failures >= 5) {
                        waitingId[0] = -1
                        phase = HelpPhase.Notice("Пропала связь с сервером. Позвоните близкому или в экстренную службу.", current.urgent)
                        say("Пропала связь с сервером. Позвоните близкому или в экстренную службу.")
                        return@LaunchedEffect
                    }
                    null
                } ?: continue
                when (st.status) {
                    "accepted" -> {
                        waitingId[0] = -1
                        val url = st.roomUrl
                        if (url != null) {
                            phase = HelpPhase.Connected(st.requestId, url, st.volunteerName)
                            say(HelpText.accepted(st.volunteerName))
                            openRoom(url)
                        }
                        return@LaunchedEffect
                    }
                    "expired" -> {
                        waitingId[0] = -1
                        val text = HelpText.expired(HelpStore.contacts.isNotEmpty(), current.urgent)
                        phase = HelpPhase.Notice(text, current.urgent)
                        say(text)
                        return@LaunchedEffect
                    }
                    "cancelled", "finished" -> {
                        waitingId[0] = -1
                        phase = HelpPhase.Menu
                        return@LaunchedEffect
                    }
                    else -> HelpText.waiting(st.volunteersOnline, st.waitedSeconds)?.let { say(it) }
                }
            }
        }
    }
    // пока идёт звонок, следим, не завершил ли его волонтёр
    if (current is HelpPhase.Connected) {
        LaunchedEffect(current.id) {
            while (true) {
                delay(5000)
                val st = try { HelpService.status(current.id, HelpStore.deviceId) } catch (e: Exception) { null } ?: continue
                if (st.status == "finished" || st.status == "cancelled") {
                    phase = HelpPhase.Rate(current.id, current.name)
                    say("Звонок завершён. Оцените помощь: выберите от одного до пяти.")
                    return@LaunchedEffect
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (autoStart) startRequest(false) else say("Раздел «Помощь». Можно позвать волонтёра, позвонить близкому или набрать экстренную службу.")
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Theme.surface,
        unfocusedContainerColor = Theme.surface,
        focusedIndicatorColor = Theme.accent,
        unfocusedIndicatorColor = Theme.divider,
        cursorColor = Theme.accent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary)
            }
            Text("Помощь", color = Theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        when (val p = phase) {
            is HelpPhase.Consent -> {
                Text(HelpText.CONSENT, color = Theme.textPrimary, fontSize = 16.sp)
                HelpButton("Согласен, позвать волонтёра", Theme.accent, Icons.Filled.Call) {
                    HelpStore.giveConsent()
                    phase = HelpPhase.Menu
                    startRequest(p.urgent)
                }
                HelpButton("Отмена", Theme.surfaceAlt, null) { phase = HelpPhase.Menu }
            }

            is HelpPhase.Waiting -> {
                Text(
                    if (p.urgent) "Срочный вызов. Ищу волонтёра…" else "Ищу свободного волонтёра…",
                    color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text("Обычно это занимает меньше минуты. Я скажу, когда кто-то ответит.", color = Theme.textSecondary, fontSize = 14.sp)
                HelpButton("Отменить поиск", Theme.surfaceAlt, null) {
                    val id = p.id
                    waitingId[0] = -1
                    phase = HelpPhase.Menu
                    scope.launch { try { HelpService.cancel(id, HelpStore.deviceId) } catch (_: Exception) {} }
                    say("Поиск отменён.")
                }
                EmergencyAndContacts(::dial, ::tellWhereIAm, showContacts = true)
            }

            is HelpPhase.Connected -> {
                Text(
                    "Видеозвонок" + (p.name?.let { " с $it" } ?: ""),
                    color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Если видеозвонок закрылся, нажмите «Открыть звонок снова». Нужна бесплатная программа Jitsi Meet.",
                    color = Theme.textSecondary, fontSize = 14.sp
                )
                HelpButton("Открыть звонок снова", Theme.accent, Icons.Filled.Call) { openRoom(p.url) }
                HelpButton("Завершить звонок", Theme.surfaceAlt, null) {
                    scope.launch { try { HelpService.cancel(p.id, HelpStore.deviceId) } catch (_: Exception) {} }
                    phase = HelpPhase.Rate(p.id, p.name)
                    say("Звонок завершён. Оцените помощь: выберите от одного до пяти.")
                }
                EmergencyAndContacts(::dial, ::tellWhereIAm, showContacts = false)
            }

            is HelpPhase.Rate -> {
                Text("Как всё прошло? Оцените помощь от 1 до 5.", color = Theme.textPrimary, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    (1..5).forEach { n ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Theme.accent)
                                .clickable(onClickLabel = "Оценка $n из 5", role = Role.Button) {
                                    scope.launch { try { HelpService.rate(p.id, HelpStore.deviceId, n) } catch (_: Exception) {} }
                                    phase = HelpPhase.Menu
                                    say(HelpText.rated(n))
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("$n", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                HelpButton("Пожаловаться на волонтёра", Theme.surfaceAlt, Icons.Filled.Warning) {
                    scope.launch { try { HelpService.report(p.id, HelpStore.deviceId, "Жалоба из приложения") } catch (_: Exception) {} }
                    phase = HelpPhase.Menu
                    say("Жалоба отправлена. Администратор проверит её. Спасибо.")
                }
                HelpButton("Пропустить", Theme.surfaceAlt, null) { phase = HelpPhase.Menu }
            }

            is HelpPhase.Notice -> {
                Text(p.text, color = Theme.textPrimary, fontSize = 17.sp)
                HelpButton("Позвать волонтёра ещё раз", Theme.accent, Icons.Filled.Call) {
                    phase = HelpPhase.Menu
                    startRequest(p.urgent)
                }
                EmergencyAndContacts(::dial, ::tellWhereIAm, showContacts = true)
                HelpButton("В меню помощи", Theme.surfaceAlt, null) { phase = HelpPhase.Menu }
            }

            HelpPhase.Menu -> {
                Text(HelpText.DISCLAIMER, color = Theme.textSecondary, fontSize = 14.sp)
                error?.let { ErrorBanner(it) }
                HelpButton("Позвать волонтёра", Theme.accent, Icons.Filled.Call, enabled = !busy) { startRequest(false) }
                HelpButton("Срочный вызов с геопозицией", Theme.warning, Icons.Filled.Warning, enabled = !busy) {
                    if (!hasLocation) {
                        say("Для срочного вызова нужен доступ к местоположению. Разрешите его в окне.")
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else startRequest(true)
                }
                EmergencyAndContacts(::dial, ::tellWhereIAm, showContacts = true)
                ContactsEditor(fieldColors)
            }
        }
        Box(modifier = Modifier.padding(bottom = 24.dp))
    }
}

/** Экстренная служба и близкие — доступны на каждом шаге, потому что при тревоге именно это нужно быстрее всего. */
@Composable
private fun EmergencyAndContacts(
    onDial: (String) -> Unit,
    onWhere: (TrustedContact) -> Unit,
    showContacts: Boolean
) {
    HelpButton("Экстренная служба: ${HelpStore.emergencyNumber}", Theme.critical, Icons.Filled.Call) { onDial(HelpStore.emergencyNumber) }
    if (!showContacts) return
    if (HelpStore.contacts.isEmpty()) {
        Text("Добавьте близких ниже — тогда им можно будет позвонить одним нажатием.", color = Theme.textSecondary, fontSize = 14.sp)
        return
    }
    HelpStore.contacts.forEach { c ->
        HelpButton("Позвонить: ${c.name}", Theme.surfaceAlt, Icons.Filled.Call) { onDial(c.phone) }
        ActionButton(
            "Отправить ${c.name}, где я",
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { onWhere(c) }
    }
}

@Composable
private fun ContactsEditor(fieldColors: androidx.compose.material3.TextFieldColors) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var emergency by remember { mutableStateOf(HelpStore.emergencyNumber) }

    Text("Близкие люди", color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    HelpStore.contacts.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${c.name} · ${c.phone}", color = Theme.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            ActionButton("Удалить") { HelpStore.removeContact(c) }
        }
    }
    OutlinedTextField(
        value = name, onValueChange = { name = it }, label = { Text("Имя") },
        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = phone, onValueChange = { phone = it }, label = { Text("Телефон, например +992…") },
        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
    )
    message?.let { Text(it, color = Theme.critical, fontSize = 14.sp) }
    ActionButton("Добавить близкого", filled = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        val err = HelpStore.addContact(name, phone)
        message = err
        if (err == null) {
            name = ""
            phone = ""
            SpeechSynthesizer.speak("Добавлено.", SettingsStore.speechRate)
        } else SpeechSynthesizer.speak(err, SettingsStore.speechRate)
    }
    Text(
        "Номер экстренной службы зависит от страны. Проверьте его и при необходимости измените.",
        color = Theme.textSecondary, fontSize = 13.sp
    )
    OutlinedTextField(
        value = emergency,
        onValueChange = { emergency = it; HelpStore.updateEmergencyNumber(it) },
        label = { Text("Номер экстренной службы") },
        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HelpButton(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Theme.surfaceAlt)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Box(modifier = Modifier.padding(start = 10.dp))
        }
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
