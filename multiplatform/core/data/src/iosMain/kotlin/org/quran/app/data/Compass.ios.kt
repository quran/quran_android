@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app.data

import org.quran.app.domain.CompassProvider
import platform.CoreLocation.*
import platform.Foundation.NSError
import platform.darwin.NSObject

actual fun platformCompassProvider(): CompassProvider = IosCompass()

private class IosCompass : CompassProvider {
    private val manager = CLLocationManager()
    private var onHeading: ((Double) -> Unit)? = null
    private var onUnavailable: (() -> Unit)? = null

    override fun start(latitude: Double, longitude: Double, onHeading: (Double) -> Unit, onUnavailable: () -> Unit) {
        stop()
        this.onHeading = onHeading
        this.onUnavailable = onUnavailable
        manager.delegate = delegate
        if (!CLLocationManager.headingAvailable()) { onUnavailable(); return }
        // Apple needs a location fix to supply trueHeading. Only requested after opting into live compass.
        manager.requestWhenInUseAuthorization()
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
    }

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        if (didUpdateHeading.headingAccuracy < 0 || didUpdateHeading.headingAccuracy > 30 || didUpdateHeading.trueHeading < 0) {
            onUnavailable?.invoke()
        } else {
            onHeading?.invoke(didUpdateHeading.trueHeading)
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onUnavailable?.invoke()
    }

    }

    override fun stop() {
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        manager.delegate = null
        onHeading = null
        onUnavailable = null
    }
}
