package com.aslamshoh.glazaai.volunteer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.volunteer.OfferText
import com.aslamshoh.glazaai.volunteer.VolText
import com.aslamshoh.glazaai.volunteer.VolunteerRepo
import com.aslamshoh.glazaai.volunteer.net.ApiError
import com.aslamshoh.glazaai.volunteer.net.ProfileBody
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AuthHost() {
    when (val step = AuthNav.current) {
        AuthStep.Welcome -> WelcomeScreen()
        AuthStep.Phone -> PhoneScreen()
        AuthStep.Code -> CodeScreen()
        AuthStep.Personal -> PersonalScreen()
        AuthStep.ProfileSetup -> ProfileSetupScreen()
        AuthStep.VerifyMenu -> VerifyMenuScreen()
        AuthStep.DocPhoto -> DocPhotoScreen()
        AuthStep.Selfie -> SelfieScreen()
        AuthStep.FaceCheck -> FaceCheckScreen()
        AuthStep.Terms -> TermsScreen()
        AuthStep.Success -> SuccessScreen()
        is AuthStep.Legal -> LegalPage(step.doc) { AuthNav.pop() }
    }
}

/** Общий каркас шага регистрации: стрелка назад, заголовок, прокручиваемое содержимое и кнопки внизу. */
@Composable
private fun AuthPage(
    title: String,
    subtitle: String? = null,
    canGoBack: Boolean = true,
    bottom: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopBar("", onBack = if (canGoBack && AuthNav.stack.size > 1) ({ AuthNav.pop() }) else null)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = VColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, color = VColors.textSecondary, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            content()
            Gap(8.dp)
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
    }
}

// ───────────────────────── 1. Приветствие ─────────────────────────
@Composable
private fun WelcomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1660), VColors.background)))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(104.dp).clip(CircleShape).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFF8A82FF), modifier = Modifier.size(60.dp))
        }
        Gap(18.dp)
        Text("Eyes AI", color = VColors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Volunteer", color = VColors.text, fontSize = 22.sp)
        Gap(14.dp)
        Text(
            "Помогай людям в экстренных ситуациях",
            color = VColors.text, fontSize = 17.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("Начать") { RegState.loginMode = false; AuthNav.push(AuthStep.Phone) }
            GhostButton("Уже есть аккаунт") { RegState.loginMode = true; AuthNav.push(AuthStep.Phone) }
        }
    }
}

// ───────────────────────── 3. Номер телефона ─────────────────────────
@Composable
private fun PhoneScreen() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCountry by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(VolunteerStore.serverUrl) }
    var showServer by remember { mutableStateOf(VolunteerStore.serverUrl.isEmpty()) }
    val notice by VolunteerRepo.message.collectAsState()

    AuthPage(
        title = if (RegState.loginMode) "Вход" else "Регистрация",
        subtitle = "Введите номер телефона" + if (RegState.loginMode) " для входа" else " для создания аккаунта",
        bottom = {
            ErrorText(error ?: notice)
            PrimaryButton("Получить код", enabled = !busy) {
                val problem = VolText.phoneProblem(RegState.country.dial, RegState.national)
                if (problem != null) { error = problem; return@PrimaryButton }
                busy = true; error = null
                scope.launch {
                    try {
                        RegState.devCode = VolunteerRepo.requestCode(RegState.phone)
                        VolunteerRepo.showMessage(null)
                        AuthNav.push(AuthStep.Code)
                    } catch (e: Exception) { error = messageOf(e) } finally { busy = false }
                }
            }
            if (!RegState.loginMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "Нажимая «Получить код», вы соглашаетесь с ",
                        color = VColors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "Условиями использования", color = VColors.accent, fontSize = 12.sp,
                        modifier = Modifier.clickable(role = Role.Button) { AuthNav.push(AuthStep.Legal(Doc.AGREEMENT)) }.padding(6.dp)
                    )
                    Text(" и ", color = VColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        "Политикой конфиденциальности", color = VColors.accent, fontSize = 12.sp,
                        modifier = Modifier.clickable(role = Role.Button) { AuthNav.push(AuthStep.Legal(Doc.PRIVACY)) }.padding(6.dp)
                    )
                }
            }
        }
    ) {
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(VColors.surface)
                    .clickable(role = Role.Button, onClickLabel = "Выбрать страну") { pickCountry = !pickCountry }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("${RegState.country.flag} ${RegState.country.dial} ▾", color = VColors.text, fontSize = 16.sp) }
            VField(RegState.national, { RegState.national = it.filter { c -> c.isDigit() || c == ' ' || c == '-' }.take(16) },
                "Номер телефона", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        if (pickCountry) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VolText.COUNTRIES.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VColors.surface)
                            .clickable(role = Role.Button) { RegState.country = c; RegState.countryName = c.name; pickCountry = false }
                            .heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${c.flag}  ${c.name}", color = VColors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(c.dial, color = VColors.textSecondary, fontSize = 15.sp)
                    }
                }
            }
        }
        if (showServer) {
            VField(server, { server = it; VolunteerStore.updateServerUrl(it) }, "Адрес сервера, например https://…", keyboardType = KeyboardType.Uri)
        } else {
            Text(
                "Изменить адрес сервера", color = VColors.textSecondary, fontSize = 13.sp,
                modifier = Modifier.clickable(role = Role.Button) { showServer = true }.padding(vertical = 8.dp)
            )
        }
    }
}

