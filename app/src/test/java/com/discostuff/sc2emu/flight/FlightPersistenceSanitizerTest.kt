package com.discostuff.sc2emu.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightPersistenceSanitizerTest {
    @Test
    fun `invalid persisted settings fail closed to safe defaults`() {
        val fallback = FlightSettings(
            maxAltitudeMeters = 120f,
            minAltitudeMeters = 5f,
            maxDistanceMeters = 2_000f,
            geofenceEnabled = true,
            rthMinAltitudeMeters = 40f,
            rthDelaySeconds = 0,
            loiterRadiusMeters = 30,
            loiterAltitudeMeters = 60f,
        )

        val sanitized = FlightPersistenceSanitizer.sanitizeSettings(
            raw = fallback.copy(maxDistanceMeters = 0f),
            fallback = fallback,
        )

        assertTrue(sanitized.corrected)
        assertEquals("max distance must be greater than 0 when geofence is on", sanitized.reason)
        assertEquals(fallback, sanitized.value)
    }

    @Test
    fun `invalid persisted config fails closed to safe defaults`() {
        val fallback = FlightConfig(
            discoIp = "10.147.0.10",
            discoveryPort = 44444,
            c2dPort = 54321,
            d2cPort = 9988,
            streamVideoPort = 55004,
            streamControlPort = 55005,
        )

        val sanitized = FlightPersistenceSanitizer.sanitizeConfig(
            raw = fallback.copy(discoIp = ""),
            fallback = fallback,
        )

        assertTrue(sanitized.corrected)
        assertEquals("plane IP is required", sanitized.reason)
        assertEquals(fallback, sanitized.value)
    }

    @Test
    fun `valid persisted values stay unchanged`() {
        val raw = FlightSettings(
            maxAltitudeMeters = 90f,
            minAltitudeMeters = 10f,
            maxDistanceMeters = 1_500f,
            geofenceEnabled = true,
            rthMinAltitudeMeters = 50f,
            rthDelaySeconds = 2,
            loiterRadiusMeters = 45,
            loiterAltitudeMeters = 75f,
        )

        val sanitized = FlightPersistenceSanitizer.sanitizeSettings(
            raw = raw,
            fallback = raw.copy(maxAltitudeMeters = 120f),
        )

        assertFalse(sanitized.corrected)
        assertEquals(raw, sanitized.value)
    }
}
