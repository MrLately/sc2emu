package com.discostuff.sc2emu.flight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FlightSessionStore {
    private val lock = Any()
    private val _state = MutableStateFlow(FlightState())
    val state: StateFlow<FlightState> = _state.asStateFlow()

    fun update(transform: (FlightState) -> FlightState) {
        synchronized(lock) {
            _state.value = transform(_state.value)
        }
    }

    fun reset(message: String = "ready") {
        update { FlightState(message = message) }
    }
}
