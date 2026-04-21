package com.discostuff.sc2emu

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.discostuff.sc2emu.controls.VerticalSeekBar
import com.discostuff.sc2emu.controls.VirtualStickView
import com.discostuff.sc2emu.flight.FlightConfig
import com.discostuff.sc2emu.flight.FlightEngineService
import com.discostuff.sc2emu.flight.FlightSessionStore
import com.discostuff.sc2emu.flight.FlightState
import com.discostuff.sc2emu.mission.MissionMavlinkBuilder
import com.discostuff.sc2emu.video.RtpH264VideoReceiver
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREF_KEY_DISCO_IP = "disco_ip"
        private const val PREF_KEY_DISCOVERY_PORT = "discovery_port"
        private const val PREF_KEY_C2D_PORT = "c2d_port"
        private const val PREF_KEY_D2C_PORT = "d2c_port"
        private const val PREF_KEY_STREAM_VIDEO_PORT = "stream_video_port"
        private const val PREF_KEY_STREAM_CONTROL_PORT = "stream_control_port"
        private const val PREF_KEY_CONFIG_VISIBLE = "config_visible"
        private const val PREF_KEY_BENCH_LOCK_ENABLED = "bench_lock_enabled"
        private const val PREF_KEY_MAX_ALTITUDE_M = "max_altitude_m"
        private const val PREF_KEY_MIN_ALTITUDE_M = "min_altitude_m"
        private const val PREF_KEY_MAX_DISTANCE_M = "max_distance_m"
        private const val PREF_KEY_GEOFENCE_ENABLED = "geofence_enabled"
        private const val PREF_KEY_RTH_MIN_ALTITUDE_M = "rth_min_altitude_m"
        private const val PREF_KEY_RTH_DELAY_SEC = "rth_delay_sec"
        private const val PREF_KEY_LOITER_RADIUS_M = "loiter_radius_m"
        private const val PREF_KEY_LOITER_ALTITUDE_M = "loiter_altitude_m"

        private const val DEFAULT_MAX_ALTITUDE_M = 120f
        private const val DEFAULT_MIN_ALTITUDE_M = 5f
        private const val DEFAULT_MAX_DISTANCE_M = 2000f
        private const val DEFAULT_GEOFENCE_ENABLED = true
        private const val DEFAULT_RTH_MIN_ALTITUDE_M = 40f
        private const val DEFAULT_RTH_DELAY_SEC = 0
        private const val DEFAULT_LOITER_RADIUS_M = 30
        private const val DEFAULT_LOITER_ALTITUDE_M = 60f

        private const val MODEM_TELEMETRY_PORT = 18080
        private const val MODEM_TELEMETRY_PATH = "/telemetry.json"
        private const val MODEM_TELEMETRY_POLL_MS = 2500L
        private const val MODEM_TELEMETRY_TIMEOUT_MS = 1200
        private const val MODEM_TELEMETRY_UNAVAILABLE_GRACE_MS = 12_000L
        private const val TELEMETRY_WARN_AGE_MS = 3_500L
        private const val TELEMETRY_STALE_AGE_MS = 6_000L
        private const val ALERT_LOG_MAX_LINES = 120
        private const val COMMAND_LOG_MAX_LINES = 160
        private const val LOW_PLANE_BATTERY_WARN_PCT = 25
        private const val LOW_PLANE_BATTERY_CRITICAL_PCT = 15
        private const val LOW_PHONE_BATTERY_WARN_PCT = 15
        private const val LOW_PHONE_BATTERY_CRITICAL_PCT = 10

        private const val PREF_KEY_MISSION_PATTERN_INDEX = "mission_pattern_index"
        private const val PREF_KEY_MISSION_ALTITUDE_M = "mission_altitude_m"
        private const val PREF_KEY_MISSION_SPACING_M = "mission_spacing_m"
        private const val PREF_KEY_MISSION_POINTS_JSON = "mission_points_json"
        private const val PREF_KEY_MISSION_TERMINAL_ACTION = "mission_terminal_action"
        private const val PREF_KEY_MISSION_PREVIEW_MODE = "mission_preview_mode"
        private const val PREF_KEY_GRID_WIDTH_M = "mission_grid_width_m"
        private const val PREF_KEY_GRID_LANE_COUNT = "mission_grid_lane_count"
        private const val PREF_KEY_GRID_START_SIDE = "mission_grid_start_side"
        private const val PREF_KEY_CORRIDOR_WIDTH_M = "mission_corridor_width_m"
        private const val PREF_KEY_CORRIDOR_PASS_MODE = "mission_corridor_pass_mode"
        private const val PREF_KEY_ORBIT_RADIUS_M = "mission_orbit_radius_m"
        private const val PREF_KEY_ORBIT_TURNS = "mission_orbit_turns"
        private const val PREF_KEY_ORBIT_DIRECTION = "mission_orbit_direction"
        private const val PREF_KEY_MISSION_PLANS_JSON = "mission_saved_plans_json"
        private const val PREF_KEY_MISSION_MAP_LAT = "mission_map_lat"
        private const val PREF_KEY_MISSION_MAP_LON = "mission_map_lon"
        private const val PREF_KEY_MISSION_MAP_ZOOM = "mission_map_zoom"
        private const val DEFAULT_MISSION_ALTITUDE_M = 60f
        private const val DEFAULT_MISSION_SPACING_M = 40f
        private const val DEFAULT_GRID_WIDTH_M = 120f
        private const val DEFAULT_GRID_LANE_COUNT = 0
        private const val DEFAULT_CORRIDOR_WIDTH_M = 40f
        private const val DEFAULT_ORBIT_RADIUS_M = 0f
        private const val DEFAULT_ORBIT_TURNS = 1
        private const val START_WAYPOINT_ALTITUDE_M = 50f
        private const val DEFAULT_MISSION_PATTERN_INDEX = 0
        private const val DEFAULT_MISSION_MAP_LAT = 39.50
        private const val DEFAULT_MISSION_MAP_LON = -98.35
        private const val DEFAULT_MISSION_MAP_ZOOM = 4.5
        private const val PILOT_MAP_FOCUS_ZOOM = 18.0
        private const val DRY_RUN_STEP_INTERVAL_MS = 150L
        private const val DRY_RUN_SPEED_MPS = 22.0
        private const val MISSION_TAP_MAX_MOVE_PX = 16f
        private const val MISSION_LONG_PRESS_MS = 450L
        private const val MISSION_FILE_PREFIX = "sc2emu_plan_"
        private const val MAVLINK_TYPE_FLIGHT_PLAN = 0
        private const val CONTROLLER_GPS_MIN_SEND_INTERVAL_MS = 4_000L
        private const val MISSION_FTP_PORT = 61
        private const val MISSION_FTP_TIMEOUT_MS = 5_000
        private const val CRITICAL_COMMAND_MAX_TELEMETRY_AGE_MS = 6_000L
        private const val VIDEO_BOOTSTRAP_INITIAL_DELAY_MS = 450L
        private const val VIDEO_BOOTSTRAP_RETRY_DELAY_MS = 1_800L
        private const val VIDEO_BOOTSTRAP_MAX_RETRIES = 2
    }

    private enum class MissionPattern(val label: String) {
        POINTS("Points"),
        GRID("Grid (Ag)"),
        CORRIDOR("Corridor"),
        ORBIT("Orbit (SAR)"),
    }

    private enum class MissionTerminalAction(val label: String) {
        LOITER("Loiter"),
        CIRCULAR_LANDING("Circular Landing"),
        LINEAR_LANDING("Linear Landing"),
    }

    private enum class MissionExecutionState {
        IDLE,
        READY,
        RUNNING,
        PAUSED,
    }

    private enum class DryRunState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETE,
    }

    private enum class MissionPreviewMode(val label: String) {
        GENERATED("Preview: Gen"),
        ANCHORS("Preview: Anch"),
        BOTH("Preview: Both"),
    }

    private enum class GridStartSide(val label: String) {
        AUTO("Auto"),
        LEFT("Start Left"),
        RIGHT("Start Right"),
    }

    private enum class CorridorPassMode(val label: String) {
        OUT_BACK("Out+Back"),
        SINGLE_CENTER("Single"),
    }

    private enum class OrbitDirection(val label: String) {
        CW("CW"),
        CCW("CCW"),
    }

    private enum class SafetySeverity {
        INFO,
        WARN,
        CRITICAL,
    }

    private data class MissionNode(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Float,
    )

    private data class MissionPatternSettings(
        val gridWidthMeters: Float,
        val gridLaneCount: Int,
        val gridStartSide: GridStartSide,
        val corridorWidthMeters: Float,
        val corridorPassMode: CorridorPassMode,
        val orbitRadiusMeters: Float,
        val orbitTurns: Int,
        val orbitDirection: OrbitDirection,
    )

    private data class SafetyBanner(
        val severity: SafetySeverity,
        val message: String,
    )

    private data class MissionPlan(
        val name: String,
        val pattern: MissionPattern,
        val spacingMeters: Float,
        val terminalAction: MissionTerminalAction,
        val patternSettings: MissionPatternSettings,
        val nodes: List<MissionNode>,
    )

    private data class MissionPreflightResult(
        val ok: Boolean,
        val message: String,
    )

    private data class FlightSettings(
        val maxAltitudeMeters: Float,
        val minAltitudeMeters: Float,
        val maxDistanceMeters: Float,
        val geofenceEnabled: Boolean,
        val rthMinAltitudeMeters: Float,
        val rthDelaySeconds: Int,
        val loiterRadiusMeters: Int,
        val loiterAltitudeMeters: Float,
    )

    private lateinit var prefs: SharedPreferences

    private lateinit var etDiscoIp: EditText
    private lateinit var etDiscoveryPort: EditText
    private lateinit var etC2dPort: EditText
    private lateinit var etD2cPort: EditText
    private lateinit var etStreamVideoPort: EditText
    private lateinit var etStreamControlPort: EditText
    private lateinit var etMaxAltitude: EditText
    private lateinit var etMinAltitude: EditText
    private lateinit var etMaxDistance: EditText
    private lateinit var etRthMinAltitude: EditText
    private lateinit var etRthDelay: EditText
    private lateinit var etLoiterRadius: EditText
    private lateinit var etLoiterAltitude: EditText
    private lateinit var swGeofence: SwitchCompat
    private lateinit var swBenchLock: SwitchCompat
    private lateinit var tvGeofenceHelp: TextView
    private lateinit var tvHelpMaxAltitude: TextView
    private lateinit var tvHelpMinAltitude: TextView
    private lateinit var tvHelpMaxDistance: TextView
    private lateinit var tvHelpGeofence: TextView
    private lateinit var tvHelpRthMinAltitude: TextView
    private lateinit var tvHelpRthDelay: TextView
    private lateinit var tvHelpLoiterRadius: TextView
    private lateinit var tvHelpLoiterAltitude: TextView
    private lateinit var tvNetworkPhase: TextView
    private lateinit var panelConfig: View
    private lateinit var panelMission: View

    private lateinit var btnSettings: ImageButton
    private lateinit var btnMission: ImageButton
    private lateinit var btnTakeoffLand: Button
    private lateinit var btnRth: Button
    private lateinit var btnApplyConfig: Button
    private lateinit var btnReconnect: Button
    private lateinit var btnBenchChecklist: Button
    private lateinit var btnExportLogs: Button
    private lateinit var btnMissionClose: ImageButton
    private lateinit var btnMissionTools: Button
    private lateinit var btnMissionPilot: Button
    private lateinit var btnMissionAddMode: Button
    private lateinit var btnMissionUndo: Button
    private lateinit var btnMissionClear: Button
    private lateinit var btnMissionQuickStart: Button
    private lateinit var btnMissionSave: Button
    private lateinit var btnMissionLoad: Button
    private lateinit var btnMissionStart: Button
    private lateinit var missionControlBar: View
    private lateinit var tvMissionControlStatus: TextView
    private lateinit var tvMissionBadgeMav: TextView
    private lateinit var tvMissionBadgeLanding: TextView
    private lateinit var tvMissionBadgeWp: TextView
    private lateinit var btnMissionCtrlStart: Button
    private lateinit var btnMissionCtrlPauseResume: Button
    private lateinit var btnMissionCtrlAbort: Button
    private lateinit var tvSafetyBanner: TextView

    private lateinit var textureVideo: TextureView
    private lateinit var stickLeft: VirtualStickView
    private lateinit var stickRight: VirtualStickView
    private lateinit var attitudeIndicatorContainer: View
    private lateinit var attitudeHorizonLine: View
    private lateinit var tvInfoSpeed: TextView
    private lateinit var tvInfoAltitude: TextView
    private lateinit var tvInfoDistance: TextView
    private lateinit var tvInfoZt: TextView
    private lateinit var tvInfoPlane: TextView
    private lateinit var tvInfoPhone: TextView
    private lateinit var mapMission: MapView
    private lateinit var panelMissionTools: View
    private lateinit var spMissionPattern: AppCompatSpinner
    private lateinit var etMissionAltitude: EditText
    private lateinit var etMissionSpacing: EditText
    private lateinit var groupGridSettings: View
    private lateinit var groupCorridorSettings: View
    private lateinit var groupOrbitSettings: View
    private lateinit var etGridWidth: EditText
    private lateinit var etGridLanes: EditText
    private lateinit var spGridStartSide: AppCompatSpinner
    private lateinit var etCorridorWidth: EditText
    private lateinit var spCorridorPassMode: AppCompatSpinner
    private lateinit var etOrbitRadius: EditText
    private lateinit var etOrbitTurns: EditText
    private lateinit var spOrbitDirection: AppCompatSpinner
    private lateinit var btnPreviewMode: Button
    private lateinit var btnDryRunStartPause: Button
    private lateinit var btnDryRunReset: Button
    private lateinit var tvDryRunStatus: TextView
    private lateinit var tvMissionSummary: TextView

    private lateinit var videoReceiver: RtpH264VideoReceiver
    private var surfaceAvailable = false
    private var previewSurface: Surface? = null
    private var videoBootstrapJob: Job? = null
    private var waitingForFirstVideoFrame = false

    private var configVisible = false
    private var missionVisible = false
    private var missionAddMode = true
    private var missionToolsExpanded = false
    private var missionExecutionState = MissionExecutionState.IDLE
    private var rthActive = false
    private var airborne = false

    private var pitchAxis = 0
    private var rollAxis = 0
    private var yawAxis = 0
    private var throttleAxis = 0
    private var modemTelemetryJob: Job? = null

    @Volatile
    private var modemSignalPercent: Int? = null

    @Volatile
    private var modemPlaneBatteryPercent: Int? = null

    @Volatile
    private var modemZt: String? = null

    @Volatile
    private var modemTelemetryLastSuccessAtMs: Long = 0L

    @Volatile
    private var modemTelemetryFirstFailureAtMs: Long = 0L

    private var pilotHomeLatitude: Double? = null
    private var pilotHomeLongitude: Double? = null
    private var pilotPhoneLatitude: Double? = null
    private var pilotPhoneLongitude: Double? = null
    private var pilotPhoneAltitudeMeters: Double = 0.0
    private var pilotPhoneHorizontalAccuracyMeters: Float = 8f
    private var pilotPhoneVerticalAccuracyMeters: Float = 12f
    private var lastControllerGpsSentAtMs: Long = 0L
    private var pendingFlightSettings: FlightSettings? = null
    private val missionNodes = mutableListOf<MissionNode>()
    private val missionMarkers = mutableListOf<Marker>()
    private var missionGeneratedPath: Polyline? = null
    private var missionAnchorPath: Polyline? = null
    private var missionPreviewMode = MissionPreviewMode.BOTH
    private var missionTouchDownX = 0f
    private var missionTouchDownY = 0f
    private var missionTouchDownTimeMs = 0L
    private var missionTerminalAction = MissionTerminalAction.LOITER
    private var missionFilePath: String? = null
    private var missionUploadJob: Job? = null
    private var lastHandledMavlinkPlayError: Int? = null
    private var reconnectJob: Job? = null
    private var reconnectInProgress = false
    private var benchLockEnabled = true
    private var lastObservedStateMessage: String? = null
    private val benchEventLog = ArrayDeque<String>()
    private val commandEventLog = ArrayDeque<String>()
    private val safetyAlertLog = ArrayDeque<String>()
    private var lastSafetyBannerMessage: String? = null
    private var dryRunJob: Job? = null
    private var dryRunState: DryRunState = DryRunState.IDLE
    private var dryRunProgressMeters: Double = 0.0
    private var dryRunTotalMeters: Double = 0.0
    private var dryRunMarker: Marker? = null
    private val savedMissionPlans = mutableListOf<MissionPlan>()
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationListener = LocationListener { location ->
        onPilotLocationChanged(location)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op */ }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasLocationPermission()) {
            startLocationUpdatesIfPermitted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        prefs = getSharedPreferences("flight_config", MODE_PRIVATE)
        bindViews()
        restoreConfig()
        restoreFlightSettings()
        setupButtons()
        setupSettingsUi()
        setupSticks()
        setupVideoPreview()
        setupMissionPlanner()
        applyConfigPanelUi()
        applyMissionPanelUi()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        if (::mapMission.isInitialized) {
            mapMission.onResume()
        }
        startLocationUpdatesIfPermitted()
        applyImmersiveMode()
    }

    override fun onPause() {
        if (::mapMission.isInitialized) {
            saveMissionViewport()
            mapMission.onPause()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onStart() {
        super.onStart()
        requestNotificationPermissionIfNeeded()
        requestLocationPermissionIfNeeded()
        startLocationUpdatesIfPermitted()
        autoStartEngine()
        startModemTelemetryPolling()
    }

    override fun onStop() {
        stopModemTelemetryPolling()
        stopLocationUpdates()
        if (!isChangingConfigurations) {
            centerSticks()
            sendSticks(force = true)
        }
        super.onStop()
    }

    private fun bindViews() {
        etDiscoIp = findViewById(R.id.etDiscoIp)
        etDiscoveryPort = findViewById(R.id.etDiscoveryPort)
        etC2dPort = findViewById(R.id.etC2dPort)
        etD2cPort = findViewById(R.id.etD2cPort)
        etStreamVideoPort = findViewById(R.id.etStreamVideoPort)
        etStreamControlPort = findViewById(R.id.etStreamControlPort)
        etMaxAltitude = findViewById(R.id.etMaxAltitude)
        etMinAltitude = findViewById(R.id.etMinAltitude)
        etMaxDistance = findViewById(R.id.etMaxDistance)
        etRthMinAltitude = findViewById(R.id.etRthMinAltitude)
        etRthDelay = findViewById(R.id.etRthDelay)
        etLoiterRadius = findViewById(R.id.etLoiterRadius)
        etLoiterAltitude = findViewById(R.id.etLoiterAltitude)
        swGeofence = findViewById(R.id.swGeofence)
        swBenchLock = findViewById(R.id.swBenchLock)
        tvGeofenceHelp = findViewById(R.id.tvGeofenceHelp)
        tvHelpMaxAltitude = findViewById(R.id.tvHelpMaxAltitude)
        tvHelpMinAltitude = findViewById(R.id.tvHelpMinAltitude)
        tvHelpMaxDistance = findViewById(R.id.tvHelpMaxDistance)
        tvHelpGeofence = findViewById(R.id.tvHelpGeofence)
        tvHelpRthMinAltitude = findViewById(R.id.tvHelpRthMinAltitude)
        tvHelpRthDelay = findViewById(R.id.tvHelpRthDelay)
        tvHelpLoiterRadius = findViewById(R.id.tvHelpLoiterRadius)
        tvHelpLoiterAltitude = findViewById(R.id.tvHelpLoiterAltitude)
        tvNetworkPhase = findViewById(R.id.tvNetworkPhase)
        panelConfig = findViewById(R.id.panelConfig)
        panelMission = findViewById(R.id.panelMission)

        btnSettings = findViewById(R.id.btnSettings)
        btnMission = findViewById(R.id.btnMission)
        btnTakeoffLand = findViewById(R.id.btnTakeoffLand)
        btnRth = findViewById(R.id.btnRth)
        btnApplyConfig = findViewById(R.id.btnApplyConfig)
        btnReconnect = findViewById(R.id.btnReconnect)
        btnBenchChecklist = findViewById(R.id.btnBenchChecklist)
        btnExportLogs = findViewById(R.id.btnExportLogs)
        btnMissionClose = findViewById(R.id.btnMissionClose)
        btnMissionTools = findViewById(R.id.btnMissionTools)
        btnMissionPilot = findViewById(R.id.btnMissionPilot)
        btnMissionAddMode = findViewById(R.id.btnMissionAddMode)
        btnMissionUndo = findViewById(R.id.btnMissionUndo)
        btnMissionClear = findViewById(R.id.btnMissionClear)
        btnMissionQuickStart = findViewById(R.id.btnMissionQuickStart)
        btnMissionSave = findViewById(R.id.btnMissionSave)
        btnMissionLoad = findViewById(R.id.btnMissionLoad)
        btnMissionStart = findViewById(R.id.btnMissionStart)
        missionControlBar = findViewById(R.id.missionControlBar)
        tvMissionControlStatus = findViewById(R.id.tvMissionControlStatus)
        tvMissionBadgeMav = findViewById(R.id.tvMissionBadgeMav)
        tvMissionBadgeLanding = findViewById(R.id.tvMissionBadgeLanding)
        tvMissionBadgeWp = findViewById(R.id.tvMissionBadgeWp)
        btnMissionCtrlStart = findViewById(R.id.btnMissionCtrlStart)
        btnMissionCtrlPauseResume = findViewById(R.id.btnMissionCtrlPauseResume)
        btnMissionCtrlAbort = findViewById(R.id.btnMissionCtrlAbort)
        tvSafetyBanner = findViewById(R.id.tvSafetyBanner)

        textureVideo = findViewById(R.id.textureVideo)
        stickLeft = findViewById(R.id.stickLeft)
        stickRight = findViewById(R.id.stickRight)
        attitudeIndicatorContainer = findViewById(R.id.attitudeIndicatorContainer)
        attitudeHorizonLine = findViewById(R.id.attitudeHorizonLine)
        tvInfoSpeed = findViewById(R.id.tvInfoSpeed)
        tvInfoAltitude = findViewById(R.id.tvInfoAltitude)
        tvInfoDistance = findViewById(R.id.tvInfoDistance)
        tvInfoZt = findViewById(R.id.tvInfoZt)
        tvInfoPlane = findViewById(R.id.tvInfoPlane)
        tvInfoPhone = findViewById(R.id.tvInfoPhone)
        mapMission = findViewById(R.id.mapMission)
        panelMissionTools = findViewById(R.id.panelMissionTools)
        spMissionPattern = findViewById(R.id.spMissionPattern)
        etMissionAltitude = findViewById(R.id.etMissionAltitude)
        etMissionSpacing = findViewById(R.id.etMissionSpacing)
        groupGridSettings = findViewById(R.id.groupGridSettings)
        groupCorridorSettings = findViewById(R.id.groupCorridorSettings)
        groupOrbitSettings = findViewById(R.id.groupOrbitSettings)
        etGridWidth = findViewById(R.id.etGridWidth)
        etGridLanes = findViewById(R.id.etGridLanes)
        spGridStartSide = findViewById(R.id.spGridStartSide)
        etCorridorWidth = findViewById(R.id.etCorridorWidth)
        spCorridorPassMode = findViewById(R.id.spCorridorPassMode)
        etOrbitRadius = findViewById(R.id.etOrbitRadius)
        etOrbitTurns = findViewById(R.id.etOrbitTurns)
        spOrbitDirection = findViewById(R.id.spOrbitDirection)
        btnPreviewMode = findViewById(R.id.btnPreviewMode)
        btnDryRunStartPause = findViewById(R.id.btnDryRunStartPause)
        btnDryRunReset = findViewById(R.id.btnDryRunReset)
        tvDryRunStatus = findViewById(R.id.tvDryRunStatus)
        tvMissionSummary = findViewById(R.id.tvMissionSummary)
    }

    private fun setupButtons() {
        btnSettings.setOnClickListener {
            if (missionVisible) {
                missionVisible = false
                saveMissionViewport()
                applyMissionPanelUi()
            }
            configVisible = !configVisible
            saveConfigVisible()
            applyConfigPanelUi()
        }

        btnMission.setOnClickListener {
            if (configVisible) {
                configVisible = false
                saveConfigVisible()
                applyConfigPanelUi()
            }
            missionVisible = !missionVisible
            if (missionVisible) {
                missionToolsExpanded = false
                updateMissionToolsUi()
            }
            applyMissionPanelUi()
            if (missionVisible) {
                centerMissionMapFresh()
            } else {
                saveMissionViewport()
                saveMissionDraft()
            }
        }

        btnApplyConfig.setOnClickListener {
            val config = readConfigFromInputs() ?: run {
                showStatusToast("invalid config")
                return@setOnClickListener
            }
            val settings = readFlightSettingsFromInputs() ?: run {
                showStatusToast("invalid flight settings")
                return@setOnClickListener
            }
            val oldConfig = readConfigFromPrefs()
            saveConfig(config)
            saveFlightSettings(settings)
            queueFlightSettingsApply(settings)

            val state = FlightSessionStore.state.value
            val shouldRestart = state.engineRunning && oldConfig != null && oldConfig != config

            if (shouldRestart) {
                lifecycleScope.launch {
                    autoStopEngine()
                    delay(250)
                    autoStartEngine()
                }
            } else if (state.engineRunning && state.discoveryOk) {
                applyPendingFlightSettingsIfReady()
            }

            configVisible = false
            saveConfigVisible()
            applyConfigPanelUi()
        }

        btnReconnect.setOnClickListener {
            reconnectEngineSession()
        }

        btnBenchChecklist.setOnClickListener {
            showBenchChecklistDialog()
        }

        btnExportLogs.setOnClickListener {
            exportDiagnosticsBundle()
        }

        btnTakeoffLand.setOnClickListener {
            if (airborne) {
                if (!ensureCriticalCommandReady("land", requireAirborne = true, requireFreshTelemetry = false)) {
                    return@setOnClickListener
                }
                sendSimpleAction(FlightEngineService.ACTION_LAND)
            } else {
                if (benchLockEnabled) {
                    showStatusToast("takeoff blocked: bench lock on")
                    appendCommandEvent("Takeoff blocked (bench lock)")
                    return@setOnClickListener
                }
                if (!ensureCriticalCommandReady("takeoff", requireAirborne = false, requireFreshTelemetry = true)) {
                    return@setOnClickListener
                }
                showTakeoffConfirmation()
            }
        }

        btnRth.setOnClickListener {
            if (!ensureCriticalCommandReady("rth", requireAirborne = true, requireFreshTelemetry = true)) {
                return@setOnClickListener
            }
            if (rthActive) {
                sendSimpleAction(FlightEngineService.ACTION_RTH_STOP)
                rthActive = false
            } else {
                sendSimpleAction(FlightEngineService.ACTION_RTH_START)
                rthActive = true
            }
            btnRth.text = if (rthActive) "RTH Off" else "RTH"
        }

        btnMissionCtrlStart.setOnClickListener {
            startMissionExecution()
        }

        btnMissionCtrlPauseResume.setOnClickListener {
            toggleMissionPauseResume()
        }

        btnMissionCtrlAbort.setOnClickListener {
            abortMissionExecution()
        }

        updateMissionControlUi()
    }

    private fun setupSettingsUi() {
        setupSettingHelpBadges()
        benchLockEnabled = prefs.getBoolean(PREF_KEY_BENCH_LOCK_ENABLED, true)
        swBenchLock.isChecked = benchLockEnabled
        swBenchLock.setOnCheckedChangeListener { _, isChecked ->
            if (benchLockEnabled == isChecked) return@setOnCheckedChangeListener
            benchLockEnabled = isChecked
            prefs.edit().putBoolean(PREF_KEY_BENCH_LOCK_ENABLED, benchLockEnabled).apply()
            updateTakeoffButtonUi()
            appendCommandEvent("Bench lock ${if (benchLockEnabled) "ON" else "OFF"}")
            if (benchLockEnabled && !airborne) {
                showStatusToast("bench lock on: takeoff blocked")
            }
        }
        swGeofence.setOnCheckedChangeListener { _, _ ->
            renderGeofenceUi()
        }
        renderGeofenceUi()
        updateTakeoffButtonUi()
    }

    private fun setupSettingHelpBadges() {
        tvHelpMaxAltitude.setOnClickListener {
            showSettingHelp(
                title = "Max Altitude",
                body = "Highest altitude limit above takeoff.\nTypical: 60-120m for testing.\nSet higher only when you need it.",
            )
        }
        tvHelpMinAltitude.setOnClickListener {
            showSettingHelp(
                title = "Min Altitude",
                body = "Lowest allowed altitude for assisted behavior.\nUse 0-5m if you want near-ground passes.",
            )
        }
        tvHelpMaxDistance.setOnClickListener {
            showSettingHelp(
                title = "Max Distance",
                body = "Distance cap from home/takeoff point.\nOnly enforced when Geofence is ON.",
            )
        }
        tvHelpGeofence.setOnClickListener {
            showSettingHelp(
                title = "Geofence",
                body = "ON: plane should not pass Max Distance.\nOFF: no distance fence limit.",
            )
        }
        tvHelpRthMinAltitude.setOnClickListener {
            showSettingHelp(
                title = "RTH Min Altitude",
                body = "Minimum climb altitude used during Return-To-Home.\nSet high enough to clear trees/obstacles.",
            )
        }
        tvHelpRthDelay.setOnClickListener {
            showSettingHelp(
                title = "RTH Delay",
                body = "Delay in seconds before RTH engages after command/failsafe path uses this value.",
            )
        }
        tvHelpLoiterRadius.setOnClickListener {
            showSettingHelp(
                title = "Loiter Radius",
                body = "Circle size in meters.\nDefault: 30m.\nSmaller = tighter orbit.\nCommon values: 30-60m.",
            )
        }
        tvHelpLoiterAltitude.setOnClickListener {
            showSettingHelp(
                title = "Loiter Altitude",
                body = "Target altitude for circle/loiter behavior.\nUse a stable value above local obstacles.",
            )
        }
    }

    private fun showSettingHelp(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupSticks() {
        stickLeft.onStickChanged = { pos ->
            yawAxis = (pos.x * 100f).toInt().coerceIn(-100, 100)
            throttleAxis = (pos.y * 100f).toInt().coerceIn(-100, 100)
            sendSticks()
        }

        stickRight.onStickChanged = { pos ->
            rollAxis = (pos.x * 100f).toInt().coerceIn(-100, 100)
            pitchAxis = (pos.y * 100f).toInt().coerceIn(-100, 100)
            sendSticks()
        }

        centerSticks()
    }

    private fun setupVideoPreview() {
        videoReceiver = RtpH264VideoReceiver(
            onStatus = { _ ->
                runOnUiThread {
                    val current = FlightSessionStore.state.value
                    renderInfo(current)
                }
            },
            onFirstFrameDecoded = {
                runOnUiThread {
                    onFirstVideoFrameDecoded()
                }
            },
        )

        textureVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                surfaceAvailable = true
                previewSurface?.release()
                previewSurface = Surface(surface)
                applyVideoAspectTransform(width, height)
                startVideoReceiverIfPossible()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyVideoAspectTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                surfaceAvailable = false
                stopVideoReceiver()
                previewSurface?.release()
                previewSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun setupMissionPlanner() {
        val patternAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            MissionPattern.values().map { it.label },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spMissionPattern.adapter = patternAdapter

        val gridStartSideAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            GridStartSide.values().map { it.label },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spGridStartSide.adapter = gridStartSideAdapter

        val corridorPassModeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            CorridorPassMode.values().map { it.label },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spCorridorPassMode.adapter = corridorPassModeAdapter

        val orbitDirectionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            OrbitDirection.values().map { it.label },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spOrbitDirection.adapter = orbitDirectionAdapter

        mapMission.setTileSource(TileSourceFactory.MAPNIK)
        mapMission.setMultiTouchControls(true)
        mapMission.isTilesScaledToDpi = true

        restoreMissionDraft()
        loadSavedMissionPlansFromPrefs()
        ensureStartWaypointIfKnown()
        refreshMissionMapOverlays()
        refreshMissionSummary()
        updateMissionAddModeUi()
        updateMissionToolsUi()
        updateMissionPatternSettingsUi()
        updatePreviewModeButton()
        updateDryRunUi()

        spMissionPattern.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onMissionPlannerParamsChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val simpleSpinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onMissionPlannerParamsChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spGridStartSide.onItemSelectedListener = simpleSpinnerListener
        spCorridorPassMode.onItemSelectedListener = simpleSpinnerListener
        spOrbitDirection.onItemSelectedListener = simpleSpinnerListener

        listOf(
            etMissionAltitude,
            etMissionSpacing,
            etGridWidth,
            etGridLanes,
            etCorridorWidth,
            etOrbitRadius,
            etOrbitTurns,
        ).forEach { field ->
            field.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onMissionPlannerParamsChanged()
                }
            }
        }

        btnPreviewMode.setOnClickListener {
            missionPreviewMode = when (missionPreviewMode) {
                MissionPreviewMode.GENERATED -> MissionPreviewMode.ANCHORS
                MissionPreviewMode.ANCHORS -> MissionPreviewMode.BOTH
                MissionPreviewMode.BOTH -> MissionPreviewMode.GENERATED
            }
            updatePreviewModeButton()
            refreshMissionMapOverlays()
            refreshMissionSummary()
            saveMissionDraft()
        }

        btnDryRunStartPause.setOnClickListener {
            toggleDryRunStartPause()
        }

        btnDryRunReset.setOnClickListener {
            resetDryRun(clearMarker = true, keepProgress = false)
            updateDryRunUi()
            refreshMissionMapOverlays()
        }

        mapMission.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    missionTouchDownX = event.x
                    missionTouchDownY = event.y
                    missionTouchDownTimeMs = event.eventTime
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - missionTouchDownX
                    val dy = event.y - missionTouchDownY
                    val movedTooMuch = (dx * dx) + (dy * dy) > (MISSION_TAP_MAX_MOVE_PX * MISSION_TAP_MAX_MOVE_PX)
                    val longPress = (event.eventTime - missionTouchDownTimeMs) >= MISSION_LONG_PRESS_MS

                    if (!movedTooMuch && longPress) {
                        val targetIndex = findNodeIndexNearScreenPoint(event.x, event.y)
                        if (targetIndex != null) {
                            showWaypointEditor(targetIndex)
                            return@setOnTouchListener false
                        }
                    }

                    if (!movedTooMuch && missionAddMode) {
                        val point = mapMission.projection.fromPixels(event.x.toInt(), event.y.toInt())
                        addMissionNode(GeoPoint(point.latitude, point.longitude))
                    }
                }
            }
            false
        }

        btnMissionClose.setOnClickListener {
            missionVisible = false
            saveMissionDraft()
            saveMissionViewport()
            applyMissionPanelUi()
        }

        btnMissionTools.setOnClickListener {
            missionToolsExpanded = !missionToolsExpanded
            updateMissionToolsUi()
        }

        btnMissionPilot.setOnClickListener {
            val centeredOnPilot = centerMissionMapOnPilotLocation(forceZoom = true)
            if (!centeredOnPilot) {
                val centeredOnPlane = centerMissionMapFromFlightState(forceZoom = true)
                if (!centeredOnPlane) {
                    showStatusToast("pilot location unavailable")
                }
            }
        }

        btnMissionAddMode.setOnClickListener {
            missionAddMode = !missionAddMode
            updateMissionAddModeUi()
            saveMissionDraft()
        }

        btnMissionUndo.setOnClickListener {
            if (missionNodes.size > 1) {
                missionNodes.removeAt(missionNodes.lastIndex)
                if (missionNodes.size < 2) {
                    missionExecutionState = MissionExecutionState.IDLE
                    updateMissionControlUi()
                }
                onMissionPlannerNodesChanged()
            } else {
                showStatusToast("point 1 is the start point")
            }
        }

        btnMissionClear.setOnClickListener {
            if (missionNodes.isNotEmpty()) {
                missionNodes.clear()
                missionExecutionState = MissionExecutionState.IDLE
                updateMissionControlUi()
                onMissionPlannerNodesChanged()
            }
        }

        btnMissionSave.setOnClickListener {
            promptSaveMissionPlan()
        }

        btnMissionLoad.setOnClickListener {
            promptLoadMissionPlan()
        }

        btnMissionQuickStart.setOnClickListener {
            loadCurrentMissionToFlightView()
        }

        btnMissionStart.setOnClickListener {
            loadCurrentMissionToFlightView()
        }
    }

    private fun loadCurrentMissionToFlightView() {
        saveMissionDraft()
        if (missionNodes.size < 2) {
            showStatusToast("add at least 2 points first")
            return
        }
        val preflight = runMissionPreflight(strictGeofence = false)
        if (!preflight.ok) {
            showStatusToast("Mission preflight failed: ${preflight.message}")
            return
        }
        stopDryRunForFlightView()
        missionExecutionState = MissionExecutionState.READY
        missionVisible = false
        saveMissionViewport()
        applyMissionPanelUi()
        updateMissionControlUi()

        val state = FlightSessionStore.state.value
        if (state.engineRunning && state.discoveryOk) {
            showStatusToast("Mission loaded. Take off, then press Start in flight view.")
        } else {
            showStatusToast("Mission saved. Flight view ready. Connect plane to execute later.")
        }
    }

    private fun startMissionExecution() {
        if (missionNodes.size < 2) {
            missionExecutionState = MissionExecutionState.IDLE
            updateMissionControlUi()
            showStatusToast("mission has fewer than 2 points")
            return
        }
        if (missionExecutionState == MissionExecutionState.RUNNING || missionUploadJob?.isActive == true) return

        if (!ensureCriticalCommandReady("mission start", requireAirborne = true, requireFreshTelemetry = true)) {
            return
        }

        val preflight = runMissionPreflight(strictGeofence = true)
        if (!preflight.ok) {
            showStatusToast("Mission preflight failed: ${preflight.message}")
            return
        }

        val existingMissionFile = missionFilePath
        if (!existingMissionFile.isNullOrBlank()) {
            lastHandledMavlinkPlayError = null
            sendControllerGpsToPlaneIfReady(force = true)
            sendMavlinkStart(existingMissionFile)
            missionExecutionState = MissionExecutionState.RUNNING
            updateMissionControlUi()
            showStatusToast("Mission start sent")
            return
        }

        val config = readConfigFromInputs() ?: readConfigFromPrefs() ?: run {
            showStatusToast("set plane ip first")
            return
        }

        val mavlinkContent = buildMissionMavlink()
        val fileName = "$MISSION_FILE_PREFIX${UUID.randomUUID().toString().replace("-", "").take(12)}.mavlink"

        missionUploadJob?.cancel()
        missionUploadJob = lifecycleScope.launch {
            tvMissionControlStatus.text = "Mission Uploading..."
            val uploaded = withContext(Dispatchers.IO) {
                uploadMissionFileToDrone(config.discoIp, fileName, mavlinkContent)
            }
            if (!uploaded) {
                missionFilePath = null
                missionExecutionState = MissionExecutionState.READY
                updateMissionControlUi()
                activateManualOverride("Mission upload failed. Manual control active.")
                return@launch
            }

            missionFilePath = fileName
            lastHandledMavlinkPlayError = null
            sendControllerGpsToPlaneIfReady(force = true)
            sendMavlinkStart(fileName)
            missionExecutionState = MissionExecutionState.RUNNING
            updateMissionControlUi()
            showStatusToast("Mission started")
        }
    }

    private fun ensureCriticalCommandReady(
        actionLabel: String,
        requireAirborne: Boolean,
        requireFreshTelemetry: Boolean,
    ): Boolean {
        val state = FlightSessionStore.state.value
        val reason = when {
            reconnectInProgress -> "reconnecting"
            !state.engineRunning -> "engine not running"
            !state.discoveryOk -> "discovery not ready"
            requireAirborne && !airborne -> "aircraft is not airborne"
            requireFreshTelemetry && !hasFreshTelemetry(state) -> "telemetry stale"
            else -> null
        }
        if (reason != null) {
            showStatusToast("$actionLabel blocked: $reason")
            appendCommandEvent("${actionLabel.uppercase(Locale.US)} blocked: $reason")
            return false
        }
        return true
    }

    private fun hasFreshTelemetry(state: FlightState): Boolean {
        val lastTelemetryAtMs = state.lastTelemetryAtMs ?: return false
        val age = System.currentTimeMillis() - lastTelemetryAtMs
        return age in 0..CRITICAL_COMMAND_MAX_TELEMETRY_AGE_MS
    }

    private fun runMissionPreflight(strictGeofence: Boolean): MissionPreflightResult {
        val executionNodes = buildExecutionMissionNodes()
        if (executionNodes.size < 2) {
            return MissionPreflightResult(ok = false, message = "need at least 2 generated waypoints")
        }

        val settings = readFlightSettingsFromInputs() ?: readFlightSettingsFromPrefs()
        val maxAltitude = settings.maxAltitudeMeters
        val minAltitude = settings.minAltitudeMeters

        executionNodes.forEachIndexed { index, node ->
            if (!node.latitude.isFinite() || !node.longitude.isFinite()) {
                return MissionPreflightResult(ok = false, message = "invalid coordinate at WP${index + 1}")
            }
            if (index > 0 && node.altitudeMeters !in minAltitude..maxAltitude) {
                return MissionPreflightResult(
                    ok = false,
                    message = "WP${index + 1} altitude ${formatInputFloat(node.altitudeMeters)}m outside ${formatInputFloat(minAltitude)}-${formatInputFloat(maxAltitude)}m",
                )
            }
        }

        var pathMeters = 0.0
        for (i in 1 until executionNodes.size) {
            val previous = executionNodes[i - 1]
            val current = executionNodes[i]
            val segmentMeters = haversineMeters(previous.latitude, previous.longitude, current.latitude, current.longitude)
            if (segmentMeters < 5.0) {
                return MissionPreflightResult(ok = false, message = "WP${i} to WP${i + 1} segment is too short")
            }
            if (segmentMeters > 6_000.0) {
                return MissionPreflightResult(ok = false, message = "WP${i} to WP${i + 1} segment is too long")
            }
            pathMeters += segmentMeters
        }
        if (pathMeters > 80_000.0) {
            return MissionPreflightResult(ok = false, message = "mission path too long")
        }

        if (settings.geofenceEnabled && settings.maxDistanceMeters > 0f) {
            val reference = resolveMissionDistanceReference()
            if (reference != null) {
                val maxDistance = executionNodes.maxOf { node ->
                    haversineMeters(reference.latitude, reference.longitude, node.latitude, node.longitude)
                }
                if (maxDistance > settings.maxDistanceMeters.toDouble()) {
                    return MissionPreflightResult(
                        ok = false,
                        message = "path exceeds max distance ${formatInputFloat(settings.maxDistanceMeters)}m",
                    )
                }
            } else if (strictGeofence) {
                return MissionPreflightResult(
                    ok = false,
                    message = "pilot/home position unavailable for geofence preflight",
                )
            }
        }

        return MissionPreflightResult(ok = true, message = "ok")
    }

    private fun resolveMissionDistanceReference(): GeoPoint? {
        val phoneLat = pilotPhoneLatitude
        val phoneLon = pilotPhoneLongitude
        if (phoneLat != null && phoneLon != null && phoneLat.isFinite() && phoneLon.isFinite()) {
            return GeoPoint(phoneLat, phoneLon)
        }

        val homeLat = pilotHomeLatitude
        val homeLon = pilotHomeLongitude
        if (homeLat != null && homeLon != null && homeLat.isFinite() && homeLon.isFinite()) {
            return GeoPoint(homeLat, homeLon)
        }

        val state = FlightSessionStore.state.value
        val planeLat = state.latitude
        val planeLon = state.longitude
        if (planeLat != null && planeLon != null && planeLat.isFinite() && planeLon.isFinite()) {
            return GeoPoint(planeLat, planeLon)
        }
        return null
    }

    private fun activateManualOverride(reason: String) {
        centerSticks()
        if (airborne) {
            sendArmState(true)
        } else {
            sendArmState(false)
        }
        sendSticks(force = true)
        showStatusToast(reason)
    }

    private fun toggleMissionPauseResume() {
        when (missionExecutionState) {
            MissionExecutionState.RUNNING -> {
                sendMavlinkPause()
                missionExecutionState = MissionExecutionState.PAUSED
                centerSticks()
                sendSticks(force = true)
                updateMissionControlUi()
                showStatusToast("Mission paused")
            }

            MissionExecutionState.PAUSED -> {
                if (!ensureCriticalCommandReady("mission resume", requireAirborne = true, requireFreshTelemetry = true)) {
                    return
                }
                val filePath = missionFilePath
                if (filePath.isNullOrBlank()) {
                    showStatusToast("mission file missing, press Start")
                    return
                }
                sendMavlinkStart(filePath)
                missionExecutionState = MissionExecutionState.RUNNING
                updateMissionControlUi()
                showStatusToast("Mission resumed")
            }

            MissionExecutionState.READY -> showStatusToast("Press Start to run mission")
            MissionExecutionState.IDLE -> showStatusToast("Load or create a mission first")
        }
    }

    private fun abortMissionExecution() {
        if (missionExecutionState == MissionExecutionState.IDLE) return

        missionUploadJob?.cancel()
        missionUploadJob = null
        sendMavlinkStop()
        missionExecutionState = if (missionNodes.size >= 2) {
            MissionExecutionState.READY
        } else {
            MissionExecutionState.IDLE
        }
        activateManualOverride("Mission aborted. Manual control active.")
        updateMissionControlUi()
    }

    private fun invalidatePreparedMissionFile() {
        missionUploadJob?.cancel()
        missionUploadJob = null
        missionFilePath = null
        resetDryRun(clearMarker = true, keepProgress = false)
        updateDryRunUi()
    }

    private fun buildMissionMavlink(): String {
        val executionNodes = buildExecutionMissionNodes()
        val terminalAction = when (missionTerminalAction) {
            MissionTerminalAction.LOITER -> MissionMavlinkBuilder.TerminalAction.LOITER
            MissionTerminalAction.CIRCULAR_LANDING -> MissionMavlinkBuilder.TerminalAction.CIRCULAR_LANDING
            MissionTerminalAction.LINEAR_LANDING -> MissionMavlinkBuilder.TerminalAction.LINEAR_LANDING
        }
        return MissionMavlinkBuilder.build(
            executionNodes = executionNodes.map {
                MissionMavlinkBuilder.Node(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitudeMeters = it.altitudeMeters,
                )
            },
            terminalAction = terminalAction,
        )
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
        val theta = Math.toDegrees(atan2(y, x))
        return ((theta % 360.0) + 360.0) % 360.0
    }

    private fun uploadMissionFileToDrone(host: String, fileName: String, content: String): Boolean {
        val ftp = FTPClient()
        return try {
            ftp.connectTimeout = MISSION_FTP_TIMEOUT_MS
            ftp.defaultTimeout = MISSION_FTP_TIMEOUT_MS
            ftp.setDataTimeout(Duration.ofMillis(MISSION_FTP_TIMEOUT_MS.toLong()))
            ftp.connect(host, MISSION_FTP_PORT)
            if (!ftp.login("anonymous", "")) {
                false
            } else {
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                val bytes = content.toByteArray(Charsets.UTF_8)
                val candidates = listOf("", "flightplans", "/flightplans", "internal_000/flightplans", "/internal_000/flightplans")
                candidates.any { directory ->
                    tryUploadMissionFile(ftp, directory, fileName, bytes)
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            try {
                ftp.logout()
            } catch (_: Exception) {
            }
            try {
                ftp.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun tryUploadMissionFile(ftp: FTPClient, directory: String, fileName: String, bytes: ByteArray): Boolean {
        return try {
            ftp.changeWorkingDirectory("/")
            val normalized = directory.trim().trim('/').replace('\\', '/')
            if (normalized.isNotBlank() && !ftp.changeWorkingDirectory(normalized)) {
                return false
            }
            ByteArrayInputStream(bytes).use { input ->
                ftp.storeFile(fileName, input)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendMavlinkStart(filePath: String) {
        appendCommandEvent("Mission start file=${filePath.takeLast(18)}")
        startService(
            Intent(this, FlightEngineService::class.java)
                .setAction(FlightEngineService.ACTION_MAVLINK_START)
                .putExtra(FlightEngineService.EXTRA_MAVLINK_FILE_PATH, filePath)
                .putExtra(FlightEngineService.EXTRA_MAVLINK_TYPE, MAVLINK_TYPE_FLIGHT_PLAN),
        )
    }

    private fun sendMavlinkPause() {
        sendSimpleAction(FlightEngineService.ACTION_MAVLINK_PAUSE)
    }

    private fun sendMavlinkStop() {
        sendSimpleAction(FlightEngineService.ACTION_MAVLINK_STOP)
    }

    private fun selectedMissionPattern(): MissionPattern {
        val index = spMissionPattern.selectedItemPosition
        return MissionPattern.values().getOrElse(index) { MissionPattern.POINTS }
    }

    private fun selectedMissionPatternSettings(): MissionPatternSettings {
        val gridWidth = (etGridWidth.text?.toString()?.toFloatOrNull() ?: DEFAULT_GRID_WIDTH_M)
            .coerceIn(30f, 1_000f)
        val gridLaneCount = (etGridLanes.text?.toString()?.toIntOrNull() ?: DEFAULT_GRID_LANE_COUNT)
            .coerceIn(0, 40)
        val gridStartSide = GridStartSide.values().getOrElse(spGridStartSide.selectedItemPosition) {
            GridStartSide.AUTO
        }

        val corridorWidth = (etCorridorWidth.text?.toString()?.toFloatOrNull() ?: DEFAULT_CORRIDOR_WIDTH_M)
            .coerceIn(10f, 400f)
        val corridorPassMode = CorridorPassMode.values().getOrElse(spCorridorPassMode.selectedItemPosition) {
            CorridorPassMode.OUT_BACK
        }

        val orbitRadius = (etOrbitRadius.text?.toString()?.toFloatOrNull() ?: DEFAULT_ORBIT_RADIUS_M)
            .coerceIn(0f, 500f)
        val orbitTurns = (etOrbitTurns.text?.toString()?.toIntOrNull() ?: DEFAULT_ORBIT_TURNS)
            .coerceIn(1, 8)
        val orbitDirection = OrbitDirection.values().getOrElse(spOrbitDirection.selectedItemPosition) {
            OrbitDirection.CW
        }

        return MissionPatternSettings(
            gridWidthMeters = gridWidth,
            gridLaneCount = gridLaneCount,
            gridStartSide = gridStartSide,
            corridorWidthMeters = corridorWidth,
            corridorPassMode = corridorPassMode,
            orbitRadiusMeters = orbitRadius,
            orbitTurns = orbitTurns,
            orbitDirection = orbitDirection,
        )
    }

    private fun applyMissionPatternSettingsToInputs(settings: MissionPatternSettings) {
        etGridWidth.setText(formatInputFloat(settings.gridWidthMeters))
        etGridLanes.setText(settings.gridLaneCount.toString())
        spGridStartSide.setSelection(settings.gridStartSide.ordinal, false)
        etCorridorWidth.setText(formatInputFloat(settings.corridorWidthMeters))
        spCorridorPassMode.setSelection(settings.corridorPassMode.ordinal, false)
        etOrbitRadius.setText(formatInputFloat(settings.orbitRadiusMeters))
        etOrbitTurns.setText(settings.orbitTurns.toString())
        spOrbitDirection.setSelection(settings.orbitDirection.ordinal, false)
    }

    private fun defaultMissionPatternSettings(): MissionPatternSettings {
        return MissionPatternSettings(
            gridWidthMeters = DEFAULT_GRID_WIDTH_M,
            gridLaneCount = DEFAULT_GRID_LANE_COUNT,
            gridStartSide = GridStartSide.AUTO,
            corridorWidthMeters = DEFAULT_CORRIDOR_WIDTH_M,
            corridorPassMode = CorridorPassMode.OUT_BACK,
            orbitRadiusMeters = DEFAULT_ORBIT_RADIUS_M,
            orbitTurns = DEFAULT_ORBIT_TURNS,
            orbitDirection = OrbitDirection.CW,
        )
    }

    private fun updateMissionPatternSettingsUi() {
        when (selectedMissionPattern()) {
            MissionPattern.POINTS -> {
                groupGridSettings.visibility = View.GONE
                groupCorridorSettings.visibility = View.GONE
                groupOrbitSettings.visibility = View.GONE
            }

            MissionPattern.GRID -> {
                groupGridSettings.visibility = View.VISIBLE
                groupCorridorSettings.visibility = View.GONE
                groupOrbitSettings.visibility = View.GONE
            }

            MissionPattern.CORRIDOR -> {
                groupGridSettings.visibility = View.GONE
                groupCorridorSettings.visibility = View.VISIBLE
                groupOrbitSettings.visibility = View.GONE
            }

            MissionPattern.ORBIT -> {
                groupGridSettings.visibility = View.GONE
                groupCorridorSettings.visibility = View.GONE
                groupOrbitSettings.visibility = View.VISIBLE
            }
        }
    }

    private fun updatePreviewModeButton() {
        btnPreviewMode.text = missionPreviewMode.label
    }

    private fun updateMissionToolsUi() {
        panelMissionTools.visibility = if (missionToolsExpanded) View.VISIBLE else View.GONE
        btnMissionTools.text = if (missionToolsExpanded) "Tools ^" else "Tools v"
    }

    private fun onMissionPlannerParamsChanged() {
        updateMissionPatternSettingsUi()
        onMissionPlannerNodesChanged()
    }

    private fun onMissionPlannerNodesChanged() {
        invalidatePreparedMissionFile()
        refreshMissionMapOverlays()
        refreshMissionSummary()
        saveMissionDraft()
    }

    private fun updateMissionAddModeUi() {
        btnMissionAddMode.text = if (missionAddMode) "Add: ON" else "Add: OFF"
    }

    private fun addMissionNode(point: GeoPoint) {
        val pattern = selectedMissionPattern()
        val altitude = when {
            missionNodes.isEmpty() -> START_WAYPOINT_ALTITUDE_M
            missionNodes.size == 1 -> missionNodes.first().altitudeMeters
            else -> missionNodes.last().altitudeMeters
        }
        val node = MissionNode(
            latitude = point.latitude,
            longitude = point.longitude,
            altitudeMeters = altitude,
        )

        when (pattern) {
            MissionPattern.GRID, MissionPattern.ORBIT -> {
                if (missionNodes.size >= 2) {
                    missionNodes[missionNodes.lastIndex] = node.copy(altitudeMeters = missionNodes.last().altitudeMeters)
                    val message = if (pattern == MissionPattern.GRID) {
                        "Grid uses Start + Land anchors. Updated land point."
                    } else {
                        "Orbit uses Center + Exit anchors. Updated exit point."
                    }
                    showStatusToast(message)
                } else {
                    missionNodes.add(node)
                }
            }

            else -> missionNodes.add(node)
        }
        onMissionPlannerNodesChanged()
    }

    private fun ensureStartWaypointIfKnown() {
        if (missionNodes.isNotEmpty()) return
        val state = FlightSessionStore.state.value
        val lat = state.latitude ?: pilotPhoneLatitude
        val lon = state.longitude ?: pilotPhoneLongitude
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return
        if (lat == 0.0 && lon == 0.0) return
        missionNodes.add(
            MissionNode(
                latitude = lat,
                longitude = lon,
                altitudeMeters = START_WAYPOINT_ALTITUDE_M,
            ),
        )
    }

    private fun selectedMissionCruiseAltitude(): Float =
        (etMissionAltitude.text?.toString()?.toFloatOrNull() ?: DEFAULT_MISSION_ALTITUDE_M)
            .coerceIn(10f, 220f)

    private fun selectedMissionSpacing(): Float =
        (etMissionSpacing.text?.toString()?.toFloatOrNull() ?: DEFAULT_MISSION_SPACING_M)
            .coerceIn(1f, 500f)

    private data class LocalPointMeters(
        val x: Double,
        val y: Double,
    )

    private fun buildExecutionMissionNodes(): List<MissionNode> {
        val base = missionNodes.toList()
        if (base.size < 2) return base
        return when (selectedMissionPattern()) {
            MissionPattern.POINTS -> buildPointsMissionNodes(base)
            MissionPattern.GRID -> buildGridMissionNodes(base)
            MissionPattern.CORRIDOR -> buildCorridorMissionNodes(base)
            MissionPattern.ORBIT -> buildOrbitMissionNodes(base)
        }
    }

    private fun buildPointsMissionNodes(base: List<MissionNode>): List<MissionNode> {
        return base.mapIndexed { index, node ->
            if (index == 0) node.copy(altitudeMeters = START_WAYPOINT_ALTITUDE_M) else node
        }
    }

    private fun buildGridMissionNodes(base: List<MissionNode>): List<MissionNode> {
        val start = base.first().copy(altitudeMeters = START_WAYPOINT_ALTITUDE_M)
        val landing = base.last()
        val endLocal = latLonToLocalMeters(start.latitude, start.longitude, landing.latitude, landing.longitude)
        val legLength = hypot(endLocal.x, endLocal.y)
        if (legLength < 20.0) return buildPointsMissionNodes(base)

        val uX = endLocal.x / legLength
        val uY = endLocal.y / legLength
        val perpX = -uY
        val perpY = uX

        val spacing = selectedMissionSpacing().coerceIn(8f, 160f).toDouble()
        val settings = selectedMissionPatternSettings()
        val requestedWidth = settings.gridWidthMeters.toDouble().coerceAtLeast(spacing * 1.5)
        val halfWidth = (requestedWidth / 2.0).coerceIn(spacing, 500.0)
        val laneCount = when {
            settings.gridLaneCount > 0 -> settings.gridLaneCount.coerceIn(2, 40)
            else -> (((halfWidth * 2.0) / spacing).toInt() + 1).coerceIn(3, 40)
        }
        val firstOffset = -((laneCount - 1) / 2.0) * spacing
        val cruiseAltitude = selectedMissionCruiseAltitude()
        val offsets = MutableList(laneCount) { lane -> firstOffset + (lane * spacing) }
        val orderedOffsets = when (settings.gridStartSide) {
            GridStartSide.LEFT -> offsets
            GridStartSide.RIGHT -> offsets.asReversed()
            GridStartSide.AUTO -> offsets
        }

        val generated = mutableListOf<MissionNode>()
        orderedOffsets.forEachIndexed { lane, offset ->
            val nearX = perpX * offset
            val nearY = perpY * offset
            val farX = endLocal.x + (perpX * offset)
            val farY = endLocal.y + (perpY * offset)
            val (first, second) = if (lane % 2 == 0) {
                localMetersToLatLon(start.latitude, start.longitude, nearX, nearY) to
                    localMetersToLatLon(start.latitude, start.longitude, farX, farY)
            } else {
                localMetersToLatLon(start.latitude, start.longitude, farX, farY) to
                    localMetersToLatLon(start.latitude, start.longitude, nearX, nearY)
            }
            generated += MissionNode(first.latitude, first.longitude, cruiseAltitude)
            generated += MissionNode(second.latitude, second.longitude, cruiseAltitude)
        }

        val result = mutableListOf(start)
        result.addAll(generated)
        result.add(landing.copy(altitudeMeters = cruiseAltitude))
        return dedupeNearbyNodes(result)
    }

    private fun buildCorridorMissionNodes(base: List<MissionNode>): List<MissionNode> {
        val start = base.first().copy(altitudeMeters = START_WAYPOINT_ALTITUDE_M)
        val cruiseAltitude = selectedMissionCruiseAltitude()
        val settings = selectedMissionPatternSettings()
        val centerline = base.mapIndexed { index, node ->
            if (index == 0) start else node.copy(altitudeMeters = cruiseAltitude)
        }
        if (centerline.size < 2) return centerline

        if (settings.corridorPassMode == CorridorPassMode.SINGLE_CENTER) {
            return dedupeNearbyNodes(centerline)
        }

        val offset = (settings.corridorWidthMeters / 2f).coerceIn(5f, 200f).toDouble()
        val leftPass = offsetPolylineNodes(centerline, offset)
        val rightPass = offsetPolylineNodes(centerline, -offset)

        val result = mutableListOf<MissionNode>()
        result += start
        result += leftPass.drop(1)
        result += rightPass.asReversed().drop(1)
        result += centerline.last()
        return dedupeNearbyNodes(result)
    }

    private fun buildOrbitMissionNodes(base: List<MissionNode>): List<MissionNode> {
        val center = base.first()
        val landing = base.last()
        val landingLocal = latLonToLocalMeters(center.latitude, center.longitude, landing.latitude, landing.longitude)
        val spacing = selectedMissionSpacing().coerceIn(8f, 120f).toDouble()
        val settings = selectedMissionPatternSettings()
        val landingDistance = hypot(landingLocal.x, landingLocal.y)
        val configuredRadius = settings.orbitRadiusMeters.toDouble()
        val radius = if (configuredRadius > 0.0) {
            configuredRadius.coerceIn(20.0, 320.0)
        } else if (landingDistance >= 20.0) {
            landingDistance.coerceIn(20.0, 320.0)
        } else {
            (spacing * 2.0).coerceIn(20.0, 200.0)
        }

        val entryAngle = if (landingDistance > 0.1) atan2(landingLocal.y, landingLocal.x) else 0.0
        val turns = settings.orbitTurns.coerceIn(1, 8)
        val pointCountPerTurn = ((2.0 * PI * radius) / spacing).toInt().coerceIn(8, 40)
        val totalPointCount = (pointCountPerTurn * turns).coerceIn(8, 320)
        val directionSign = if (settings.orbitDirection == OrbitDirection.CCW) 1.0 else -1.0
        val cruiseAltitude = selectedMissionCruiseAltitude()

        val orbitPoints = mutableListOf<MissionNode>()
        for (i in 0 until totalPointCount) {
            val theta = entryAngle + directionSign * ((2.0 * PI * i) / pointCountPerTurn.toDouble())
            val x = radius * cos(theta)
            val y = radius * sin(theta)
            val point = localMetersToLatLon(center.latitude, center.longitude, x, y)
            val altitude = if (i == 0) START_WAYPOINT_ALTITUDE_M else cruiseAltitude
            orbitPoints += MissionNode(point.latitude, point.longitude, altitude)
        }

        val result = mutableListOf<MissionNode>()
        result += orbitPoints
        result += landing.copy(altitudeMeters = cruiseAltitude)
        return dedupeNearbyNodes(result)
    }

    private fun offsetPolylineNodes(nodes: List<MissionNode>, offsetMeters: Double): List<MissionNode> {
        if (nodes.size < 2) return nodes
        val refLat = nodes.first().latitude
        val refLon = nodes.first().longitude
        val local = nodes.map { latLonToLocalMeters(refLat, refLon, it.latitude, it.longitude) }
        val shifted = mutableListOf<MissionNode>()

        local.forEachIndexed { index, point ->
            val direction = when (index) {
                0 -> LocalPointMeters(local[1].x - point.x, local[1].y - point.y)
                local.lastIndex -> LocalPointMeters(point.x - local[index - 1].x, point.y - local[index - 1].y)
                else -> LocalPointMeters(local[index + 1].x - local[index - 1].x, local[index + 1].y - local[index - 1].y)
            }
            val length = hypot(direction.x, direction.y)
            if (length < 0.001) {
                shifted += nodes[index]
            } else {
                val normalX = -direction.y / length
                val normalY = direction.x / length
                val shiftedPoint = localMetersToLatLon(
                    refLat = refLat,
                    refLon = refLon,
                    xMeters = point.x + (normalX * offsetMeters),
                    yMeters = point.y + (normalY * offsetMeters),
                )
                shifted += nodes[index].copy(
                    latitude = shiftedPoint.latitude,
                    longitude = shiftedPoint.longitude,
                )
            }
        }
        return shifted
    }

    private fun dedupeNearbyNodes(nodes: List<MissionNode>, minDistanceMeters: Double = 3.0): List<MissionNode> {
        if (nodes.size <= 1) return nodes
        val result = mutableListOf(nodes.first())
        for (i in 1 until nodes.size) {
            val current = nodes[i]
            val previous = result.last()
            val distance = haversineMeters(previous.latitude, previous.longitude, current.latitude, current.longitude)
            if (distance >= minDistanceMeters) {
                result += current
            }
        }
        return result
    }

    private fun latLonToLocalMeters(refLat: Double, refLon: Double, lat: Double, lon: Double): LocalPointMeters {
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(refLat)).coerceAtLeast(0.01)
        val x = (lon - refLon) * metersPerDegLon
        val y = (lat - refLat) * metersPerDegLat
        return LocalPointMeters(x = x, y = y)
    }

    private fun localMetersToLatLon(refLat: Double, refLon: Double, xMeters: Double, yMeters: Double): GeoPoint {
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(refLat)).coerceAtLeast(0.01)
        val lat = refLat + (yMeters / metersPerDegLat)
        val lon = refLon + (xMeters / metersPerDegLon)
        return GeoPoint(lat, lon)
    }

    private fun setMissionNodeAltitudeAndPropagate(fromIndex: Int, altitudeMeters: Float) {
        if (fromIndex <= 0 || fromIndex >= missionNodes.size) return
        val value = altitudeMeters.coerceIn(10f, 220f)
        for (i in fromIndex until missionNodes.size) {
            val node = missionNodes[i]
            missionNodes[i] = node.copy(altitudeMeters = value)
        }
        invalidatePreparedMissionFile()
    }

    private fun showWaypointEditor(nodeIndex: Int) {
        val node = missionNodes.getOrNull(nodeIndex) ?: return
        val isStart = nodeIndex == 0
        val isLast = nodeIndex == missionNodes.lastIndex

        val content = LayoutInflater.from(this).inflate(R.layout.dialog_waypoint_edit, null)
        val tvTitle = content.findViewById<TextView>(R.id.tvWaypointTitle)
        val tvHint = content.findViewById<TextView>(R.id.tvWaypointHint)
        val tvAltitudeValue = content.findViewById<TextView>(R.id.tvWaypointAltitudeValue)
        val seekAltitude = content.findViewById<VerticalSeekBar>(R.id.seekWaypointAltitude)
        val groupTerminalAction = content.findViewById<LinearLayout>(R.id.groupTerminalAction)
        val spTerminalAction = content.findViewById<AppCompatSpinner>(R.id.spTerminalAction)

        tvTitle.text = "Waypoint ${nodeIndex + 1}"
        seekAltitude.min = 10
        seekAltitude.max = 220
        seekAltitude.progress = node.altitudeMeters.toInt().coerceIn(seekAltitude.min, seekAltitude.max)
        tvAltitudeValue.text = "${seekAltitude.progress} m"

        seekAltitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAltitudeValue.text = "$progress m"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        if (isStart) {
            seekAltitude.isEnabled = false
            seekAltitude.progress = START_WAYPOINT_ALTITUDE_M.toInt()
            tvAltitudeValue.text = "${START_WAYPOINT_ALTITUDE_M.toInt()} m"
            tvHint.text = "Point 1 is the start point and fixed at 50m."
        }

        if (isLast) {
            groupTerminalAction.visibility = View.VISIBLE
            val actionAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                MissionTerminalAction.values().map { it.label },
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spTerminalAction.adapter = actionAdapter
            spTerminalAction.setSelection(missionTerminalAction.ordinal, false)
        } else {
            groupTerminalAction.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Waypoint")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                if (!isStart) {
                    val newAltitude = seekAltitude.progress.toFloat()
                    setMissionNodeAltitudeAndPropagate(nodeIndex, newAltitude)
                    etMissionAltitude.setText(formatInputFloat(newAltitude))
                }
                if (isLast) {
                    missionTerminalAction =
                        MissionTerminalAction.values().getOrElse(spTerminalAction.selectedItemPosition) {
                            MissionTerminalAction.LOITER
                        }
                    invalidatePreparedMissionFile()
                }
                onMissionPlannerNodesChanged()
            }
            .show()
    }

    private fun findNodeIndexNearScreenPoint(x: Float, y: Float): Int? {
        var bestIndex: Int? = null
        var bestDistanceSquared = 44f * 44f
        missionNodes.forEachIndexed { index, node ->
            val screenPoint = mapMission.projection.toPixels(GeoPoint(node.latitude, node.longitude), null)
            val dx = screenPoint.x - x
            val dy = screenPoint.y - y
            val distanceSquared = (dx * dx) + (dy * dy)
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun refreshMissionMapOverlays() {
        missionGeneratedPath?.let { mapMission.overlays.remove(it) }
        missionGeneratedPath = null
        missionAnchorPath?.let { mapMission.overlays.remove(it) }
        missionAnchorPath = null
        missionMarkers.forEach { mapMission.overlays.remove(it) }
        missionMarkers.clear()
        dryRunMarker?.let { mapMission.overlays.remove(it) }

        val showGenerated = missionPreviewMode == MissionPreviewMode.GENERATED || missionPreviewMode == MissionPreviewMode.BOTH
        val showAnchors = missionPreviewMode == MissionPreviewMode.ANCHORS || missionPreviewMode == MissionPreviewMode.BOTH
        val anchorNodes = missionNodes.toList()
        val previewNodes = buildExecutionMissionNodes()
        if (showGenerated && previewNodes.size >= 2) {
            val path = Polyline().apply {
                setPoints(previewNodes.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = Color.CYAN
                outlinePaint.strokeWidth = 5f
            }
            missionGeneratedPath = path
            mapMission.overlays.add(path)
        }

        if (showAnchors && anchorNodes.size >= 2) {
            val path = Polyline().apply {
                setPoints(anchorNodes.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = Color.parseColor("#FFFFC107")
                outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            }
            missionAnchorPath = path
            mapMission.overlays.add(path)
        }

        anchorNodes.forEachIndexed { index, node ->
            val isStart = index == 0
            val isLast = index == anchorNodes.lastIndex
            val isLinearLanding = isLast && missionTerminalAction == MissionTerminalAction.LINEAR_LANDING && anchorNodes.size >= 2
            val heading = if (isLinearLanding) {
                val prev = anchorNodes[index - 1]
                bearingDegrees(prev.latitude, prev.longitude, node.latitude, node.longitude).toFloat()
            } else {
                0f
            }
            val marker = Marker(mapMission).apply {
                position = GeoPoint(node.latitude, node.longitude)
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    if (isLinearLanding) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM,
                )
                title = when {
                    isStart -> "Start (WP 1)"
                    isLinearLanding -> "Landing Cone (WP ${index + 1})"
                    else -> "WP ${index + 1}"
                }
                snippet = String.format(
                    Locale.US,
                    "ALT %.0fm | %.6f, %.6f",
                    node.altitudeMeters,
                    node.latitude,
                    node.longitude,
                )
                icon = if (isLinearLanding) {
                    createLandingConeIconDrawable(index + 1)
                } else {
                    createWaypointIconDrawable(index + 1, isStart)
                }
                rotation = heading
                alpha = if (missionPreviewMode == MissionPreviewMode.GENERATED) 0.92f else 1f
            }
            missionMarkers.add(marker)
            mapMission.overlays.add(marker)
        }

        if (dryRunState != DryRunState.IDLE || dryRunProgressMeters > 0.0) {
            renderDryRunMarker()
        }
        mapMission.invalidate()
    }

    private fun createWaypointIconDrawable(number: Int, isStart: Boolean): BitmapDrawable {
        val sizePx = 88
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val radius = (sizePx / 2f) - 6f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStart) Color.parseColor("#2E7D32") else Color.parseColor("#0277BD")
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawCircle(center, center, radius, fill)
        canvas.drawCircle(center, center, radius, stroke)
        val baseline = center - ((text.descent() + text.ascent()) / 2f)
        canvas.drawText(number.toString(), center, baseline, text)

        return BitmapDrawable(resources, bitmap)
    }

    private fun createLandingConeIconDrawable(number: Int): BitmapDrawable {
        val sizePx = 96
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f

        val cone = Path().apply {
            moveTo(center, 10f)
            lineTo(sizePx - 12f, sizePx - 12f)
            lineTo(12f, sizePx - 12f)
            close()
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8F00")
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawPath(cone, fill)
        canvas.drawPath(cone, stroke)
        val baseline = (sizePx * 0.64f) - ((text.descent() + text.ascent()) / 2f)
        canvas.drawText(number.toString(), center, baseline, text)

        return BitmapDrawable(resources, bitmap)
    }

    private fun createDryRunMarkerIconDrawable(): BitmapDrawable {
        val sizePx = 54
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00BCD4")
            style = Paint.Style.FILL
        }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 3f, outer)
        canvas.drawCircle(center, center, center / 2.8f, inner)
        return BitmapDrawable(resources, bitmap)
    }

    private fun refreshMissionSummary() {
        val executionNodes = buildExecutionMissionNodes()
        val altitude = if (executionNodes.size >= 2) {
            executionNodes.last().altitudeMeters
        } else {
            selectedMissionCruiseAltitude()
        }
        val spacing = selectedMissionSpacing()
        val pathMeters = calculateMissionPathMeters(executionNodes)
        val pathText = formatDistance(pathMeters)
        val pattern = selectedMissionPattern().label
        val patternSettings = selectedMissionPatternSettings()
        val endAction = missionTerminalAction.label
        val patternHint = when (selectedMissionPattern()) {
            MissionPattern.POINTS -> "Points: tap map to place each waypoint."
            MissionPattern.GRID -> {
                val lanes = if (patternSettings.gridLaneCount > 0) patternSettings.gridLaneCount.toString() else "Auto"
                "Grid: width ${formatInputFloat(patternSettings.gridWidthMeters)}m, lanes $lanes, ${patternSettings.gridStartSide.label.lowercase(Locale.US)}."
            }
            MissionPattern.CORRIDOR -> {
                "Corridor: width ${formatInputFloat(patternSettings.corridorWidthMeters)}m, mode ${patternSettings.corridorPassMode.label.lowercase(Locale.US)}."
            }
            MissionPattern.ORBIT -> {
                val radius = if (patternSettings.orbitRadiusMeters > 0f) {
                    "${formatInputFloat(patternSettings.orbitRadiusMeters)}m"
                } else {
                    "auto"
                }
                "Orbit: radius $radius, turns ${patternSettings.orbitTurns}, ${patternSettings.orbitDirection.label}."
            }
        }
        val nodeText = if (executionNodes.size != missionNodes.size) {
            "${missionNodes.size}->${executionNodes.size}"
        } else {
            missionNodes.size.toString()
        }
        val previewText = missionPreviewMode.label.removePrefix("Preview: ")
        tvMissionSummary.text =
            "Pattern: $pattern | Nodes: $nodeText | Path: $pathText | Alt: ${formatInputFloat(altitude)}m | Spacing: ${formatInputFloat(spacing)}m | End: $endAction | Preview: $previewText\n$patternHint"
    }

    private fun calculateMissionPathMeters(nodes: List<MissionNode>): Double {
        if (nodes.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until nodes.size) {
            val a = nodes[i - 1]
            val b = nodes[i]
            total += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    private fun saveMissionDraft() {
        val patternSettings = selectedMissionPatternSettings()
        val pointsJson = JSONArray()
        missionNodes.forEach { point ->
            pointsJson.put(
                JSONObject()
                    .put("lat", point.latitude)
                    .put("lon", point.longitude)
                    .put("alt", point.altitudeMeters),
            )
        }
        prefs.edit()
            .putInt(PREF_KEY_MISSION_PATTERN_INDEX, spMissionPattern.selectedItemPosition.coerceAtLeast(0))
            .putFloat(PREF_KEY_MISSION_ALTITUDE_M, selectedMissionCruiseAltitude())
            .putFloat(PREF_KEY_MISSION_SPACING_M, selectedMissionSpacing())
            .putString(PREF_KEY_MISSION_POINTS_JSON, pointsJson.toString())
            .putString(PREF_KEY_MISSION_TERMINAL_ACTION, missionTerminalAction.name)
            .putString(PREF_KEY_MISSION_PREVIEW_MODE, missionPreviewMode.name)
            .putFloat(PREF_KEY_GRID_WIDTH_M, patternSettings.gridWidthMeters)
            .putInt(PREF_KEY_GRID_LANE_COUNT, patternSettings.gridLaneCount)
            .putString(PREF_KEY_GRID_START_SIDE, patternSettings.gridStartSide.name)
            .putFloat(PREF_KEY_CORRIDOR_WIDTH_M, patternSettings.corridorWidthMeters)
            .putString(PREF_KEY_CORRIDOR_PASS_MODE, patternSettings.corridorPassMode.name)
            .putFloat(PREF_KEY_ORBIT_RADIUS_M, patternSettings.orbitRadiusMeters)
            .putInt(PREF_KEY_ORBIT_TURNS, patternSettings.orbitTurns)
            .putString(PREF_KEY_ORBIT_DIRECTION, patternSettings.orbitDirection.name)
            .apply()
    }

    private fun restoreMissionDraft() {
        val defaultPatternSettings = defaultMissionPatternSettings()
        etMissionAltitude.setText(formatInputFloat(prefs.getFloat(PREF_KEY_MISSION_ALTITUDE_M, DEFAULT_MISSION_ALTITUDE_M)))
        etMissionSpacing.setText(formatInputFloat(prefs.getFloat(PREF_KEY_MISSION_SPACING_M, DEFAULT_MISSION_SPACING_M)))
        etGridWidth.setText(formatInputFloat(prefs.getFloat(PREF_KEY_GRID_WIDTH_M, defaultPatternSettings.gridWidthMeters)))
        etGridLanes.setText(prefs.getInt(PREF_KEY_GRID_LANE_COUNT, defaultPatternSettings.gridLaneCount).toString())
        etCorridorWidth.setText(formatInputFloat(prefs.getFloat(PREF_KEY_CORRIDOR_WIDTH_M, defaultPatternSettings.corridorWidthMeters)))
        etOrbitRadius.setText(formatInputFloat(prefs.getFloat(PREF_KEY_ORBIT_RADIUS_M, defaultPatternSettings.orbitRadiusMeters)))
        etOrbitTurns.setText(prefs.getInt(PREF_KEY_ORBIT_TURNS, defaultPatternSettings.orbitTurns).toString())
        val patternIndex = prefs.getInt(PREF_KEY_MISSION_PATTERN_INDEX, DEFAULT_MISSION_PATTERN_INDEX)
            .coerceIn(0, MissionPattern.values().lastIndex)
        spMissionPattern.setSelection(patternIndex, false)
        missionPreviewMode = missionPreviewModeFromName(prefs.getString(PREF_KEY_MISSION_PREVIEW_MODE, null))
        spGridStartSide.setSelection(
            gridStartSideFromName(prefs.getString(PREF_KEY_GRID_START_SIDE, null)).ordinal,
            false,
        )
        spCorridorPassMode.setSelection(
            corridorPassModeFromName(prefs.getString(PREF_KEY_CORRIDOR_PASS_MODE, null)).ordinal,
            false,
        )
        spOrbitDirection.setSelection(
            orbitDirectionFromName(prefs.getString(PREF_KEY_ORBIT_DIRECTION, null)).ordinal,
            false,
        )
        missionTerminalAction = missionTerminalActionFromName(prefs.getString(PREF_KEY_MISSION_TERMINAL_ACTION, null))
        invalidatePreparedMissionFile()

        missionNodes.clear()
        val pointsRaw = prefs.getString(PREF_KEY_MISSION_POINTS_JSON, "[]").orEmpty()
        try {
            val points = JSONArray(pointsRaw)
            var inheritedAltitude = selectedMissionCruiseAltitude()
            for (i in 0 until points.length()) {
                val item = points.optJSONObject(i) ?: continue
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) {
                    val parsedAltitude = item.optDouble("alt", Double.NaN)
                    val altitude = when {
                        i == 0 -> START_WAYPOINT_ALTITUDE_M
                        parsedAltitude.isFinite() -> parsedAltitude.toFloat().coerceIn(10f, 220f)
                        else -> inheritedAltitude
                    }
                    inheritedAltitude = altitude
                    missionNodes.add(
                        MissionNode(
                            latitude = lat,
                            longitude = lon,
                            altitudeMeters = altitude,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            missionNodes.clear()
        }

        mapMission.controller.setZoom(DEFAULT_MISSION_MAP_ZOOM)
        mapMission.controller.setCenter(GeoPoint(DEFAULT_MISSION_MAP_LAT, DEFAULT_MISSION_MAP_LON))
        updateMissionPatternSettingsUi()
        updatePreviewModeButton()
        updateDryRunUi()
    }

    private fun saveMissionViewport() {
        // Intentionally no-op: planner map should open fresh and re-center to pilot/plane each time.
    }

    private fun missionTerminalActionFromName(name: String?): MissionTerminalAction {
        return MissionTerminalAction.values().firstOrNull { it.name == name } ?: MissionTerminalAction.LOITER
    }

    private fun missionPreviewModeFromName(name: String?): MissionPreviewMode {
        return MissionPreviewMode.values().firstOrNull { it.name == name } ?: MissionPreviewMode.BOTH
    }

    private fun gridStartSideFromName(name: String?): GridStartSide {
        return GridStartSide.values().firstOrNull { it.name == name } ?: GridStartSide.AUTO
    }

    private fun corridorPassModeFromName(name: String?): CorridorPassMode {
        return CorridorPassMode.values().firstOrNull { it.name == name } ?: CorridorPassMode.OUT_BACK
    }

    private fun orbitDirectionFromName(name: String?): OrbitDirection {
        return OrbitDirection.values().firstOrNull { it.name == name } ?: OrbitDirection.CW
    }

    private fun loadSavedMissionPlansFromPrefs() {
        savedMissionPlans.clear()
        val raw = prefs.getString(PREF_KEY_MISSION_PLANS_JSON, "[]").orEmpty()
        try {
            val plansArray = JSONArray(raw)
            for (i in 0 until plansArray.length()) {
                val planObj = plansArray.optJSONObject(i) ?: continue
                val name = planObj.optString("name", "").trim()
                if (name.isBlank()) continue
                val patternName = planObj.optString("pattern", MissionPattern.POINTS.name)
                val pattern = MissionPattern.values().firstOrNull { it.name == patternName } ?: MissionPattern.POINTS
                val spacing = planObj.optDouble("spacing", DEFAULT_MISSION_SPACING_M.toDouble()).toFloat().coerceIn(1f, 500f)
                val terminalAction = missionTerminalActionFromName(planObj.optString("terminal_action", MissionTerminalAction.LOITER.name))
                val settingsObj = planObj.optJSONObject("pattern_settings")
                val patternSettings = MissionPatternSettings(
                    gridWidthMeters = (
                        settingsObj?.optDouble("grid_width_m", DEFAULT_GRID_WIDTH_M.toDouble())
                            ?: DEFAULT_GRID_WIDTH_M.toDouble()
                        ).toFloat().coerceIn(30f, 1_000f),
                    gridLaneCount = (
                        settingsObj?.optInt("grid_lane_count", DEFAULT_GRID_LANE_COUNT)
                            ?: DEFAULT_GRID_LANE_COUNT
                        ).coerceIn(0, 40),
                    gridStartSide = gridStartSideFromName(
                        settingsObj?.optString("grid_start_side", GridStartSide.AUTO.name),
                    ),
                    corridorWidthMeters = (
                        settingsObj?.optDouble("corridor_width_m", DEFAULT_CORRIDOR_WIDTH_M.toDouble())
                            ?: DEFAULT_CORRIDOR_WIDTH_M.toDouble()
                        ).toFloat().coerceIn(10f, 400f),
                    corridorPassMode = corridorPassModeFromName(
                        settingsObj?.optString("corridor_pass_mode", CorridorPassMode.OUT_BACK.name),
                    ),
                    orbitRadiusMeters = (
                        settingsObj?.optDouble("orbit_radius_m", DEFAULT_ORBIT_RADIUS_M.toDouble())
                            ?: DEFAULT_ORBIT_RADIUS_M.toDouble()
                        ).toFloat().coerceIn(0f, 500f),
                    orbitTurns = (
                        settingsObj?.optInt("orbit_turns", DEFAULT_ORBIT_TURNS)
                            ?: DEFAULT_ORBIT_TURNS
                        ).coerceIn(1, 8),
                    orbitDirection = orbitDirectionFromName(
                        settingsObj?.optString("orbit_direction", OrbitDirection.CW.name),
                    ),
                )
                val nodesJson = planObj.optJSONArray("nodes") ?: JSONArray()
                val nodes = mutableListOf<MissionNode>()
                for (j in 0 until nodesJson.length()) {
                    val nodeObj = nodesJson.optJSONObject(j) ?: continue
                    val lat = nodeObj.optDouble("lat", Double.NaN)
                    val lon = nodeObj.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val alt = if (j == 0) {
                        START_WAYPOINT_ALTITUDE_M
                    } else {
                        nodeObj.optDouble("alt", DEFAULT_MISSION_ALTITUDE_M.toDouble()).toFloat().coerceIn(10f, 220f)
                    }
                    nodes.add(MissionNode(lat, lon, alt))
                }
                if (nodes.isNotEmpty()) {
                    savedMissionPlans.add(
                        MissionPlan(
                            name = name,
                            pattern = pattern,
                            spacingMeters = spacing,
                            terminalAction = terminalAction,
                            patternSettings = patternSettings,
                            nodes = nodes,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            savedMissionPlans.clear()
        }
    }

    private fun persistSavedMissionPlansToPrefs() {
        val plansArray = JSONArray()
        savedMissionPlans.forEach { plan ->
            val nodesArray = JSONArray()
            plan.nodes.forEach { node ->
                nodesArray.put(
                    JSONObject()
                        .put("lat", node.latitude)
                        .put("lon", node.longitude)
                        .put("alt", node.altitudeMeters),
                )
            }
            plansArray.put(
                JSONObject()
                    .put("name", plan.name)
                    .put("pattern", plan.pattern.name)
                    .put("spacing", plan.spacingMeters)
                    .put("terminal_action", plan.terminalAction.name)
                    .put(
                        "pattern_settings",
                        JSONObject()
                            .put("grid_width_m", plan.patternSettings.gridWidthMeters)
                            .put("grid_lane_count", plan.patternSettings.gridLaneCount)
                            .put("grid_start_side", plan.patternSettings.gridStartSide.name)
                            .put("corridor_width_m", plan.patternSettings.corridorWidthMeters)
                            .put("corridor_pass_mode", plan.patternSettings.corridorPassMode.name)
                            .put("orbit_radius_m", plan.patternSettings.orbitRadiusMeters)
                            .put("orbit_turns", plan.patternSettings.orbitTurns)
                            .put("orbit_direction", plan.patternSettings.orbitDirection.name),
                    )
                    .put("nodes", nodesArray),
            )
        }
        prefs.edit().putString(PREF_KEY_MISSION_PLANS_JSON, plansArray.toString()).apply()
    }

    private fun promptSaveMissionPlan() {
        if (missionNodes.size < 2) {
            showStatusToast("add at least 2 points first")
            return
        }

        val input = EditText(this).apply {
            hint = "Plan Name"
            setText("Plan ${savedMissionPlans.size + 1}")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Save Flight Plan")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    showStatusToast("plan name required")
                    return@setPositiveButton
                }
                saveCurrentMissionAsPlan(name)
                showStatusToast("saved: $name")
            }
            .show()
    }

    private fun saveCurrentMissionAsPlan(name: String) {
        val plan = MissionPlan(
            name = name,
            pattern = selectedMissionPattern(),
            spacingMeters = selectedMissionSpacing(),
            terminalAction = missionTerminalAction,
            patternSettings = selectedMissionPatternSettings(),
            nodes = missionNodes.toList(),
        )
        val existingIndex = savedMissionPlans.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existingIndex >= 0) {
            savedMissionPlans[existingIndex] = plan
        } else {
            savedMissionPlans.add(plan)
        }
        persistSavedMissionPlansToPrefs()
    }

    private fun promptLoadMissionPlan() {
        if (savedMissionPlans.isEmpty()) {
            showStatusToast("no saved plans")
            return
        }
        val names = savedMissionPlans.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Load Flight Plan")
            .setItems(names) { _, which ->
                loadMissionPlan(savedMissionPlans[which])
            }
            .show()
    }

    private fun loadMissionPlan(plan: MissionPlan) {
        missionNodes.clear()
        missionNodes.addAll(plan.nodes)
        invalidatePreparedMissionFile()
        if (missionNodes.isNotEmpty()) {
            val start = missionNodes[0]
            missionNodes[0] = start.copy(altitudeMeters = START_WAYPOINT_ALTITUDE_M)
        }
        spMissionPattern.setSelection(plan.pattern.ordinal, false)
        etMissionSpacing.setText(formatInputFloat(plan.spacingMeters))
        etMissionAltitude.setText(formatInputFloat(plan.nodes.lastOrNull()?.altitudeMeters ?: DEFAULT_MISSION_ALTITUDE_M))
        applyMissionPatternSettingsToInputs(plan.patternSettings)
        missionTerminalAction = plan.terminalAction
        onMissionPlannerParamsChanged()
        centerMissionMapOnNodes(buildExecutionMissionNodes())
        showStatusToast("loaded: ${plan.name}")
    }

    private fun centerMissionMapOnNodes(nodes: List<MissionNode>) {
        if (nodes.isEmpty()) {
            centerMissionMapFresh()
            return
        }
        if (nodes.size == 1) {
            val single = nodes.first()
            mapMission.controller.setZoom(PILOT_MAP_FOCUS_ZOOM)
            mapMission.controller.animateTo(GeoPoint(single.latitude, single.longitude))
            return
        }
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        nodes.forEach { node ->
            minLat = minOf(minLat, node.latitude)
            maxLat = maxOf(maxLat, node.latitude)
            minLon = minOf(minLon, node.longitude)
            maxLon = maxOf(maxLon, node.longitude)
        }
        val center = GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0015)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0015)
        mapMission.controller.setCenter(center)
        mapMission.controller.zoomToSpan(latSpan, lonSpan)
    }

    private fun centerMissionMapFromFlightState(forceZoom: Boolean = false): Boolean {
        val state = FlightSessionStore.state.value
        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return false
        if (lat == 0.0 && lon == 0.0) return false
        if (forceZoom) {
            mapMission.controller.setZoom(PILOT_MAP_FOCUS_ZOOM)
        }
        mapMission.controller.animateTo(GeoPoint(lat, lon))
        return true
    }

    private fun centerMissionMapOnPilotLocation(forceZoom: Boolean = false): Boolean {
        val lat = pilotPhoneLatitude
        val lon = pilotPhoneLongitude
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return false
        if (forceZoom) {
            mapMission.controller.setZoom(PILOT_MAP_FOCUS_ZOOM)
        }
        mapMission.controller.animateTo(GeoPoint(lat, lon))
        return true
    }

    private fun centerMissionMapFresh() {
        val centeredOnPilot = centerMissionMapOnPilotLocation(forceZoom = true)
        if (centeredOnPilot) return
        val centeredOnPlane = centerMissionMapFromFlightState(forceZoom = true)
        if (centeredOnPlane) return
        mapMission.controller.setZoom(DEFAULT_MISSION_MAP_ZOOM)
        mapMission.controller.setCenter(GeoPoint(DEFAULT_MISSION_MAP_LAT, DEFAULT_MISSION_MAP_LON))
    }

    private data class DryRunSample(
        val point: GeoPoint,
        val headingDegrees: Float,
    )

    private fun stopDryRunForFlightView() {
        resetDryRun(clearMarker = true, keepProgress = false)
        updateDryRunUi()
    }

    private fun toggleDryRunStartPause() {
        when (dryRunState) {
            DryRunState.RUNNING -> {
                dryRunState = DryRunState.PAUSED
                dryRunJob?.cancel()
                dryRunJob = null
                updateDryRunUi()
                showStatusToast("Dry run paused")
            }

            DryRunState.PAUSED,
            DryRunState.IDLE,
            DryRunState.COMPLETE,
            -> {
                val previewNodes = buildExecutionMissionNodes()
                if (previewNodes.size < 2) {
                    showStatusToast("add at least 2 points first")
                    return
                }

                if (dryRunState == DryRunState.COMPLETE) {
                    dryRunProgressMeters = 0.0
                }
                if (dryRunState == DryRunState.IDLE) {
                    dryRunProgressMeters = 0.0
                }
                dryRunTotalMeters = calculateMissionPathMeters(previewNodes).coerceAtLeast(0.0)
                if (dryRunTotalMeters <= 0.0) {
                    showStatusToast("dry run route is too short")
                    return
                }

                dryRunState = DryRunState.RUNNING
                updateDryRunUi()
                startDryRunLoop()
            }
        }
    }

    private fun startDryRunLoop() {
        dryRunJob?.cancel()
        val previewNodes = buildExecutionMissionNodes()
        if (previewNodes.size < 2) {
            dryRunState = DryRunState.IDLE
            updateDryRunUi()
            return
        }
        val points = previewNodes.map { GeoPoint(it.latitude, it.longitude) }
        if (dryRunTotalMeters <= 0.0) {
            dryRunTotalMeters = calculateMissionPathMeters(previewNodes)
        }
        renderDryRunMarker()
        dryRunJob = lifecycleScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive && dryRunState == DryRunState.RUNNING) {
                val now = System.currentTimeMillis()
                val dtSec = ((now - lastTickMs).coerceAtLeast(1L)).toDouble() / 1_000.0
                lastTickMs = now
                dryRunProgressMeters = (dryRunProgressMeters + (DRY_RUN_SPEED_MPS * dtSec))
                    .coerceAtMost(dryRunTotalMeters)
                renderDryRunMarker(points)
                updateDryRunUi()

                if (dryRunProgressMeters >= dryRunTotalMeters) {
                    dryRunState = DryRunState.COMPLETE
                    updateDryRunUi()
                    break
                }
                delay(DRY_RUN_STEP_INTERVAL_MS)
            }
        }
    }

    private fun resetDryRun(clearMarker: Boolean, keepProgress: Boolean) {
        dryRunJob?.cancel()
        dryRunJob = null
        if (!keepProgress) {
            dryRunProgressMeters = 0.0
            dryRunTotalMeters = 0.0
        }
        dryRunState = DryRunState.IDLE
        if (clearMarker) {
            dryRunMarker?.let { mapMission.overlays.remove(it) }
            dryRunMarker = null
            mapMission.invalidate()
        }
    }

    private fun updateDryRunUi() {
        btnDryRunStartPause.text = when (dryRunState) {
            DryRunState.IDLE -> "Dry Run"
            DryRunState.RUNNING -> "Pause Dry"
            DryRunState.PAUSED -> "Resume Dry"
            DryRunState.COMPLETE -> "Replay Dry"
        }
        val canReset = dryRunProgressMeters > 0.0 || dryRunState != DryRunState.IDLE
        btnDryRunReset.isEnabled = canReset
        val pct = if (dryRunTotalMeters > 0.0) {
            ((dryRunProgressMeters / dryRunTotalMeters) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
        val status = when (dryRunState) {
            DryRunState.IDLE -> "Idle"
            DryRunState.RUNNING -> "Running"
            DryRunState.PAUSED -> "Paused"
            DryRunState.COMPLETE -> "Complete"
        }
        val progressText = if (dryRunTotalMeters > 0.0) {
            "${formatDistance(dryRunProgressMeters)} / ${formatDistance(dryRunTotalMeters)}"
        } else {
            "--"
        }
        tvDryRunStatus.text = String.format(Locale.US, "Dry: %s %.0f%%  %s", status, pct, progressText)
    }

    private fun renderDryRunMarker(precomputedPoints: List<GeoPoint>? = null) {
        val points = precomputedPoints ?: buildExecutionMissionNodes().map { GeoPoint(it.latitude, it.longitude) }
        if (points.size < 2) {
            dryRunMarker?.let { mapMission.overlays.remove(it) }
            dryRunMarker = null
            return
        }
        val sample = interpolateDryRunSample(points, dryRunProgressMeters) ?: return
        val marker = dryRunMarker ?: Marker(mapMission).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Dry-run aircraft"
            icon = createDryRunMarkerIconDrawable()
            dryRunMarker = this
        }
        marker.position = sample.point
        marker.rotation = sample.headingDegrees
        if (!mapMission.overlays.contains(marker)) {
            mapMission.overlays.add(marker)
        }
        mapMission.invalidate()
    }

    private fun interpolateDryRunSample(points: List<GeoPoint>, distanceMeters: Double): DryRunSample? {
        if (points.size < 2) return null
        val clamped = distanceMeters.coerceAtLeast(0.0)
        var walked = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val segmentMeters = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (segmentMeters <= 0.01) continue
            if (walked + segmentMeters >= clamped) {
                val t = ((clamped - walked) / segmentMeters).coerceIn(0.0, 1.0)
                val lat = a.latitude + ((b.latitude - a.latitude) * t)
                val lon = a.longitude + ((b.longitude - a.longitude) * t)
                val heading = bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude).toFloat()
                return DryRunSample(point = GeoPoint(lat, lon), headingDegrees = heading)
            }
            walked += segmentMeters
        }
        val tail = points.last()
        val prev = points[points.lastIndex - 1]
        val heading = bearingDegrees(prev.latitude, prev.longitude, tail.latitude, tail.longitude).toFloat()
        return DryRunSample(point = tail, headingDegrees = heading)
    }

    private fun applyVideoAspectTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val targetAspect = 16f / 9f
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

        val sx: Float
        val sy: Float
        if (viewAspect > targetAspect) {
            sx = targetAspect / viewAspect
            sy = 1f
        } else {
            sx = 1f
            sy = viewAspect / targetAspect
        }
        val matrix = Matrix().apply {
            setScale(sx, sy, viewWidth / 2f, viewHeight / 2f)
        }
        textureVideo.setTransform(matrix)
    }

    private fun showTakeoffConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Takeoff")
            .setMessage("Start motors and take off?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Takeoff") { _, _ ->
                if (benchLockEnabled) {
                    showStatusToast("takeoff blocked: bench lock on")
                    appendCommandEvent("Takeoff blocked (bench lock)")
                    return@setPositiveButton
                }
                if (!ensureCriticalCommandReady("takeoff", requireAirborne = false, requireFreshTelemetry = true)) {
                    return@setPositiveButton
                }
                if (airborne) {
                    showStatusToast("takeoff blocked: aircraft already airborne")
                    return@setPositiveButton
                }
                sendArmState(true)
                lifecycleScope.launch {
                    delay(120)
                    sendSimpleAction(FlightEngineService.ACTION_TAKEOFF)
                }
            }
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                FlightSessionStore.state.collect { state ->
                    airborne = state.flyingState?.let { it != 0 } ?: false
                    updateTakeoffButtonUi()
                    updateConnectionPhaseUi(state)
                    appendBenchLogIfStateMessageChanged(state)

                    when (state.mavlinkPlayingState) {
                        0 -> missionExecutionState = MissionExecutionState.RUNNING
                        2 -> missionExecutionState = MissionExecutionState.PAUSED
                        1, 3 -> {
                            if (missionExecutionState != MissionExecutionState.IDLE) {
                                missionExecutionState = if (missionNodes.size >= 2) {
                                    MissionExecutionState.READY
                                } else {
                                    MissionExecutionState.IDLE
                                }
                            }
                        }
                    }

                    if (!state.engineRunning || !state.discoveryOk) {
                        if (missionExecutionState == MissionExecutionState.RUNNING || missionExecutionState == MissionExecutionState.PAUSED) {
                            missionExecutionState = if (missionNodes.size >= 2) {
                                MissionExecutionState.READY
                            } else {
                                MissionExecutionState.IDLE
                            }
                        }
                    } else if (!airborne && missionExecutionState == MissionExecutionState.RUNNING) {
                        missionExecutionState = MissionExecutionState.READY
                    }

                    handleMissionFailureIfNeeded(state)

                    if (state.engineRunning && state.discoveryOk) {
                        applyPendingFlightSettingsIfReady()
                        sendControllerGpsToPlaneIfReady()
                        startVideoReceiverIfPossible()
                    } else {
                        stopVideoReceiver()
                    }

                    updateAttitudeIndicator(state)
                    renderInfo(state)
                    updateSafetyBanner(state)
                    updateMissionControlUi()
                }
            }
        }
    }
    private fun updateConnectionPhaseUi(state: FlightState) {
        val phase = when {
            reconnectInProgress -> "RECONNECTING"
            state.engineRunning && state.discoveryOk -> "READY"
            state.engineRunning && !state.discoveryOk -> "DISCOVERY"
            else -> "OFFLINE"
        }
        tvNetworkPhase.text = "Link: $phase"
    }

    private fun appendBenchLogIfStateMessageChanged(state: FlightState) {
        val message = state.message.trim()
        if (message.isEmpty()) return
        if (message == lastObservedStateMessage) return
        lastObservedStateMessage = message
        val timestamp = String.format(
            Locale.US,
            "%1\$tH:%1\$tM:%1\$tS",
            System.currentTimeMillis(),
        )
        benchEventLog.addLast("$timestamp  $message")
        while (benchEventLog.size > 28) {
            benchEventLog.removeFirst()
        }
    }

    private fun appendCommandEvent(message: String) {
        val timestamp = String.format(
            Locale.US,
            "%1\$tH:%1\$tM:%1\$tS",
            System.currentTimeMillis(),
        )
        commandEventLog.addLast("$timestamp  $message")
        while (commandEventLog.size > COMMAND_LOG_MAX_LINES) {
            commandEventLog.removeFirst()
        }
    }

    private fun commandActionLabel(action: String): String? {
        return when (action) {
            FlightEngineService.ACTION_TAKEOFF -> "Takeoff command"
            FlightEngineService.ACTION_LAND -> "Land command"
            FlightEngineService.ACTION_RTH_START -> "RTH start"
            FlightEngineService.ACTION_RTH_STOP -> "RTH stop"
            FlightEngineService.ACTION_MAVLINK_PAUSE -> "Mission pause"
            FlightEngineService.ACTION_MAVLINK_STOP -> "Mission stop"
            FlightEngineService.ACTION_STOP -> "Engine stop"
            else -> null
        }
    }

    private fun showBenchChecklistDialog() {
        val state = FlightSessionStore.state.value
        val executionNodes = buildExecutionMissionNodes()
        val hasMissionPlan = executionNodes.size >= 2
        val strictPreflight = if (hasMissionPlan) runMissionPreflight(strictGeofence = true) else null
        val softPreflight = if (hasMissionPlan) runMissionPreflight(strictGeofence = false) else null
        val checklist = buildString {
            appendLine("Prop-off bench checklist")
            appendLine()
            appendLine("- ${if (state.engineRunning) "PASS" else "FAIL"} Engine running")
            appendLine("- ${if (state.discoveryOk) "PASS" else "FAIL"} Discovery ready")
            appendLine("- ${if (hasFreshTelemetry(state)) "PASS" else "FAIL"} Fresh telemetry")
            appendLine("- ${if (surfaceAvailable) "PASS" else "FAIL"} Video surface ready")
            appendLine("- ${if (FlightSessionStore.state.value.controlsArmed) "WARN" else "PASS"} Controls disarmed on bench")
            if (hasMissionPlan && softPreflight != null && strictPreflight != null) {
                appendLine("- PASS Mission has >=2 waypoints")
                appendLine("- ${if (softPreflight.ok) "PASS" else "FAIL"} Mission preflight (soft): ${softPreflight.message}")
                appendLine("- ${if (strictPreflight.ok) "PASS" else "WARN"} Mission preflight (strict): ${strictPreflight.message}")
            } else {
                appendLine("- INFO Mission checks: N/A (no mission loaded)")
            }
            appendLine()
            appendLine("Recent events")
            if (benchEventLog.isEmpty()) {
                appendLine("- (none)")
            } else {
                benchEventLog.forEach { line ->
                    appendLine("- $line")
                }
            }
            appendLine()
            appendLine("Command events")
            if (commandEventLog.isEmpty()) {
                appendLine("- (none)")
            } else {
                commandEventLog.toList().takeLast(14).forEach { line ->
                    appendLine("- $line")
                }
            }
            appendLine()
            appendLine("Safety alerts")
            if (safetyAlertLog.isEmpty()) {
                appendLine("- (none)")
            } else {
                safetyAlertLog.toList().takeLast(12).forEach { line ->
                    appendLine("- $line")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Bench Panel")
            .setMessage(checklist)
            .setNegativeButton("Close", null)
            .setPositiveButton("Refresh") { _, _ -> showBenchChecklistDialog() }
            .show()
    }

    private fun exportDiagnosticsBundle() {
        btnExportLogs.isEnabled = false
        btnExportLogs.text = "Exporting..."

        val snapshot = buildDiagnosticsSnapshotJson()
        val fileName = "discopilot_log_${
            String.format(
                Locale.US,
                "%1\$tY%1\$tm%1\$td_%1\$tH%1\$tM%1\$tS",
                Date(),
            )
        }.json"

        lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) {
                writeDiagnosticsFile(fileName, snapshot.toString(2))
            }

            btnExportLogs.isEnabled = true
            btnExportLogs.text = "Export Logs"

            if (location == null) {
                showStatusToast("export failed")
                return@launch
            }
            showStatusToast("logs exported")
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Logs Exported")
                .setMessage("Saved to:\n$location")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun buildDiagnosticsSnapshotJson(): JSONObject {
        val state = FlightSessionStore.state.value
        val config = readConfigFromInputs() ?: readConfigFromPrefs()
        val settings = readFlightSettingsFromInputs() ?: readFlightSettingsFromPrefs()
        val anchorNodes = missionNodes.toList()
        val executionNodes = buildExecutionMissionNodes()
        val hasMission = executionNodes.size >= 2
        val softPreflight = if (hasMission) runMissionPreflight(strictGeofence = false) else null
        val strictPreflight = if (hasMission) runMissionPreflight(strictGeofence = true) else null
        val telemetryAgeMs = telemetryAgeMs(state)

        return JSONObject().apply {
            put("timestamp_unix_ms", System.currentTimeMillis())
            put(
                "timestamp_local",
                String.format(
                    Locale.US,
                    "%1\$tF %1\$tT",
                    Date(),
                ),
            )

            put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("android_api", Build.VERSION.SDK_INT),
            )

            put(
                "network_config",
                JSONObject()
                    .put("disco_ip", config?.discoIp)
                    .put("discovery_port", config?.discoveryPort)
                    .put("c2d_port", config?.c2dPort)
                    .put("d2c_port", config?.d2cPort)
                    .put("stream_video_port", config?.streamVideoPort)
                    .put("stream_control_port", config?.streamControlPort),
            )

            put(
                "flight_settings",
                JSONObject()
                    .put("bench_lock_enabled", benchLockEnabled)
                    .put("max_altitude_m", settings.maxAltitudeMeters)
                    .put("min_altitude_m", settings.minAltitudeMeters)
                    .put("max_distance_m", settings.maxDistanceMeters)
                    .put("geofence_enabled", settings.geofenceEnabled)
                    .put("rth_min_altitude_m", settings.rthMinAltitudeMeters)
                    .put("rth_delay_sec", settings.rthDelaySeconds)
                    .put("loiter_radius_m", settings.loiterRadiusMeters)
                    .put("loiter_altitude_m", settings.loiterAltitudeMeters),
            )

            put(
                "live_state",
                JSONObject()
                    .put("engine_running", state.engineRunning)
                    .put("discovery_ok", state.discoveryOk)
                    .put("controls_armed", state.controlsArmed)
                    .put("tx_packets", state.txPackets)
                    .put("rx_packets", state.rxPackets)
                    .put("plane_battery_pct", state.planeBatteryPercent ?: modemPlaneBatteryPercent)
                    .put("plane_link_pct", state.planeLinkPercent)
                    .put("ground_speed_mps", state.groundSpeedMps)
                    .put("altitude_m", state.altitudeMeters)
                    .put("latitude", state.latitude)
                    .put("longitude", state.longitude)
                    .put("flying_state", state.flyingState)
                    .put("landing_state", state.landingState)
                    .put("mavlink_playing_state", state.mavlinkPlayingState)
                    .put("mavlink_file_path", state.mavlinkFilePath)
                    .put("mavlink_type", state.mavlinkType)
                    .put("mavlink_play_error", state.mavlinkPlayError)
                    .put("mission_item_executed_index", state.missionItemExecutedIndex)
                    .put("telemetry_age_ms", telemetryAgeMs)
                    .put("state_message", state.message)
                    .put("modem_signal_pct", modemSignalPercent)
                    .put("modem_zt", modemZt)
                    .put("modem_telemetry_unavailable", isModemTelemetryUnavailable())
                    .put("modem_telemetry_last_success_at_ms", modemTelemetryLastSuccessAtMs.takeIf { it > 0L })
                    .put("modem_telemetry_first_failure_at_ms", modemTelemetryFirstFailureAtMs.takeIf { it > 0L }),
            )

            put(
                "pilot_state",
                JSONObject()
                    .put("phone_latitude", pilotPhoneLatitude)
                    .put("phone_longitude", pilotPhoneLongitude)
                    .put("phone_altitude_m", pilotPhoneAltitudeMeters)
                    .put("phone_signal_pct", readPhoneSignalPercent())
                    .put("phone_battery_pct", readPhoneBatteryPercent())
                    .put("home_latitude", pilotHomeLatitude)
                    .put("home_longitude", pilotHomeLongitude),
            )

            put(
                "mission",
                JSONObject()
                    .put("visible", missionVisible)
                    .put("pattern", selectedMissionPattern().name)
                    .put("terminal_action", missionTerminalAction.name)
                    .put("preview_mode", missionPreviewMode.name)
                    .put("execution_state", missionExecutionState.name)
                    .put("dry_run_state", dryRunState.name)
                    .put("dry_run_progress_m", dryRunProgressMeters)
                    .put("dry_run_total_m", dryRunTotalMeters)
                    .put("anchor_nodes", missionNodesToJson(anchorNodes))
                    .put("execution_nodes", missionNodesToJson(executionNodes))
                    .put(
                        "pattern_settings",
                        JSONObject().apply {
                            val s = selectedMissionPatternSettings()
                            put("grid_width_m", s.gridWidthMeters)
                            put("grid_lane_count", s.gridLaneCount)
                            put("grid_start_side", s.gridStartSide.name)
                            put("corridor_width_m", s.corridorWidthMeters)
                            put("corridor_pass_mode", s.corridorPassMode.name)
                            put("orbit_radius_m", s.orbitRadiusMeters)
                            put("orbit_turns", s.orbitTurns)
                            put("orbit_direction", s.orbitDirection.name)
                        },
                    )
                    .put(
                        "preflight_soft",
                        softPreflight?.let { JSONObject().put("ok", it.ok).put("message", it.message) },
                    )
                    .put(
                        "preflight_strict",
                        strictPreflight?.let { JSONObject().put("ok", it.ok).put("message", it.message) },
                    ),
            )

            put(
                "bench_events",
                JSONArray().apply {
                    benchEventLog.forEach { put(it) }
                },
            )
            put(
                "command_events",
                JSONArray().apply {
                    commandEventLog.forEach { put(it) }
                },
            )
            put(
                "safety_alert_history",
                JSONArray().apply {
                    safetyAlertLog.forEach { put(it) }
                },
            )
        }
    }

    private fun missionNodesToJson(nodes: List<MissionNode>): JSONArray {
        return JSONArray().apply {
            nodes.forEachIndexed { index, node ->
                put(
                    JSONObject()
                        .put("index", index + 1)
                        .put("lat", node.latitude)
                        .put("lon", node.longitude)
                        .put("alt_m", node.altitudeMeters),
                )
            }
        }
    }

    private fun writeDiagnosticsFile(fileName: String, content: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DiscoPilot")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer?.write(content)
                    } ?: return null
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    "Downloads/DiscoPilot/$fileName"
                } catch (_: Exception) {
                    contentResolver.delete(uri, null, null)
                    null
                }
            } else {
                val dir = (getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir).resolve("logs")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, fileName)
                file.writeText(content, Charsets.UTF_8)
                file.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleMissionFailureIfNeeded(state: FlightState) {
        val error = state.mavlinkPlayError ?: return
        if (error == 0) return
        if (lastHandledMavlinkPlayError == error) return
        lastHandledMavlinkPlayError = error

        val wasMissionActive = missionExecutionState == MissionExecutionState.RUNNING || missionExecutionState == MissionExecutionState.PAUSED
        if (!wasMissionActive) return

        sendMavlinkStop()
        missionExecutionState = if (missionNodes.size >= 2) {
            MissionExecutionState.READY
        } else {
            MissionExecutionState.IDLE
        }
        activateManualOverride("Mission error $error. Manual control active.")
        updateMissionControlUi()
    }

    private fun renderInfo(state: FlightState) {
        val speed = state.groundSpeedMps?.let { String.format(Locale.US, "%.1fm/s", it) } ?: "--"
        val safeAltitudeMeters = state.altitudeMeters?.takeIf { it.isFinite() && it in -1000f..15000f }
        val elev = safeAltitudeMeters?.let { String.format(Locale.US, "%.1fm", it) } ?: "--"
        val distanceMeters = computeDistanceFromPilotMeters(state.latitude, state.longitude)
        val distance = formatDistance(distanceMeters)
        val planeLinkPercent = state.planeLinkPercent
        val planeBatteryPercent = state.planeBatteryPercent ?: modemPlaneBatteryPercent
        val landingMode = when (state.landingState) {
            0 -> "LIN"
            1 -> "SPR"
            else -> "--"
        }
        val ztText = when (modemZt) {
            "D" -> "D"
            "R" -> "R"
            else -> "-"
        }
        val phoneSignalPercent = readPhoneSignalPercent()
        val phoneConnected = readPhoneConnectivityOnline()
        val phoneBatteryPercent = readPhoneBatteryPercent()
        val telemetryAgeMs = telemetryAgeMs(state)
        val telemetryAgeText = when {
            telemetryAgeMs == null -> "--"
            telemetryAgeMs < 1_000L -> "<1s"
            else -> "${telemetryAgeMs / 1_000L}s"
        }
        val planeLink = planeLinkPercent?.let { "$it%" } ?: "--"
        val planeBattery = planeBatteryPercent?.let { "$it%" } ?: "--"
        val phoneSignal = phoneSignalPercent?.let { "$it%" } ?: "--"
        val phoneBattery = phoneBatteryPercent?.let { "$it%" } ?: "--"

        tvInfoSpeed.text = "SPD $speed"
        tvInfoAltitude.text = "ALT $elev"
        tvInfoDistance.text = "DST $distance"
        tvInfoZt.text = "ZT $ztText T$telemetryAgeText"
        tvInfoPlane.text = "PLN $planeLink/$planeBattery $landingMode"
        tvInfoPhone.text = "PHN $phoneSignal/$phoneBattery ${if (phoneConnected) "ON" else "OFF"}"

        applyHudFreshnessAndSignalStyling(
            telemetryAgeMs = telemetryAgeMs,
            planeBatteryPercent = planeBatteryPercent,
            planeLinkPercent = planeLinkPercent,
            phoneBatteryPercent = phoneBatteryPercent,
            phoneConnected = phoneConnected,
            ztMode = ztText,
        )
    }

    private fun telemetryAgeMs(state: FlightState): Long? {
        val at = state.lastTelemetryAtMs ?: return null
        return (System.currentTimeMillis() - at).coerceAtLeast(0L)
    }

    private fun applyHudFreshnessAndSignalStyling(
        telemetryAgeMs: Long?,
        planeBatteryPercent: Int?,
        planeLinkPercent: Int?,
        phoneBatteryPercent: Int?,
        phoneConnected: Boolean,
        ztMode: String,
    ) {
        val freshColor = Color.WHITE
        val warnColor = Color.parseColor("#FFF0B14A")
        val staleColor = Color.parseColor("#FFFF6B6B")
        val infoDimColor = Color.parseColor("#CCFFFFFF")

        val telemetryColor = when {
            telemetryAgeMs == null -> staleColor
            telemetryAgeMs > TELEMETRY_STALE_AGE_MS -> staleColor
            telemetryAgeMs > TELEMETRY_WARN_AGE_MS -> warnColor
            else -> freshColor
        }

        tvInfoSpeed.setTextColor(telemetryColor)
        tvInfoAltitude.setTextColor(telemetryColor)
        tvInfoDistance.setTextColor(telemetryColor)

        tvInfoZt.setTextColor(
            when {
                telemetryColor != freshColor -> telemetryColor
                ztMode == "D" -> Color.parseColor("#FF6BE08C")
                ztMode == "R" -> warnColor
                else -> infoDimColor
            },
        )

        val planeColor = when {
            planeBatteryPercent != null && planeBatteryPercent <= LOW_PLANE_BATTERY_CRITICAL_PCT -> staleColor
            planeBatteryPercent != null && planeBatteryPercent <= LOW_PLANE_BATTERY_WARN_PCT -> warnColor
            planeLinkPercent != null && planeLinkPercent <= 20 -> warnColor
            else -> freshColor
        }
        tvInfoPlane.setTextColor(planeColor)

        val phoneColor = when {
            !phoneConnected -> warnColor
            phoneBatteryPercent != null && phoneBatteryPercent <= LOW_PHONE_BATTERY_CRITICAL_PCT -> staleColor
            phoneBatteryPercent != null && phoneBatteryPercent <= LOW_PHONE_BATTERY_WARN_PCT -> warnColor
            else -> freshColor
        }
        tvInfoPhone.setTextColor(phoneColor)
    }

    private fun updateSafetyBanner(state: FlightState) {
        val banner = buildSafetyBanner(state)
        if (banner == null) {
            tvSafetyBanner.visibility = View.GONE
            lastSafetyBannerMessage = null
            return
        }

        tvSafetyBanner.visibility = View.VISIBLE
        tvSafetyBanner.text = banner.message
        tvSafetyBanner.setBackgroundColor(
            when (banner.severity) {
                SafetySeverity.INFO -> Color.parseColor("#CC1E3A5F")
                SafetySeverity.WARN -> Color.parseColor("#CC7A4C00")
                SafetySeverity.CRITICAL -> Color.parseColor("#CC7F1D1D")
            },
        )

        if (lastSafetyBannerMessage != banner.message) {
            appendSafetyAlertLog(banner.message)
            lastSafetyBannerMessage = banner.message
        }
    }

    private fun buildSafetyBanner(state: FlightState): SafetyBanner? {
        if (reconnectInProgress) {
            return SafetyBanner(SafetySeverity.INFO, "Reconnecting link session...")
        }
        if (!state.engineRunning) return null
        if (!state.discoveryOk) {
            return SafetyBanner(SafetySeverity.CRITICAL, "Discovery offline: control link unavailable")
        }

        val telemetryAgeMs = telemetryAgeMs(state)
        if (telemetryAgeMs == null || telemetryAgeMs > TELEMETRY_STALE_AGE_MS) {
            val ageText = if (telemetryAgeMs == null) "n/a" else "${telemetryAgeMs / 1_000L}s"
            return SafetyBanner(SafetySeverity.CRITICAL, "Telemetry stale ($ageText). Commands may be blocked.")
        }
        if (telemetryAgeMs > TELEMETRY_WARN_AGE_MS) {
            return SafetyBanner(SafetySeverity.WARN, "Telemetry delay ${telemetryAgeMs / 1_000L}s")
        }

        val missionError = state.mavlinkPlayError ?: 0
        if (missionError != 0) {
            return SafetyBanner(SafetySeverity.CRITICAL, "Mission error code $missionError")
        }

        val planeBattery = state.planeBatteryPercent ?: modemPlaneBatteryPercent
        if (planeBattery != null) {
            if (planeBattery <= LOW_PLANE_BATTERY_CRITICAL_PCT) {
                return SafetyBanner(SafetySeverity.CRITICAL, "Plane battery critical: $planeBattery%")
            }
            if (planeBattery <= LOW_PLANE_BATTERY_WARN_PCT) {
                return SafetyBanner(SafetySeverity.WARN, "Plane battery low: $planeBattery%")
            }
        }

        val phoneBattery = readPhoneBatteryPercent()
        if (phoneBattery != null) {
            if (phoneBattery <= LOW_PHONE_BATTERY_CRITICAL_PCT) {
                return SafetyBanner(SafetySeverity.CRITICAL, "Phone battery critical: $phoneBattery%")
            }
            if (phoneBattery <= LOW_PHONE_BATTERY_WARN_PCT) {
                return SafetyBanner(SafetySeverity.WARN, "Phone battery low: $phoneBattery%")
            }
        }

        val link = state.planeLinkPercent
        if (airborne && link != null && link <= 20) {
            return SafetyBanner(SafetySeverity.WARN, "Plane link weak: $link%")
        }

        if (isModemTelemetryUnavailable()) {
            return SafetyBanner(SafetySeverity.INFO, "Modem telemetry unavailable. Core flight/video still works.")
        }

        if (benchLockEnabled && !airborne) {
            return SafetyBanner(SafetySeverity.INFO, "Bench lock ON: takeoff blocked")
        }

        return null
    }

    private fun appendSafetyAlertLog(message: String) {
        val timestamp = String.format(
            Locale.US,
            "%1\$tH:%1\$tM:%1\$tS",
            System.currentTimeMillis(),
        )
        safetyAlertLog.addLast("$timestamp  $message")
        while (safetyAlertLog.size > ALERT_LOG_MAX_LINES) {
            safetyAlertLog.removeFirst()
        }
    }

    private fun startModemTelemetryPolling() {
        if (modemTelemetryJob?.isActive == true) return
        modemTelemetryJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                pollModemTelemetryOnce()
                delay(MODEM_TELEMETRY_POLL_MS)
            }
        }
    }

    private fun stopModemTelemetryPolling() {
        modemTelemetryJob?.cancel()
        modemTelemetryJob = null
    }

    private fun isModemTelemetryUnavailable(nowMs: Long = System.currentTimeMillis()): Boolean {
        val state = FlightSessionStore.state.value
        if (!state.engineRunning || !state.discoveryOk) return false
        val firstFailure = modemTelemetryFirstFailureAtMs
        if (firstFailure <= 0L) return false
        return (nowMs - firstFailure) >= MODEM_TELEMETRY_UNAVAILABLE_GRACE_MS
    }

    private fun markModemTelemetrySuccess(nowMs: Long = System.currentTimeMillis()) {
        modemTelemetryLastSuccessAtMs = nowMs
        modemTelemetryFirstFailureAtMs = 0L
    }

    private fun markModemTelemetryFailure(nowMs: Long = System.currentTimeMillis()) {
        if (modemTelemetryFirstFailureAtMs <= 0L) {
            modemTelemetryFirstFailureAtMs = nowMs
        }
    }

    private suspend fun pollModemTelemetryOnce() {
        val host = withContext(Dispatchers.Main) {
            etDiscoIp.text?.toString()?.trim().orEmpty()
        }
        if (host.isBlank()) return

        val connection = (URL("http://$host:$MODEM_TELEMETRY_PORT$MODEM_TELEMETRY_PATH").openConnection() as? HttpURLConnection)
            ?: return

        try {
            connection.connectTimeout = MODEM_TELEMETRY_TIMEOUT_MS
            connection.readTimeout = MODEM_TELEMETRY_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.useCaches = false

            val code = connection.responseCode
            if (code !in 200..299) {
                markModemTelemetryFailure()
                return
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            modemSignalPercent = jsonOptPercent(json, "modem_signal_pct")
            modemPlaneBatteryPercent = jsonOptPercent(json, "plane_battery_pct")
            val zt = json.optString("zt", "").trim().uppercase()
            modemZt = when (zt) {
                "D", "R" -> zt
                else -> null
            }
            markModemTelemetrySuccess()

            withContext(Dispatchers.Main) {
                renderInfo(FlightSessionStore.state.value)
            }
        } catch (_: Exception) {
            markModemTelemetryFailure()
            // Keep the previous values when endpoint is temporarily unavailable.
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonOptPercent(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optInt(key, -1)
        return value.takeIf { it in 0..100 }
    }

    private fun readPhoneBatteryPercent(): Int? {
        val batteryManager = getSystemService(BATTERY_SERVICE) as? BatteryManager ?: return null
        val value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return value.takeIf { it in 0..100 }
    }

    private fun readPhoneSignalPercent(): Int? {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

        val activeNetwork = connectivity.activeNetwork
        if (activeNetwork != null) {
            val activeCapabilities = connectivity.getNetworkCapabilities(activeNetwork)
            if (activeCapabilities != null) {
                wifiSignalPercentFrom(activeCapabilities)?.let { return it }
                cellularSignalPercentFrom(activeCapabilities)?.let { return it }
            }
        }

        for (network in connectivity.allNetworks) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
            wifiSignalPercentFrom(capabilities)?.let { return it }
            cellularSignalPercentFrom(capabilities)?.let { return it }
        }

        return null
    }

    private fun readPhoneConnectivityOnline(): Boolean {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun wifiSignalPercentFrom(capabilities: NetworkCapabilities): Int? {
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val rssi = wifiManager.connectionInfo?.rssi ?: return null
        if (rssi <= -127) return null
        val normalized = (((rssi + 100f) / 55f) * 100f).toInt()
        return normalized.coerceIn(0, 100)
    }

    private fun cellularSignalPercentFrom(capabilities: NetworkCapabilities): Int? {
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val telephony = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return try {
            val level = telephony.signalStrength?.level ?: return null
            (level * 25).coerceIn(0, 100)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun updateAttitudeIndicator(state: FlightState) {
        val containerH = attitudeIndicatorContainer.height
        val lineH = attitudeHorizonLine.height
        if (containerH <= 0 || lineH <= 0) return

        val rollRad = state.attitudeRollRad ?: 0f
        val pitchRad = state.attitudePitchRad ?: 0f

        val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat().coerceIn(-50f, 50f)
        val maxOffsetY = ((containerH - lineH) / 2f) - 6f
        val pitchNorm = (pitchRad / 0.35f).coerceIn(-1f, 1f)

        // Conventional horizon: nose up => horizon moves down.
        attitudeHorizonLine.rotation = rollDeg
        attitudeHorizonLine.translationY = pitchNorm * maxOffsetY
    }

    private fun autoStartEngine() {
        queueFlightSettingsApply(readFlightSettingsFromInputs() ?: readFlightSettingsFromPrefs())
        val state = FlightSessionStore.state.value
        if (state.engineRunning) {
            sendSimpleAction(FlightEngineService.ACTION_VIDEO_ON)
            applyPendingFlightSettingsIfReady()
            return
        }

        val config = readConfigFromInputs() ?: run {
            showStatusToast("missing config")
            return
        }
        resetPilotHomeReference()
        saveConfig(config)
        startForegroundService(config)
        sendArmState(false)
        sendSimpleAction(FlightEngineService.ACTION_VIDEO_ON)
    }

    private fun reconnectEngineSession() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = lifecycleScope.launch {
            setReconnectUi(true)
            try {
                showStatusToast("Reconnecting engine...")
                val wasRunning = FlightSessionStore.state.value.engineRunning
                if (wasRunning) {
                    autoStopEngine()
                    delay(450)
                }
                autoStartEngine()
            } finally {
                setReconnectUi(false)
            }
        }
    }

    private fun setReconnectUi(inProgress: Boolean) {
        reconnectInProgress = inProgress
        btnReconnect.isEnabled = !inProgress
        btnReconnect.text = if (inProgress) "Reconnecting..." else "Reconnect"
        updateConnectionPhaseUi(FlightSessionStore.state.value)
    }

    private fun autoStopEngine() {
        centerSticks()
        sendSticks(force = true)
        sendSimpleAction(FlightEngineService.ACTION_VIDEO_OFF)
        sendSimpleAction(FlightEngineService.ACTION_STOP)
        resetPilotHomeReference()
    }

    private fun onFirstVideoFrameDecoded() {
        waitingForFirstVideoFrame = false
        videoBootstrapJob?.cancel()
        videoBootstrapJob = null
    }

    private fun startVideoBootstrapRecovery() {
        videoBootstrapJob?.cancel()
        waitingForFirstVideoFrame = true
        videoBootstrapJob = lifecycleScope.launch {
            delay(VIDEO_BOOTSTRAP_INITIAL_DELAY_MS)
            var retryCount = 0
            while (waitingForFirstVideoFrame && retryCount < VIDEO_BOOTSTRAP_MAX_RETRIES) {
                sendSimpleAction(FlightEngineService.ACTION_VIDEO_ON)
                retryCount += 1
                delay(VIDEO_BOOTSTRAP_RETRY_DELAY_MS)
            }
        }
    }

    private fun startVideoReceiverIfPossible() {
        if (videoReceiver.isRunning()) return
        val surface = previewSurface
        if (!surfaceAvailable || surface == null || !surface.isValid) return
        val videoPort = etStreamVideoPort.text.toString().toIntOrNull() ?: 55004
        videoReceiver.start(surface, videoPort)
        startVideoBootstrapRecovery()
    }

    private fun stopVideoReceiver() {
        videoBootstrapJob?.cancel()
        videoBootstrapJob = null
        waitingForFirstVideoFrame = false
        if (videoReceiver.isRunning()) {
            videoReceiver.stop()
        }
    }

    private fun readConfigFromInputs(): FlightConfig? {
        val ip = etDiscoIp.text.toString().trim()
        if (ip.isEmpty()) return null

        val discoveryPort = etDiscoveryPort.text.toString().toIntOrNull() ?: 44444
        val c2dPort = etC2dPort.text.toString().toIntOrNull() ?: 54321
        val d2cPort = etD2cPort.text.toString().toIntOrNull() ?: 9988
        val streamVideoPort = etStreamVideoPort.text.toString().toIntOrNull() ?: 55004
        val streamControlPort = etStreamControlPort.text.toString().toIntOrNull() ?: 55005

        return FlightConfig(
            discoIp = ip,
            discoveryPort = discoveryPort,
            c2dPort = c2dPort,
            d2cPort = d2cPort,
            streamVideoPort = streamVideoPort,
            streamControlPort = streamControlPort,
        )
    }

    private fun readConfigFromPrefs(): FlightConfig? {
        val ip = prefs.getString(PREF_KEY_DISCO_IP, null)?.trim().orEmpty()
        if (ip.isEmpty()) return null
        return FlightConfig(
            discoIp = ip,
            discoveryPort = prefs.getInt(PREF_KEY_DISCOVERY_PORT, 44444),
            c2dPort = prefs.getInt(PREF_KEY_C2D_PORT, 54321),
            d2cPort = prefs.getInt(PREF_KEY_D2C_PORT, 9988),
            streamVideoPort = prefs.getInt(PREF_KEY_STREAM_VIDEO_PORT, 55004),
            streamControlPort = prefs.getInt(PREF_KEY_STREAM_CONTROL_PORT, 55005),
        )
    }

    private fun readFlightSettingsFromInputs(): FlightSettings? {
        val maxAltitude = etMaxAltitude.text.toString().toFloatOrNull() ?: return null
        val minAltitude = etMinAltitude.text.toString().toFloatOrNull() ?: return null
        val maxDistance = etMaxDistance.text.toString().toFloatOrNull() ?: return null
        val rthMinAltitude = etRthMinAltitude.text.toString().toFloatOrNull() ?: return null
        val rthDelay = etRthDelay.text.toString().toIntOrNull() ?: return null
        val loiterRadius = etLoiterRadius.text.toString().toIntOrNull() ?: return null
        val loiterAltitude = etLoiterAltitude.text.toString().toFloatOrNull() ?: return null
        val geofenceEnabled = swGeofence.isChecked

        if (!maxAltitude.isFinite() || !minAltitude.isFinite() || !maxDistance.isFinite() || !rthMinAltitude.isFinite() || !loiterAltitude.isFinite()) {
            return null
        }
        if (maxAltitude <= 0f || minAltitude < 0f || minAltitude > maxAltitude) {
            return null
        }
        if (maxDistance < 0f || rthMinAltitude < 0f || loiterAltitude < 0f || rthDelay < 0) {
            return null
        }
        if (geofenceEnabled && maxDistance <= 0f) {
            return null
        }
        if (loiterRadius <= 0) {
            return null
        }

        return FlightSettings(
            maxAltitudeMeters = maxAltitude,
            minAltitudeMeters = minAltitude,
            maxDistanceMeters = maxDistance,
            geofenceEnabled = geofenceEnabled,
            rthMinAltitudeMeters = rthMinAltitude,
            rthDelaySeconds = rthDelay,
            loiterRadiusMeters = loiterRadius,
            loiterAltitudeMeters = loiterAltitude,
        )
    }

    private fun readFlightSettingsFromPrefs(): FlightSettings {
        return FlightSettings(
            maxAltitudeMeters = prefs.getFloat(PREF_KEY_MAX_ALTITUDE_M, DEFAULT_MAX_ALTITUDE_M),
            minAltitudeMeters = prefs.getFloat(PREF_KEY_MIN_ALTITUDE_M, DEFAULT_MIN_ALTITUDE_M),
            maxDistanceMeters = prefs.getFloat(PREF_KEY_MAX_DISTANCE_M, DEFAULT_MAX_DISTANCE_M),
            geofenceEnabled = prefs.getBoolean(PREF_KEY_GEOFENCE_ENABLED, DEFAULT_GEOFENCE_ENABLED),
            rthMinAltitudeMeters = prefs.getFloat(PREF_KEY_RTH_MIN_ALTITUDE_M, DEFAULT_RTH_MIN_ALTITUDE_M),
            rthDelaySeconds = prefs.getInt(PREF_KEY_RTH_DELAY_SEC, DEFAULT_RTH_DELAY_SEC),
            loiterRadiusMeters = prefs.getInt(PREF_KEY_LOITER_RADIUS_M, DEFAULT_LOITER_RADIUS_M),
            loiterAltitudeMeters = prefs.getFloat(PREF_KEY_LOITER_ALTITUDE_M, DEFAULT_LOITER_ALTITUDE_M),
        )
    }

    private fun saveFlightSettings(settings: FlightSettings) {
        prefs.edit()
            .putFloat(PREF_KEY_MAX_ALTITUDE_M, settings.maxAltitudeMeters)
            .putFloat(PREF_KEY_MIN_ALTITUDE_M, settings.minAltitudeMeters)
            .putFloat(PREF_KEY_MAX_DISTANCE_M, settings.maxDistanceMeters)
            .putBoolean(PREF_KEY_GEOFENCE_ENABLED, settings.geofenceEnabled)
            .putFloat(PREF_KEY_RTH_MIN_ALTITUDE_M, settings.rthMinAltitudeMeters)
            .putInt(PREF_KEY_RTH_DELAY_SEC, settings.rthDelaySeconds)
            .putInt(PREF_KEY_LOITER_RADIUS_M, settings.loiterRadiusMeters)
            .putFloat(PREF_KEY_LOITER_ALTITUDE_M, settings.loiterAltitudeMeters)
            .apply()
    }

    private fun restoreFlightSettings() {
        val settings = readFlightSettingsFromPrefs()
        etMaxAltitude.setText(formatInputFloat(settings.maxAltitudeMeters))
        etMinAltitude.setText(formatInputFloat(settings.minAltitudeMeters))
        etMaxDistance.setText(formatInputFloat(settings.maxDistanceMeters))
        swGeofence.isChecked = settings.geofenceEnabled
        etRthMinAltitude.setText(formatInputFloat(settings.rthMinAltitudeMeters))
        etRthDelay.setText(settings.rthDelaySeconds.toString())
        etLoiterRadius.setText(settings.loiterRadiusMeters.toString())
        etLoiterAltitude.setText(formatInputFloat(settings.loiterAltitudeMeters))
    }

    private fun formatInputFloat(value: Float): String {
        val rounded = value.toInt().toFloat()
        return if (rounded == value) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun queueFlightSettingsApply(settings: FlightSettings?) {
        if (settings == null) return
        pendingFlightSettings = settings
    }

    private fun applyPendingFlightSettingsIfReady() {
        val settings = pendingFlightSettings ?: return
        val state = FlightSessionStore.state.value
        if (!state.engineRunning || !state.discoveryOk) return
        sendFlightSettings(settings)
        pendingFlightSettings = null
    }

    private fun sendFlightSettings(settings: FlightSettings) {
        startService(
            Intent(this, FlightEngineService::class.java)
                .setAction(FlightEngineService.ACTION_APPLY_SETTINGS)
                .putExtra(FlightEngineService.EXTRA_MAX_ALTITUDE_METERS, settings.maxAltitudeMeters)
                .putExtra(FlightEngineService.EXTRA_MIN_ALTITUDE_METERS, settings.minAltitudeMeters)
                .putExtra(FlightEngineService.EXTRA_MAX_DISTANCE_METERS, settings.maxDistanceMeters)
                .putExtra(FlightEngineService.EXTRA_GEOFENCE_ENABLED, settings.geofenceEnabled)
                .putExtra(FlightEngineService.EXTRA_RTH_MIN_ALTITUDE_METERS, settings.rthMinAltitudeMeters)
                .putExtra(FlightEngineService.EXTRA_RTH_DELAY_SECONDS, settings.rthDelaySeconds)
                .putExtra(FlightEngineService.EXTRA_LOITER_RADIUS_METERS, settings.loiterRadiusMeters)
                .putExtra(FlightEngineService.EXTRA_LOITER_ALTITUDE_METERS, settings.loiterAltitudeMeters),
        )
    }

    private fun saveConfig(config: FlightConfig) {
        prefs.edit()
            .putString(PREF_KEY_DISCO_IP, config.discoIp)
            .putInt(PREF_KEY_DISCOVERY_PORT, config.discoveryPort)
            .putInt(PREF_KEY_C2D_PORT, config.c2dPort)
            .putInt(PREF_KEY_D2C_PORT, config.d2cPort)
            .putInt(PREF_KEY_STREAM_VIDEO_PORT, config.streamVideoPort)
            .putInt(PREF_KEY_STREAM_CONTROL_PORT, config.streamControlPort)
            .apply()
    }

    private fun restoreConfig() {
        etDiscoIp.setText(prefs.getString(PREF_KEY_DISCO_IP, "10.147.0.10"))
        etDiscoveryPort.setText(prefs.getInt(PREF_KEY_DISCOVERY_PORT, 44444).toString())
        etC2dPort.setText(prefs.getInt(PREF_KEY_C2D_PORT, 54321).toString())
        etD2cPort.setText(prefs.getInt(PREF_KEY_D2C_PORT, 9988).toString())
        etStreamVideoPort.setText(prefs.getInt(PREF_KEY_STREAM_VIDEO_PORT, 55004).toString())
        etStreamControlPort.setText(prefs.getInt(PREF_KEY_STREAM_CONTROL_PORT, 55005).toString())
        configVisible = prefs.getBoolean(PREF_KEY_CONFIG_VISIBLE, false)
        applyConfigPanelUi()
    }

    private fun saveConfigVisible() {
        prefs.edit()
            .putBoolean(PREF_KEY_CONFIG_VISIBLE, configVisible)
            .apply()
    }

    private fun applyConfigPanelUi() {
        val visible = configVisible && !missionVisible
        panelConfig.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            panelConfig.bringToFront()
            panelConfig.translationZ = 100f
        } else {
            panelConfig.translationZ = 0f
        }
    }

    private fun applyMissionPanelUi() {
        panelMission.visibility = if (missionVisible) View.VISIBLE else View.GONE
        if (missionVisible) {
            panelMission.bringToFront()
            panelMission.translationZ = 120f
            ensureStartWaypointIfKnown()
            refreshMissionMapOverlays()
            refreshMissionSummary()
            applyImmersiveMode()
        } else {
            panelMission.translationZ = 0f
            if (dryRunState == DryRunState.RUNNING) {
                dryRunState = DryRunState.PAUSED
                dryRunJob?.cancel()
                dryRunJob = null
                updateDryRunUi()
            }
        }
        updateMissionControlUi()
    }

    private fun updateMissionControlUi() {
        val state = FlightSessionStore.state.value
        updateMissionStatusBadges(state)

        val hasMission = missionNodes.size >= 2
        if (!hasMission) {
            missionExecutionState = MissionExecutionState.IDLE
        }
        val showBar = !missionVisible && hasMission && missionExecutionState != MissionExecutionState.IDLE
        missionControlBar.visibility = if (showBar) View.VISIBLE else View.GONE
        if (!showBar) return

        val connected = state.engineRunning && state.discoveryOk
        val statusTail = when {
            !connected -> " (offline)"
            !airborne -> " (ground)"
            else -> ""
        }

        when (missionExecutionState) {
            MissionExecutionState.READY -> {
                tvMissionControlStatus.text = "Mission Ready$statusTail"
                btnMissionCtrlStart.isEnabled = true
                btnMissionCtrlPauseResume.isEnabled = false
                btnMissionCtrlPauseResume.text = "Pause"
                btnMissionCtrlAbort.isEnabled = true
            }

            MissionExecutionState.RUNNING -> {
                tvMissionControlStatus.text = "Mission Running$statusTail"
                btnMissionCtrlStart.isEnabled = false
                btnMissionCtrlPauseResume.isEnabled = true
                btnMissionCtrlPauseResume.text = "Pause"
                btnMissionCtrlAbort.isEnabled = true
            }

            MissionExecutionState.PAUSED -> {
                tvMissionControlStatus.text = "Mission Paused$statusTail"
                btnMissionCtrlStart.isEnabled = false
                btnMissionCtrlPauseResume.isEnabled = true
                btnMissionCtrlPauseResume.text = "Resume"
                btnMissionCtrlAbort.isEnabled = true
            }

            MissionExecutionState.IDLE -> {
                tvMissionControlStatus.text = "Mission Idle"
                btnMissionCtrlStart.isEnabled = false
                btnMissionCtrlPauseResume.isEnabled = false
                btnMissionCtrlAbort.isEnabled = false
            }
        }
    }

    private fun updateMissionStatusBadges(state: FlightState) {
        val badgeTextColor = Color.WHITE

        val (mavText, mavColor) = when (state.mavlinkPlayingState) {
            0 -> "PLAY" to Color.parseColor("#FF2E7D32")
            2 -> "PAUSE" to Color.parseColor("#FF9C6B00")
            1 -> "STOP" to Color.parseColor("#FF616161")
            3 -> "LOAD" to Color.parseColor("#FF1E5EA8")
            else -> "--" to Color.parseColor("#FF424242")
        }
        tvMissionBadgeMav.text = mavText
        tvMissionBadgeMav.setTextColor(badgeTextColor)
        tvMissionBadgeMav.setBackgroundColor(mavColor)

        val (landingText, landingColor) = when (state.landingState) {
            0 -> "LIN" to Color.parseColor("#FF455A64")
            1 -> "SPR" to Color.parseColor("#FF00695C")
            else -> "--" to Color.parseColor("#FF424242")
        }
        tvMissionBadgeLanding.text = landingText
        tvMissionBadgeLanding.setTextColor(badgeTextColor)
        tvMissionBadgeLanding.setBackgroundColor(landingColor)

        val wpText = state.missionItemExecutedIndex?.let { "WP${it + 1}" } ?: "WP-"
        val wpColor = if (state.missionItemExecutedIndex != null) {
            Color.parseColor("#FF5D4037")
        } else {
            Color.parseColor("#FF424242")
        }
        tvMissionBadgeWp.text = wpText
        tvMissionBadgeWp.setTextColor(badgeTextColor)
        tvMissionBadgeWp.setBackgroundColor(wpColor)
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun startForegroundService(config: FlightConfig) {
        appendCommandEvent("Engine start ip=${config.discoIp}")
        val intent = Intent(this, FlightEngineService::class.java)
            .setAction(FlightEngineService.ACTION_START)
            .putExtra(FlightEngineService.EXTRA_DISCO_IP, config.discoIp)
            .putExtra(FlightEngineService.EXTRA_DISCOVERY_PORT, config.discoveryPort)
            .putExtra(FlightEngineService.EXTRA_C2D_PORT, config.c2dPort)
            .putExtra(FlightEngineService.EXTRA_D2C_PORT, config.d2cPort)
            .putExtra(FlightEngineService.EXTRA_STREAM_VIDEO_PORT, config.streamVideoPort)
            .putExtra(FlightEngineService.EXTRA_STREAM_CONTROL_PORT, config.streamControlPort)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendSimpleAction(action: String) {
        commandActionLabel(action)?.let { appendCommandEvent(it) }
        startService(Intent(this, FlightEngineService::class.java).setAction(action))
    }

    private fun sendControllerGps(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        horizontalAccuracy: Double,
        verticalAccuracy: Double,
    ) {
        startService(
            Intent(this, FlightEngineService::class.java)
                .setAction(FlightEngineService.ACTION_SEND_CONTROLLER_GPS)
                .putExtra(FlightEngineService.EXTRA_CONTROLLER_LATITUDE, latitude)
                .putExtra(FlightEngineService.EXTRA_CONTROLLER_LONGITUDE, longitude)
                .putExtra(FlightEngineService.EXTRA_CONTROLLER_ALTITUDE, altitude)
                .putExtra(FlightEngineService.EXTRA_CONTROLLER_HORIZONTAL_ACCURACY, horizontalAccuracy)
                .putExtra(FlightEngineService.EXTRA_CONTROLLER_VERTICAL_ACCURACY, verticalAccuracy),
        )
    }

    private fun sendArmState(armed: Boolean) {
        appendCommandEvent(if (armed) "Arm ON" else "Arm OFF")
        startService(
            Intent(this, FlightEngineService::class.java)
                .setAction(FlightEngineService.ACTION_SET_ARM)
                .putExtra(FlightEngineService.EXTRA_ARMED, armed),
        )
    }

    private fun sendSticks(force: Boolean = false) {
        if (!force && !FlightSessionStore.state.value.controlsArmed) return

        startService(
            Intent(this, FlightEngineService::class.java)
                .setAction(FlightEngineService.ACTION_SET_STICKS)
                .putExtra(FlightEngineService.EXTRA_PITCH, pitchAxis)
                .putExtra(FlightEngineService.EXTRA_ROLL, rollAxis)
                .putExtra(FlightEngineService.EXTRA_YAW, yawAxis)
                .putExtra(FlightEngineService.EXTRA_THROTTLE, throttleAxis),
        )
    }

    private fun centerSticks() {
        pitchAxis = 0
        rollAxis = 0
        yawAxis = 0
        throttleAxis = 0
        stickLeft.setCentered(notify = false)
        stickRight.setCentered(notify = false)
    }

    private fun computeDistanceFromPilotMeters(latitude: Double?, longitude: Double?): Double? {
        if (latitude == null || longitude == null) return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude == 0.0 && longitude == 0.0) return null

        val phoneLat = pilotPhoneLatitude
        val phoneLon = pilotPhoneLongitude
        if (phoneLat != null && phoneLon != null && phoneLat.isFinite() && phoneLon.isFinite()) {
            return haversineMeters(phoneLat, phoneLon, latitude, longitude)
        }

        val homeLat = pilotHomeLatitude
        val homeLon = pilotHomeLongitude
        if (homeLat == null || homeLon == null) {
            pilotHomeLatitude = latitude
            pilotHomeLongitude = longitude
            return 0.0
        }

        return haversineMeters(homeLat, homeLon, latitude, longitude)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2.0) +
            cos(startLat) * cos(endLat) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadiusMeters * c
    }

    private fun formatDistance(distanceMeters: Double?): String {
        if (distanceMeters == null) return "--"
        if (distanceMeters >= 1000.0) {
            return String.format(Locale.US, "%.2fkm", distanceMeters / 1000.0)
        }
        return String.format(Locale.US, "%.0fm", distanceMeters)
    }

    private fun resetPilotHomeReference() {
        pilotHomeLatitude = null
        pilotHomeLongitude = null
    }

    private fun showStatusToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun renderGeofenceUi() {
        val enabled = swGeofence.isChecked
        etMaxDistance.alpha = if (enabled) 1f else 0.65f
        tvGeofenceHelp.text = if (enabled) {
            "When ON, plane should not fly beyond Max Distance."
        } else {
            "When OFF, Max Distance is ignored (distance fence disabled)."
        }
    }

    private fun updateTakeoffButtonUi() {
        if (!::btnTakeoffLand.isInitialized) return
        if (airborne) {
            btnTakeoffLand.text = "Land"
            btnTakeoffLand.alpha = 1f
            return
        }
        if (benchLockEnabled) {
            btnTakeoffLand.text = "Takeoff (Locked)"
            btnTakeoffLand.alpha = 0.92f
        } else {
            btnTakeoffLand.text = "Takeoff"
            btnTakeoffLand.alpha = 1f
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) return
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun startLocationUpdatesIfPermitted() {
        if (!hasLocationPermission()) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2_000L,
                    1f,
                    locationListener,
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2_000L,
                    1f,
                    locationListener,
                )
            }
            captureLastKnownPilotLocation()
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
    }

    private fun captureLastKnownPilotLocation() {
        if (!hasLocationPermission()) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val candidates = providers.mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }
        val best = candidates.maxByOrNull { it.time } ?: return
        onPilotLocationChanged(best)
    }

    private fun onPilotLocationChanged(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        if (!lat.isFinite() || !lon.isFinite()) return

        pilotPhoneLatitude = lat
        pilotPhoneLongitude = lon
        pilotPhoneAltitudeMeters = if (location.hasAltitude()) location.altitude else 0.0
        pilotPhoneHorizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.coerceAtLeast(1f) else 8f
        pilotPhoneVerticalAccuracyMeters =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters.coerceAtLeast(1f)
            } else {
                12f
            }

        sendControllerGpsToPlaneIfReady()
        if (missionNodes.isEmpty()) {
            ensureStartWaypointIfKnown()
            refreshMissionMapOverlays()
            refreshMissionSummary()
        }
    }

    private fun sendControllerGpsToPlaneIfReady(force: Boolean = false) {
        val lat = pilotPhoneLatitude ?: return
        val lon = pilotPhoneLongitude ?: return
        val state = FlightSessionStore.state.value
        if (!state.engineRunning || !state.discoveryOk) return

        val now = System.currentTimeMillis()
        if (!force && now - lastControllerGpsSentAtMs < CONTROLLER_GPS_MIN_SEND_INTERVAL_MS) return
        lastControllerGpsSentAtMs = now

        sendControllerGps(
            latitude = lat,
            longitude = lon,
            altitude = pilotPhoneAltitudeMeters,
            horizontalAccuracy = pilotPhoneHorizontalAccuracyMeters.toDouble(),
            verticalAccuracy = pilotPhoneVerticalAccuracyMeters.toDouble(),
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        stopModemTelemetryPolling()
        stopLocationUpdates()
        missionUploadJob?.cancel()
        dryRunJob?.cancel()
        reconnectJob?.cancel()
        videoBootstrapJob?.cancel()
        videoBootstrapJob = null
        waitingForFirstVideoFrame = false
        setReconnectUi(false)
        if (isFinishing && !isChangingConfigurations) {
            autoStopEngine()
        }
        saveMissionDraft()
        saveMissionViewport()
        resetPilotHomeReference()
        if (::videoReceiver.isInitialized) {
            videoReceiver.destroy()
        }
        if (::mapMission.isInitialized) {
            mapMission.onDetach()
        }
        previewSurface?.release()
        previewSurface = null
        super.onDestroy()
    }
}
