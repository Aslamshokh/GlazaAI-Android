package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.model.RecognitionMode

@Composable
fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusSmall))
            .background(Theme.critical.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.critical)
        Text(message, color = Theme.textPrimary, fontSize = 13.sp)
    }
}

@Composable
fun ModeTile(mode: RecognitionMode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Theme.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(mode.icon, contentDescription = null, tint = Theme.accent)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(mode.title, color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(mode.subtitle, color = Theme.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun ResultCard(
    title: String,
    description: String,
    badge: String? = null,
    badgeColor: Color = Theme.success,
    extraChips: List<String> = emptyList(),
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
            .background(Theme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                color = Theme.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { if (isSpeaking) onStop() else onSpeak() }) {
                Icon(
                    if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = if (isSpeaking) "Остановить озвучивание" else "Озвучить",
                    tint = Theme.accent
                )
            }
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Text(description, color = Theme.textPrimary.copy(alpha = 0.9f), fontSize = 14.sp)

        if (extraChips.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                extraChips.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Theme.surfaceAlt)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(chip, color = Theme.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ShutterButton(isBusy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (isBusy) Theme.surfaceAlt else Theme.accent)
            .clickable(enabled = !isBusy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(color = Theme.textPrimary, modifier = Modifier.size(28.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
