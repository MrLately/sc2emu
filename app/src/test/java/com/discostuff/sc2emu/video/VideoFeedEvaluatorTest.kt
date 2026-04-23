package com.discostuff.sc2emu.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFeedEvaluatorTest {
    @Test
    fun `startup stays waiting until timeout then becomes no video`() {
        val snapshot = VideoReceiverSnapshot(running = true)

        val waiting = VideoFeedEvaluator.evaluate(
            engineRunning = true,
            discoveryOk = true,
            transportReady = true,
            surfaceReady = true,
            bootstrapActive = true,
            bootstrapStartedAtMs = 1_000L,
            snapshot = snapshot,
            nowMs = 4_000L,
            startupTimeoutMs = 4_500L,
            staleFrameMs = 2_500L,
            stalePacketMs = 2_500L,
        )
        assertEquals(VideoFeedState.STARTING, waiting.state)

        val timedOut = VideoFeedEvaluator.evaluate(
            engineRunning = true,
            discoveryOk = true,
            transportReady = true,
            surfaceReady = true,
            bootstrapActive = false,
            bootstrapStartedAtMs = 1_000L,
            snapshot = snapshot,
            nowMs = 6_000L,
            startupTimeoutMs = 4_500L,
            staleFrameMs = 2_500L,
            stalePacketMs = 2_500L,
        )
        assertEquals(VideoFeedState.NO_VIDEO, timedOut.state)
        assertTrue(timedOut.shouldAttemptRecovery)
    }

    @Test
    fun `decoded frames become stale when frame age exceeds threshold`() {
        val stale = VideoFeedEvaluator.evaluate(
            engineRunning = true,
            discoveryOk = true,
            transportReady = true,
            surfaceReady = true,
            bootstrapActive = false,
            bootstrapStartedAtMs = 0L,
            snapshot = VideoReceiverSnapshot(
                running = true,
                decoderReady = true,
                lastRtpPacketAtMs = 8_000L,
                lastFrameDecodedAtMs = 8_000L,
                decodedFrames = 24,
            ),
            nowMs = 11_000L,
            startupTimeoutMs = 4_500L,
            staleFrameMs = 2_500L,
            stalePacketMs = 2_500L,
        )

        assertEquals(VideoFeedState.STALE, stale.state)
        assertTrue(stale.blankVideo)
        assertTrue(stale.shouldAttemptRecovery)
    }

    @Test
    fun `decoder failures stay non-live and do not masquerade as waiting`() {
        val failed = VideoFeedEvaluator.evaluate(
            engineRunning = true,
            discoveryOk = true,
            transportReady = true,
            surfaceReady = true,
            bootstrapActive = false,
            bootstrapStartedAtMs = 0L,
            snapshot = VideoReceiverSnapshot(
                running = false,
                decoderReady = false,
                lastError = "decoder init failed: codec unavailable",
            ),
            nowMs = 2_000L,
            startupTimeoutMs = 4_500L,
            staleFrameMs = 2_500L,
            stalePacketMs = 2_500L,
        )

        assertEquals(VideoFeedState.DECODER_FAILED, failed.state)
        assertFalse(failed.label.isNullOrBlank())
        assertTrue(failed.shouldAttemptRecovery)
    }
}
