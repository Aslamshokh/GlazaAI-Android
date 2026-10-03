package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.store.HistoryStore

@Composable
fun HistoryScreen() {
    Column(modifier = Modifier.fillMaxSize().background(Theme.background).padding(16.dp)) {
        Text(
            "История",
            color = Theme.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (HistoryStore.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся ваши результаты распознавания.", color = Theme.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HistoryStore.entries, key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Theme.cornerRadiusMedium))
                            .background(Theme.surface)
                            .padding(14.dp)
                    ) {
                        Text(entry.title, color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(entry.module, color = Theme.accent, fontSize = 12.sp)
                        Text(entry.description, color = Theme.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
