package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.model.RecognitionMode
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.SettingsStore

@Composable
fun HomeScreen(
    onOpenMode: (RecognitionMode) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (!SettingsStore.isBackendConfigured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.cornerRadiusSmall))
                    .background(Theme.warning.copy(alpha = 0.15f))
                    .clickable(onClick = onOpenSettings)
                    .padding(12.dp)
            ) {
                Text(
                    "Backend не настроен. Откройте Настройки и укажите адрес сервера, чтобы распознавание заработало.",
                    color = Theme.textPrimary,
                    fontSize = 13.sp
                )
            }
        }

        HeroSection(onStartCamera = { onOpenMode(RecognitionMode.OBJECTS) })

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Режимы распознавания",
                color = Theme.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                modifier = Modifier.height(380.dp)
            ) {
                items(RecognitionMode.entries.toList()) { mode ->
                    ModeTile(mode = mode, onClick = { onOpenMode(mode) })
                }
            }
        }

        val last = HistoryStore.entries.firstOrNull()
        if (last != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Последний результат",
                    color = Theme.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                        .background(Theme.surface)
                        .padding(14.dp)
                ) {
                    Text(last.title, color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(last.module, color = Theme.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroSection(onStartCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusLarge))
            .background(Theme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Theme.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.RemoveRedEye,
                contentDescription = null,
                tint = Theme.accent,
                modifier = Modifier.size(40.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Что перед мной?",
                color = Theme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Наведите камеру и узнайте, что это, цену, текст и многое другое",
                color = Theme.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                .background(Theme.accent)
                .clickable(onClick = onStartCamera)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Camera, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                    Text(
                        "Запустить камеру",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
