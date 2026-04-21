package com.discostuff.sc2emu.flight

data class FlightConfig(
    val discoIp: String,
    val discoveryPort: Int = 44444,
    val c2dPort: Int = 54321,
    val d2cPort: Int = 9988,
    val streamVideoPort: Int = 55004,
    val streamControlPort: Int = 55005,
)
