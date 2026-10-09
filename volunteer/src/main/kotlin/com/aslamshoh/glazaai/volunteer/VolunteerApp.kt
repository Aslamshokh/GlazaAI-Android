package com.aslamshoh.glazaai.volunteer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore

class VolunteerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VolunteerStore.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(DutyService.CHANNEL_DUTY, "Вы на связи", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Постоянное уведомление, пока вы готовы помогать"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(DutyService.CHANNEL_OFFER, "Входящие вызовы", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Кому-то нужна помощь"
                    enableVibration(true)
                }
            )
        }
    }
}
