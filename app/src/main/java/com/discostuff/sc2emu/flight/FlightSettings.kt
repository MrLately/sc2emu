package com.discostuff.sc2emu.flight

data class FlightSettings(
    val maxAltitudeMeters: Float,
    val minAltitudeMeters: Float,
    val maxDistanceMeters: Float,
    val geofenceEnabled: Boolean,
    val rthMinAltitudeMeters: Float,
    val rthDelaySeconds: Int,
    val loiterRadiusMeters: Int,
    val loiterAltitudeMeters: Float,
)
