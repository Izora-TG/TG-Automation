package com.tg.automation.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.tg.automation.engine.Engine
import kotlin.math.sqrt

/**
 * Erkennt Schütteln und Umdrehen über den Beschleunigungssensor.
 * Läuft nur, solange der Bildschirm an ist, mit niedriger Abtastrate.
 */
class MotionDetector(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var running = false

    // Schütteln
    private var spikes = 0
    private var firstSpike = 0L
    private var peak = 0f
    private var lastShake = 0L

    // Umdrehen
    private var faceDownSince = 0L
    private var isFaceDown = false

    fun start() {
        if (running || sensor == null) return
        running = true
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(this)
        spikes = 0
        faceDownSince = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = SystemClock.elapsedRealtime()
        detectShake(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH, now)
        detectFlip(z, now)
    }

    private fun detectShake(g: Float, now: Long) {
        if (now - lastShake < 1_500) return
        if (g > 1.8f) {
            if (spikes == 0 || now - firstSpike > 800) {
                spikes = 0
                peak = 0f
                firstSpike = now
            }
            spikes++
            if (g > peak) peak = g
            if (spikes >= 3) {
                lastShake = now
                spikes = 0
                Engine.dispatch("shake", mapOf("g" to peak.toString()))
            }
        }
    }

    private fun detectFlip(z: Float, now: Long) {
        if (z < -8.5f) {
            if (faceDownSince == 0L) faceDownSince = now
            if (!isFaceDown && now - faceDownSince > 1_000) {
                isFaceDown = true
                Engine.dispatch("face_down", mapOf("state" to "down"))
            }
        } else {
            faceDownSince = 0L
            if (isFaceDown && z > 3f) {
                isFaceDown = false
                Engine.dispatch("face_down", mapOf("state" to "up"))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
