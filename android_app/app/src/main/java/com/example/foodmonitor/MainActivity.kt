package com.example.foodmonitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import org.json.JSONObject

/** Mirrors the web dashboard palette — warm gray + #edb100 accent. */
data class RipePalette(
    val bg: Color,
    val panel: Color,
    val panelHi: Color,
    val sunken: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val fresh: Color,
    val spoiled: Color,
    val warn: Color,
    val accentDeep: Color,
    val tintFresh: Color,
    val tintWarn: Color,
    val tintBad: Color,
    val track: Color,
    val displayBg: Color,
    val displayInk: Color,
    val displayMuted: Color,
    val chartLine: Color
)

val LightPalette = RipePalette(
    bg = Color(0xFFE8E4DB),
    panel = Color(0xFFF5F2EB),
    panelHi = Color(0xFFFCFBF7),
    sunken = Color(0xFFDDD7CA),
    ink = Color(0xFF23211D),
    ink2 = Color(0xFF4B4740),
    muted = Color(0xFF837C6F),
    fresh = Color(0xFF4C7A4D),
    spoiled = Color(0xFFB23A25),
    warn = Color(0xFFEDB100),
    accentDeep = Color(0xFF8A6400),
    tintFresh = Color(0xFFE6ECE1),
    tintWarn = Color(0xFFF7ECD2),
    tintBad = Color(0xFFF1DED7),
    track = Color(0xFF3A362F),
    displayBg = Color(0xFF23211D),
    displayInk = Color(0xFFF2EEE5),
    displayMuted = Color(0xFF9A948A),
    chartLine = Color(0xFF3A362F)
)

val DarkPalette = RipePalette(
    bg = Color(0xFF1B1815),
    panel = Color(0xFF24211C),
    panelHi = Color(0xFF2E2B24),
    sunken = Color(0xFF3A362F),
    ink = Color(0xFFF2EEE5),
    ink2 = Color(0xFFC9C2B3),
    muted = Color(0xFF918A7D),
    fresh = Color(0xFF79A878),
    spoiled = Color(0xFFD86A4F),
    warn = Color(0xFFEDB100),
    accentDeep = Color(0xFFF0C04A),
    tintFresh = Color(0xFF232A20),
    tintWarn = Color(0xFF302916),
    tintBad = Color(0xFF33221D),
    track = Color(0xFF2E2B25),
    displayBg = Color(0xFF0E0D0B),
    displayInk = Color(0xFFF2EEE5),
    displayMuted = Color(0xFF948D80),
    chartLine = Color(0xFFC9C2B3)
)

/** Resolve the active palette from the Settings theme (Light / Dark / System). */
@Composable
fun activePalette(theme: String): RipePalette =
    when (theme) {
        "Dark" -> DarkPalette
        "Light" -> LightPalette
        else -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    }

// Bundled fonts (res/font) — no Google Play Services / downloadable fonts
// needed, so they work offline on any device or emulator image. Two pairings
// mirror the web dashboard's [data-font] switch:
//   • Grotesk (default): Space Grotesk for headings/figures + Urbanist for body
//   • Editorial:         Merriweather for headings/figures + PT Sans for body
val SpaceGrotesk: FontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val Urbanist: FontFamily = FontFamily(
    Font(R.font.urbanist, FontWeight.Normal),
    Font(R.font.urbanist, FontWeight.Medium),
    Font(R.font.urbanist, FontWeight.Bold)
)

val Merriweather: FontFamily = FontFamily(
    Font(R.font.merriweather, FontWeight.Normal),
    Font(R.font.merriweather_bold, FontWeight.Bold)
)

val PtSans: FontFamily = FontFamily(
    Font(R.font.pt_sans, FontWeight.Normal),
    Font(R.font.pt_sans_bold, FontWeight.Bold)
)

/** The active display/UI type pair, provided by [FoodMonitorApp] from Settings →
 *  Appearance so switching Grotesk/Editorial re-skins the whole app, exactly
 *  like the web dashboard's `[data-font]` attribute. */
val LocalDisplayFont = compositionLocalOf { SpaceGrotesk }
val LocalUiFont = compositionLocalOf { Urbanist }

data class LogEntry(val time: String, val status: String, val rawValue: Int)

/** App-level settings, mirrors the web dashboard's Settings sheet. */
data class RipeSettings(
    val theme: String = "Light",
    val font: String = "Grotesk",
    val mode: String = "Advanced",
    val source: String = "Mock server",
    val host: String = "localhost",
    val port: String = "8012",
    val routing: String = "Via local server",
    val pollingMs: Long = 2000L,
    /** "Off" / "Publish this device" / "Follow the cloud" — mirrors the web. */
    val sync: String = "Off"
)

/** Direct GET of a JSON payload (ESP32 /data, or local server /proxy/data). */
suspend fun fetchData(urlString: String): String = withContext(Dispatchers.IO) {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 1500
    connection.readTimeout = 1500
    try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** One firmware JSON snapshot — the fields this app consumes. */
data class Reading(
    val raw: Int,
    val avg: Int,
    val pct: Int,
    val status: String,
    val warn: Int,
    val spoiled: Int,
    val floor: Int,
    val ceil: Int
)

/** Thousands-separated integer — mirrors the web's fmt(). */
private fun fmtInt(n: Int): String = String.format(Locale.US, "%,d", n)

/** Basic-mode verdict sentence: plain language, no numbers (mirrors web plainText()). */
fun basicVerdict(status: String): String = when (status) {
    "SPOILED" -> "Spoiled — do not eat."
    "WARNING" -> "Starting to spoil — check it soon."
    "FRESH"   -> "Still fresh — safe to eat."
    else      -> "Waiting for the first sample…"
}

/** Advanced-mode verdict sentence: computed from live values (mirrors web hintText()). */
fun advancedVerdict(avg: Int, status: String, warn: Int, spoiled: Int, floor: Int): String = when (status) {
    "SPOILED" -> {
        val o = avg - spoiled
        "${fmtInt(avg)} counts — " +
            (if (o >= 0) "${fmtInt(o)} past the " else "${fmtInt(-o)} still under the ") +
            "${fmtInt(spoiled)}-count discard line. Do not eat this."
    }
    "WARNING" -> {
        val w = avg - warn
        "${fmtInt(avg)} counts — " +
            (if (w >= 0) "${fmtInt(w)} past the " else "${fmtInt(-w)} below the ") +
            "${fmtInt(warn)}-count warning line. VOC load is climbing in the sealed headspace."
    }
    else -> {
        val f = avg - floor
        "${fmtInt(avg)} counts — ${fmtInt(kotlin.math.abs(f))}" +
            (if (f >= 0) " above" else " below") +
            " the ${fmtInt(floor)}-count clean-air floor. Chamber air is at baseline."
    }
}

/** POST a JSON body to the ESP32 /config endpoint and return its reply. */
suspend fun postJson(urlString: String, body: String): String = withContext(Dispatchers.IO) {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 1500
    connection.readTimeout = 2500
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        stream?.bufferedReader()?.use { it.readText() } ?: ""
    } finally {
        connection.disconnect()
    }
}

