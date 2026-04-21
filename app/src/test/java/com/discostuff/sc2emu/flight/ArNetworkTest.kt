package com.discostuff.sc2emu.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ArNetworkTest {
    @Test
    fun generateAndParseFrame_roundTripsPayloadAndHeader() {
        val sequencer = ArNetwork.Sequencer()
        val payload = byteArrayOf(0x01, 0x17, 0x02, 0x00)

        val frame = ArNetwork.generateFrame(
            payload = payload,
            sequencer = sequencer,
            type = ArNetwork.FRAME_TYPE_DATA_WITH_ACK,
            id = ArNetwork.BD_NET_CD_ACK_ID,
        )

        val parsed = ArNetwork.parseFrame(frame, frame.size)
        assertNotNull(parsed)
        parsed ?: return

        assertEquals(ArNetwork.FRAME_TYPE_DATA_WITH_ACK, parsed.type)
        assertEquals(ArNetwork.BD_NET_CD_ACK_ID, parsed.id)
        assertEquals(payload.toList(), parsed.payload.toList())
    }
}
