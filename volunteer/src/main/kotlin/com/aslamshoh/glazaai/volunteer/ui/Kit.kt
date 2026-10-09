package com.aslamshoh.glazaai.volunteer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Палитра приложения «Eyes AI Volunteer» (тёмно-фиолетовая, по макету). */
object VColors {
    val background = Color(0xFF0A0A1F)
    val surface = Color(0xFF14142E)
    val surfaceAlt = Color(0xFF1E1E3C)
    val accent = Color(0xFF5B4FF5)
    val accentSoft = Color(0xFF2B2870)
    val text = Color.White
    val textSecondary = Color(0xFF9A9CC0)
    val success = Color(0xFF2ECC71)
    val warning = Color(0xFFF5A623)
    val critical = Color(0xFFE5383B)
    val sosBackground = Color(0xFF2A0A12)
    val divider = Color(0xFF26264A)
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

fun messageOf(e: Exception): String = e.message ?: "Неизвестная ошибка."

/** Верхняя строка экрана: стрелка «назад», заголовок и необязательная кнопка справа. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = VColors.text) }
        } else Spacer(Modifier.width(16.dp))
        Text(title, color = VColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

@Composable
fun PrimaryButton(label: String, enabled: Boolean = true, color: Color = VColors.accent, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else VColors.surfaceAlt)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) Color.White else VColors.textSecondary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

/** Кнопка-контур («Уже есть аккаунт»). */
@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp))
            .background(VColors.divider).padding(1.5.dp).clip(RoundedCornerShape(13.dp)).background(VColors.background)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = VColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = VColors.surface,
    unfocusedContainerColor = VColors.surface,
    focusedIndicatorColor = VColors.accent,
    unfocusedIndicatorColor = VColors.divider,
    cursorColor = VColors.accent
)

/** Поле ввода: тёмная плашка, иконка слева. */
@Composable
fun VField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        leadingIcon = if (icon != null) ({ Icon(icon, contentDescription = null, tint = VColors.textSecondary) }) else null,
        singleLine = singleLine, colors = fieldColors(), shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = modifier
    )
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) Text(message, color = VColors.warning, fontSize = 14.sp)
}

@Composable
fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) VColors.accent else VColors.surfaceAlt)
            .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

/** Ряды чипов: по [perRow] в ряд. */
@Composable
fun ChipGrid(items: List<Pair<String, String>>, selected: Set<String>, perRow: Int = 2, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (code, label) -> SelectChip(label, code in selected, Modifier.weight(1f)) { onToggle(code) } }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = VColors.surface, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(14.dp)) { content() }
}

/** Строка меню со значком, подписью и стрелкой (профиль, настройки, экстренные функции). */
@Composable
fun MenuRow(
    icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null,
    iconColor: Color = VColors.accent, enabled: Boolean = true, onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VColors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) VColors.text else VColors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = VColors.textSecondary, fontSize = 12.sp)
        }
        if (trailing != null) Text(trailing, color = VColors.textSecondary, fontSize = 13.sp)
        if (enabled) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = VColors.textSecondary)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = VColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = VColors.textSecondary, fontSize = 15.sp)
    }
}

/** Круглое фото профиля; если фото нет — силуэт. */
@Composable
fun Avatar(path: String, size: Dp) {
    val bitmap = remember(path) { if (path.isEmpty()) null else try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null } }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(VColors.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Фото профиля", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = VColors.textSecondary, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Плашка-статус («На связи», «Завершён»). */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/** Красная плашка «SOS». */
@Composable
fun SosBadge(size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(VColors.critical),
        contentAlignment = Alignment.Center
    ) { Text("SOS", color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Gap(h: Dp = 8.dp) { Spacer(Modifier.height(h)) }
