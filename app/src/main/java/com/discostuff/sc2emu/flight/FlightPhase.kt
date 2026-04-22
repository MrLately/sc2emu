package com.discostuff.sc2emu.flight

enum class FlightPhase(
    val rawValue: Int?,
    val label: String,
) {
    UNKNOWN(null, "unknown"),
    LANDED(0, "landed"),
    TAKING_OFF(1, "taking off"),
    HOVERING(2, "hovering"),
    FLYING(3, "flying"),
    LANDING(4, "landing"),
    EMERGENCY(5, "emergency"),
    USER_TAKEOFF(6, "user takeoff"),
    MOTOR_RAMPING(7, "motor ramping"),
    EMERGENCY_LANDING(8, "emergency landing");

    val isAirborne: Boolean
        get() = this == TAKING_OFF || this == HOVERING || this == FLYING || this == LANDING || this == EMERGENCY_LANDING

    val allowsTakeoffStart: Boolean
        get() = this == LANDED

    val allowsTakeoffAbort: Boolean
        get() = this == USER_TAKEOFF || this == MOTOR_RAMPING

    val allowsLandCommand: Boolean
        get() = this == HOVERING || this == FLYING

    val allowsNavigateHomeStart: Boolean
        get() = this == HOVERING || this == FLYING

    val allowsMissionStart: Boolean
        get() = this == HOVERING || this == FLYING

    companion object {
        fun fromArsdk(rawValue: Int?): FlightPhase {
            return entries.firstOrNull { it.rawValue == rawValue } ?: UNKNOWN
        }
    }
}

enum class NavigateHomeState(
    val rawValue: Int?,
) {
    UNKNOWN(null),
    AVAILABLE(0),
    IN_PROGRESS(1),
    UNAVAILABLE(2),
    PENDING(3);

    val isActive: Boolean
        get() = this == IN_PROGRESS || this == PENDING

    companion object {
        fun fromArsdk(rawValue: Int?): NavigateHomeState {
            return entries.firstOrNull { it.rawValue == rawValue } ?: UNKNOWN
        }
    }
}

enum class NavigateHomeReason(
    val rawValue: Int?,
) {
    UNKNOWN(null),
    USER_REQUEST(0),
    CONNECTION_LOST(1),
    LOW_BATTERY(2),
    FINISHED(3),
    STOPPED(4),
    DISABLED(5),
    ENABLED(6),
    FLIGHTPLAN(7),
    ICING(8);

    companion object {
        fun fromArsdk(rawValue: Int?): NavigateHomeReason {
            return entries.firstOrNull { it.rawValue == rawValue } ?: UNKNOWN
        }
    }
}
