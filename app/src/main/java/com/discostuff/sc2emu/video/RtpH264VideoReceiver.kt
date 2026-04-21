package com.discostuff.sc2emu.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RtpH264VideoReceiver(
    private val onStatus: (String) -> Unit,
    private val onFirstFrameDecoded: (() -> Unit)? = null,
) {
    companion object {
        private const val TAG = "DiscoVideoRx"
        private const val MIME_TYPE = "video/avc"
        private const val RTP_HEADER_SIZE = 12
        private const val JITTER_WAIT_MS = 24L
        private const val JITTER_MAX_REORDER = 6
        private const val JITTER_MAX_PENDING = 96
        private const val JITTER_MAX_DRAIN_PER_TICK = 32
        private val ANNEXB_START = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverJob: Job? = null
    private var socket: DatagramSocket? = null
    private var codec: MediaCodec? = null
    private var fuBuffer: ByteArrayOutputStream? = null
    private var accessUnitBuffer = ByteArrayOutputStream(16 * 1024)
    private var accessUnitHasIdr = false
    private var accessUnitNalCount = 0
    private var currentAccessUnitTimestamp = -1L
    private var currentAccessUnitCorrupt = false
    private var currentAccessUnitGapScore = 0
    private var expectedSequence: Int? = null
    private var expectedSsrc: Long? = null
    private var jitterExpectedSequence: Int? = null
    private val pendingPackets = HashMap<Int, PendingPacket>(128)
    private var lastGapSize = 0
    private var rtpPackets = 0L
    private var queuedNals = 0L
    private var queuedAccessUnits = 0L
    private var droppedCorruptAccessUnits = 0L
    private var decodedFrames = 0L
    private var droppedNoInputBuffer = 0L
    private var spsCount = 0L
    private var ppsCount = 0L
    private var idrCount = 0L
    private var lastProgressUpdateMs = 0L
    private var firstFrameNotified = false

    @Volatile
    private var running = false

    private data class PendingPacket(
        val sequence: Int,
        val bytes: ByteArray,
        val length: Int,
        val arrivalMs: Long,
    )

    fun isRunning(): Boolean = running

    fun start(surface: Surface, port: Int, width: Int = 856, height: Int = 480) {
        stopReceiver()

        try {
            configureCodec(surface, width, height)
        } catch (e: Exception) {
            onStatus("decoder init failed: ${e.message ?: e::class.java.simpleName}")
            releaseCodec()
            return
        }

        running = true
        rtpPackets = 0
        queuedNals = 0
        queuedAccessUnits = 0
        decodedFrames = 0
        droppedNoInputBuffer = 0
        spsCount = 0
        ppsCount = 0
        idrCount = 0
        accessUnitBuffer = ByteArrayOutputStream(16 * 1024)
        accessUnitHasIdr = false
        accessUnitNalCount = 0
        currentAccessUnitTimestamp = -1L
        currentAccessUnitCorrupt = false
        currentAccessUnitGapScore = 0
        expectedSequence = null
        expectedSsrc = null
        jitterExpectedSequence = null
        pendingPackets.clear()
        lastGapSize = 0
        droppedCorruptAccessUnits = 0
        lastProgressUpdateMs = 0
        firstFrameNotified = false

        receiverJob = scope.launch {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 120
                    bind(InetSocketAddress(port))
                }
                onStatus("listening on UDP $port")
                Log.i(TAG, "receiver started on UDP $port")

                val packetBuffer = ByteArray(4096)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(packetBuffer, packetBuffer.size)
                        socket?.receive(packet)
                        rtpPackets++
                        enqueuePendingPacket(packet.data, packet.length)
                        drainPendingPackets(force = false)
                        maybeUpdateProgress()
                    } catch (_: SocketTimeoutException) {
                        // No packet yet.
                        drainPendingPackets(force = true)
                        maybeUpdateProgress()
                    } catch (e: Exception) {
                        if (!running || !isActive) break
                        onStatus("video rx error: ${e.message ?: e::class.java.simpleName}")
                    }
                    drainDecoder()
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "receiver failed", e)
            onStatus("video receiver failed: ${e.message ?: e::class.java.simpleName}")
                }
            } finally {
                drainPendingPackets(force = true)
                cleanup()
            }
        }
    }

    fun stop() {
        stopReceiver()
        Log.i(TAG, "receiver stop requested")
    }

    fun destroy() {
        stopReceiver()
        scope.cancel()
    }

    private fun stopReceiver() {
        running = false
        val job = receiverJob
        receiverJob = null
        job?.cancel()
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        // Release active resources immediately without blocking the caller thread.
        cleanup()
        if (job != null) {
            scope.launch {
                runCatching { job.join() }
            }
        }
    }

    private fun configureCodec(surface: Surface, width: Int, height: Int) {
        codec = MediaCodec.createDecoderByType(MIME_TYPE).apply {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
            configure(format, surface, null, 0)
            start()
        }
        Log.i(TAG, "decoder ready ${width}x$height")
        onStatus("decoder ready")
    }

    private fun parseRtpPacket(data: ByteArray, length: Int) {
        if (length < RTP_HEADER_SIZE) return
        val b0 = data[0].toInt() and 0xFF
        val version = b0 ushr 6
        if (version != 2) return
        val marker = (data[1].toInt() and 0x80) != 0
        val sequence = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val rtpTimestamp = readRtpTimestamp(data, length)
        val ssrc = readRtpSsrc(data, length)

        if (expectedSsrc == null) {
            expectedSsrc = ssrc
        } else if (expectedSsrc != ssrc) {
            // Some links can swap RTP SSRC mid-stream; reset parser state and resync.
            expectedSsrc = ssrc
            expectedSequence = null
            fuBuffer = null
            currentAccessUnitTimestamp = -1L
            currentAccessUnitCorrupt = false
            currentAccessUnitGapScore = 0
            dropCurrentAccessUnit()
        }

        when (updateSequenceStatus(sequence)) {
            SequenceStatus.OUT_OF_ORDER -> {
                // During startup, tolerate reordering and resync quickly so we can lock onto the stream.
                if (decodedFrames > 0) return
                expectedSequence = (sequence + 1) and 0xFFFF
                fuBuffer = null
            }
            SequenceStatus.GAP -> {
                if (currentAccessUnitTimestamp >= 0L) {
                    val gapIsLarge = lastGapSize >= 4
                    val gapDuringFragment = fuBuffer != null
                    currentAccessUnitGapScore += lastGapSize.coerceAtLeast(1)

                    // Avoid starving startup: tolerate early loss until decoder starts outputting.
                    if (decodedFrames > 0 && (gapIsLarge || gapDuringFragment || currentAccessUnitGapScore >= 6)) {
                        currentAccessUnitCorrupt = true
                    }
                }
                fuBuffer = null
            }
            SequenceStatus.OK -> Unit
        }

        val csrcCount = b0 and 0x0F
        val hasPadding = (b0 and 0x20) != 0
        val hasExtension = (b0 and 0x10) != 0

        var offset = RTP_HEADER_SIZE + csrcCount * 4
        if (offset >= length) return

        if (hasExtension) {
            if (length < offset + 4) return
            val extensionWords = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            offset += 4 + extensionWords * 4
            if (offset >= length) return
        }

        var payloadEnd = length
        if (hasPadding) {
            val padBytes = data[length - 1].toInt() and 0xFF
            if (padBytes in 1 until (length - offset + 1)) {
                payloadEnd = length - padBytes
            }
        }

        val payloadLength = payloadEnd - offset
        if (payloadLength <= 0) return

        if (currentAccessUnitTimestamp < 0) {
            currentAccessUnitTimestamp = rtpTimestamp
        } else if (rtpTimestamp != currentAccessUnitTimestamp) {
            finalizeCurrentAccessUnit()
            currentAccessUnitTimestamp = rtpTimestamp
            currentAccessUnitCorrupt = false
            currentAccessUnitGapScore = 0
        }

        handleH264Payload(data, offset, payloadLength)
        if (marker) {
            finalizeCurrentAccessUnit()
            currentAccessUnitTimestamp = -1L
            currentAccessUnitCorrupt = false
            currentAccessUnitGapScore = 0
        }
    }

    private fun handleH264Payload(packet: ByteArray, offset: Int, payloadLength: Int) {
        if (payloadLength <= 0) return
        val nalHeader = packet[offset].toInt() and 0xFF
        val nalType = nalHeader and 0x1F

        when (nalType) {
            in 1..23 -> {
                appendSingleNalToAccessUnit(packet, offset, payloadLength, nalType)
            }
            24 -> {
                handleStapA(packet, offset, payloadLength)
            }
            28 -> handleFuA(packet, offset, payloadLength)
            else -> {
                // Unsupported packet type for now.
            }
        }
    }

    private fun handleStapA(packet: ByteArray, offset: Int, payloadLength: Int) {
        var pos = offset + 1
        val end = offset + payloadLength
        while (pos + 2 <= end) {
            val nalSize = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2
            if (nalSize <= 0 || pos + nalSize > end) {
                return
            }
            val nalType = packet[pos].toInt() and 0x1F
            appendSingleNalToAccessUnit(packet, pos, nalSize, nalType)
            pos += nalSize
        }
    }

    private fun handleFuA(packet: ByteArray, offset: Int, payloadLength: Int) {
        if (payloadLength < 2) return
        val fuIndicator = packet[offset].toInt() and 0xFF
        val fuHeader = packet[offset + 1].toInt() and 0xFF
        val start = (fuHeader and 0x80) != 0
        val end = (fuHeader and 0x40) != 0
        val originalNalType = fuHeader and 0x1F
        val reconstructedHeader = ((fuIndicator and 0xE0) or originalNalType).toByte()

        if (start) {
            fuBuffer = ByteArrayOutputStream(payloadLength + 128).apply {
                write(ANNEXB_START)
                write(reconstructedHeader.toInt())
                write(packet, offset + 2, payloadLength - 2)
            }
        } else {
            fuBuffer?.write(packet, offset + 2, payloadLength - 2)
        }

        if (end) {
            val completeNal = fuBuffer?.toByteArray() ?: return
            fuBuffer = null
            appendAnnexBNalToAccessUnit(completeNal, originalNalType)
        }
    }

    private fun readRtpTimestamp(data: ByteArray, length: Int): Long {
        if (length < 8) return -1L
        return ((data[4].toLong() and 0xFF) shl 24) or
            ((data[5].toLong() and 0xFF) shl 16) or
            ((data[6].toLong() and 0xFF) shl 8) or
            (data[7].toLong() and 0xFF)
    }

    private fun readRtpSequence(data: ByteArray, length: Int): Int {
        if (length < 4) return -1
        return ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
    }

    private fun readRtpSsrc(data: ByteArray, length: Int): Long {
        if (length < 12) return -1L
        return ((data[8].toLong() and 0xFF) shl 24) or
            ((data[9].toLong() and 0xFF) shl 16) or
            ((data[10].toLong() and 0xFF) shl 8) or
            (data[11].toLong() and 0xFF)
    }

    private fun enqueuePendingPacket(data: ByteArray, length: Int) {
        if (length < RTP_HEADER_SIZE) return
        val sequence = readRtpSequence(data, length)
        if (sequence < 0) return
        if (pendingPackets.containsKey(sequence)) return

        pendingPackets[sequence] = PendingPacket(
            sequence = sequence,
            bytes = data.copyOf(length),
            length = length,
            arrivalMs = System.currentTimeMillis(),
        )
        if (jitterExpectedSequence == null) {
            jitterExpectedSequence = sequence
        }
    }

    private fun drainPendingPackets(force: Boolean) {
        if (pendingPackets.isEmpty()) return

        var loops = 0
        while (loops < JITTER_MAX_DRAIN_PER_TICK) {
            val expected = jitterExpectedSequence ?: break
            val inOrder = pendingPackets.remove(expected)
            if (inOrder != null) {
                parseRtpPacket(inOrder.bytes, inOrder.length)
                jitterExpectedSequence = (expected + 1) and 0xFFFF
                loops++
                continue
            }

            val nextSequence = findClosestForwardSequence(expected) ?: break
            val distance = forwardDistance(expected, nextSequence)
            val oldestAgeMs = oldestPendingAgeMs()
            val backlogPressure = pendingPackets.size >= (JITTER_MAX_PENDING / 2)
            val timeoutReached = oldestAgeMs >= JITTER_WAIT_MS
            val shouldSkip = force || timeoutReached || backlogPressure || distance > JITTER_MAX_REORDER
            if (!shouldSkip) {
                break
            }

            // Advance over likely lost sequence IDs until we hit available packets.
            val jumps = if (distance > JITTER_MAX_REORDER * 2) distance else 1
            jitterExpectedSequence = (expected + jumps) and 0xFFFF
            loops++
        }

        if (pendingPackets.size > JITTER_MAX_PENDING) {
            val expected = jitterExpectedSequence ?: return
            var dropKey: Int? = null
            var farthestDistance = -1
            for ((sequence, _) in pendingPackets) {
                val distance = forwardDistance(expected, sequence)
                if (distance > farthestDistance) {
                    farthestDistance = distance
                    dropKey = sequence
                }
            }
            if (dropKey != null) {
                pendingPackets.remove(dropKey)
            }
        }
    }

    private fun oldestPendingAgeMs(): Long {
        if (pendingPackets.isEmpty()) return 0L
        val now = System.currentTimeMillis()
        var oldestArrival = Long.MAX_VALUE
        for (pending in pendingPackets.values) {
            if (pending.arrivalMs < oldestArrival) {
                oldestArrival = pending.arrivalMs
            }
        }
        return if (oldestArrival == Long.MAX_VALUE) 0L else (now - oldestArrival)
    }

    private fun findClosestForwardSequence(expected: Int): Int? {
        var bestSequence: Int? = null
        var bestDistance = Int.MAX_VALUE
        for ((sequence, _) in pendingPackets) {
            val distance = forwardDistance(expected, sequence)
            if (distance < bestDistance) {
                bestDistance = distance
                bestSequence = sequence
            }
        }
        return bestSequence
    }

    private fun forwardDistance(from: Int, to: Int): Int {
        return (to - from + 0x10000) and 0xFFFF
    }

    private fun appendSingleNalToAccessUnit(packet: ByteArray, offset: Int, nalSize: Int, nalType: Int) {
        val annexB = ByteArray(ANNEXB_START.size + nalSize)
        System.arraycopy(ANNEXB_START, 0, annexB, 0, ANNEXB_START.size)
        System.arraycopy(packet, offset, annexB, ANNEXB_START.size, nalSize)
        appendAnnexBNalToAccessUnit(annexB, nalType)
    }

    private fun appendAnnexBNalToAccessUnit(data: ByteArray, nalType: Int) {
        val normalizedNalType = nalType and 0x1F
        if (normalizedNalType == 7 && accessUnitNalCount > 0) {
            // SPS frequently marks a new encoded picture boundary on this stream.
            flushAccessUnitToDecoder()
        }

        when (normalizedNalType) {
            5 -> {
                idrCount++
                accessUnitHasIdr = true
            }
            7 -> spsCount++
            8 -> ppsCount++
        }

        // Guard against runaway buffers if marker packets are missing.
        if (accessUnitBuffer.size() > 1024 * 1024) {
            flushAccessUnitToDecoder()
        }

        accessUnitBuffer.write(data, 0, data.size)
        accessUnitNalCount++
        if (accessUnitNalCount >= 120) {
            flushAccessUnitToDecoder()
        }
    }

    private fun flushAccessUnitToDecoder() {
        if (accessUnitNalCount <= 0 || accessUnitBuffer.size() <= 0) return
        queueAccessUnit(accessUnitBuffer.toByteArray(), accessUnitHasIdr, accessUnitNalCount)
        accessUnitBuffer.reset()
        accessUnitHasIdr = false
        accessUnitNalCount = 0
        currentAccessUnitGapScore = 0
    }

    private fun queueAccessUnit(data: ByteArray, isKeyframe: Boolean, nalCount: Int) {
        val mediaCodec = codec ?: return
        val inputIndex = try {
            mediaCodec.dequeueInputBuffer(1000)
        } catch (_: IllegalStateException) {
            return
        }
        if (inputIndex < 0) {
            droppedNoInputBuffer++
            return
        }

        val inputBuffer = mediaCodec.getInputBuffer(inputIndex) ?: return
        if (data.size > inputBuffer.capacity()) {
            mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
            onStatus("drop nal too large (${data.size})")
            return
        }

        inputBuffer.clear()
        inputBuffer.put(data)
        val ptsUs = System.nanoTime() / 1000L
        val flags = if (isKeyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        mediaCodec.queueInputBuffer(inputIndex, 0, data.size, ptsUs, flags)
        queuedNals += nalCount.toLong()
        queuedAccessUnits++
    }

    private fun finalizeCurrentAccessUnit() {
        if (currentAccessUnitCorrupt) {
            dropCurrentAccessUnit()
            return
        }
        flushAccessUnitToDecoder()
    }

    private fun dropCurrentAccessUnit() {
        if (accessUnitNalCount > 0 || accessUnitBuffer.size() > 0) {
            droppedCorruptAccessUnits++
        }
        accessUnitBuffer.reset()
        accessUnitHasIdr = false
        accessUnitNalCount = 0
    }

    private fun drainDecoder() {
        val mediaCodec = codec ?: return
        val info = MediaCodec.BufferInfo()
        var outIndex = try {
            mediaCodec.dequeueOutputBuffer(info, 0)
        } catch (_: IllegalStateException) {
            return
        }
        while (outIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outIndex, true)
            if (!firstFrameNotified) {
                firstFrameNotified = true
                onFirstFrameDecoded?.invoke()
            }
            decodedFrames++
            outIndex = mediaCodec.dequeueOutputBuffer(info, 0)
            maybeUpdateProgress()
        }

        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i(TAG, "output format changed: ${mediaCodec.outputFormat}")
            onStatus("video format: ${mediaCodec.outputFormat}")
        }
    }

    private fun cleanup() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        fuBuffer = null
        accessUnitBuffer.reset()
        accessUnitHasIdr = false
        accessUnitNalCount = 0
        currentAccessUnitTimestamp = -1L
        currentAccessUnitCorrupt = false
        currentAccessUnitGapScore = 0
        expectedSequence = null
        expectedSsrc = null
        jitterExpectedSequence = null
        pendingPackets.clear()
        lastGapSize = 0
        firstFrameNotified = false
        releaseCodec()
        running = false
        Log.i(TAG, "receiver cleaned up")
    }

    private fun releaseCodec() {
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
    }

    private fun maybeUpdateProgress() {
        val now = System.currentTimeMillis()
        if (lastProgressUpdateMs == 0L || now - lastProgressUpdateMs >= 1500L) {
            lastProgressUpdateMs = now
            val status = "rx=$rtpPackets jb=${pendingPackets.size} au=$queuedAccessUnits nal=$queuedNals dec=$decodedFrames idr=$idrCount sps=$spsCount pps=$ppsCount wait=$droppedNoInputBuffer corr=$droppedCorruptAccessUnits"
            Log.d(TAG, status)
            onStatus(status)
        }
    }

    private enum class SequenceStatus {
        OK,
        GAP,
        OUT_OF_ORDER,
    }

    private fun updateSequenceStatus(sequence: Int): SequenceStatus {
        val expected = expectedSequence
        if (expected == null) {
            expectedSequence = (sequence + 1) and 0xFFFF
            return SequenceStatus.OK
        }

        val delta = (sequence - expected + 0x10000) and 0xFFFF
        return when {
            delta == 0 -> {
                expectedSequence = (expected + 1) and 0xFFFF
                SequenceStatus.OK
            }
            delta < 0x8000 -> {
                // Forward jump means at least one packet got lost.
                lastGapSize = delta
                expectedSequence = (sequence + 1) and 0xFFFF
                SequenceStatus.GAP
            }
            else -> {
                // Late/out-of-order packet; ignore it to avoid corrupting current AU.
                SequenceStatus.OUT_OF_ORDER
            }
        }
    }
}
