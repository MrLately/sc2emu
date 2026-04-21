package com.discostuff.sc2emu.flight

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ArCommandEncoderTest {
    @Test
    fun encodeSimpleCommand_includesProjectClassIdAndArgs() {
        val payload = ArCommandEncoder.encodeSimpleCommand(
            project = 1,
            commandClass = 0,
            commandId = 8,
            args = byteArrayOf(1),
        )

        assertEquals(5, payload.size)
        assertEquals(1, payload[0].toInt() and 0xFF)
        assertEquals(0, payload[1].toInt() and 0xFF)
        val commandId = ByteBuffer.wrap(payload, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        assertEquals(8, commandId)
        assertEquals(1, payload[4].toInt() and 0xFF)
    }

    @Test
    fun encodeControllerGps_hasExpectedLayout() {
        val payload = ArCommandEncoder.encodeControllerGps(
            latitude = 41.5,
            longitude = -81.6,
            altitude = 120.25,
            horizontalAccuracy = 3.0,
            verticalAccuracy = 8.0,
        )

        assertEquals(44, payload.size)
        assertEquals(1, payload[0].toInt() and 0xFF)
        assertEquals(23, payload[1].toInt() and 0xFF)
        val commandId = ByteBuffer.wrap(payload, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        assertEquals(2, commandId)

        val args = ByteBuffer.wrap(payload, 4, 40).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(41.5, args.double, 1e-9)
        assertEquals(-81.6, args.double, 1e-9)
        assertEquals(120.25, args.double, 1e-9)
        assertEquals(3.0, args.double, 1e-9)
        assertEquals(8.0, args.double, 1e-9)
    }
}