/** Persist the settings so they survive app restarts (SharedPreferences — no
 *  extra dependencies; DataStore can replace this later if preferred). */
private fun saveSettings(context: Context, s: RipeSettings) {
    context.getSharedPreferences("food_monitor_settings", Context.MODE_PRIVATE).edit()
        .putString("theme", s.theme)
        .putString("font", s.font)
        .putString("mode", s.mode)
        .putString("source", s.source)
        .putString("host", s.host)
        .putString("port", s.port)
        .putString("routing", s.routing)
        .putLong("polling_ms", s.pollingMs)
        .putString("sync", s.sync)
        .apply()
}

private fun loadSettings(context: Context): RipeSettings {
    val prefs = context.getSharedPreferences("food_monitor_settings", Context.MODE_PRIVATE)
    return RipeSettings(
        theme = prefs.getString("theme", null) ?: "Light",
        font = prefs.getString("font", null) ?: "Grotesk",
        mode = prefs.getString("mode", null) ?: "Advanced",
        source = prefs.getString("source", null) ?: "Mock server",
        host = prefs.getString("host", null) ?: "localhost",
        port = prefs.getString("port", null) ?: "8012",
        routing = prefs.getString("routing", null) ?: "Via local server",
        pollingMs = prefs.getLong("polling_ms", 2000L),
        sync = prefs.getString("sync", null) ?: "Off"
    )
}

/* =====================================================================
 *  6. BLE CLIENT — Nordic UART service, matches the ESP32 firmware:
 *     service 6e400001-…, RX (write) 6e400002-…, TX (notify) 6e400003-….
 *     The firmware pushes {"raw":..,"pct":..,"status":".."} every
 *     REFRESH_MS and answers a "GET" write with one fresh snapshot.
 * ===================================================================== */
object BleUart {
    const val SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val RX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"   // phone -> ESP32 (write)
    const val TX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"   // ESP32 -> phone (notify)
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
    const val DEVICE_NAME = "FoodMonitor"
}

