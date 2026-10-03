package org.quran.app.data

import android.content.Context
import android.hardware.*
import android.view.Surface
import android.view.WindowManager
import org.quran.app.domain.CompassProvider

actual fun platformCompassProvider(): CompassProvider = AndroidCompass(platformContext())

private class AndroidCompass(private val context: Context) : CompassProvider, SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var onHeading: ((Double) -> Unit)? = null
    private var onUnavailable: (() -> Unit)? = null
    private var declination = 0.0
    private var accurate = true

    override fun start(latitude: Double, longitude: Double, onHeading: (Double) -> Unit, onUnavailable: () -> Unit) {
        stop()
        this.onHeading = onHeading
        this.onUnavailable = onUnavailable
        declination = GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
        accurate = true
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null || !sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)) onUnavailable()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!accurate || event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) { onUnavailable?.invoke(); return }
        val matrix = FloatArray(9)
        val adjusted = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)
        SensorManager.getOrientation(adjusted, orientation)
        val magnetic = Math.toDegrees(orientation[0].toDouble())
        onHeading?.invoke((magnetic + declination + 360.0) % 360.0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accurate = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        if (!accurate) onUnavailable?.invoke()
    }

    override fun stop() {
        sensors.unregisterListener(this)
        onHeading = null
        onUnavailable = null
    }
}
