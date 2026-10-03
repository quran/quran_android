package org.quran.app.domain

import kotlin.math.abs
import kotlin.test.*

class QiblaCalculatorTest {
    @Test fun knownCitiesHaveExpectedInitialGreatCircleBearing() {
        assertEquals(160.7, QiblaCalculator.bearing(31.9539, 35.9106)!!, 0.3)
        assertEquals(118.9, QiblaCalculator.bearing(51.5074, -0.1278)!!, 0.3)
        assertEquals(58.5, QiblaCalculator.bearing(40.7128, -74.0060)!!, 0.3)
    }

    @Test fun wrapAndPoleCoordinatesProduceNormalizedFiniteResults() {
        for ((lat, lon) in listOf(90.0 to 0.0, -90.0 to 0.0, 0.0 to 180.0, 0.0 to -180.0)) {
            val bearing = QiblaCalculator.bearing(lat, lon)!!
            assertTrue(bearing.isFinite())
            assertTrue(bearing >= 0.0 && bearing < 360.0)
        }
        assertTrue(abs(QiblaCalculator.bearing(0.0, 180.0)!! - QiblaCalculator.bearing(0.0, -180.0)!!) < 0.00001)
    }

    @Test fun rejectsInvalidCoordinatesRatherThanSilentlyReturningDirection() {
        for ((lat, lon) in listOf(91.0 to 0.0, -91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { QiblaCalculator.bearing(lat, lon) }
        }
    }

    @Test fun directionAtKaabaAndItsAntipodeIsUndefined() {
        assertNull(QiblaCalculator.bearing(21.4225, 39.8262))
        assertNull(QiblaCalculator.bearing(-21.4225, -140.1738))
    }
}
