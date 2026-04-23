package com.discostuff.sc2emu.flight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.discostuff.sc2emu.R
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

class FlightEngineService : Service() {

    companion object {
        private const val DISCOVERY_ATTEMPTS = 8
        private const val DISCOVERY_RETRY_DELAY_MS = 700L
        private const val DISCOVERY_BACKOFF_INITIAL_MS = 2000L
        private const val DISCOVERY_BACKOFF_MEDIUM_MS = 5000L
        private const val DISCOVERY_BACKOFF_MAX_MS = 10000L
        private const val WATCHDOG_INTERVAL_MS = 1500L
        private const val TELEMETRY_TIMEOUT_MS = 6000L
        private const val RECONNECT_COOLDOWN_MS = 3500L

        const val ACTION_START = "com.discostuff.sc2emu.action.START"
        const val ACTION_STOP = "com.discostuff.sc2emu.action.STOP"
        const val ACTION_SET_STICKS = "com.discostuff.sc2emu.action.SET_STICKS"
        const val ACTION_SET_ARM = "com.discostuff.sc2emu.action.SET_ARM"
        const val ACTION_TAKEOFF = "com.discostuff.sc2emu.action.TAKEOFF"
        const val ACTION_LAND = "com.discostuff.sc2emu.action.LAND"
        const val ACTION_RTH_START = "com.discostuff.sc2emu.action.RTH_START"
        const val ACTION_RTH_STOP = "com.discostuff.sc2emu.action.RTH_STOP"
        const val ACTION_VIDEO_ON = "com.discostuff.sc2emu.action.VIDEO_ON"
        const val ACTION_VIDEO_OFF = "com.discostuff.sc2emu.action.VIDEO_OFF"
        const val ACTION_APPLY_SETTINGS = "com.discostuff.sc2emu.action.APPLY_SETTINGS"
        const val ACTION_MAVLINK_START = "com.discostuff.sc2emu.action.MAVLINK_START"
        const val ACTION_MAVLINK_PAUSE = "com.discostuff.sc2emu.action.MAVLINK_PAUSE"
        const val ACTION_MAVLINK_STOP = "com.discostuff.sc2emu.action.MAVLINK_STOP"
        const val ACTION_SEND_CONTROLLER_GPS = "com.discostuff.sc2emu.action.SEND_CONTROLLER_GPS"

        const val EXTRA_DISCO_IP = "extra.disco_ip"
        const val EXTRA_DISCOVERY_PORT = "extra.discovery_port"
        const val EXTRA_C2D_PORT = "extra.c2d_port"
        const val EXTRA_D2C_PORT = "extra.d2c_port"
        const val EXTRA_STREAM_VIDEO_PORT = "extra.stream_video_port"
        const val EXTRA_STREAM_CONTROL_PORT = "extra.stream_control_port"
        const val EXTRA_CLEAR_SESSION_STATE = "extra.clear_session_state"

        const val EXTRA_PITCH = "extra.pitch"
        const val EXTRA_ROLL = "extra.roll"
        const val EXTRA_YAW = "extra.yaw"
        const val EXTRA_THROTTLE = "extra.throttle"
        const val EXTRA_ARMED = "extra.armed"
        const val EXTRA_MAX_ALTITUDE_METERS = "extra.max_altitude_m"
        const val EXTRA_MIN_ALTITUDE_METERS = "extra.min_altitude_m"
        const val EXTRA_MAX_DISTANCE_METERS = "extra.max_distance_m"
        const val EXTRA_GEOFENCE_ENABLED = "extra.geofence_enabled"
        const val EXTRA_RTH_MIN_ALTITUDE_METERS = "extra.rth_min_altitude_m"
        const val EXTRA_RTH_DELAY_SECONDS = "extra.rth_delay_sec"
        const val EXTRA_LOITER_RADIUS_METERS = "extra.loiter_radius_m"
        const val EXTRA_LOITER_ALTITUDE_METERS = "extra.loiter_altitude_m"
        const val EXTRA_MAVLINK_FILE_PATH = "extra.mavlink_file_path"
        const val EXTRA_MAVLINK_TYPE = "extra.mavlink_type"
        const val EXTRA_CONTROLLER_LATITUDE = "extra.controller_latitude"
        const val EXTRA_CONTROLLER_LONGITUDE = "extra.controller_longitude"
        const val EXTRA_CONTROLLER_ALTITUDE = "extra.controller_altitude"
        const val EXTRA_CONTROLLER_HORIZONTAL_ACCURACY = "extra.controller_horizontal_accuracy"
        const val EXTRA_CONTROLLER_VERTICAL_ACCURACY = "extra.controller_vertical_accuracy"

        private const val NOTIFICATION_CHANNEL_ID = "flight_engine_channel"
        private const val NOTIFICATION_ID = 37

        private const val PREFS_NAME = "flight_engine_service"
        private const val KEY_RESUME_ENGINE = "resume_engine"
        private const val KEY_DISCO_IP = "disco_ip"
        private const val KEY_DISCOVERY_PORT = "discovery_port"
        private const val KEY_C2D_PORT = "c2d_port"
        private const val KEY_D2C_PORT = "d2c_port"
        private const val KEY_STREAM_VIDEO_PORT = "stream_video_port"
        private const val KEY_STREAM_CONTROL_PORT = "stream_control_port"
        private const val KEY_HOME_LATITUDE = "home_latitude"
        private const val KEY_HOME_LONGITUDE = "home_longitude"
        private const val KEY_HOME_ALTITUDE_METERS = "home_altitude_m"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionMutex = Mutex()
    private val servicePrefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private var engineJob: Job? = null
    private var telemetryJob: Job? = null
    private var controlLoopJob: Job? = null
    private var watchdogJob: Job? = null

    private var txSocket: DatagramSocket? = null
    private var rxSocket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null

    private val sequencer = ArNetwork.Sequencer()

    @Volatile
    private var pitch = 0

    @Volatile
    private var roll = 0

    @Volatile
    private var yaw = 0

    @Volatile
    private var throttle = 0

    @Volatile
    private var runningConfig: FlightConfig? = null

    @Volatile
    private var controlsArmed = false

    @Volatile
    private var lastAnyRxAtMs = 0L

    @Volatile
    private var reconnecting = false

    @Volatile
    private var reconnectAttempt = 0

    @Volatile
    private var lastReconnectAtMs = 0L

    @Volatile
    private var backgroundDiscoveryFailures = 0

    @Volatile
    private var nextDiscoveryAttemptAtMs = 0L

    private data class DiscoveryResult(
        val ok: Boolean,
        val error: String? = null,
    )

    private enum class ReconnectMode {
        FAST_BURST,
        BACKGROUND_SINGLE,
    }

    private data class HomeReference(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Float,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            maybeRecoverEngineAfterRestart()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val config = parseConfig(intent) ?: run {
                    updateStatus("invalid config")
                    return START_NOT_STICKY
                }
                startForegroundIfNeeded()
                startEngine(config)
            }

            ACTION_STOP -> {
                val clearSessionState = intent.getBooleanExtra(EXTRA_CLEAR_SESSION_STATE, true)
                stopEngine("stopped by user", clearResumeFlag = clearSessionState)
                stopSelf()
            }
            ACTION_SET_STICKS -> {
                pitch = clampAxis(intent.getIntExtra(EXTRA_PITCH, 0))
                roll = clampAxis(intent.getIntExtra(EXTRA_ROLL, 0))
                yaw = clampAxis(intent.getIntExtra(EXTRA_YAW, 0))
                throttle = clampAxis(intent.getIntExtra(EXTRA_THROTTLE, 0))
            }

            ACTION_SET_ARM -> {
                controlsArmed = intent.getBooleanExtra(EXTRA_ARMED, false)
                if (!controlsArmed) {
                    pitch = 0
                    roll = 0
                    yaw = 0
                    throttle = 0
                }
                FlightSessionStore.update { it.copy(controlsArmed = controlsArmed) }
                updateStatus(if (controlsArmed) "controls armed" else "controls disarmed")
            }

            ACTION_TAKEOFF -> safeActionAsync("takeoff") { sendTakeoff() }
            ACTION_LAND -> safeActionAsync("land") { sendLand() }
            ACTION_RTH_START -> safeActionAsync("rth start") { sendNavigateHome(true) }
            ACTION_RTH_STOP -> safeActionAsync("rth stop") { sendNavigateHome(false) }
            ACTION_VIDEO_ON -> safeActionAsync("video on") { sendVideoEnable(true) }
            ACTION_VIDEO_OFF -> safeActionAsync("video off") { sendVideoEnable(false) }
            ACTION_APPLY_SETTINGS -> safeActionAsync("apply settings") {
                val settings = parseFlightSettings(intent) ?: run {
                    updateStatus("apply settings skipped: invalid payload")
                    return@safeActionAsync
                }
                applyFlightSettings(settings)
            }
            ACTION_MAVLINK_START -> safeActionAsync("mavlink start") {
                val filePath = intent.getStringExtra(EXTRA_MAVLINK_FILE_PATH)?.trim().orEmpty()
                if (filePath.isEmpty()) {
                    updateStatus("mavlink start skipped: missing file path")
                    return@safeActionAsync
                }
                val type = intent.getIntExtra(EXTRA_MAVLINK_TYPE, 0).coerceIn(0, 2)
                sendMavlinkStart(filePath, type)
            }
            ACTION_MAVLINK_PAUSE -> safeActionAsync("mavlink pause") { sendMavlinkPause() }
            ACTION_MAVLINK_STOP -> safeActionAsync("mavlink stop") { sendMavlinkStop() }
            ACTION_SEND_CONTROLLER_GPS -> safeActionAsync("controller gps") {
                val latitude = intent.getDoubleExtra(EXTRA_CONTROLLER_LATITUDE, Double.NaN)
                val longitude = intent.getDoubleExtra(EXTRA_CONTROLLER_LONGITUDE, Double.NaN)
                val altitude = intent.getDoubleExtra(EXTRA_CONTROLLER_ALTITUDE, 0.0)
                val horizontalAccuracy = intent.getDoubleExtra(EXTRA_CONTROLLER_HORIZONTAL_ACCURACY, 5.0)
                val verticalAccuracy = intent.getDoubleExtra(EXTRA_CONTROLLER_VERTICAL_ACCURACY, 8.0)
                if (!latitude.isFinite() || !longitude.isFinite()) {
                    updateStatus("controller gps skipped: invalid coordinates")
                    return@safeActionAsync
                }
                sendControllerGps(latitude, longitude, altitude, horizontalAccuracy, verticalAccuracy)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopEngine("service destroyed", clearResumeFlag = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopEngine("app task removed")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun parseConfig(intent: Intent): FlightConfig? {
        val ip = intent.getStringExtra(EXTRA_DISCO_IP)?.trim().orEmpty()
        if (ip.isBlank()) return null

        return FlightConfig(
            discoIp = ip,
            discoveryPort = intent.getIntExtra(EXTRA_DISCOVERY_PORT, 44444),
            c2dPort = intent.getIntExtra(EXTRA_C2D_PORT, 54321),
            d2cPort = intent.getIntExtra(EXTRA_D2C_PORT, 9988),
            streamVideoPort = intent.getIntExtra(EXTRA_STREAM_VIDEO_PORT, 55004),
            streamControlPort = intent.getIntExtra(EXTRA_STREAM_CONTROL_PORT, 55005),
        )
    }

    private fun parseFlightSettings(intent: Intent): FlightSettings? {
        val maxAltitude = intent.getFloatExtra(EXTRA_MAX_ALTITUDE_METERS, Float.NaN)
        val minAltitude = intent.getFloatExtra(EXTRA_MIN_ALTITUDE_METERS, Float.NaN)
        val maxDistance = intent.getFloatExtra(EXTRA_MAX_DISTANCE_METERS, Float.NaN)
        val geofenceEnabled = intent.getBooleanExtra(EXTRA_GEOFENCE_ENABLED, true)
        val rthMinAltitude = intent.getFloatExtra(EXTRA_RTH_MIN_ALTITUDE_METERS, Float.NaN)
        val rthDelaySeconds = intent.getIntExtra(EXTRA_RTH_DELAY_SECONDS, -1)
        val loiterRadius = intent.getIntExtra(EXTRA_LOITER_RADIUS_METERS, -1)
        val loiterAltitude = intent.getFloatExtra(EXTRA_LOITER_ALTITUDE_METERS, Float.NaN)

        if (!maxAltitude.isFinite() || !minAltitude.isFinite() || !maxDistance.isFinite() || !rthMinAltitude.isFinite() || !loiterAltitude.isFinite()) {
            return null
        }
        if (maxAltitude <= 0f || minAltitude < 0f || minAltitude > maxAltitude) {
            return null
        }
        if (maxDistance < 0f || rthMinAltitude < 0f || loiterAltitude < 0f || rthDelaySeconds < 0 || loiterRadius <= 0) {
            return null
        }
        if (geofenceEnabled && maxDistance <= 0f) {
            return null
        }

        return FlightSettings(
            maxAltitudeMeters = maxAltitude,
            minAltitudeMeters = minAltitude,
            maxDistanceMeters = maxDistance,
            geofenceEnabled = geofenceEnabled,
            rthMinAltitudeMeters = rthMinAltitude,
            rthDelaySeconds = rthDelaySeconds,
            loiterRadiusMeters = loiterRadius,
            loiterAltitudeMeters = loiterAltitude,
        )
    }

    private fun startEngine(config: FlightConfig) {
        if (FlightSessionStore.state.value.engineRunning || telemetryJob?.isActive == true || controlLoopJob?.isActive == true) {
            updateStatus("engine already running")
            return
        }

        persistEngineConfig(config, resume = true)
        val savedHome = readSavedHomeReference()
        runningConfig = config
        pitch = 0
        roll = 0
        yaw = 0
        throttle = 0

        FlightSessionStore.update {
            it.copy(
                engineRunning = true,
                discoveryOk = false,
                transportReady = false,
                controlsArmed = controlsArmed,
                planeLinkPercent = 0,
                groundSpeedMps = 0f,
                txPackets = 0,
                rxPackets = 0,
                homeLatitude = savedHome?.latitude,
                homeLongitude = savedHome?.longitude,
                homeAltitudeMeters = savedHome?.altitudeMeters,
                message = "starting engine",
            )
        }
        refreshNotification("starting")

        engineJob = serviceScope.launch {
            try {
                if (watchdogJob?.isActive != true) {
                    watchdogJob = serviceScope.launch { watchdogLoop(config) }
                }

                val discovery = reconnectTransport(config, mode = ReconnectMode.FAST_BURST)
                if (!discovery.ok) {
                    val discoveryMessage = buildString {
                        append("discovery failed")
                        if (!discovery.error.isNullOrBlank()) {
                            append(": ")
                            append(discovery.error)
                        }
                    }

                    backgroundDiscoveryFailures = 1
                    nextDiscoveryAttemptAtMs = System.currentTimeMillis() + DISCOVERY_BACKOFF_INITIAL_MS
                    FlightSessionStore.update {
                        it.copy(
                            discoveryOk = false,
                            transportReady = false,
                            controlsArmed = controlsArmed,
                            planeLinkPercent = 0,
                            homeLatitude = savedHome?.latitude,
                            homeLongitude = savedHome?.longitude,
                            homeAltitudeMeters = savedHome?.altitudeMeters,
                            message = "$discoveryMessage (background retry)",
                        )
                    }
                    refreshNotification("discovery searching")
                    return@launch
                }

                onTransportConnected(
                    config = config,
                    stateMessage = "discovery ok",
                    notificationStatus = "connected",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                telemetryJob?.cancel()
                controlLoopJob?.cancel()
                watchdogJob?.cancel()
                telemetryJob = null
                controlLoopJob = null
                watchdogJob = null
                clearNetworkResources()
                runningConfig = null
                persistEngineConfig(config, resume = false)
                backgroundDiscoveryFailures = 0
                nextDiscoveryAttemptAtMs = 0L
                FlightSessionStore.update {
                    it.copy(
                        engineRunning = false,
                        discoveryOk = false,
                        transportReady = false,
                        controlsArmed = controlsArmed,
                        planeLinkPercent = 0,
                        homeLatitude = savedHome?.latitude,
                        homeLongitude = savedHome?.longitude,
                        homeAltitudeMeters = savedHome?.altitudeMeters,
                        message = "engine error: ${e.message ?: e::class.java.simpleName}",
                    )
                }
                refreshNotification("error")
            }
        }
    }

    private fun stopEngine(reason: String, clearResumeFlag: Boolean = true) {
        engineJob?.cancel()
        telemetryJob?.cancel()
        controlLoopJob?.cancel()
        watchdogJob?.cancel()
        engineJob = null
        telemetryJob = null
        controlLoopJob = null
        watchdogJob = null

        clearNetworkResources()
        targetAddress = null
        runningConfig = null
        pitch = 0
        roll = 0
        yaw = 0
        throttle = 0
        reconnecting = false
        reconnectAttempt = 0
        lastReconnectAtMs = 0L
        lastAnyRxAtMs = 0L
        backgroundDiscoveryFailures = 0
        nextDiscoveryAttemptAtMs = 0L
        if (clearResumeFlag) {
            servicePrefs.edit().putBoolean(KEY_RESUME_ENGINE, false).apply()
            persistHomeReference(null)
        }

        FlightSessionStore.reset(reason)
        FlightSessionStore.update { it.copy(controlsArmed = controlsArmed) }
        refreshNotification("stopped")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(false)
        }
    }

    private suspend fun telemetryLoop(config: FlightConfig) {
        val packetBuffer = ByteArray(2048)

        while (currentCoroutineContext().isActive) {
            val socket = rxSocket
            if (socket == null) {
                delay(150)
                continue
            }

            try {
                val packet = DatagramPacket(packetBuffer, packetBuffer.size)
                socket.receive(packet)
                lastAnyRxAtMs = System.currentTimeMillis()
                FlightSessionStore.update {
                    it.copy(
                        rxPackets = it.rxPackets + 1,
                        planeLinkPercent = computePlaneLinkPercent(lastAnyRxAtMs),
                    )
                }

                val frame = ArNetwork.parseFrame(packet.data, packet.length) ?: continue
                handleFrame(frame, config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (e.message?.contains("Socket closed", ignoreCase = true) == true) {
                    delay(120)
                }
            } catch (e: Exception) {
                updateStatus("telemetry error: ${e.message}")
            }
        }
    }

    private suspend fun controlLoop(config: FlightConfig) {
        while (currentCoroutineContext().isActive) {
            try {
                sendPcmd(config)
                delay(40)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStatus("control loop error: ${e.message}")
                delay(200)
            }
        }
    }

    private suspend fun watchdogLoop(config: FlightConfig) {
        while (currentCoroutineContext().isActive) {
            delay(WATCHDOG_INTERVAL_MS)
            if (!currentCoroutineContext().isActive) break

            val state = FlightSessionStore.state.value
            if (!state.engineRunning) continue
            if (reconnecting) continue

            val now = System.currentTimeMillis()
            FlightSessionStore.update { it.copy(planeLinkPercent = computePlaneLinkPercent(now)) }

            val noTelemetryMs = if (lastAnyRxAtMs > 0L) now - lastAnyRxAtMs else Long.MAX_VALUE
            val staleTelemetry = noTelemetryMs > TELEMETRY_TIMEOUT_MS
            val reconnectReady = now - lastReconnectAtMs >= RECONNECT_COOLDOWN_MS

            if (state.discoveryOk && (!state.transportReady || staleTelemetry)) {
                if (!reconnectReady) continue
                val reason = if (!state.transportReady) {
                    "transport not ready"
                } else {
                    "telemetry timeout ${noTelemetryMs}ms"
                }
                performReconnect(config, reason, mode = ReconnectMode.FAST_BURST)
                continue
            }

            if (!state.discoveryOk) {
                if (now < nextDiscoveryAttemptAtMs) continue
                performReconnect(config, "discovery search", mode = ReconnectMode.BACKGROUND_SINGLE)
            }
        }
    }

    private suspend fun performReconnect(config: FlightConfig, reason: String, mode: ReconnectMode) {
        reconnecting = true
        reconnectAttempt += 1
        lastReconnectAtMs = System.currentTimeMillis()

        val attemptLabel = if (mode == ReconnectMode.FAST_BURST) "reconnect" else "discovery"
        FlightSessionStore.update {
            it.copy(
                discoveryOk = false,
                transportReady = false,
                message = "$attemptLabel #$reconnectAttempt: $reason",
            )
        }
        refreshNotification("$attemptLabel #$reconnectAttempt")
        val result = reconnectTransport(config, mode = mode)
        if (result.ok) {
            reconnectAttempt = 0
            onTransportConnected(
                config = config,
                stateMessage = "reconnected",
                notificationStatus = "reconnected",
            )
        } else {
            val error = result.error ?: "no response"
            if (mode == ReconnectMode.BACKGROUND_SINGLE) {
                backgroundDiscoveryFailures += 1
                val backoffMs = backgroundDiscoveryBackoffMs(backgroundDiscoveryFailures)
                nextDiscoveryAttemptAtMs = System.currentTimeMillis() + backoffMs
                val waiting = "discovery retry in ${backoffMs / 1000L}s: $error"
                FlightSessionStore.update {
                    it.copy(
                        discoveryOk = false,
                        transportReady = false,
                        planeLinkPercent = 0,
                        message = waiting,
                    )
                }
                refreshNotification(waiting)
            } else {
                backgroundDiscoveryFailures = 1
                nextDiscoveryAttemptAtMs = System.currentTimeMillis() + DISCOVERY_BACKOFF_INITIAL_MS
                val failure = "reconnect failed: $error"
                FlightSessionStore.update {
                    it.copy(
                        discoveryOk = false,
                        transportReady = false,
                        planeLinkPercent = 0,
                        message = failure,
                    )
                }
                refreshNotification(failure)
            }
        }
        reconnecting = false
    }

    private fun handleFrame(frame: ArNetwork.ParsedFrame, config: FlightConfig) {
        if (frame.type == ArNetwork.FRAME_TYPE_DATA_WITH_ACK) {
            sendAck(config, frame.id, frame.seq)
        }

        if (frame.id == ArNetwork.INTERNAL_PING_ID) {
            sendPong(config, frame.payload)
            return
        }

        if (frame.id == ArNetwork.BD_NET_DC_EVENT_ID || frame.id == ArNetwork.BD_NET_DC_NAVDATA_ID) {
            parseTelemetryPayload(frame.payload)
        }
    }

    private fun parseTelemetryPayload(payload: ByteArray) {
        if (payload.size < 4) return
        val project = payload[0].toInt() and 0xFF
        val commandClass = payload[1].toInt() and 0xFF
        val commandId = ByteBuffer.wrap(payload, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        val now = System.currentTimeMillis()

        when {
            project == 0 && commandClass == 12 && commandId == 0 && payload.size >= 8 -> {
                val state = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                var offset = 8
                val filePath = readNullTerminatedString(payload, offset)
                offset += filePath.toByteArray(Charsets.UTF_8).size + 1
                val type = if (payload.size >= offset + 4) {
                    ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                } else {
                    null
                }
                FlightSessionStore.update {
                    it.copy(
                        mavlinkPlayingState = state,
                        mavlinkFilePath = filePath.ifBlank { null },
                        mavlinkType = type,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 0 && commandClass == 12 && commandId == 1 && payload.size >= 8 -> {
                val error = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                FlightSessionStore.update {
                    it.copy(
                        mavlinkPlayError = error,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 0 && commandClass == 12 && commandId == 2 && payload.size >= 8 -> {
                val idx = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                FlightSessionStore.update {
                    it.copy(
                        missionItemExecutedIndex = idx,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 0 && commandClass == 5 && commandId == 1 && payload.size >= 5 -> {
                val battery = payload[4].toInt() and 0xFF
                FlightSessionStore.update {
                    it.copy(
                        planeBatteryPercent = battery,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 1 && commandClass == 4 && commandId == 8 && payload.size >= 8 -> {
                val altitude = when {
                    // Disco reports AltitudeChanged as double in the command schema.
                    payload.size >= 12 -> ByteBuffer.wrap(payload, 4, 8).order(ByteOrder.LITTLE_ENDIAN).double.toFloat()
                    else -> ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
                }
                val normalizedAltitude = altitude.takeIf { it.isFinite() && it in -1000f..15000f }
                FlightSessionStore.update {
                    it.copy(
                        altitudeMeters = normalizedAltitude,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            // ARDRONE3 PilotingState.SpeedChanged (speedX, speedY, speedZ in m/s)
            project == 1 && commandClass == 4 && commandId == 5 && payload.size >= 16 -> {
                val speedX = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
                val speedY = ByteBuffer.wrap(payload, 8, 4).order(ByteOrder.LITTLE_ENDIAN).float
                val horizontal = sqrt((speedX * speedX + speedY * speedY).toDouble()).toFloat()
                FlightSessionStore.update {
                    it.copy(
                        groundSpeedMps = horizontal,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            // ARDRONE3 PilotingState.AttitudeChanged (roll, pitch, yaw in radians)
            project == 1 && commandClass == 4 && commandId == 6 && payload.size >= 16 -> {
                val roll = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
                val pitch = ByteBuffer.wrap(payload, 8, 4).order(ByteOrder.LITTLE_ENDIAN).float
                FlightSessionStore.update {
                    it.copy(
                        attitudeRollRad = roll,
                        attitudePitchRad = pitch,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 1 && commandClass == 4 && commandId == 4 && payload.size >= 20 -> {
                val lat = ByteBuffer.wrap(payload, 4, 8).order(ByteOrder.LITTLE_ENDIAN).double
                val lon = ByteBuffer.wrap(payload, 12, 8).order(ByteOrder.LITTLE_ENDIAN).double
                if (lat != 0.0 || lon != 0.0) {
                    FlightSessionStore.update {
                        it.copy(
                            latitude = lat,
                            longitude = lon,
                            lastTelemetryAtMs = now,
                        )
                    }
                }
            }

            project == 1 && commandClass == 4 && commandId == 1 && payload.size >= 8 -> {
                val state = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                FlightSessionStore.update {
                    it.copy(
                        flyingState = state,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            project == 1 && commandClass == 4 && commandId == 3 && payload.size >= 12 -> {
                val state = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val reason = ByteBuffer.wrap(payload, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                FlightSessionStore.update {
                    it.copy(
                        navigateHomeState = state,
                        navigateHomeReason = reason,
                        lastTelemetryAtMs = now,
                    )
                }
            }

            // ARDRONE3 PilotingState.LandingStateChanged (0=linear, 1=spiral)
            project == 1 && commandClass == 4 && commandId == 10 && payload.size >= 8 -> {
                val state = ByteBuffer.wrap(payload, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                FlightSessionStore.update {
                    it.copy(
                        landingState = state,
                        lastTelemetryAtMs = now,
                    )
                }
            }
        }
    }

    private fun readNullTerminatedString(payload: ByteArray, offset: Int): String {
        if (offset >= payload.size) return ""
        var end = offset
        while (end < payload.size && payload[end] != 0.toByte()) {
            end++
        }
        if (end <= offset) return ""
        return String(payload, offset, end - offset, Charsets.UTF_8)
    }

    private fun sendPcmd(config: FlightConfig) {
        val armed = controlsArmed
        val localPitch = if (armed) clampAxis(pitch) else 0
        val localRoll = if (armed) clampAxis(roll) else 0
        val localYaw = if (armed) clampAxis(yaw) else 0
        val localThrottle = if (armed) clampAxis(throttle) else 0
        val movementFlag = if (armed && (localPitch != 0 || localRoll != 0 || localThrottle != 0)) 1 else 0

        val payload = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(1) // ARDRONE3 project
            put(0) // Piloting class
            putShort(2) // PCMD command id
            put(movementFlag.toByte())
            put(localRoll.toByte())
            put(localPitch.toByte())
            put(localYaw.toByte())
            put(localThrottle.toByte())
            putFloat(0f) // psi
        }.array()

        sendPayload(config, payload)
    }

    private fun sendTakeoff() {
        val config = requireCriticalCommandConfig("takeoff") ?: return
        val phase = FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState)
        if (!phase.allowsTakeoffStart) {
            updateStatus("takeoff blocked: aircraft is ${phase.label}")
            return
        }
        if (!controlsArmed) {
            updateStatus("takeoff blocked: arm controls first")
            return
        }
        val home = setHomeFromCurrentPosition(config)
        if (!sendUserTakeOff(config, enabled = true)) {
            return
        }
        if (home != null) {
            persistHomeReference(home)
        }
        updateStatus(if (home != null) "takeoff command sent (home set)" else "takeoff command sent (home unchanged)")
    }

    private fun sendUserTakeOff(config: FlightConfig, enabled: Boolean): Boolean {
        return sendSimpleCommand(
            config = config,
            project = 1,
            commandClass = 0,
            commandId = 8,
            args = byteArrayOf(if (enabled) 1 else 0),
            label = if (enabled) "takeoff" else "takeoff abort",
        )
    }

    private fun sendLand() {
        val config = requireCriticalCommandConfig("land") ?: return
        when (FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState)) {
            FlightPhase.USER_TAKEOFF,
            FlightPhase.MOTOR_RAMPING,
            -> {
                if (!sendUserTakeOff(config, enabled = false)) {
                    return
                }
                updateStatus("takeoff cancel sent")
            }

            FlightPhase.HOVERING,
            FlightPhase.FLYING,
            -> {
                if (!sendSimpleCommand(config, project = 1, commandClass = 0, commandId = 3, label = "land")) {
                    return
                }
                updateStatus("land command sent")
            }

            else -> {
                updateStatus("land blocked: aircraft is ${FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState).label}")
            }
        }
    }

    private fun sendNavigateHome(start: Boolean) {
        val config = requireCriticalCommandConfig(if (start) "rth start" else "rth stop") ?: return
        if (start && !FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState).allowsNavigateHomeStart) {
            updateStatus("rth start blocked: aircraft is ${FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState).label}")
            return
        }
        if (!sendSimpleCommand(
                config,
                project = 1,
                commandClass = 0,
                commandId = 5,
                args = byteArrayOf(if (start) 1 else 0),
                label = if (start) "rth start" else "rth stop",
            )
        ) {
            return
        }
        updateStatus(if (start) "rth start sent" else "rth stop sent")
    }

    private fun sendMavlinkStart(filePath: String, type: Int) {
        val config = requireCriticalCommandConfig("mavlink start") ?: return
        val phase = FlightPhase.fromArsdk(FlightSessionStore.state.value.flyingState)
        if (!phase.allowsMissionStart) {
            updateStatus("mavlink start blocked: aircraft is ${phase.label}")
            return
        }
        val normalizedPath = filePath.trim()
        val pathBytes = normalizedPath.toByteArray(Charsets.UTF_8)
        val args = ByteBuffer.allocate(pathBytes.size + 1 + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(pathBytes)
            put(0)
            putInt(type)
        }.array()
        if (!sendSimpleCommand(config, project = 0, commandClass = 11, commandId = 0, args = args, label = "mavlink start")) {
            return
        }
        updateStatus("mavlink start sent: $normalizedPath")
    }

    private fun sendMavlinkPause() {
        val config = requireCriticalCommandConfig("mavlink pause") ?: return
        if (!sendSimpleCommand(config, project = 0, commandClass = 11, commandId = 1, label = "mavlink pause")) {
            return
        }
        updateStatus("mavlink pause sent")
    }

    private fun sendMavlinkStop() {
        val config = requireCriticalCommandConfig("mavlink stop") ?: return
        if (!sendSimpleCommand(config, project = 0, commandClass = 11, commandId = 2, label = "mavlink stop")) {
            return
        }
        updateStatus("mavlink stop sent")
    }

    private fun sendControllerGps(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        horizontalAccuracy: Double,
        verticalAccuracy: Double,
    ) {
        val config = requireRunningConfig("controller gps") ?: return
        val payload = ArCommandEncoder.encodeControllerGps(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            horizontalAccuracy = horizontalAccuracy,
            verticalAccuracy = verticalAccuracy,
        )
        sendPayloadWithAck(config, payload)
    }

    private fun setHomeFromCurrentPosition(config: FlightConfig): HomeReference? {
        val state = FlightSessionStore.state.value
        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return null
        if (lat == 0.0 && lon == 0.0) return null

        val altitudeMeters = state.altitudeMeters ?: 0f
        val altitude = altitudeMeters.toDouble()
        val setHomeArgs = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putDouble(lat)
            putDouble(lon)
            putDouble(altitude)
        }.array()
        if (!sendSimpleCommand(config, project = 1, commandClass = 23, commandId = 0, args = setHomeArgs, label = "set home")) {
            return null
        }
        if (!sendSimpleCommand(config, project = 1, commandClass = 23, commandId = 3, args = byteArrayOf(0), label = "home type")) {
            return null
        }
        return HomeReference(latitude = lat, longitude = lon, altitudeMeters = altitudeMeters)
    }

    private fun applyFlightSettings(settings: FlightSettings) {
        val config = requireCriticalCommandConfig("apply settings") ?: return

        val applied =
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 2,
            commandId = 0,
            args = encodeFloat(settings.maxAltitudeMeters),
            label = "apply settings",
        ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 2,
            commandId = 11,
            args = encodeFloat(settings.minAltitudeMeters),
            label = "apply settings",
        ) &&
            (
                settings.maxDistanceMeters <= 0f ||
                    sendSimpleCommand(
                config,
                project = 1,
                commandClass = 2,
                commandId = 3,
                args = encodeFloat(settings.maxDistanceMeters),
                label = "apply settings",
            )
            ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 2,
            commandId = 4,
            args = byteArrayOf(if (settings.geofenceEnabled) 1 else 0),
            label = "apply settings",
        ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 2,
            commandId = 13,
            args = encodeU16(settings.loiterRadiusMeters),
            label = "apply settings",
        ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 2,
            commandId = 14,
            args = encodeU16(settings.loiterAltitudeMeters.toInt()),
            label = "apply settings",
        ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 23,
            commandId = 5,
            args = encodeFloat(settings.rthMinAltitudeMeters),
            label = "apply settings",
        ) &&
            sendSimpleCommand(
            config,
            project = 1,
            commandClass = 23,
            commandId = 4,
            args = encodeU16(settings.rthDelaySeconds),
            label = "apply settings",
        )
        if (!applied) {
            return
        }
        updateStatus("flight settings applied: ${FlightInputParsers.formatSettingsSummary(settings)}")
    }

    private fun encodeFloat(value: Float): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()

    private fun encodeU16(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.coerceIn(0, 65535).toShort()).array()

    private fun sendVideoEnable(enabled: Boolean) {
        val config = requireRunningConfig(if (enabled) "video on" else "video off") ?: return
        if (enabled) {
            // Request high reliability stream mode (enum 1) before enabling.
            val reliabilityMode = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array()
            if (!sendSimpleCommand(
                config,
                project = 1,
                commandClass = 21,
                commandId = 1,
                args = reliabilityMode,
                label = "video reliability",
            )) {
                return
            }
        }
        try {
            if (!sendSimpleCommand(
                config,
                project = 1,
                commandClass = 21,
                commandId = 0,
                args = byteArrayOf(if (enabled) 1 else 0),
                label = if (enabled) "video on" else "video off",
            )) {
                return
            }
        } catch (e: Exception) {
            if (!enabled && isSocketClosedError(e)) {
                // Shutdown races can close sockets before VIDEO_OFF; this is harmless.
                return
            }
            throw e
        }
        updateStatus(if (enabled) "video enable sent" else "video disable sent")
    }

    private fun isSocketClosedError(error: Exception): Boolean {
        val message = error.message ?: return false
        return message.contains("Socket closed", ignoreCase = true) ||
            message.contains("Socket is closed", ignoreCase = true)
    }

    private fun sendAllStates() {
        val config = requireRunningConfig("all states") ?: return
        sendSimpleCommand(config, project = 0, commandClass = 4, commandId = 0)
    }

    private fun sendSimpleCommand(
        config: FlightConfig,
        project: Int,
        commandClass: Int,
        commandId: Int,
        args: ByteArray = byteArrayOf(),
        label: String? = null,
    ): Boolean {
        val payload = ArCommandEncoder.encodeSimpleCommand(
            project = project,
            commandClass = commandClass,
            commandId = commandId,
            args = args,
        )
        return sendPayload(config, payload, label)
    }

    private fun sendPayload(config: FlightConfig, payload: ByteArray, label: String? = null): Boolean {
        val frame = ArNetwork.generateFrame(
            payload = payload,
            sequencer = sequencer,
            type = ArNetwork.FRAME_TYPE_DATA,
            id = ArNetwork.BD_NET_CD_NONACK_ID,
        )
        return sendDatagram(config, frame, label)
    }

    private fun sendPayloadWithAck(config: FlightConfig, payload: ByteArray, label: String? = null): Boolean {
        val frame = ArNetwork.generateFrame(
            payload = payload,
            sequencer = sequencer,
            type = ArNetwork.FRAME_TYPE_DATA_WITH_ACK,
            id = ArNetwork.BD_NET_CD_ACK_ID,
        )
        return sendDatagram(config, frame, label)
    }

    private fun sendAck(config: FlightConfig, incomingId: Int, incomingSeq: Int) {
        val ackId = (incomingId + 128) and 0xFF
        val frame = ArNetwork.generateFrame(
            payload = byteArrayOf(incomingSeq.toByte()),
            sequencer = sequencer,
            type = ArNetwork.FRAME_TYPE_ACK,
            id = ackId,
        )
        sendDatagram(config, frame)
    }

    private fun sendPong(config: FlightConfig, pingPayload: ByteArray) {
        val frame = ArNetwork.generateFrame(
            payload = pingPayload,
            sequencer = sequencer,
            type = ArNetwork.FRAME_TYPE_DATA,
            id = ArNetwork.INTERNAL_PONG_ID,
        )
        sendDatagram(config, frame)
    }

    private fun sendDatagram(config: FlightConfig, bytes: ByteArray, label: String? = null): Boolean {
        val socket = txSocket ?: return markSendFailure(label, "transport socket unavailable")
        val address = targetAddress ?: return markSendFailure(label, "target address unavailable")
        val packet = DatagramPacket(bytes, bytes.size, address, config.c2dPort)
        return try {
            socket.send(packet)
            FlightSessionStore.update {
                it.copy(
                    txPackets = it.txPackets + 1,
                    transportReady = isTransportReady(),
                )
            }
            true
        } catch (e: IOException) {
            FlightSessionStore.update { it.copy(transportReady = false) }
            markSendFailure(label, e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun reconnectTransport(
        config: FlightConfig,
        mode: ReconnectMode = ReconnectMode.FAST_BURST,
    ): DiscoveryResult {
        return try {
            clearNetworkResources()
            targetAddress = InetAddress.getByName(config.discoIp)
            txSocket = DatagramSocket()
            rxSocket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 1000
                bind(InetSocketAddress(config.d2cPort))
            }

            val discovery = when (mode) {
                ReconnectMode.FAST_BURST -> performDiscovery(config)
                ReconnectMode.BACKGROUND_SINGLE -> performDiscoverySingle(config)
            }
            if (!discovery.ok) {
                clearNetworkResources()
            }
            discovery
        } catch (e: Exception) {
            clearNetworkResources()
            DiscoveryResult(ok = false, error = e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun performDiscovery(config: FlightConfig): DiscoveryResult {
        var lastError = "no response"

        repeat(DISCOVERY_ATTEMPTS) { index ->
            val attempt = index + 1
            val attemptResult = performDiscoverySingle(config)
            if (attemptResult.ok) {
                return attemptResult
            }

            lastError = attemptResult.error ?: "no response"
            if (attempt < DISCOVERY_ATTEMPTS) {
                updateStatus("discovery retry $attempt/$DISCOVERY_ATTEMPTS: $lastError")
                delay(DISCOVERY_RETRY_DELAY_MS)
            }
        }

        return DiscoveryResult(ok = false, error = lastError)
    }

    private fun performDiscoverySingle(config: FlightConfig): DiscoveryResult {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.discoIp, config.discoveryPort), 3000)
                socket.soTimeout = 3000

                val discoveryJson = """
                    {"controller_type":"Skycontroller 2 Dummy","controller_name":"DiscoPilot-Android","d2c_port":${config.d2cPort},"arstream2_client_stream_port":${config.streamVideoPort},"arstream2_client_control_port":${config.streamControlPort},"arstream2_supported_metadata_version":1,"qos_mode":1}
                """.trimIndent().replace("\n", "")

                socket.getOutputStream().write(discoveryJson.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()

                val buffer = ByteArray(512)
                val bytes = socket.getInputStream().read(buffer)
                if (bytes > 0) {
                    DiscoveryResult(ok = true)
                } else {
                    DiscoveryResult(ok = false, error = "empty discovery reply")
                }
            }
        } catch (e: Exception) {
            DiscoveryResult(ok = false, error = e.message ?: e::class.java.simpleName)
        }
    }

    private fun onTransportConnected(
        config: FlightConfig,
        stateMessage: String,
        notificationStatus: String,
    ) {
        lastAnyRxAtMs = System.currentTimeMillis()
        reconnectAttempt = 0
        reconnecting = false
        lastReconnectAtMs = 0L
        backgroundDiscoveryFailures = 0
        nextDiscoveryAttemptAtMs = 0L

        FlightSessionStore.update {
            it.copy(
                discoveryOk = true,
                transportReady = isTransportReady(),
                controlsArmed = controlsArmed,
                planeLinkPercent = 100,
                message = stateMessage,
            )
        }
        refreshNotification(notificationStatus)

        sendAllStates()
        sendVideoEnable(true)
        ensureDataLoopsRunning(config)
    }

    private fun ensureDataLoopsRunning(config: FlightConfig) {
        if (telemetryJob?.isActive != true) {
            telemetryJob = serviceScope.launch { telemetryLoop(config) }
        }
        if (controlLoopJob?.isActive != true) {
            controlLoopJob = serviceScope.launch { controlLoop(config) }
        }
        if (watchdogJob?.isActive != true) {
            watchdogJob = serviceScope.launch { watchdogLoop(config) }
        }
    }

    private fun backgroundDiscoveryBackoffMs(failureCount: Int): Long {
        return when {
            failureCount <= 1 -> DISCOVERY_BACKOFF_INITIAL_MS
            failureCount == 2 -> DISCOVERY_BACKOFF_MEDIUM_MS
            else -> DISCOVERY_BACKOFF_MAX_MS
        }
    }

    private fun clampAxis(value: Int): Int = value.coerceIn(-100, 100)

    private fun isTransportReady(): Boolean = txSocket != null && targetAddress != null

    private fun computePlaneLinkPercent(nowMs: Long): Int {
        if (lastAnyRxAtMs <= 0L) return 0
        val age = nowMs - lastAnyRxAtMs
        return when {
            age <= 800L -> 100
            age <= 1600L -> 85
            age <= 2800L -> 70
            age <= 4200L -> 55
            age <= 6000L -> 40
            age <= 8000L -> 20
            else -> 0
        }
    }

    private fun requireRunningConfig(action: String): FlightConfig? {
        val config = runningConfig
        if (config == null) {
            updateStatus("$action skipped: engine not running")
        }
        return config
    }

    private fun requireCriticalCommandConfig(action: String): FlightConfig? {
        val config = requireRunningConfig(action) ?: return null
        val state = FlightSessionStore.state.value
        if (reconnecting) {
            updateStatus("$action blocked: reconnecting")
            return null
        }
        if (!state.discoveryOk) {
            updateStatus("$action blocked: discovery not ready")
            return null
        }
        if (!state.transportReady || !isTransportReady()) {
            updateStatus("$action blocked: transport not ready")
            return null
        }
        return config
    }

    private fun markSendFailure(label: String?, reason: String): Boolean {
        if (label != null) {
            updateStatus("$label failed: $reason")
        }
        return false
    }

    private fun safeAction(label: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            updateStatus("$label failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun safeActionAsync(label: String, action: () -> Unit) {
        serviceScope.launch {
            actionMutex.withLock {
                safeAction(label, action)
            }
        }
    }

    private fun clearNetworkResources() {
        try {
            txSocket?.close()
        } catch (_: Exception) {
        }
        try {
            rxSocket?.close()
        } catch (_: Exception) {
        }
        txSocket = null
        rxSocket = null
    }

    private fun persistEngineConfig(config: FlightConfig, resume: Boolean) {
        servicePrefs.edit()
            .putBoolean(KEY_RESUME_ENGINE, resume)
            .putString(KEY_DISCO_IP, config.discoIp)
            .putInt(KEY_DISCOVERY_PORT, config.discoveryPort)
            .putInt(KEY_C2D_PORT, config.c2dPort)
            .putInt(KEY_D2C_PORT, config.d2cPort)
            .putInt(KEY_STREAM_VIDEO_PORT, config.streamVideoPort)
            .putInt(KEY_STREAM_CONTROL_PORT, config.streamControlPort)
            .apply()
    }

    private fun persistHomeReference(home: HomeReference?) {
        servicePrefs.edit().apply {
            if (home == null) {
                remove(KEY_HOME_LATITUDE)
                remove(KEY_HOME_LONGITUDE)
                remove(KEY_HOME_ALTITUDE_METERS)
            } else {
                putLong(KEY_HOME_LATITUDE, java.lang.Double.doubleToRawLongBits(home.latitude))
                putLong(KEY_HOME_LONGITUDE, java.lang.Double.doubleToRawLongBits(home.longitude))
                putFloat(KEY_HOME_ALTITUDE_METERS, home.altitudeMeters)
            }
        }.apply()

        FlightSessionStore.update {
            it.copy(
                homeLatitude = home?.latitude,
                homeLongitude = home?.longitude,
                homeAltitudeMeters = home?.altitudeMeters,
            )
        }
    }

    private fun readSavedHomeReference(): HomeReference? {
        if (!servicePrefs.contains(KEY_HOME_LATITUDE) || !servicePrefs.contains(KEY_HOME_LONGITUDE)) {
            return null
        }
        val latitude = java.lang.Double.longBitsToDouble(servicePrefs.getLong(KEY_HOME_LATITUDE, 0L))
        val longitude = java.lang.Double.longBitsToDouble(servicePrefs.getLong(KEY_HOME_LONGITUDE, 0L))
        val altitudeMeters = servicePrefs.getFloat(KEY_HOME_ALTITUDE_METERS, 0f)
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return null
        }
        return HomeReference(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
        )
    }

    private fun readSavedConfig(): FlightConfig? {
        val ip = servicePrefs.getString(KEY_DISCO_IP, null) ?: return null
        if (ip.isBlank()) return null

        return FlightConfig(
            discoIp = ip,
            discoveryPort = servicePrefs.getInt(KEY_DISCOVERY_PORT, 44444),
            c2dPort = servicePrefs.getInt(KEY_C2D_PORT, 54321),
            d2cPort = servicePrefs.getInt(KEY_D2C_PORT, 9988),
            streamVideoPort = servicePrefs.getInt(KEY_STREAM_VIDEO_PORT, 55004),
            streamControlPort = servicePrefs.getInt(KEY_STREAM_CONTROL_PORT, 55005),
        )
    }

    private fun maybeRecoverEngineAfterRestart() {
        if (engineJob?.isActive == true || FlightSessionStore.state.value.engineRunning) return
        if (!servicePrefs.getBoolean(KEY_RESUME_ENGINE, false)) return

        val config = readSavedConfig() ?: run {
            servicePrefs.edit().putBoolean(KEY_RESUME_ENGINE, false).apply()
            return
        }

        startForegroundIfNeeded()
        startEngine(config)
        updateStatus("service restarted, recovering engine")
    }

    private fun updateStatus(text: String) {
        FlightSessionStore.update { it.copy(message = text) }
        refreshNotification(text)
    }

    private fun startForegroundIfNeeded() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("starting"))
    }

    private fun refreshNotification(status: String) {
        createNotificationChannel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val state = FlightSessionStore.state.value
        val subtitle = String.format(
            Locale.US,
            "Engine %s | TX %d RX %d",
            if (state.engineRunning) "ON" else "OFF",
            state.txPackets,
            state.rxPackets,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$subtitle | $status")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Flight Engine",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }
}
