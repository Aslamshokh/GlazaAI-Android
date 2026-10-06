package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Палитра и размеры — по макету «ИИ Глаз» (тёмно-синий фон, синий акцент, цветные рамки). */
object Theme {
    val background = Color(0xFF070C1A)
    val surface = Color(0xFF111A2E)
    val surfaceAlt = Color(0xFF1A2540)
    val bar = Color(0xFF0B1224)
    val accent = Color(0xFF2E7BFF)
    val accentSoft = Color(0x262E7BFF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF9AA6BF)
    val success = Color(0xFF34C77B)
    val warning = Color(0xFFF5A623)
    val critical = Color(0xFFFF5C5C)
    val divider = Color(0x14FFFFFF)

    // PRO-экраны: фиолетовый градиент.
    val proTop = Color(0xFF3A1478)
    val proBottom = Color(0xFF0B0720)
    val proAccent = Color(0xFF8B3DFF)

    /** Цвета рамок найденных предметов: у каждого предмета свой, как на макете. */
    val boxPalette = listOf(
        Color(0xFF34C77B), // зелёный
        Color(0xFFF5A623), // оранжевый
        Color(0xFFA463F2), // фиолетовый
        Color(0xFF3B9BFF), // голубой
        Color(0xFFF2D13B), // жёлтый
        Color(0xFFFF5C5C)  // красный
    )

    val cornerRadiusLarge = 24.dp
    val cornerRadiusMedium = 16.dp
    val cornerRadiusSmall = 12.dp
}

@Composable
fun GlazaAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Theme.accent,
            background = Theme.background,
            surface = Theme.surface,
            onPrimary = Color.White,
            onBackground = Theme.textPrimary,
            onSurface = Theme.textPrimary
        ),
        shapes = Shapes(
            small = RoundedCornerShape(Theme.cornerRadiusSmall),
            medium = RoundedCornerShape(Theme.cornerRadiusMedium),
            large = RoundedCornerShape(Theme.cornerRadiusLarge)
        ),
        content = content
    )
}