// ───────────────────────── 4. Код ─────────────────────────
@Composable
private fun CodeBoxes(value: String, onChange: (String) -> Unit, length: Int = 6) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    repeat(length) { i ->
                        val active = i == value.length
                        Box(
                            modifier = Modifier.size(width = 46.dp, height = 58.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (active) VColors.accent else VColors.divider).padding(if (active) 2.dp else 1.dp)
                                .clip(RoundedCornerShape(11.dp)).background(VColors.surface),
                            contentAlignment = Alignment.Center
                        ) { Text(value.getOrNull(i)?.toString() ?: "", color = VColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Box(modifier = Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        }
    )
}

@Composable
private fun CodeScreen() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var left by remember { mutableStateOf(45) }
    var round by remember { mutableStateOf(0) }

    LaunchedEffect(round) {
        left = 45
        while (left > 0) { delay(1000); left-- }
    }

    fun submit(c: String) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val p = VolunteerRepo.verifyCode(RegState.phone, c)
                if (p.profileComplete) {
                    // вход в существующий аккаунт — главный экран откроется сам
                    RegState.clearSensitive()
                    MainNav.reset()
                } else {
                    if (p.name.isNotBlank() && p.name != "Волонтёр" && RegState.name.isBlank()) RegState.name = p.name
                    AuthNav.reset(AuthStep.Personal)
                }
            } catch (e: Exception) {
                error = messageOf(e); code = ""
            } finally { busy = false }
        }
    }

    AuthPage(
        title = "Введите код",
        subtitle = "Мы отправили код на номер\n${RegState.country.dial} ${RegState.national}",
        bottom = {
            ErrorText(error)
            if (left > 0) {
                Text("Отправить код повторно через ${VolText.timer(left.toLong())}", color = VColors.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                Text(
                    "Отправить код ещё раз", color = VColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        scope.launch {
                            try { RegState.devCode = VolunteerRepo.requestCode(RegState.phone); round++; error = null } catch (e: Exception) { error = messageOf(e) }
                        }
                    }.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) {
        Gap(16.dp)
        CodeBoxes(code, { code = it; if (it.length == 6) submit(it) })
        RegState.devCode?.let {
            Text("Тестовый режим сервера: код $it", color = VColors.warning, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        if (busy) Text("Проверяю…", color = VColors.textSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── 5. Личные данные ─────────────────────────
@Composable
private fun PersonalScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !saveAvatarFromUri(context, uri)) error = "Не удалось открыть фото."
    }

    AuthPage(
        title = "Личные данные",
        canGoBack = false,
        bottom = {
            ErrorText(error)
            PrimaryButton("Продолжить") {
                val p = VolText.personalProblem(RegState.name, RegState.email, RegState.birth, LocalDate.now())
                if (p != null) { error = p; return@PrimaryButton }
                error = null
                AuthNav.push(AuthStep.ProfileSetup)
            }
            Text("Эти данные будут видны только администрации", color = VColors.textSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Добавить фото") { picker.launch("image/*") },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(VolunteerStore.avatarPath, 110.dp)
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(VColors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Gap(6.dp)
            Text("Добавить фото", color = VColors.text, fontSize = 15.sp)
            Text("(необязательно)", color = VColors.textSecondary, fontSize = 12.sp)
        }
        VField(RegState.name, { RegState.name = it.take(40) }, "Имя", Icons.Filled.Person)
        VField(RegState.email, { RegState.email = it.take(80) }, "Электронная почта (необязательно)", Icons.Filled.Email, KeyboardType.Email)
        VField(RegState.birth, { RegState.birth = VolText.formatBirthInput(it) }, "Дата рождения, например 10.07.2002", Icons.Filled.CalendarMonth, KeyboardType.Number)
    }
}

// ───────────────────────── 6. Профиль волонтёра ─────────────────────────
@Composable
private fun ProfileSetupScreen() {
    var error by remember { mutableStateOf<String?>(null) }
    var pickCountry by remember { mutableStateOf(false) }
    AuthPage(
        title = "Профиль волонтёра",
        subtitle = "Расскажите немного о себе",
        bottom = {
            ErrorText(error)
            PrimaryButton("Продолжить") {
                if (RegState.languages.isEmpty()) { error = "Выберите хотя бы один язык."; return@PrimaryButton }
                error = null
                AuthNav.push(AuthStep.VerifyMenu)
            }
        }
    ) {
        SectionLabelSmall("Страна")
        CountryField(RegState.countryName, pickCountry, { pickCountry = !pickCountry }) { RegState.countryName = it; pickCountry = false }
        SectionLabelSmall("Город")
        VField(RegState.city, { RegState.city = it.take(60) }, "Например, Душанбе")
        SectionLabelSmall("Языки")
        ChipGrid(OfferText.LANGUAGES, RegState.languages, perRow = 2) { c ->
            RegState.languages = if (c in RegState.languages) RegState.languages - c else RegState.languages + c
        }
        SectionLabelSmall("Специализация")
        ChipGrid(VolText.SPECIALTIES, RegState.specialties, perRow = 2) { c ->
            RegState.specialties = if (c in RegState.specialties) RegState.specialties - c else RegState.specialties + c
        }
    }
}

@Composable
fun SectionLabelSmall(text: String) {
    Text(text, color = VColors.textSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
}

/** Поле выбора страны: нажатие раскрывает список прямо под полем. */
@Composable
fun CountryField(selected: String, open: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val flag = VolText.COUNTRIES.firstOrNull { it.name == selected }?.flag ?: "🌍"
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface)
                .clickable(role = Role.Button, onClickLabel = "Выбрать страну", onClick = onToggle).heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$flag  $selected", color = VColors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("▾", color = VColors.textSecondary, fontSize = 16.sp)
        }
        if (open) {
            VolText.COUNTRIES.forEach { c ->
                Text(
                    "${c.flag}  ${c.name}", color = VColors.text, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VColors.surfaceAlt)
                        .clickable(role = Role.Button) { onPick(c.name) }.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ───────────────────────── 7–10. Проверка личности ─────────────────────────
@Composable
private fun VerifyMenuScreen() {
    AuthPage(
        title = "Проверка личности",
        subtitle = "Для безопасности пользователей необходимо подтвердить личность",
        bottom = {
            PrimaryButton("Продолжить") { AuthNav.push(AuthStep.Terms) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = VColors.success, modifier = Modifier.size(22.dp))
                Text(
                    "Снимки остаются на вашем телефоне и никуда не отправляются. Подтверждение администратором будет подключено позже.",
                    color = VColors.textSecondary, fontSize = 12.sp
                )
            }
        }
    ) {
        VerifyRow(Icons.Filled.Badge, "Паспорт / ID", "Сфотографируйте документ", RegState.docPhoto != null) { AuthNav.push(AuthStep.DocPhoto) }
        VerifyRow(Icons.Filled.Person, "Селфи", "Сфотографируйте себя", RegState.selfie != null) { AuthNav.push(AuthStep.Selfie) }
        VerifyRow(Icons.Filled.Face, "Проверка лица", "Сделайте снимок с поворотом головы", RegState.faceShot != null) { AuthNav.push(AuthStep.FaceCheck) }
    }
}

@Composable
private fun VerifyRow(icon: ImageVector, title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF9D96FF))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = VColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(if (done) "Готово" else subtitle, color = if (done) VColors.success else VColors.textSecondary, fontSize = 12.sp)
        }
        Icon(if (done) Icons.Filled.Check else Icons.Filled.ChevronRight, contentDescription = null, tint = if (done) VColors.success else VColors.textSecondary)
    }
}

@Composable
private fun PhotoFrame(bitmap: android.graphics.Bitmap?, oval: Boolean, placeholder: String, onTake: () -> Unit) {
    val shape = if (oval) RoundedCornerShape(150.dp) else RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier.fillMaxWidth().height(if (oval) 340.dp else 220.dp).clip(shape).background(VColors.accentSoft)
            .clickable(role = Role.Button, onClickLabel = placeholder, onClick = onTake),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Снимок", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(VColors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Gap(10.dp)
                Text(placeholder, color = VColors.text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun DocPhotoScreen() {
    val shot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b -> if (b != null) RegState.docPhoto = b }
    AuthPage(
        title = "Фото документа",
        subtitle = "Сфотографируйте паспорт или ID",
        bottom = {
            PrimaryButton("Продолжить", enabled = RegState.docPhoto != null) { AuthNav.pop() }
            GhostButton(if (RegState.docPhoto == null) "Сделать фото" else "Переснять") { shot.launch(null) }
        }
    ) {
        PhotoFrame(RegState.docPhoto, false, "Нажмите, чтобы сделать фото") { shot.launch(null) }
        Checklist(listOf("Все данные читаемы", "Хорошее освещение", "Без бликов и обрезки"))
    }
}

@Composable
private fun Checklist(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(VColors.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text(it, color = VColors.text, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SelfieScreen() {
    val shot = rememberLauncherForActivityResult(FrontCameraPreview()) { b -> if (b != null) RegState.selfie = b }
    AuthPage(
        title = "Селфи",
        subtitle = "Сделайте селфи, чтобы подтвердить, что это вы",
        bottom = {
            PrimaryButton("Продолжить", enabled = RegState.selfie != null) { AuthNav.pop() }
            GhostButton(if (RegState.selfie == null) "Сделать снимок" else "Переснять") { shot.launch(null) }
        }
    ) { PhotoFrame(RegState.selfie, true, "Нажмите, чтобы сделать селфи") { shot.launch(null) } }
}

@Composable
private fun FaceCheckScreen() {
    val shot = rememberLauncherForActivityResult(FrontCameraPreview()) { b -> if (b != null) RegState.faceShot = b }
    AuthPage(
        title = "Проверка лица",
        subtitle = "Медленно поверните голову вправо и сделайте снимок",
        bottom = {
            PrimaryButton("Продолжить", enabled = RegState.faceShot != null) { AuthNav.pop() }
            GhostButton(if (RegState.faceShot == null) "Сделать снимок" else "Переснять") { shot.launch(null) }
        }
    ) { PhotoFrame(RegState.faceShot, true, "Нажмите, чтобы сделать снимок") { shot.launch(null) } }
}

// ───────────────────────── 11. Условия ─────────────────────────
@Composable
private fun TermsScreen() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AuthPage(
        title = "Условия использования",
        bottom = {
            ErrorText(error)
            PrimaryButton("Создать аккаунт", enabled = RegState.acceptTerms && RegState.acceptData && !busy) {
                busy = true; error = null
                scope.launch {
                    try {
                        val body = ProfileBody(
                            name = RegState.name.trim(), birthDate = RegState.birth, languages = RegState.languages.toList(),
                            acceptTerms = true, email = RegState.email.trim().ifEmpty { null },
                            country = RegState.countryName, city = RegState.city.trim().ifEmpty { null },
                            specialties = RegState.specialties.toList()
                        )
                        RegState.inSuccess = true
                        VolunteerRepo.saveProfile(body)
                        RegState.clearSensitive()
                        AuthNav.reset(AuthStep.Success)
                    } catch (e: Exception) {
                        RegState.inSuccess = false
                        error = messageOf(e)
                        // ошибка в данных — вернём на нужный шаг
                        if (e is ApiError && e.status == 400) AuthNav.reset(AuthStep.Personal)
                    } finally { busy = false }
                }
            }
        }
    ) {
        MenuRow(Icons.Filled.Description, Doc.AGREEMENT.title) { AuthNav.push(AuthStep.Legal(Doc.AGREEMENT)) }
        MenuRow(Icons.Filled.Shield, Doc.PRIVACY.title) { AuthNav.push(AuthStep.Legal(Doc.PRIVACY)) }
        MenuRow(Icons.Filled.VerifiedUser, Doc.RULES.title) { AuthNav.push(AuthStep.Legal(Doc.RULES)) }
        Gap(4.dp)
        CheckLine("Я согласен с условиями использования", RegState.acceptTerms) { RegState.acceptTerms = it }
        CheckLine("Я согласен с обработкой персональных данных", RegState.acceptData) { RegState.acceptData = it }
    }
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, color = VColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────── 12. Успех ─────────────────────────
@Composable
private fun SuccessScreen() {
    AuthPage(
        title = "Аккаунт создан!",
        subtitle = "Добро пожаловать в команду Eyes AI Volunteer",
        canGoBack = false,
        bottom = { PrimaryButton("Перейти в приложение") { RegState.inSuccess = false; MainNav.reset() } }
    ) {
        Gap(8.dp)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(110.dp).clip(CircleShape).background(VColors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF9D96FF), modifier = Modifier.size(64.dp))
            }
        }
        Gap(8.dp)
        MenuRow(Icons.Filled.SupportAgent, "Вы можете принимать экстренные обращения", enabled = false)
        MenuRow(Icons.Filled.Person, "Помогать людям онлайн", enabled = false)
        MenuRow(Icons.Filled.Favorite, "Делать мир доступнее", enabled = false)
    }
}
