package com.discostuff.sc2emu.flight

data class FlightMapModel(
    val showPlaneMarker: Boolean,
    val showFlightTrail: Boolean,
    val focusTargetAvailable: Boolean,
    val overlayLabel: String?,
)

object FlightMapEvaluator {
    fun evaluate(
        planePositionAvailable: Boolean,
        pilotPositionAvailable: Boolean,
        telemetryAgeMs: Long?,
        telemetryStaleAgeMs: Long,
    ): FlightMapModel {
        val planeTelemetryFresh = planePositionAvailable &&
            telemetryAgeMs != null &&
            telemetryAgeMs in 0..telemetryStaleAgeMs
        val focusTargetAvailable = planeTelemetryFresh || pilotPositionAvailable

        val overlayLabel = when {
            planePositionAvailable && !planeTelemetryFresh && pilotPositionAvailable -> "Plane telemetry stale"
            !focusTargetAvailable -> "Position unavailable"
            else -> null
        }

        return FlightMapModel(
            showPlaneMarker = planeTelemetryFresh,
            showFlightTrail = planeTelemetryFresh,
            focusTargetAvailable = focusTargetAvailable,
            overlayLabel = overlayLabel,
        )
    }
}
