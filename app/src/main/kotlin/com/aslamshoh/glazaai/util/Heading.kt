package com.aslamshoh.glazaai.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Компас: куда смотрит камера телефона (0° = север, 90° = восток). Телефон держат вертикально,
 * поэтому оси переставлены так, чтобы направление считалось по задней камере. Если в телефоне
 * нет датчика поворота, available = false и экран просто не рисует компас.
 */
class HeadingProvider(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null

    var degrees by mutableFloatStateOf(0f)
        private set

    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, orientation)
        var target = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (target < 0f) target += 360f
        // Сглаживание по кругу: без него стрелка дрожит, а при переходе 359° → 1° крутится кругом.
        var diff = target - degrees
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        var next = degrees + diff * 0.25f
        if (next < 0f) next += 360f
        if (next >= 360f) next -= 360f
        degrees = next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
