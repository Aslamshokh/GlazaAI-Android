package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * «Крупно»: распознанный текст на весь экран, жёлтые буквы на чёрном (самый читаемый контраст для слабовидящих).
 * Размер букв меняется кнопками «Крупнее» / «Мельче».
 */
@Composable
fun LargeTextDialog(text: String, onClose: () -> Unit) {
    var size by remember { mutableStateOf(44) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Мельче", modifier = Modifier.weight(1f), enabled = size > 24) { size = (size - 8).coerceAtLeast(24) }
                ActionButton("Крупнее", modifier = Modifier.weight(1f), enabled = size < 96) { size = (size + 8).coerceAtMost(96) }
                ActionButton("Закрыть", filled = true, modifier = Modifier.weight(1f), onClick = onClose)
            }
            Text(
                text,
                color = Color(0xFFFFE600),
                fontSize = size.sp,
                lineHeight = (size * 1.25f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            )
        }
    }
}
