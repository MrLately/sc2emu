package com.discostuff.sc2emu.flight

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ArNetwork {
    const val FRAME_TYPE_ACK = 1
    const val FRAME_TYPE_DATA = 2
    const val FRAME_TYPE_DATA_WITH_ACK = 4

    const val BD_NET_CD_NONACK_ID = 10
    const val BD_NET_CD_ACK_ID = 11
    const val BD_NET_DC_EVENT_ID = 126
    const val BD_NET_DC_NAVDATA_ID = 127

    const val INTERNAL_PING_ID = 0
    const val INTERNAL_PONG_ID = 1

    class Sequencer {
        private val seq = IntArray(256)

        @Synchronized
        fun next(id: Int): Int {
            val index = id and 0xFF
            seq[index] = (seq[index] + 1) and 0xFF
            return seq[index]
        }
    }

    data class ParsedFrame(
        val type: Int,
        val id: Int,
        val seq: Int,
        val size: Int,
        val payload: ByteArray,
    )

    fun generateFrame(
        payload: ByteArray,
        sequencer: Sequencer,
        type: Int = FRAME_TYPE_DATA,
        id: Int = BD_NET_CD_NONACK_ID,
    ): ByteArray {
        val header = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        header.put(type.toByte())
        header.put(id.toByte())
        header.put(sequencer.next(id).toByte())
        header.putInt(payload.size + 7)
        return header.array() + payload
    }

    fun parseFrame(data: ByteArray, length: Int): ParsedFrame? {
        if (length < 7) return null

        val size = ByteBuffer.wrap(data, 3, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (size < 7) return null

        val boundedSize = minOf(size, length)
        val payload = data.copyOfRange(7, boundedSize)

        return ParsedFrame(
            type = data[0].toInt() and 0xFF,
            id = data[1].toInt() and 0xFF,
            seq = data[2].toInt() and 0xFF,
            size = boundedSize,
            payload = payload,
        )
    }
}
