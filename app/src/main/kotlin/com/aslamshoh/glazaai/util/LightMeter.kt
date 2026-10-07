package com.aslamshoh.glazaai.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.delay

/** Освещённость в люксах от датчика света телефона (он рядом с фронтальной камерой). */
object LightMeter {
    /** Слушает датчик до [timeoutMs] и возвращает среднее по нескольким замерам; null — датчика нет. */
    suspend fun read(context: Context, timeoutMs: Long = 1500L): Float? {
        val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return null
        val values = ArrayList<Float>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                synchronized(values) { values.add(event.values[0]) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        try {
            var waited = 0L
            while (waited < timeoutMs) {
                delay(100)
                waited += 100
                if (synchronized(values) { values.size } >= 5) break
            }
        } finally {
            manager.unregisterListener(listener)
        }
        val copy = synchronized(values) { values.toList() }
        if (copy.isEmpty()) return null
        // Медиана устойчивее к одному выбросу, чем среднее.
        return copy.sorted()[copy.size / 2]
    }
}
