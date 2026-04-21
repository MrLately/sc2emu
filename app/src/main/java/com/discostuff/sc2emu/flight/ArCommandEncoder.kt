package com.discostuff.sc2emu.flight

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ArCommandEncoder {
    fun encodeSimpleCommand(
        project: Int,
        commandClass: Int,
        commandId: Int,
        args: ByteArray = byteArrayOf(),
    ): ByteArray {
        return ByteBuffer.allocate(4 + args.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(project.toByte())
            put(commandClass.toByte())
            putShort(commandId.toShort())
            put(args)
        }.array()
    }

    fun encodeControllerGps(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        horizontalAccuracy: Double,
        verticalAccuracy: Double,
    ): ByteArray {
        val args = ByteBuffer.allocate(8 * 5).order(ByteOrder.LITTLE_ENDIAN).apply {
            putDouble(latitude)
            putDouble(longitude)
            putDouble(altitude)
            putDouble(horizontalAccuracy)
            putDouble(verticalAccuracy)
        }.array()
        return encodeSimpleCommand(
            project = 1,
            commandClass = 23,
            commandId = 2,
            args = args,
        )
    }
}
