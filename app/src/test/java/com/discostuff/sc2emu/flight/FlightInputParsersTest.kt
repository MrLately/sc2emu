package com.discostuff.sc2emu.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightInputParsersTest {
    @Test
    fun parseConfig_acceptsExplicitPorts() {
        val result = FlightInputParsers.parseConfig(
            discoIp = "10.147.0.10",
            discoveryPort = "44444",
            c2dPort = "54321",
            d2cPort = "9988",
            streamVideoPort = "55004",
            streamControlPort = "55005",
        )

        val config = result.value
        assertNull(result.error)
        requireNotNull(config)
        assertEquals("10.147.0.10", config.discoIp)
        assertEquals(44444, config.discoveryPort)
        assertEquals(54321, config.c2dPort)
        assertEquals(9988, config.d2cPort)
        assertEquals(55004, config.streamVideoPort)
        assertEquals(55005, config.streamControlPort)
    }

    @Test
    fun parseConfig_rejectsOutOfRangePort() {
        val result = FlightInputParsers.parseConfig(
            discoIp = "10.147.0.10",
            discoveryPort = "70000",
            c2dPort = "54321",
            d2cPort = "9988",
            streamVideoPort = "55004",
            streamControlPort = "55005",
        )

        assertNull(result.value)
        assertEquals("Discovery port must be between 1 and 65535", result.error)
    }

    @Test
    fun parseFlightSettings_acceptsValidValues() {
        val result = FlightInputParsers.parseFlightSettings(
            maxAltitude = "120",
            minAltitude = "5",
            maxDistance = "2000",
            geofenceEnabled = true,
            rthMinAltitude = "40",
            rthDelay = "0",
            loiterRadius = "30",
            loiterAltitude = "60",
        )

        val settings = result.value
        assertNull(result.error)
        requireNotNull(settings)
        assertEquals(120f, settings.maxAltitudeMeters)
        assertEquals(5f, settings.minAltitudeMeters)
        assertEquals(2000f, settings.maxDistanceMeters)
        assertTrue(settings.geofenceEnabled)
        assertEquals(40f, settings.rthMinAltitudeMeters)
        assertEquals(0, settings.rthDelaySeconds)
        assertEquals(30, settings.loiterRadiusMeters)
        assertEquals(60f, settings.loiterAltitudeMeters)
    }

    @Test
    fun parseFlightSettings_rejectsGeofenceWithoutDistance() {
        val result = FlightInputParsers.parseFlightSettings(
            maxAltitude = "120",
            minAltitude = "5",
            maxDistance = "0",
            geofenceEnabled = true,
            rthMinAltitude = "40",
            rthDelay = "0",
            loiterRadius = "30",
            loiterAltitude = "60",
        )

        assertNull(result.value)
        assertEquals("max distance must be greater than 0 when geofence is on", result.error)
    }
}