/** True when the BLE permissions for this Android version are all granted. */
private fun hasBlePermissions(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun neededBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** Scans for the FoodMonitor device, connects over GATT, subscribes to the
 *  TX notifications, and relays parsed readings back to the UI. */
private class BleClient(
    private val context: Context,
    private val onReading: (Reading) -> Unit,
    private val onConnectionState: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var ready = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name
            if (name != null && name.contains(BleUart.DEVICE_NAME, ignoreCase = true)) {
                scanner?.stopScan(this)
                scanner = null
                post("Found $name — connecting…")
                result.device.connectGatt(context, false, gattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanner = null
            post("BLE scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("DEPRECATION")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    ready = false
                    post("Connected — discovering services…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    ready = false
                    post("Disconnected")
                    g.close()
                    if (gatt === g) gatt = null
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { post("Service discovery failed"); return }
            val svc = g.getService(UUID.fromString(BleUart.SERVICE)) ?: run {
                post("UART service not found"); return
            }
            val tx = svc.getCharacteristic(UUID.fromString(BleUart.TX)) ?: run {
                post("TX characteristic missing"); return
            }
            g.setCharacteristicNotification(tx, true)
            tx.getDescriptor(UUID.fromString(BleUart.CCCD))?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(desc)
            }
            ready = true
            post("Listening — request sent")
            poll()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handlePayload(characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handlePayload(value)
        }
    }

    private fun handlePayload(value: ByteArray) {
        val text = String(value, Charsets.UTF_8).trim()
        main.post {
            try {
                val json = JSONObject(text)
                if (json.has("raw")) {
                    onReading(
                        Reading(
                            raw = json.optInt("raw", 0),
                            avg = json.optInt("avg", json.optInt("raw", 0)),
                            pct = json.optInt("pct", 0),
                            status = json.optString("status", "FRESH"),
                            warn = json.optInt("thr_warn", 1800),
                            spoiled = json.optInt("thr_spoiled", 2800),
                            floor = json.optInt("cal_floor", 1000),
                            ceil = json.optInt("cal_ceil", 3800)
                        )
                    )
                } else if (json.has("ok")) {
                    // config acknowledgement from a "CFG:…" write — nothing to do
                } else {
                    onConnectionState("BLE: $text")
                }
            } catch (_: Exception) {
                onConnectionState("BLE: $text")
            }
        }
    }

    /** Scan for the device and connect (no-op while already connected). */
    @Suppress("DEPRECATION")
    fun connect() {
        if (gatt != null) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            post("Bluetooth not supported"); return
        }
        if (!adapter.isEnabled) { post("Bluetooth is off"); return }
        post("Scanning for ${BleUart.DEVICE_NAME}…")
        val s = adapter.bluetoothLeScanner ?: run { post("No BLE scanner"); return }
        scanner = s
        s.startScan(scanCallback)
    }

    /** Disconnect, stop scanning and free resources. */
    @Suppress("DEPRECATION")
    fun disconnect() {
        scanner?.stopScan(scanCallback)
        scanner = null
        gatt?.let { g ->
            g.disconnect()
            g.close()
        }
        gatt = null
        ready = false
    }

    /** Ask the firmware for one fresh JSON snapshot ("GET"). No-op until ready. */
    fun poll() {
        if (!ready) return
        send("GET")
    }

    /** Write a raw command to the RX characteristic ("GET", "PING", "CFG:w,s,f,c"). */
    @Suppress("DEPRECATION")
    fun send(command: String): Boolean {
        val g = gatt ?: return false
        val svc = g.getService(UUID.fromString(BleUart.SERVICE)) ?: return false
        val rx = svc.getCharacteristic(UUID.fromString(BleUart.RX)) ?: return false
        rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        rx.value = command.toByteArray()
        return g.writeCharacteristic(rx)
    }

    private fun post(text: String) {
        main.post { onConnectionState(text) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialSettings = loadSettings(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    FoodMonitorApp(initialSettings = initialSettings)
                }
            }
        }
    }
}

@Composable
fun FoodMonitorApp(initialSettings: RipeSettings = RipeSettings()) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(initialSettings) }
    var showSettings by remember { mutableStateOf(false) }
    val p = activePalette(settings.theme)
    val editorial = settings.font == "Editorial"

    // --- Cloud account + sync (mirrors web Settings → Account / Cloud sync) --
    remember { RipeCloud.init() }
    var cloudUid by remember { mutableStateOf(RipeCloud.currentUser?.uid) }
    var cloudEmail by remember { mutableStateOf(RipeCloud.currentUser?.email) }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudMsg by remember { mutableStateOf<String?>(null) }
    var cloudMsgBad by remember { mutableStateOf(false) }
    var followedReading by remember { mutableStateOf<Reading?>(null) }
    var cloudLogs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    DisposableEffect(Unit) {
        val listener = RipeCloud.watchAuth { u ->
            cloudUid = u?.uid
            cloudEmail = u?.email
        }
        onDispose { RipeCloud.unwatchAuth(listener) }
    }

    // Google sign-in runs through Credential Manager; its bottom sheet is the
    // account chooser, and the API is suspending, so it needs a coroutine scope.
    val scope = rememberCoroutineScope()

    // Settings follow the account: read them live, seed the doc once, and write
    // on every local change — exactly like the web dashboard.
    DisposableEffect(cloudUid) {
        val uid = cloudUid
        if (uid == null) {
            onDispose { }
        } else {
            val reg = RipeCloud.watchPrefs(
                uid,
                onData = { cp ->
                    if (cp == null) {
                        RipeCloud.pushPrefs(uid, settings.theme, settings.font, settings.mode, settings.pollingMs)
                    } else {
                        val merged = settings.copy(
                            theme = cp.theme?.takeIf { it.isNotEmpty() } ?: settings.theme,
                            font = cp.font?.takeIf { it.isNotEmpty() } ?: settings.font,
                            mode = cp.mode?.takeIf { it.isNotEmpty() } ?: settings.mode,
                            pollingMs = cp.pollingMs ?: settings.pollingMs
                        )
                        if (merged != settings) {
                            settings = merged
                            saveSettings(context, merged)
                        }
                    }
                },
                onError = { }
            )
            onDispose { reg.remove() }
        }
    }

    val follow = settings.sync == "Follow the cloud" && cloudUid != null

    DisposableEffect(follow, cloudUid) {
        val uid = cloudUid
        if (!follow || uid == null) {
            followedReading = null
            cloudLogs = emptyList()
            onDispose { }
        } else {
            val reg = RipeCloud.watchLatest(uid, { cr -> followedReading = cr?.toReading() }, { })
            RipeCloud.recentReadings(uid, 5) { rows -> cloudLogs = rows }
            onDispose { reg.remove() }
        }
    }

    val onPublish: (Reading) -> Unit = { r ->
        val uid = cloudUid
        if (uid != null && settings.sync == "Publish this device") RipeCloud.publish(uid, r)
    }

    CompositionLocalProvider(
        LocalDisplayFont provides if (editorial) Merriweather else SpaceGrotesk,
        LocalUiFont provides if (editorial) PtSans else Urbanist
    ) {
        Box(modifier = Modifier.fillMaxSize().background(p.bg)) {
            FoodMonitorDashboard(
                settings = settings,
                cloudEmail = cloudEmail,
                cloudLogs = cloudLogs,
                follow = follow,
                followedReading = followedReading,
                onPublish = onPublish,
                onOpenSettings = { showSettings = true }
            )

            if (showSettings) {
                SettingsDialog(
                    settings = settings,
                    cloudEmail = cloudEmail,
                    cloudBusy = cloudBusy,
                    cloudMsg = cloudMsg,
                    cloudMsgBad = cloudMsgBad,
                    onApply = { updated ->
                        settings = updated
                        saveSettings(context, updated)
                        val uid = cloudUid
                        if (uid != null) {
                            RipeCloud.pushPrefs(uid, updated.theme, updated.font, updated.mode, updated.pollingMs)
                        }
                        showSettings = false
                    },
                    onCloudSignIn = { email, pass ->
                        cloudBusy = true; cloudMsg = "Signing in…"; cloudMsgBad = false
                        RipeCloud.signIn(email, pass) { err ->
                            cloudBusy = false
                            cloudMsg = err ?: "Signed in."
                            cloudMsgBad = err != null
                        }
                    },
                    onCloudCreate = { email, pass ->
                        cloudBusy = true; cloudMsg = "Creating account…"; cloudMsgBad = false
                        RipeCloud.createAccount(email, pass) { err ->
                            cloudBusy = false
                            cloudMsg = err ?: "Account created — you are signed in."
                            cloudMsgBad = err != null
                        }
                    },
                    onCloudGoogle = {
                        cloudBusy = true; cloudMsg = "Opening Google…"; cloudMsgBad = false
                        scope.launch {
                            RipeCloud.googleSignIn(context) { err ->
                                cloudBusy = false
                                cloudMsg = err ?: "Signed in."
                                cloudMsgBad = err != null
                            }
                        }
                    },
                    onCloudSignOut = {
                        scope.launch { RipeCloud.googleSignOut(context) }
                        RipeCloud.signOutNow()
                        cloudMsg = "Signed out."; cloudMsgBad = false
                    },
                    onClose = { showSettings = false }
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    settings: RipeSettings,
    cloudEmail: String?,
    cloudBusy: Boolean,
    cloudMsg: String?,
    cloudMsgBad: Boolean,
    onApply: (RipeSettings) -> Unit,
    onCloudSignIn: (String, String) -> Unit,
    onCloudCreate: (String, String) -> Unit,
    onCloudGoogle: () -> Unit,
    onCloudSignOut: () -> Unit,
    onClose: () -> Unit
) {
    var themeSelection by remember(settings) { mutableStateOf(settings.theme) }
    var fontSelection by remember(settings) { mutableStateOf(settings.font) }
    var modeSelection by remember(settings) { mutableStateOf(settings.mode) }
    var sourceSelection by remember(settings) { mutableStateOf(settings.source) }
    var hostIp by remember(settings) { mutableStateOf(settings.host) }
    var port by remember(settings) { mutableStateOf(settings.port) }
    var routingSelection by remember(settings) { mutableStateOf(settings.routing) }
    var pollingSelection by remember(settings) {
        mutableStateOf(when (settings.pollingMs) { 1000L -> "1 s"; 5000L -> "5 s"; else -> "2 s" })
    }
    var syncSelection by remember(settings) {
        mutableStateOf(
            if (settings.sync == "Publish this device" || settings.sync == "Follow the cloud")
                settings.sync else "Off"
        )
    }
    var acctEmail by remember { mutableStateOf("") }
    var acctPass by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val p = activePalette(settings.theme)

    fun buildUrl(): String =
        if (routingSelection == "Via local server")
            "http://$hostIp:$port/proxy/data?host=$hostIp&port=$port"
        else
            "http://$hostIp:$port/data"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.panel)
                    .padding(24.dp)
            ) {
                // Settings Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SETTINGS", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.muted, letterSpacing = 1.sp)
                    Text(
                        "Close", 
                        fontFamily = LocalUiFont.current, 
                        fontSize = 13.5.sp, 
                        color = p.ink,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onClose() }
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    item {
                        SettingsSection("DASHBOARD", p)
                        SegmentedControl(listOf("Basic", "Advanced"), modeSelection, p) { modeSelection = it }
                        SettingsHint(if (modeSelection == "Basic")
                            "Basic hides the numbers — just says whether the food is spoiled."
                        else
                            "Advanced shows the live ADC readout, VOC trend, status history and calibration.",
                            p)
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("APPEARANCE", p)
                        SegmentedControl(listOf("Light", "Dark", "System"), themeSelection, p) { themeSelection = it }
                        SettingsHint("Stored on this device — the ESP32 stores no theme data.", p)
                        Spacer(modifier = Modifier.height(12.dp))
                        SegmentedControl(listOf("Grotesk", "Editorial"), fontSelection, p) { fontSelection = it }
                        SettingsHint(
                            if (fontSelection == "Editorial")
                                "Editorial: Merriweather for headings and figures, PT Sans for body text."
                            else
                                "Grotesk: Space Grotesk + Urbanist.",
                            p
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("DATA SOURCE", p)
                        SegmentedControl(listOf("Mock server", "ESP32 (Wi-Fi)", "Bluetooth (BLE)"), sourceSelection, p) { sourceSelection = it }
                        SettingsHint(if (sourceSelection == "Bluetooth (BLE)")
                            "Bluetooth: the app scans for “FoodMonitor” and pairs automatically — no Wi-Fi needed."
                        else
                            "ESP32 over Wi-Fi: readings come from the device at http://$hostIp:$port/data.", p)
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("ESP32 ADDRESS", p)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("HOST OR IP", fontFamily = LocalDisplayFont.current, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
                                OutlinedTextField(
                                    value = hostIp,
                                    onValueChange = { hostIp = it },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = p.panelHi,
                                        focusedContainerColor = p.panelHi,
                                        unfocusedBorderColor = p.sunken,
                                        focusedBorderColor = p.warn,
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalDisplayFont.current, fontSize = 15.sp, color = p.ink),
                                    modifier = Modifier.height(50.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PORT", fontFamily = LocalDisplayFont.current, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
                                OutlinedTextField(
                                    value = port,
                                    onValueChange = { port = it },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = p.panelHi,
                                        focusedContainerColor = p.panelHi,
                                        unfocusedBorderColor = p.sunken,
                                        focusedBorderColor = p.warn,
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalDisplayFont.current, fontSize = 15.sp, color = p.ink),
                                    modifier = Modifier.height(50.dp)
                                )
                            }
                        }
                        SettingsHint("The last saved address is remembered, so a cleared browser or a new device still finds it.", p)
                        Spacer(modifier = Modifier.height(12.dp))
                        SegmentedControl(listOf("Via local server", "Direct from browser"), routingSelection, p) { routingSelection = it }
                        SettingsHint("Requests go through this server. No CORS needed — use this when the page is served by the local server.", p)
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("POLLING", p)
                        SegmentedControl(listOf("1 s", "2 s", "5 s"), pollingSelection, p) { pollingSelection = it }
                        SettingsHint("Firmware cadence is 2,000 ms (REFRESH_MS in config.h); faster polls return the same sample.", p)
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("ACCOUNT", p)
                        if (cloudEmail != null) {
                            Text(
                                "Signed in as $cloudEmail — readings and settings are shared with every device on this account.",
                                fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted,
                                lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                            CloudButton("Sign out", filled = false, enabled = true, p = p) { onCloudSignOut() }
                        } else {
                            Text(
                                "Not signed in — sign in to share readings and settings across your devices.",
                                fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted,
                                lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = acctEmail,
                                onValueChange = { acctEmail = it },
                                singleLine = true,
                                label = { Text("Email", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = p.panelHi,
                                    focusedContainerColor = p.panelHi,
                                    unfocusedBorderColor = p.sunken,
                                    focusedBorderColor = p.warn,
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = acctPass,
                                onValueChange = { acctPass = it },
                                singleLine = true,
                                label = { Text("Password", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = p.panelHi,
                                    focusedContainerColor = p.panelHi,
                                    unfocusedBorderColor = p.sunken,
                                    focusedBorderColor = p.warn,
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CloudButton("Sign in", filled = true, enabled = !cloudBusy, p = p) {
                                    onCloudSignIn(acctEmail.trim(), acctPass)
                                }
                                CloudButton("Create account", filled = false, enabled = !cloudBusy, p = p) {
                                    onCloudCreate(acctEmail.trim(), acctPass)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            CloudButton("Use Google instead", filled = false, enabled = !cloudBusy, p = p) {
                                onCloudGoogle()
                            }
                        }
                        if (cloudMsg != null) {
                            Text(
                                cloudMsg,
                                fontFamily = LocalUiFont.current, fontSize = 12.5.sp,
                                color = if (cloudMsgBad) p.spoiled else p.fresh,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("CLOUD SYNC", p)
                        SegmentedControl(listOf("Off", "Publish this device", "Follow the cloud"), syncSelection, p) { syncSelection = it }
                        SettingsHint(
                            when (syncSelection) {
                                "Publish this device" ->
                                    "Publish: this device keeps reading locally and mirrors each snapshot to your account, so other phones can follow it. Keep it on the ESP32's Wi-Fi."
                                "Follow the cloud" ->
                                    "Follow: this device shows whatever the publishing device is reading — open the app anywhere. Needs sign-in."
                                else -> "Off — everything stays on this device."
                            }, p
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    val pollMs = when (pollingSelection) {
                                        "1 s" -> 1000L
                                        "5 s" -> 5000L
                                        else -> 2000L
                                    }
                                    onApply(
                                        RipeSettings(
                                            theme = themeSelection,
                                            font = fontSelection,
                                            mode = modeSelection,
                                            source = sourceSelection,
                                            host = hostIp,
                                            port = port,
                                            routing = routingSelection,
                                            pollingMs = pollMs,
                                            sync = syncSelection
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = p.warn),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)
                            ) {
                                Text("Save & connect", fontFamily = LocalUiFont.current, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink)
                            }
                            Text(
                                "Test connection", 
                                fontFamily = LocalUiFont.current, 
                                fontSize = 13.5.sp, 
                                color = p.ink2,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        testResult = if (sourceSelection == "Bluetooth (BLE)") {
                                            "BLE live — reading appears on the dashboard"
                                        } else {
                                            try {
                                                val json = JSONObject(fetchData(buildUrl()))
                                                "OK — raw ${json.optInt("raw", 0)} · ${json.optString("status", "?")}"
                                            } catch (e: Exception) {
                                                "Failed — ${e.message ?: "no response"}"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsHint(("On save — ${sourceSelection.lowercase()} source · poll ${pollingSelection}" + (testResult?.let { " · $it" } ?: "")), p)
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: String, p: RipePalette, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.sunken)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) p.panelHi else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontFamily = LocalUiFont.current,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) p.ink else p.ink2
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, p: RipePalette) {
    Text(text = title, fontFamily = LocalDisplayFont.current, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.muted, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
fun SettingsHint(text: String, p: RipePalette) {
    Text(text = text, fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
}

/** Filled / quiet button matching the web sheet's .btn-primary / .btn-quiet. */
@Composable
fun CloudButton(label: String, filled: Boolean, enabled: Boolean, p: RipePalette, onClick: () -> Unit) {
    val bg = if (filled) (if (enabled) p.warn else p.sunken) else Color.Transparent
    val fg = if (filled) (if (enabled) p.ink else p.muted) else p.ink
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, p.sunken, shape))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(label, fontFamily = LocalUiFont.current, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
    }
}


@Composable
fun FoodMonitorDashboard(
    settings: RipeSettings,
    cloudEmail: String?,
    cloudLogs: List<LogEntry>,
    follow: Boolean,
    followedReading: Reading?,
    onPublish: (Reading) -> Unit,
    onOpenSettings: () -> Unit
) {
    var rawValue by remember { mutableStateOf(1398) }
    var avgValue by remember { mutableStateOf(1398) }
    var percentage by remember { mutableStateOf(7) }
    var status by remember { mutableStateOf("FRESH") }
    var offline by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf("—") }
    val p = activePalette(settings.theme)
    val basic = settings.mode == "Basic"
    // The BleClient closure is created once (remember) and would otherwise keep
    // a stale publish callback; remembered state keeps it current.
    val onPublishNow = rememberUpdatedState(onPublish)

    val trendData = remember { mutableStateListOf<Int>().apply {
        repeat(50) { add(1000 + Random.nextInt(100)) }
    }}

    val logs = remember { mutableStateListOf<LogEntry>() }

    // --- Calibration state (mirrors the web "Calibration" card) -------------
    var cfgWarn by remember { mutableStateOf("1800") }
    var cfgSpoiled by remember { mutableStateOf("2800") }
    var cfgFloor by remember { mutableStateOf("1000") }
    var cfgCeil by remember { mutableStateOf("3800") }
    var devWarn by remember { mutableStateOf(1800) }
    var devSpoiled by remember { mutableStateOf(2800) }
    var devFloor by remember { mutableStateOf(1000) }
    var devCeil by remember { mutableStateOf(3800) }
    var devSeeded by remember { mutableStateOf(false) }
    var calMsg by remember { mutableStateOf("") }
    var calMsgBad by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bleGranted by remember { mutableStateOf(hasBlePermissions(context)) }
    var blePrompted by remember { mutableStateOf(false) }
    var bleStatus by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Apply one snapshot (Wi-Fi / BLE / mock) to the dashboard and seed the
    // calibration inputs from the first reading the device sends.
    val applyReading: (Reading, Boolean) -> Unit = { r, publish ->
        rawValue = r.raw
        avgValue = r.avg
        percentage = r.pct
        if (r.status != status) {
            logs.add(0, LogEntry(timeFormat.format(Date()), r.status, r.raw))
            if (logs.size > 5) logs.removeLast()
            status = r.status
        }
        trendData.add(r.raw)
        if (trendData.size > 50) trendData.removeFirst()
        offline = false
        lastSeen = timeFormat.format(Date())
        if (!devSeeded) {
            devWarn = r.warn; devSpoiled = r.spoiled; devFloor = r.floor; devCeil = r.ceil
            cfgWarn = r.warn.toString(); cfgSpoiled = r.spoiled.toString()
            cfgFloor = r.floor.toString(); cfgCeil = r.ceil.toString()
            devSeeded = true
        }
        if (publish) onPublishNow.value(r)
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        blePrompted = false
        bleGranted = hasBlePermissions(context)
        if (!bleGranted && results.values.none { it }) {
            bleStatus = "Bluetooth permission denied — enable it in app settings"
        }
    }
    val bleClient = remember {
        BleClient(
            context = context,
            onReading = { r -> applyReading(r, true) },
            onConnectionState = { s ->
                bleStatus = s
                val bad = s.contains("fail", true) || s.contains("not ", true) ||
                          s.contains("disconnect", true) || s.contains("off", true) ||
                          s.contains("denied", true) || s.contains("required", true)
                if (bad) offline = true
            }
        )
    }
    DisposableEffect(settings.source) {
        onDispose {
            if (settings.source == "Bluetooth (BLE)") bleClient.disconnect()
        }
    }

    // --- Calibration actions (validate, write to device, undo, suggest) ------
    val refreshCalMsg: () -> Unit = {
        val w = cfgWarn.trim().toIntOrNull(); val s = cfgSpoiled.trim().toIntOrNull()
        val f = cfgFloor.trim().toIntOrNull(); val c = cfgCeil.trim().toIntOrNull()
        val valid = w != null && s != null && f != null && c != null &&
            w in 0..4095 && s in 0..4095 && f in 0..4095 && c in 0..4095 &&
            f < w && w < s && s < c
        if (!valid) { calMsg = "Rule: floor < warning < discard < ceiling, 0–4095."; calMsgBad = true }
        else { calMsg = "$f / $w / $s / $c"; calMsgBad = false }
    }
    val calW = cfgWarn.trim().toIntOrNull(); val calS = cfgSpoiled.trim().toIntOrNull()
    val calF = cfgFloor.trim().toIntOrNull(); val calC = cfgCeil.trim().toIntOrNull()
    val calValid = calW != null && calS != null && calF != null && calC != null &&
        calW in 0..4095 && calS in 0..4095 && calF in 0..4095 && calC in 0..4095 &&
        calF < calW && calW < calS && calS < calC
    val calChanged = calW != devWarn || calS != devSpoiled || calF != devFloor || calC != devCeil
    val calCanSave = calValid && calChanged

    val onSaveCfg: () -> Unit = {
        if (!calValid) {
            calMsg = "Outside the rule: floor < warning < discard < ceiling, all 0–4095."
            calMsgBad = true
        } else {
            scope.launch {
                val w = calW!!; val s = calS!!; val f = calF!!; val c = calC!!
                calMsg = "Saving…"; calMsgBad = false
                val ok = try {
                    when (settings.source) {
                        "Bluetooth (BLE)" -> bleClient.send("CFG:$w,$s,$f,$c")
                        "ESP32 (Wi-Fi)" -> {
                            val url = if (settings.routing == "Via local server")
                                "http://${settings.host}:${settings.port}/proxy/config?host=${settings.host}&port=${settings.port}"
                            else
                                "http://${settings.host}:${settings.port}/config"
                            postJson(url, "{\"warn\":$w,\"spoiled\":$s,\"floor\":$f,\"ceil\":$c}").contains("true")
                        }
                        else -> true   // mock — accept locally
                    }
                } catch (e: Exception) { false }
                if (ok) {
                    devWarn = w; devSpoiled = s; devFloor = f; devCeil = c; devSeeded = true
                    calMsg = "Saved — device now warns at $w counts."; calMsgBad = false
                } else {
                    calMsg = "The device did not accept the change. Try again."; calMsgBad = true
                }
            }
        }
    }

    LaunchedEffect(settings.source, settings.host, settings.port, settings.routing, settings.pollingMs, follow) {
        blePrompted = false
        if (follow) return@LaunchedEffect   // following the cloud — no local polling
        while (true) {
            when (settings.source) {
                "ESP32 (Wi-Fi)" -> {
                    val url = if (settings.routing == "Via local server")
                        "http://${settings.host}:${settings.port}/proxy/data?host=${settings.host}&port=${settings.port}"
                    else
                        "http://${settings.host}:${settings.port}/data"
                    try {
                        val json = JSONObject(fetchData(url))
                        applyReading(
                            Reading(
                                raw = json.optInt("raw", rawValue),
                                avg = json.optInt("avg", json.optInt("raw", rawValue)),
                                pct = json.optInt("pct", percentage),
                                status = json.optString("status", status),
                                warn = json.optInt("thr_warn", devWarn),
                                spoiled = json.optInt("thr_spoiled", devSpoiled),
                                floor = json.optInt("cal_floor", devFloor),
                                ceil = json.optInt("cal_ceil", devCeil)
                            ),
                            true
                        )
                    } catch (e: Exception) {
                        offline = true
                    }
                }
                "Bluetooth (BLE)" -> {
                    if (bleGranted) {
                        bleClient.connect()
                        bleClient.poll()   // force one fresh snapshot ("GET")
                    } else if (!blePrompted) {
                        blePrompted = true
                        offline = true
                        bleStatus = "Bluetooth permission required"
                        blePermissionLauncher.launch(neededBlePermissions())
                    } else {
                        offline = true
                    }
                }
                else -> {
                    val change = Random.nextInt(-150, 250)
                    val next = (rawValue + change).coerceIn(900, 3900)
                    val recent = trendData.takeLast(4)
                    val avg = (recent.sum() + next) / (recent.size + 1)   // ~10 s mean, like /data
                    val pct = if (devCeil > devFloor)
                        ((next - devFloor) * 100 / (devCeil - devFloor)).coerceIn(0, 100) else 0
                    val st = when {
                        next >= devSpoiled -> "SPOILED"
                        next >= devWarn -> "WARNING"
                        else -> "FRESH"
                    }
                    applyReading(Reading(raw = next, avg = avg, pct = pct, status = st,
                        warn = devWarn, spoiled = devSpoiled, floor = devFloor, ceil = devCeil), true)
                }
            }
            delay(settings.pollingMs)
        }
    }

    // While following, render whatever the publishing device pushed.
    LaunchedEffect(follow, followedReading) {
        val r = followedReading
        if (follow && r != null) applyReading(r, false)
    }

    // Seed the Status panel from the account's recent history on first follow.
    LaunchedEffect(follow, cloudLogs) {
        if (follow && cloudLogs.isNotEmpty() && logs.isEmpty()) {
            logs.addAll(cloudLogs.take(5))
        }
    }

    val statusColor = when (status) {
        "FRESH" -> p.fresh
        "WARNING" -> p.warn
        "SPOILED" -> p.spoiled
        else -> p.muted
    }

    val chipLabel = if (follow)
        "CLOUD · " + (cloudEmail?.substringBefore("@") ?: "SYNC")
    else when (settings.source) {
        "ESP32 (Wi-Fi)" -> "ESP32 · WI-FI"
        "Bluetooth (BLE)" -> "BLE"
        else -> "MOCK SERVER"
    }
    val chipColor = if (offline) p.spoiled else p.fresh

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Ripe FR-1", fontFamily = LocalDisplayFont.current, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = p.ink, letterSpacing = (-0.2).sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(chipColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(chipLabel, fontFamily = LocalUiFont.current, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.muted)
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Settings Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Transparent)
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text("Settings", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink)
                    }
                }
            }
        }
        
        item {
            if (basic) return@item
            // 1. Graphite Readout Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.displayBg)
                    .padding(horizontal = 26.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(text = "MQ-3 • GPIO 34 • 12-bit ADC 0-4095", fontFamily = LocalDisplayFont.current, fontSize = 10.sp, color = p.displayMuted, letterSpacing = 1.2.sp)
                    if (settings.source == "Bluetooth (BLE)" && bleStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = bleStatus, fontFamily = LocalUiFont.current, fontSize = 10.5.sp, color = p.displayMuted)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%,d", rawValue),
                            fontFamily = LocalDisplayFont.current,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = p.displayInk,
                            letterSpacing = (-2).sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "COUNTS", fontFamily = LocalDisplayFont.current, fontSize = 13.sp, color = p.displayMuted, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(text = "VOC CONCENTRATION", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.displayMuted, letterSpacing = 0.5.sp)
                        Text(text = "$percentage%", fontFamily = LocalDisplayFont.current, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = p.displayInk)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(p.track)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentage / 100f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    when(status) {
                                        "FRESH" -> Color(0xFF79A878)
                                        "WARNING" -> p.warn
                                        "SPOILED" -> Color(0xFFD86A4F)
                                        else -> Color.Gray
                                    }
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            // 2. Verdict Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when(status) {
                            "FRESH" -> p.tintFresh
                            "WARNING" -> p.tintWarn
                            "SPOILED" -> p.tintBad
                            else -> p.panel
                        }
                    )
                    .padding(if (basic) 30.dp else 22.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(statusColor))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = status,
                            fontFamily = LocalDisplayFont.current,
                            fontSize = if (basic) 40.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = when(status) {
                                "FRESH" -> p.fresh
                                "WARNING" -> p.accentDeep // accent-deep
                                "SPOILED" -> p.spoiled
                                else -> p.muted
                            },
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(13.dp))
                    Text(
                        text = if (basic) basicVerdict(status)
                               else advancedVerdict(avgValue, status, devWarn, devSpoiled, devFloor),
                        fontFamily = LocalUiFont.current,
                        fontSize = if (basic) 17.sp else 14.5.sp,
                        color = if (basic) p.ink else p.ink2,
                        lineHeight = if (basic) 26.sp else 22.sp
                    )
                    if (basic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "last sample $lastSeen",
                            fontFamily = LocalUiFont.current,
                            fontSize = 12.5.sp,
                            color = p.muted
                        )
                    }
                    if (!basic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "warning ${fmtInt(devWarn)} · discard ${fmtInt(devSpoiled)} counts",
                            fontFamily = LocalUiFont.current,
                            fontSize = 12.sp,
                            color = p.muted
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            if (basic) return@item
            // 3. Trend Canvas Chart
            Text(text = "VOC TREND", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                if (trendData.isEmpty()) return@Canvas
                val minVal = 900f
                val maxVal = 3900f
                val w = size.width
                val h = size.height
                val stepX = w / (50 - 1).coerceAtLeast(1)
                
                val path = Path()
                trendData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = h - ((value - minVal) / (maxVal - minVal) * h)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = p.chartLine,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("900", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.muted)
                Text("3900", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.muted)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // 4. Status History
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.panel)
                    .padding(22.dp)
            ) {
                Column {
                    Text(text = "STATUS HISTORY", fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (logs.isEmpty()) {
                        Text("No status changes since app launched.", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.muted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            logs.forEach { log ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = log.time, fontFamily = LocalDisplayFont.current, fontSize = 12.sp, color = p.muted, modifier = Modifier.width(62.dp))
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(
                                        when(log.status) {
                                            "FRESH" -> p.fresh
                                            "WARNING" -> p.warn
                                            "SPOILED" -> p.spoiled
                                            else -> p.muted
                                        }
                                    ))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = log.status, fontFamily = LocalUiFont.current, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = p.ink)
                                    if (!basic) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(text = "${log.rawValue}", fontFamily = LocalDisplayFont.current, fontSize = 12.5.sp, color = p.ink2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // 5. Bench setup — the dashboard's one custom illustration (web #6)
            ChamberCard(p)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            if (basic) return@item
            // 6. Calibration — mirrors the web "Calibration" card
            CalibrationCard(
                warn = cfgWarn, spoiled = cfgSpoiled, floor = cfgFloor, ceil = cfgCeil,
                onWarn = { cfgWarn = it; refreshCalMsg() },
                onSpoiled = { cfgSpoiled = it; refreshCalMsg() },
                onFloor = { cfgFloor = it; refreshCalMsg() },
                onCeil = { cfgCeil = it; refreshCalMsg() },
                canSave = calCanSave,
                message = calMsg, messageBad = calMsgBad,
                onSave = onSaveCfg,
                onUndo = {
                    cfgWarn = devWarn.toString(); cfgSpoiled = devSpoiled.toString()
                    cfgFloor = devFloor.toString(); cfgCeil = devCeil.toString()
                    calMsg = "Reverted to the device values."; calMsgBad = false
                },
                onSuggest = {
                    cfgWarn = "1600"; cfgSpoiled = "2600"; cfgFloor = "1000"; cfgCeil = "3800"
                    calMsg = "Suggested baseline loaded — press Save to write it to flash."; calMsgBad = false
                },
                p = p
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The dashboard's only custom illustration (web `index.html` section 6): a
 *  sealed container holding a food sample, rising vapour, and the MQ-3 probe
 *  pushed through the lid. Drawn on a Canvas in the SVG's own 240×132 space and
 *  scaled to fit, so it stays crisp at any size and tracks the active palette. */
@Composable
fun ChamberIllustration(p: RipePalette, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // SVG is 240×132; the web caps the drawing at 148 px tall and letterboxes
        // it (preserveAspectRatio "meet"), so mirror that here.
        val h = minOf(maxWidth * (132f / 240f), 148.dp)
        Canvas(modifier = Modifier.fillMaxWidth().height(h)) {
            val s = minOf(size.width / 240f, size.height / 132f)
            withTransform({
                translate((size.width - 240f * s) / 2f, (size.height - 132f * s) / 2f)
                scale(s, s, pivot = Offset.Zero)
            }) {
                val ln = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val thin = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                // lid + container body (stroked rounded rects)
                drawRoundRect(p.ink, Offset(78f, 14f), Size(84f, 17f), CornerRadius(6f), style = ln)
                drawRoundRect(p.ink, Offset(34f, 31f), Size(172f, 88f), CornerRadius(14f), style = ln)

                // probe stem into the sample + grille
                drawLine(p.ink, Offset(120f, 31f), Offset(120f, 54f), 1.5f, StrokeCap.Round)
                drawLine(p.ink, Offset(115f, 59.5f), Offset(125f, 59.5f), 1.5f, StrokeCap.Round)
                drawLine(p.ink, Offset(115f, 63f), Offset(125f, 63f), 1.5f, StrokeCap.Round)
                drawLine(p.ink, Offset(115f, 66.5f), Offset(125f, 66.5f), 1.5f, StrokeCap.Round)

                // probe head + status LED (accent)
                drawCircle(p.ink, 9f, Offset(120f, 63f), style = ln)
                drawCircle(p.warn, 3.2f, Offset(134f, 57f))

                // rising vapour
                drawPath(Path().apply {
                    moveTo(94f, 104f); cubicTo(88f, 94f, 100f, 88f, 94f, 78f)
                }, p.ink, style = thin)
                drawPath(Path().apply {
                    moveTo(146f, 104f); cubicTo(152f, 94f, 140f, 88f, 146f, 78f)
                }, p.ink, style = thin)

                // sample mound (filled) with two grains
                drawPath(Path().apply {
                    moveTo(78f, 114f)
                    cubicTo(78f, 98f, 91f, 88f, 107f, 88f)
                    lineTo(133f, 88f)
                    cubicTo(149f, 88f, 162f, 98f, 162f, 114f)
                    arcTo(Rect(154f, 110f, 162f, 118f), 0f, 90f, false)
                    lineTo(82f, 118f)
                    arcTo(Rect(78f, 110f, 86f, 118f), 90f, 90f, false)
                    close()
                }, p.sunken)
                drawCircle(p.ink, 2.4f, Offset(103f, 100f))
                drawCircle(p.ink, 2.4f, Offset(133f, 104f))
            }
        }
    }
}

/** The web dashboard's "Bench setup" block — the chamber illustration in a panel
 *  with its caption (the UI's one graphic). */
@Composable
fun ChamberCard(p: RipePalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.panel)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)
    ) {
        Column {
            ChamberIllustration(p)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Bench setup — 2.5 L sealed container, 40 g sample, probe at the neck.",
                fontFamily = LocalUiFont.current,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = p.muted
            )
        }
    }
}

/** The web dashboard's "Calibration" card: four ADC thresholds, validation,
 *  and Save / Undo / Use-suggested actions. */
@Composable
fun CalibrationCard(
    warn: String, spoiled: String, floor: String, ceil: String,
    onWarn: (String) -> Unit, onSpoiled: (String) -> Unit,
    onFloor: (String) -> Unit, onCeil: (String) -> Unit,
    canSave: Boolean, message: String, messageBad: Boolean,
    onSave: () -> Unit, onUndo: () -> Unit, onSuggest: () -> Unit,
    p: RipePalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.panel)
            .padding(22.dp)
    ) {
        Column {
            Text(
                text = "CALIBRATION",
                fontFamily = LocalDisplayFont.current, fontSize = 11.sp, color = p.muted,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "floor < warning < discard < ceiling — ADC counts, 0–4095.",
                fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted, lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalibrationField("Warning ≥", warn, onWarn, p, Modifier.weight(1f))
                CalibrationField("Discard ≥", spoiled, onSpoiled, p, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalibrationField("0 % =", floor, onFloor, p, Modifier.weight(1f))
                CalibrationField("100 % =", ceil, onCeil, p, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Read the clean-air average on Serial at 115200 baud and set 0 % to it. " +
                    "Wave a VOC source over the sensor and set the discard line near 65 % of the peak you see.",
                fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted, lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canSave) p.warn else p.sunken)
                        .clickable(enabled = canSave) { onSave() }
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = "Save to device",
                        fontFamily = LocalUiFont.current, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (canSave) p.ink else p.muted
                    )
                }
                Text(
                    text = "Undo changes", fontFamily = LocalUiFont.current, fontSize = 13.5.sp,
                    color = p.ink2, modifier = Modifier.clickable { onUndo() }
                )
                Text(
                    text = "Use suggested", fontFamily = LocalUiFont.current, fontSize = 13.5.sp,
                    color = p.ink2, modifier = Modifier.clickable { onSuggest() }
                )
            }
            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message, fontFamily = LocalUiFont.current, fontSize = 12.5.sp,
                    color = if (messageBad) p.spoiled else p.fresh
                )
            }
        }
    }
}

@Composable
fun CalibrationField(
    label: String, value: String, onValueChange: (String) -> Unit,
    p: RipePalette, modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label, fontFamily = LocalDisplayFont.current, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = p.muted,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(4)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = p.panelHi,
                focusedContainerColor = p.panelHi,
                unfocusedBorderColor = p.sunken,
                focusedBorderColor = p.warn,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalDisplayFont.current, fontSize = 15.sp, color = p.ink),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}
