package org.quran.app.data
import org.quran.app.domain.CompassProvider
actual fun platformCompassProvider(): CompassProvider = object : CompassProvider {
    override fun start(latitude: Double, longitude: Double, onHeading: (Double) -> Unit, onUnavailable: () -> Unit) = onUnavailable()
    override fun stop() = Unit
}
