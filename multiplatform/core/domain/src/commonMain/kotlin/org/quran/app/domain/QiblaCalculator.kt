package org.quran.app.domain

/** Initial great-circle bearing from true north. Magnetic heading needs platform declination. */
object QiblaCalculator {
    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262

    fun bearing(latitude: Double, longitude: Double): Double? {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }

        val radians = kotlin.math.PI / 180.0
        val lat = latitude * radians
        val targetLat = KAABA_LATITUDE * radians
        val delta = (KAABA_LONGITUDE - longitude) * radians
        val y = kotlin.math.sin(delta) * kotlin.math.cos(targetLat)
        val x = kotlin.math.cos(lat) * kotlin.math.sin(targetLat) -
            kotlin.math.sin(lat) * kotlin.math.cos(targetLat) * kotlin.math.cos(delta)
        if (kotlin.math.abs(x) < 1e-12 && kotlin.math.abs(y) < 1e-12) return null
        return (kotlin.math.atan2(y, x) / radians + 360.0) % 360.0
    }
}
