package com.discostuff.sc2emu.flight

data class SanitizedPersistedValue<T>(
    val value: T,
    val corrected: Boolean,
    val reason: String? = null,
)

object FlightPersistenceSanitizer {
    fun sanitizeConfig(raw: FlightConfig, fallback: FlightConfig): SanitizedPersistedValue<FlightConfig> {
        val parsed = FlightInputParsers.parseConfig(
            discoIp = raw.discoIp,
            discoveryPort = raw.discoveryPort.toString(),
            c2dPort = raw.c2dPort.toString(),
            d2cPort = raw.d2cPort.toString(),
            streamVideoPort = raw.streamVideoPort.toString(),
            streamControlPort = raw.streamControlPort.toString(),
        )
        return parsed.value?.let { SanitizedPersistedValue(value = it, corrected = false) }
            ?: SanitizedPersistedValue(value = fallback, corrected = true, reason = parsed.error)
    }

    fun sanitizeSettings(raw: FlightSettings, fallback: FlightSettings): SanitizedPersistedValue<FlightSettings> {
        val parsed = FlightInputParsers.parseFlightSettings(
            maxAltitude = raw.maxAltitudeMeters.toString(),
            minAltitude = raw.minAltitudeMeters.toString(),
            maxDistance = raw.maxDistanceMeters.toString(),
            geofenceEnabled = raw.geofenceEnabled,
            rthMinAltitude = raw.rthMinAltitudeMeters.toString(),
            rthDelay = raw.rthDelaySeconds.toString(),
            loiterRadius = raw.loiterRadiusMeters.toString(),
            loiterAltitude = raw.loiterAltitudeMeters.toString(),
        )
        return parsed.value?.let { SanitizedPersistedValue(value = it, corrected = false) }
            ?: SanitizedPersistedValue(value = fallback, corrected = true, reason = parsed.error)
    }
}
