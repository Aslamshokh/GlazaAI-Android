package com.aslamshoh.glazaai.volunteer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.aslamshoh.glazaai.volunteer.net.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Служба «Я на связи»: пока она работает, раз в ~3 секунды спрашивает сервер о новых вызовах и показывает
 * уведомление со звуком. Это замена push-уведомлениям (Firebase) для первой версии; позже её можно заменить на FCM.
 */
class DutyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val notified = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, DUTY_ID, dutyNotification(), type)
        if (loop == null) {
            loop = scope.launch {
                var failures = 0
                while (true) {
                    try {
                        val offers = VolunteerRepo.poll()
                        failures = 0
                        val fresh = OfferText.newOfferIds(offers.map { it.requestId }, notified)
                        offers.filter { it.requestId in fresh }.forEach { notifyOffer(it.requestId, it.userName, it.urgent) }
                        notified.addAll(fresh)
                        notified.retainAll(offers.map { it.requestId }.toSet() + fresh.toSet())
                    } catch (e: ApiError) {
                        if (e.status == 401 || e.status == 403) {
                            stopSelf()
                            return@launch
                        }
                        failures++
                        if (failures == 5) VolunteerRepo.showMessage("Нет связи с сервером. Вызовы могут не приходить.")
                    }
                    delay(3000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        loop = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, VolunteerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun dutyNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_DUTY)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Вы на связи")
            .setContentText("Вызовы приходят сюда. Чтобы отключиться, откройте приложение.")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notifyOffer(requestId: Int, userName: String, urgent: Boolean) {
        val n = NotificationCompat.Builder(this, CHANNEL_OFFER)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(OfferText.title(userName, urgent))
            .setContentText("Нажмите, чтобы ответить")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        try {
            NotificationManagerCompat.from(this).notify(OFFER_BASE_ID + requestId, n)
        } catch (_: SecurityException) {
            // нет разрешения на уведомления — вызов всё равно виден внутри приложения
        }
    }

    companion object {
        const val CHANNEL_DUTY = "duty"
        const val CHANNEL_OFFER = "offers"
        private const val DUTY_ID = 1
        private const val OFFER_BASE_ID = 1000

        fun start(context: Context) {
            val i = Intent(context, DutyService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DutyService::class.java))
        }
    }
}
