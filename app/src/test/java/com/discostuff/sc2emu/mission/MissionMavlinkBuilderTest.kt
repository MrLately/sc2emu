package com.discostuff.sc2emu.mission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionMavlinkBuilderTest {
    private val nodes = listOf(
        MissionMavlinkBuilder.Node(latitude = 41.0, longitude = -81.0, altitudeMeters = 50f),
        MissionMavlinkBuilder.Node(latitude = 41.001, longitude = -81.001, altitudeMeters = 60f),
    )

    @Test
    fun build_loiter_doesNotAppendLandingCommand() {
        val mavlink = MissionMavlinkBuilder.build(
            executionNodes = nodes,
            terminalAction = MissionMavlinkBuilder.TerminalAction.LOITER,
        )

        assertTrue(mavlink.startsWith("QGC WPL 120\n"))
        assertTrue(mavlink.contains("\t2500\t"))
        assertFalse(mavlink.contains("\t21\t"))
    }

    @Test
    fun build_linearLanding_appendsLandWithZeroOrbit() {
        val mavlink = MissionMavlinkBuilder.build(
            executionNodes = nodes,
            terminalAction = MissionMavlinkBuilder.TerminalAction.LINEAR_LANDING,
        )

        assertTrue(mavlink.contains("\t21\t"))
        assertTrue(mavlink.contains("\t0.000000\t0.000000\t0.000000\t"))
    }

    @Test
    fun build_circularLanding_appendsLandWithOrbitRadius() {
        val mavlink = MissionMavlinkBuilder.build(
            executionNodes = nodes,
            terminalAction = MissionMavlinkBuilder.TerminalAction.CIRCULAR_LANDING,
        )

        assertTrue(mavlink.contains("\t21\t"))
        assertTrue(mavlink.contains("\t0.000000\t0.000000\t30.000000\t"))
    }
}
