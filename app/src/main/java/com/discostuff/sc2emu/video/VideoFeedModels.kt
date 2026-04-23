package com.discostuff.sc2emu.video

data class VideoReceiverSnapshot(
    val running: Boolean = false,
    val decoderReady: Boolean = false,
    val lastRtpPacketAtMs: Long = 0L,
    val lastFrameDecodedAtMs: Long = 0L,
    val lastError: String? = null,
    val decodedFrames: Long = 0L,
)

enum class VideoFeedState {
    ENGINE_OFF,
    DISCOVERY_DOWN,
    TRANSPORT_DOWN,
    SURFACE_DOWN,
    STARTING,
    LIVE,
    DEGRADED,
    STALE,
    NO_VIDEO,
    DECODER_FAILED,
}

data class VideoFeedModel(
    val state: VideoFeedState,
    val label: String?,
    val blankVideo: Boolean,
    val shouldAttemptRecovery: Boolean,
)

object VideoFeedEvaluator {
    fun evaluate(
        engineRunning: Boolean,
        discoveryOk: Boolean,
        transportReady: Boolean,
        surfaceReady: Boolean,
        bootstrapActive: Boolean,
        bootstrapStartedAtMs: Long,
        snapshot: VideoReceiverSnapshot,
        nowMs: Long,
        startupTimeoutMs: Long,
        staleFrameMs: Long,
        stalePacketMs: Long,
    ): VideoFeedModel {
        val state = when {
            !engineRunning -> VideoFeedState.ENGINE_OFF
            !discoveryOk -> VideoFeedState.DISCOVERY_DOWN
            !transportReady -> VideoFeedState.TRANSPORT_DOWN
            !surfaceReady -> VideoFeedState.SURFACE_DOWN
            snapshot.lastError.isDecoderFailure() -> VideoFeedState.DECODER_FAILED
            snapshot.lastFrameDecodedAtMs > 0L -> {
                val frameAgeMs = ageMs(nowMs, snapshot.lastFrameDecodedAtMs)
                val packetAgeMs = ageMs(nowMs, snapshot.lastRtpPacketAtMs)
                if (frameAgeMs <= staleFrameMs) {
                    VideoFeedState.LIVE
                } else if (snapshot.running && packetAgeMs <= stalePacketMs) {
                    VideoFeedState.DEGRADED
                } else {
                    VideoFeedState.STALE
                }
            }
            bootstrapActive && ageMs(nowMs, bootstrapStartedAtMs) <= startupTimeoutMs -> VideoFeedState.STARTING
            snapshot.running && snapshot.lastRtpPacketAtMs > 0L &&
                ageMs(nowMs, snapshot.lastRtpPacketAtMs) <= stalePacketMs -> VideoFeedState.STARTING
            else -> VideoFeedState.NO_VIDEO
        }

        return VideoFeedModel(
            state = state,
            label = when (state) {
                VideoFeedState.ENGINE_OFF -> "Engine Off"
                VideoFeedState.DISCOVERY_DOWN -> "No Link"
                VideoFeedState.TRANSPORT_DOWN -> "Transport"
                VideoFeedState.SURFACE_DOWN -> "Video Surface"
                VideoFeedState.STARTING -> "Waiting Video"
                VideoFeedState.LIVE -> null
                VideoFeedState.DEGRADED -> "Video Degraded"
                VideoFeedState.STALE -> "Video Stale"
                VideoFeedState.NO_VIDEO -> "No Video"
                VideoFeedState.DECODER_FAILED -> "Decoder Failed"
            },
            blankVideo = state != VideoFeedState.LIVE && state != VideoFeedState.DEGRADED,
            shouldAttemptRecovery = state == VideoFeedState.STALE ||
                state == VideoFeedState.NO_VIDEO ||
                state == VideoFeedState.DECODER_FAILED,
        )
    }

    private fun ageMs(nowMs: Long, timestampMs: Long): Long {
        if (timestampMs <= 0L) return Long.MAX_VALUE
        return (nowMs - timestampMs).coerceAtLeast(0L)
    }

    private fun String?.isDecoderFailure(): Boolean {
        return this?.contains("decoder", ignoreCase = true) == true
    }
}
