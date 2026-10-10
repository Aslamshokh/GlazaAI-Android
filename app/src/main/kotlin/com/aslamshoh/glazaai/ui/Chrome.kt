package com.aslamshoh.glazaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

/** Маршруты приложения. */
object Routes {
    const val HOME = "home"
    const val NAV = "nav"
    const val BARCODE = "scan/barcode"
    const val QR = "scan/qr"
    const val TEXT = "scan/text"
    const val CURRENCY = "scan/currency"
    const val FIND = "find"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val OFFLINE = "offline"
    const val MEMORY = "memory"
    const val DOCUMENT = "scan/document"
    const val FEEDBACK = "feedback"
    const val INSPECT = "scan/inspect"
    const val HELP = "help"
    const val SHARED = "shared"
}

/** Пять вкладок нижнего меню. */
enum class AppTab(val route: String, val label: String, val filled: ImageVector, val outlined: ImageVector) {
    HOME(Routes.HOME, "Главная", Icons.Filled.Home, Icons.Outlined.Home),
    NAV(Routes.NAV, "Навигация", Icons.Filled.NearMe, Icons.Outlined.NearMe),
    SCAN(Routes.BARCODE, "Сканер", Icons.Filled.CenterFocusStrong, Icons.Outlined.CenterFocusStrong),
    HISTORY(Routes.HISTORY, "История", Icons.Filled.History, Icons.Outlined.History),
    PROFILE(Routes.PROFILE, "Профиль", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun GlazaBottomBar(selected: AppTab?, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = Theme.bar, contentColor = Theme.textSecondary, tonalElevation = 0.dp) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.filled else tab.outlined,
                        contentDescription = null
                    )
                },
                label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Theme.accent,
                    selectedTextColor = Theme.accent,
                    unselectedIconColor = Theme.textSecondary,
                    unselectedTextColor = Theme.textSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

/** Режимы на главном экране (вторая строка снизу на макете). */
enum class HomeChip(val label: String, val icon: ImageVector) {
    OBJECTS("Объекты", Icons.Outlined.ViewInAr),
    TEXT("Текст", Icons.Outlined.TextFields),
    PRODUCT("Товар", Icons.Outlined.ShoppingBag),
    CURRENCY("Валюта", Icons.Outlined.Payments),
    HAND("В руке", Icons.Outlined.PanTool),
    FIND("Поиск", Icons.Outlined.Search)
}

@Composable
fun ModeChipRow(selected: HomeChip, onSelect: (HomeChip) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Theme.bar.copy(alpha = 0.92f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeChip.entries.forEach { chip ->
            val isSelected = chip == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) Theme.accentSoft else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.5.dp, Theme.accent, shape) else Modifier)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(chip) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    chip.icon,
                    contentDescription = null,
                    tint = if (isSelected) Theme.accent else Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    chip.label,
                    color = if (isSelected) Color.White else Theme.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Переключатель режимов сканера (вкладка «Сканер»). */
enum class ScanMode(val label: String, val route: String) {
    PRODUCT("Товар", Routes.BARCODE),
    TEXT("Текст", Routes.TEXT),
    CURRENCY("Валюта", Routes.CURRENCY),
    QR("QR", Routes.QR),
    DOC("Чек", Routes.DOCUMENT),
    HAND("В руке", Routes.INSPECT)
}

@Composable
fun ScanModeSwitch(selected: ScanMode, onSelect: (ScanMode) -> Unit, modifier: Modifier = Modifier) {
    // Режимов шесть — на узком экране не помещаются, поэтому ряд прокручивается; выбранный
    // режим при открытии экрана подводится в видимую область.
    val scroll = rememberScrollState()
    LaunchedEffect(selected, scroll.maxValue) {
        val last = (ScanMode.entries.size - 1).coerceAtLeast(1)
        scroll.scrollTo(scroll.maxValue * ScanMode.entries.indexOf(selected) / last)
    }
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Theme.bar.copy(alpha = 0.85f))
            .horizontalScroll(scroll)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ScanMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Theme.accent else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(mode) })
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (isSelected) Color.White else Theme.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Theme.surfaceAlt,
    tint: Color = Color.White,
    iconSize: Dp = 26.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Верхняя панель поверх камеры: стрелка назад / заголовок / шестерёнка. */
@Composable
fun CameraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        }
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
        if (onHelp != null) {
            IconButton(onClick = onHelp, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Outlined.SupportAgent, contentDescription = "Помощь: позвать волонтёра или близкого", tint = Color.White)
            }
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = Color.White)
            }
        }
    }
}

/** Угловые скобки «рамка прицеливания» (сканер, текст, купюра, поиск). */
@Composable
fun BracketFrame(
    modifier: Modifier = Modifier,
    color: Color = Theme.accent,
    strokeWidth: Dp = 4.dp,
    arm: Dp = 34.dp
) {
    Canvas(modifier = modifier) {
        val w = strokeWidth.toPx()
        val a = arm.toPx()
        val half = w / 2f
        val left = half
        val top = half
        val right = size.width - half
        val bottom = size.height - half
        drawLine(color, Offset(left, top), Offset(left + a, top), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left, top + a), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right - a, top), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right, top + a), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left + a, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left, bottom - a), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right - a, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - a), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/** Рамка найденного предмета с подписью «название + расстояние». */
@Composable
fun DetectionBox(
    label: String,
    distance: String?,
    color: Color,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    selected: Boolean,
    onClick: () -> Unit
) {
    val description = if (distance != null) "$label, $distance" else label
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = width, height = height)
            .border(if (selected) 3.dp else 2.dp, color, RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Подробнее: $description", onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0A1020))
                .border(1.dp, color, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(label.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (distance != null) {
                Text(distance, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Крупная кнопка действия: «Читать», «Озвучить», «Подробнее»… */
@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) Theme.accent else Theme.surfaceAlt)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (enabled) Color.White else Theme.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Нижняя карточка с результатом (тёмная, скруглённая). */
@Composable
fun GlassSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusLarge))
            .background(Theme.surface.copy(alpha = 0.96f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Квадратная миниатюра: снимок или значок-заглушка. */
@Composable
fun BitmapThumb(bitmap: Bitmap?, size: Dp, modifier: Modifier = Modifier, fallback: ImageVector = Icons.Outlined.Image) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(fallback, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(size / 2))
        }
    }
}

/** «2.1 м» — как на макете (точка, а не запятая). */
fun formatMeters(distance: Double?): String? =
    distance?.let { String.format(Locale.US, "%.1f м", it) }

/** Запрашивает доступ к камере один раз и возвращает, выдан ли он. */
@Composable
fun rememberCameraPermission(): Boolean {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    return granted
}

/** Текст по центру на месте камеры: ошибка, «нужен доступ» или крутилка, пока камера стартует. */
@Composable
fun CameraHint(text: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(
                text,
                color = Theme.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        } else {
            CircularProgressIndicator(color = Theme.accent)
        }
    }
}

/** Короткая вибрация при критической опасности. */
fun vibrateAlert(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
}
