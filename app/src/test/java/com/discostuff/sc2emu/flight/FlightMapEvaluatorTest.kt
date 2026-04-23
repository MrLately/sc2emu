package com.discostuff.sc2emu.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightMapEvaluatorTest {
    @Test
    fun `fresh plane telemetry keeps plane marker and trail live`() {
        val model = FlightMapEvaluator.evaluate(
            planePositionAvailable = true,
            pilotPositionAvailable = false,
            telemetryAgeMs = 1_500L,
            telemetryStaleAgeMs = 6_000L,
        )

        assertTrue(model.showPlaneMarker)
        assertTrue(model.showFlightTrail)
        assertTrue(model.focusTargetAvailable)
        assertNull(model.overlayLabel)
    }

    @Test
    fun `stale plane telemetry falls back to pilot and warns operator`() {
        val model = FlightMapEvaluator.evaluate(
            planePositionAvailable = true,
            pilotPositionAvailable = true,
            telemetryAgeMs = 7_500L,
            telemetryStaleAgeMs = 6_000L,
        )

        assertFalse(model.showPlaneMarker)
        assertFalse(model.showFlightTrail)
        assertTrue(model.focusTargetAvailable)
        assertEquals("Plane telemetry stale", model.overlayLabel)
    }

    @Test
    fun `missing fresh target blocks focused map entry`() {
        val model = FlightMapEvaluator.evaluate(
            planePositionAvailable = false,
            pilotPositionAvailable = false,
            telemetryAgeMs = null,
            telemetryStaleAgeMs = 6_000L,
        )

        assertFalse(model.showPlaneMarker)
        assertFalse(model.showFlightTrail)
        assertFalse(model.focusTargetAvailable)
        assertEquals("Position unavailable", model.overlayLabel)
    }
}
