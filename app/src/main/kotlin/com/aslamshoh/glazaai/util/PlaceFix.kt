package com.aslamshoh.glazaai.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.aslamshoh.glazaai.nav.LocationProvider
import com.aslamshoh.glazaai.nav.NavPoint
import kotlinx.coroutines.delay

/** Положение телефона и его точность в метрах. */
data class PlaceFix(val point: NavPoint, val accuracyM: Float) {
    companion object {
        /** Положение выдано разрешением? (любое — точное или приблизительное). */
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /**
         * Ждёт положение до [timeoutMs]: хороший GPS-замер (точнее 20 м) берётся сразу, иначе —
         * лучший из пойманных. Замер старше пяти минут не принимается: «последнее известное
         * положение» могло остаться с прошлой поездки и увести не туда. Вызывать с главного потока.
         */
        suspend fun current(context: Context, timeoutMs: Long = 6000L): PlaceFix? {
            if (!hasPermission(context)) return null
            val provider = LocationProvider(context)
            if (!provider.isEnabled()) return null
            provider.start()
            try {
                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                var best: PlaceFix? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    val p = provider.point
                    val a = provider.accuracyM
                    val fresh = System.currentTimeMillis() - provider.fixTimeMs < 5 * 60_000L
                    if (p != null && a != null && fresh) {
                        if (best == null || a < best.accuracyM) best = PlaceFix(p, a)
                        if (a <= 20f) break
                    }
                    delay(250)
                }
                return best
            } finally {
                provider.stop()
            }
        }
    }
}
