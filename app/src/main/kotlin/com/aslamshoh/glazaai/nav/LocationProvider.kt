package com.aslamshoh.glazaai.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Положение телефона без Google Play Services: системный LocationManager (GPS + сеть). Так не
 * нужны лишние зависимости, и приложение работает на любом телефоне. Разрешение на геолокацию
 * проверяет экран до вызова start().
 */
class LocationProvider(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var point by mutableStateOf<NavPoint?>(null)
        private set
    var accuracyM by mutableStateOf<Float?>(null)
        private set

    /** Вызывается на главном потоке на каждый новый замер. */
    var listener: ((NavPoint, Float) -> Unit)? = null

    private var started = false
    private var lastGpsMs = 0L

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastGpsMs = SystemClock.elapsedRealtime()
            publish(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Сетевое положение грубее GPS — берём его, только пока GPS молчит (дом, подъезд).
            if (SystemClock.elapsedRealtime() - lastGpsMs > 10_000) publish(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun publish(location: Location) {
        val p = NavPoint(location.latitude, location.longitude)
        val acc = if (location.hasAccuracy()) location.accuracy else 50f
        point = p
        accuracyM = acc
        listener?.invoke(p, acc)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        try {
            // Сразу берём последнее известное положение — оно появляется быстрее, чем первый GPS-фикс.
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { name ->
                if (point == null) {
                    manager.getLastKnownLocation(name)?.let { publish(it) }
                }
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper())
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, networkListener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        manager.removeUpdates(gpsListener)
        manager.removeUpdates(networkListener)
    }

    /** Включена ли геолокация в системе (шторка «Местоположение»). */
    fun isEnabled(): Boolean =
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
