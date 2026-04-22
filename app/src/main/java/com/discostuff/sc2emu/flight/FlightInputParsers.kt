package com.discostuff.sc2emu.flight

import java.util.Locale

data class ParseResult<T>(
    val value: T? = null,
    val error: String? = null,
)

object FlightInputParsers {
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    fun parseConfig(
        discoIp: String,
        discoveryPort: String,
        c2dPort: String,
        d2cPort: String,
        streamVideoPort: String,
        streamControlPort: String,
    ): ParseResult<FlightConfig> {
        val ip = discoIp.trim()
        if (ip.isEmpty()) {
            return ParseResult(error = "plane IP is required")
        }

        val discoveryResult = parsePort("Discovery port", discoveryPort)
        val discovery = discoveryResult.value ?: return ParseResult(error = discoveryResult.error)
        val c2dResult = parsePort("C2D port", c2dPort)
        val c2d = c2dResult.value ?: return ParseResult(error = c2dResult.error)
        val d2cResult = parsePort("D2C port", d2cPort)
        val d2c = d2cResult.value ?: return ParseResult(error = d2cResult.error)
        val videoResult = parsePort("Video port", streamVideoPort)
        val video = videoResult.value ?: return ParseResult(error = videoResult.error)
        val controlResult = parsePort("Stream control port", streamControlPort)
        val control = controlResult.value ?: return ParseResult(error = controlResult.error)

        return ParseResult(
            value = FlightConfig(
                discoIp = ip,
                discoveryPort = discovery,
                c2dPort = c2d,
                d2cPort = d2c,
                streamVideoPort = video,
                streamControlPort = control,
            ),
        )
    }

    fun parseFlightSettings(
        maxAltitude: String,
        minAltitude: String,
        maxDistance: String,
        geofenceEnabled: Boolean,
        rthMinAltitude: String,
        rthDelay: String,
        loiterRadius: String,
        loiterAltitude: String,
    ): ParseResult<FlightSettings> {
        val maxAltitudeResult = parseFloat("Max altitude", maxAltitude)
        val maxAltitudeMeters = maxAltitudeResult.value ?: return ParseResult(error = maxAltitudeResult.error)
        val minAltitudeResult = parseFloat("Min altitude", minAltitude)
        val minAltitudeMeters = minAltitudeResult.value ?: return ParseResult(error = minAltitudeResult.error)
        val maxDistanceResult = parseFloat("Max distance", maxDistance)
        val maxDistanceMeters = maxDistanceResult.value ?: return ParseResult(error = maxDistanceResult.error)
        val rthMinAltitudeResult = parseFloat("RTH min altitude", rthMinAltitude)
        val rthMinAltitudeMeters = rthMinAltitudeResult.value ?: return ParseResult(error = rthMinAltitudeResult.error)
        val rthDelayResult = parseInt("RTH delay", rthDelay)
        val rthDelaySeconds = rthDelayResult.value ?: return ParseResult(error = rthDelayResult.error)
        val loiterRadiusResult = parseInt("Loiter radius", loiterRadius)
        val loiterRadiusMeters = loiterRadiusResult.value ?: return ParseResult(error = loiterRadiusResult.error)
        val loiterAltitudeResult = parseFloat("Loiter altitude", loiterAltitude)
        val loiterAltitudeMeters = loiterAltitudeResult.value ?: return ParseResult(error = loiterAltitudeResult.error)

        if (maxAltitudeMeters <= 0f) {
            return ParseResult(error = "max altitude must be greater than 0")
        }
        if (minAltitudeMeters < 0f || minAltitudeMeters > maxAltitudeMeters) {
            return ParseResult(error = "min altitude must be between 0 and max altitude")
        }
        if (maxDistanceMeters < 0f) {
            return ParseResult(error = "max distance must be 0 or greater")
        }
        if (geofenceEnabled && maxDistanceMeters <= 0f) {
            return ParseResult(error = "max distance must be greater than 0 when geofence is on")
        }
        if (rthMinAltitudeMeters < 0f) {
            return ParseResult(error = "RTH min altitude must be 0 or greater")
        }
        if (rthDelaySeconds < 0) {
            return ParseResult(error = "RTH delay must be 0 or greater")
        }
        if (loiterRadiusMeters <= 0) {
            return ParseResult(error = "loiter radius must be greater than 0")
        }
        if (loiterAltitudeMeters < 0f) {
            return ParseResult(error = "loiter altitude must be 0 or greater")
        }

        return ParseResult(
            value = FlightSettings(
                maxAltitudeMeters = maxAltitudeMeters,
                minAltitudeMeters = minAltitudeMeters,
                maxDistanceMeters = maxDistanceMeters,
                geofenceEnabled = geofenceEnabled,
                rthMinAltitudeMeters = rthMinAltitudeMeters,
                rthDelaySeconds = rthDelaySeconds,
                loiterRadiusMeters = loiterRadiusMeters,
                loiterAltitudeMeters = loiterAltitudeMeters,
            ),
        )
    }

    fun formatConfigSummary(config: FlightConfig): String {
        return buildString {
            append(config.discoIp)
            append(" d:")
            append(config.discoveryPort)
            append(" c2d:")
            append(config.c2dPort)
            append(" d2c:")
            append(config.d2cPort)
            append(" v:")
            append(config.streamVideoPort)
            append(" ctl:")
            append(config.streamControlPort)
        }
    }

    fun formatSettingsSummary(settings: FlightSettings): String {
        return String.format(
            Locale.US,
            "alt %.0f/%.0fm dist %.0fm geo %s rth %.0fm/%ds loiter %dm@%.0fm",
            settings.minAltitudeMeters,
            settings.maxAltitudeMeters,
            settings.maxDistanceMeters,
            if (settings.geofenceEnabled) "on" else "off",
            settings.rthMinAltitudeMeters,
            settings.rthDelaySeconds,
            settings.loiterRadiusMeters,
            settings.loiterAltitudeMeters,
        )
    }

    private fun parsePort(label: String, rawValue: String): ParseResult<Int> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ParseResult(error = "$label is required")
        }
        val value = trimmed.toIntOrNull()
        if (value == null) {
            return ParseResult(error = "$label must be a whole number")
        }
        if (value !in MIN_PORT..MAX_PORT) {
            return ParseResult(error = "$label must be between $MIN_PORT and $MAX_PORT")
        }
        return ParseResult(value = value)
    }

    private fun parseInt(label: String, rawValue: String): ParseResult<Int> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ParseResult(error = "$label is required")
        }
        val value = trimmed.toIntOrNull()
        if (value == null) {
            return ParseResult(error = "$label must be a whole number")
        }
        return ParseResult(value = value)
    }

    private fun parseFloat(label: String, rawValue: String): ParseResult<Float> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ParseResult(error = "$label is required")
        }
        val value = trimmed.toFloatOrNull()
        if (value == null || !value.isFinite()) {
            return ParseResult(error = "$label must be a finite number")
        }
        return ParseResult(value = value)
    }
}
