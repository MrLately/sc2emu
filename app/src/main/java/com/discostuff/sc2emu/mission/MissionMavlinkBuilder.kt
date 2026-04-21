package com.discostuff.sc2emu.mission

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object MissionMavlinkBuilder {
    data class Node(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Float,
    )

    enum class TerminalAction {
        LOITER,
        CIRCULAR_LANDING,
        LINEAR_LANDING,
    }

    fun build(
        executionNodes: List<Node>,
        terminalAction: TerminalAction,
    ): String {
        val lines = mutableListOf<String>()
        lines += "QGC WPL 120"
        lines += formatMissionRow(
            seq = 0,
            command = 2500,
            param1 = 0.0,
            param2 = 30.0,
            param3 = 2073600.0,
            param4 = 0.0,
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
        )

        executionNodes.forEachIndexed { index, node ->
            val heading = when {
                executionNodes.size <= 1 -> 0.0
                index < executionNodes.lastIndex -> {
                    val next = executionNodes[index + 1]
                    bearingDegrees(node.latitude, node.longitude, next.latitude, next.longitude)
                }
                index > 0 -> {
                    val prev = executionNodes[index - 1]
                    bearingDegrees(prev.latitude, prev.longitude, node.latitude, node.longitude)
                }
                else -> 0.0
            }
            val waypointRadius = when {
                index == executionNodes.lastIndex && terminalAction != TerminalAction.LOITER -> -30.0
                else -> 30.0
            }
            lines += formatMissionRow(
                seq = index + 1,
                command = 16,
                param1 = 0.0,
                param2 = 5.0,
                param3 = waypointRadius,
                param4 = heading,
                latitude = node.latitude,
                longitude = node.longitude,
                altitude = node.altitudeMeters.toDouble(),
            )
        }

        if (terminalAction != TerminalAction.LOITER && executionNodes.isNotEmpty()) {
            val last = executionNodes.last()
            val heading = if (executionNodes.size >= 2) {
                val prev = executionNodes[executionNodes.lastIndex - 1]
                bearingDegrees(prev.latitude, prev.longitude, last.latitude, last.longitude)
            } else {
                0.0
            }
            val landingOrbit = if (terminalAction == TerminalAction.CIRCULAR_LANDING) 30.0 else 0.0
            lines += formatMissionRow(
                seq = executionNodes.size + 1,
                command = 21,
                param1 = 0.0,
                param2 = 0.0,
                param3 = landingOrbit,
                param4 = heading,
                latitude = last.latitude,
                longitude = last.longitude,
                altitude = 0.0,
            )
        }

        return lines.joinToString("\n", postfix = "\n")
    }

    private fun formatMissionRow(
        seq: Int,
        command: Int,
        param1: Double,
        param2: Double,
        param3: Double,
        param4: Double,
        latitude: Double,
        longitude: Double,
        altitude: Double,
    ): String {
        return String.format(
            Locale.US,
            "%d\t0\t3\t%d\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t1",
            seq,
            command,
            param1,
            param2,
            param3,
            param4,
            latitude,
            longitude,
            altitude,
        )
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
        val theta = Math.toDegrees(atan2(y, x))
        return ((theta % 360.0) + 360.0) % 360.0
    }
}
