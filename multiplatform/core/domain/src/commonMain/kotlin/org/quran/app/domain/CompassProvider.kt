package org.quran.app.domain

/** Heading clockwise from true north. Adapters suppress readings when calibration is unreliable. */
interface CompassProvider {
    fun start(latitude: Double, longitude: Double, onHeading: (Double) -> Unit, onUnavailable: () -> Unit)
    fun stop()
}
