package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Единая тёмно-синяя палитра приложения — те же значения, что в GlazaAI-iOS/GlazaAI/Theme.swift
 * и glaza-ai (web), чтобы все три платформы выглядели одинаково.
 */
object Theme {
    val background = Color(0xFF0A1020)
    val surface = Color(0xFF121A2E)
    val surfaceAlt = Color(0xFF1A2438)
    val accent = Color(0xFF2F8CFF)
    val accentSoft = Color(0x262F8CFF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF9AA6BF)
    val success = Color(0xFF34C77B)
    val warning = Color(0xFFF5A623)
    val critical = Color(0xFFFF5C5C)
    val divider = Color(0x14FFFFFF)

    val cornerRadiusLarge = 24.dp
    val cornerRadiusMedium = 16.dp
    val cornerRadiusSmall = 12.dp
}

private val GlazaColorScheme = darkColorScheme(
    background = Theme.background,
    surface = Theme.surface,
    primary = Theme.accent,
    onPrimary = Color.White,
    onBackground = Theme.textPrimary,
    onSurface = Theme.textPrimary,
    secondary = Theme.accent,
    error = Theme.critical
)

private val GlazaShapes = Shapes(
    small = RoundedCornerShape(Theme.cornerRadiusSmall),
    medium = RoundedCornerShape(Theme.cornerRadiusMedium),
    large = RoundedCornerShape(Theme.cornerRadiusLarge)
)

@Composable
fun GlazaAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlazaColorScheme,
        shapes = GlazaShapes,
        content = content
    )
}
