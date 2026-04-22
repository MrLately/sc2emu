package com.discostuff.sc2emu.flight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightPhaseTest {
    @Test
    fun fromArsdk_mapsPreflightStatesWithoutMarkingAirborne() {
        val userTakeoff = FlightPhase.fromArsdk(6)
        val motorRamping = FlightPhase.fromArsdk(7)

        assertSame(FlightPhase.USER_TAKEOFF, userTakeoff)
        assertSame(FlightPhase.MOTOR_RAMPING, motorRamping)
        assertFalse(userTakeoff.isAirborne)
        assertFalse(motorRamping.isAirborne)
        assertTrue(userTakeoff.allowsTakeoffAbort)
        assertTrue(motorRamping.allowsTakeoffAbort)
    }

    @Test
    fun missionAndRthStart_areLimitedToHoveringAndFlying() {
        assertTrue(FlightPhase.HOVERING.allowsMissionStart)
        assertTrue(FlightPhase.FLYING.allowsMissionStart)
        assertTrue(FlightPhase.HOVERING.allowsNavigateHomeStart)
        assertTrue(FlightPhase.FLYING.allowsNavigateHomeStart)

        assertFalse(FlightPhase.LANDED.allowsMissionStart)
        assertFalse(FlightPhase.USER_TAKEOFF.allowsMissionStart)
        assertFalse(FlightPhase.MOTOR_RAMPING.allowsMissionStart)
        assertFalse(FlightPhase.LANDING.allowsNavigateHomeStart)
    }

    @Test
    fun landCommand_isOnlyAllowedInStableFlight() {
        assertTrue(FlightPhase.HOVERING.allowsLandCommand)
        assertTrue(FlightPhase.FLYING.allowsLandCommand)

        assertFalse(FlightPhase.TAKING_OFF.allowsLandCommand)
        assertFalse(FlightPhase.USER_TAKEOFF.allowsLandCommand)
        assertFalse(FlightPhase.MOTOR_RAMPING.allowsLandCommand)
        assertFalse(FlightPhase.LANDING.allowsLandCommand)
    }
}
