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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import org.json.JSONArray
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

/** Parse a CSS colour string (#rgb, #rrggbb, #rrggbbaa, or rgb()/rgba()). The
 *  web stores colours as CSS strings, so both platforms share one palette. */
fun parseCssColor(s: String?, fallback: Color): Color {
    val t = s?.trim().orEmpty()
    if (t.isEmpty()) return fallback
    try {
        if (t.startsWith("#")) {
            val hex = t.substring(1)
            return when (hex.length) {
                3 -> Color(
                    hex[0].digitToInt(16) * 17,
                    hex[1].digitToInt(16) * 17,
                    hex[2].digitToInt(16) * 17
                )
                6 -> Color(("ff$hex").toLong(16))
                8 -> Color(
                    hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16), hex.substring(6, 8).toInt(16)
                )
                else -> fallback
            }
        }
        val m = Regex("rgba?\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*(?:,\\s*([0-9.]+))?\\s*\\)")
            .find(t) ?: return fallback
        val r = m.groupValues[1].toFloatOrNull()?.coerceIn(0f, 255f) ?: return fallback
        val g = m.groupValues[2].toFloatOrNull()?.coerceIn(0f, 255f) ?: return fallback
        val b = m.groupValues[3].toFloatOrNull()?.coerceIn(0f, 255f) ?: return fallback
        val a = m.groupValues[4].takeIf { it.isNotEmpty() }?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        return Color(r / 255f, g / 255f, b / 255f, a)
    } catch (_: Exception) {
        return fallback
    }
}

/** The colour tokens the app can customise, keyed by the web's CSS variable
 *  names so a palette set on one platform renders on the other. */
val COLOR_TOKENS: List<Pair<String, String>> = listOf(
    "bg" to "Page background", "panel" to "Panel", "panel-hi" to "Panel highlight",
    "sunken" to "Sunken / track", "ink" to "Text", "ink-2" to "Text secondary",
    "muted" to "Muted text", "accent" to "Accent", "accent-deep" to "Accent (deep)",
    "fresh" to "Fresh", "spoiled" to "Spoiled", "tint-fresh" to "Fresh tint",
    "tint-warn" to "Warning tint", "tint-bad" to "Spoiled tint",
    "display-bg" to "Display background", "dark-ink" to "Display text",
    "dark-muted" to "Display muted", "dark-track" to "Display track"
)

/** The "Ripe" (light) defaults — used whenever a custom palette omits a token. */
val RipeDefaultColors: Map<String, String> = mapOf(
    "bg" to "#e8e4db", "panel" to "#f5f2eb", "panel-hi" to "#fcfbf7",
    "sunken" to "#ddd7ca", "ink" to "#23211d", "ink-2" to "#4b4740",
    "muted" to "#837c6f", "accent" to "#edb100", "accent-deep" to "#8a6400",
    "fresh" to "#4c7a4d", "spoiled" to "#b23a25", "tint-fresh" to "#e6ece1",
    "tint-warn" to "#f7ecd2", "tint-bad" to "#f1ded7", "display-bg" to "#23211d",
    "dark-ink" to "#f2eee5", "dark-muted" to "#9a948a", "dark-track" to "#3a362f"
)

/** The colour presets offered in Settings → Colours (mirrors the web). */
val COLOR_PRESETS: Map<String, Map<String, String>> = mapOf(
    "Ripe" to emptyMap(),
    "Midnight" to mapOf(
        "bg" to "#14161a", "panel" to "#1b1e24", "panel-hi" to "#232830", "sunken" to "#2b313a",
        "ink" to "#eef1f6", "ink-2" to "#c3cad6", "muted" to "#8b94a3", "accent" to "#ffc24d",
        "accent-deep" to "#c9902a", "fresh" to "#7bd18b", "spoiled" to "#ff7a6b",
        "tint-fresh" to "#1b2a20", "tint-warn" to "#2c2718", "tint-bad" to "#2e1c1a",
        "display-bg" to "#0f1115", "dark-ink" to "#eef1f6", "dark-muted" to "#9aa3b2",
        "dark-track" to "#2a303a"
    ),
    "Paper" to mapOf(
        "bg" to "#f2efe6", "panel" to "#faf8f2", "panel-hi" to "#ffffff", "sunken" to "#e6e1d5",
        "ink" to "#2a2823", "ink-2" to "#55514a", "muted" to "#8c867a", "accent" to "#c8892b",
        "accent-deep" to "#8a5e14", "fresh" to "#4c7a4d", "spoiled" to "#b23a25",
        "tint-fresh" to "#e9eee4", "tint-warn" to "#f6efdf", "tint-bad" to "#f2e1da",
        "display-bg" to "#2a2823", "dark-ink" to "#f2eee5", "dark-muted" to "#9a948a",
        "dark-track" to "#3a362f"
    ),
    "Contrast" to mapOf(
        "bg" to "#000000", "panel" to "#0b0b0b", "panel-hi" to "#161616", "sunken" to "#242424",
        "ink" to "#ffffff", "ink-2" to "#f0f0f0", "muted" to "#b9b9b9", "accent" to "#ffe000",
        "accent-deep" to "#ffe000", "fresh" to "#00ff66", "spoiled" to "#ff3b30",
        "tint-fresh" to "#00331a", "tint-warn" to "#332b00", "tint-bad" to "#330d0a",
        "display-bg" to "#000000", "dark-ink" to "#ffffff", "dark-muted" to "#c9c9c9",
        "dark-track" to "#3a3a3a"
    )
)

/** Build a palette from a colour map, falling back to the Ripe defaults. */
fun customPalette(colors: Map<String, String>): RipePalette {
    fun c(key: String) = parseCssColor(colors[key], parseCssColor(RipeDefaultColors[key], Color.Gray))
    return RipePalette(
        bg = c("bg"), panel = c("panel"), panelHi = c("panel-hi"), sunken = c("sunken"),
        ink = c("ink"), ink2 = c("ink-2"), muted = c("muted"),
        fresh = c("fresh"), spoiled = c("spoiled"), warn = c("accent"),
        accentDeep = c("accent-deep"), tintFresh = c("tint-fresh"),
        tintWarn = c("tint-warn"), tintBad = c("tint-bad"),
        track = c("dark-track"), displayBg = c("display-bg"),
        displayInk = c("dark-ink"), displayMuted = c("dark-muted"),
        chartLine = c("ink-2")
    )
}

/** Resolve the active palette from the Settings theme (Light / Dark / System / Custom). */
@Composable
fun activePalette(settings: RipeSettings): RipePalette = when (settings.theme) {
    "Dark" -> DarkPalette
    "Light" -> LightPalette
    "Custom" -> customPalette(settings.colors)
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

/** One panel placed on the Custom dashboard board (mirrors the web's layout). */
data class BoardItem(val id: String, val x: Int, val y: Int, val w: Int, val h: Int)

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
    val sync: String = "Off",
    /** v1.1.0 — look & feel shared with the account. */
    val name: String = "",
    val anim: String = "On",
    val fHead: String = "",
    val fBody: String = "",
    val colors: Map<String, String> = emptyMap(),
    val layout: List<BoardItem> = emptyList(),
    /** Local-only: has the default board been seeded, and has the user entered. */
    val layoutReady: Boolean = false,
    val entered: Boolean = false
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
    val colorsJson = JSONObject()
    s.colors.forEach { (k, v) -> colorsJson.put(k, v) }
    val layoutJson = JSONArray()
    s.layout.forEach { b ->
        layoutJson.put(
            JSONObject().put("id", b.id).put("x", b.x).put("y", b.y).put("w", b.w).put("h", b.h)
        )
    }
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
        .putString("name", s.name)
        .putString("anim", s.anim)
        .putString("fHead", s.fHead)
        .putString("fBody", s.fBody)
        .putString("colors", colorsJson.toString())
        .putString("layout", layoutJson.toString())
        .putBoolean("layout_ready", s.layoutReady)
        .putBoolean("entered", s.entered)
        .apply()
}

private fun loadSettings(context: Context): RipeSettings {
    val prefs = context.getSharedPreferences("food_monitor_settings", Context.MODE_PRIVATE)
    val colors = LinkedHashMap<String, String>()
    try {
        val o = JSONObject(prefs.getString("colors", "{}") ?: "{}")
        o.keys().forEach { k -> colors[k] = o.optString(k, "") }
    } catch (_: Exception) { }
    val layout = ArrayList<BoardItem>()
    try {
        val a = JSONArray(prefs.getString("layout", "[]") ?: "[]")
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            layout.add(
                BoardItem(
                    o.getString("id"), o.optInt("x"), o.optInt("y"),
                    o.optInt("w", 2), o.optInt("h", 1)
                )
            )
        }
    } catch (_: Exception) { }
    return RipeSettings(
        theme = prefs.getString("theme", null) ?: "Light",
        font = prefs.getString("font", null) ?: "Grotesk",
        mode = prefs.getString("mode", null) ?: "Advanced",
        source = prefs.getString("source", null) ?: "Mock server",
        host = prefs.getString("host", null) ?: "localhost",
        port = prefs.getString("port", null) ?: "8012",
        routing = prefs.getString("routing", null) ?: "Via local server",
        pollingMs = prefs.getLong("polling_ms", 2000L),
        sync = prefs.getString("sync", null) ?: "Off",
        name = prefs.getString("name", null) ?: "",
        anim = prefs.getString("anim", null) ?: "On",
        fHead = prefs.getString("fHead", null) ?: "",
        fBody = prefs.getString("fBody", null) ?: "",
        colors = colors,
        layout = layout,
        layoutReady = prefs.getBoolean("layout_ready", false),
        entered = prefs.getBoolean("entered", false)
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
    // GitHub-style start page — the first thing shown whenever nobody is signed in.
    var showAuth by remember { mutableStateOf(RipeCloud.currentUser == null) }
    val p = activePalette(settings)
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

    // Once a user is present, leave the start page.
    LaunchedEffect(cloudUid) { if (cloudUid != null) showAuth = false }

    // Google sign-in runs through Credential Manager; its bottom sheet is the
    // account chooser, and the API is suspending, so it needs a coroutine scope.
    val scope = rememberCoroutineScope()

    // --- Custom fonts: download the named Google Fonts at runtime -----------
    var customDisplay by remember { mutableStateOf<FontFamily?>(null) }
    var customUi by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(settings.font, settings.fHead, settings.fBody) {
        if (settings.font == "Custom") {
            val head = settings.fHead.ifBlank { "Inter" }
            val body = settings.fBody.ifBlank { head }
            val d = loadGoogleFont(context, head)
            val b = loadGoogleFont(context, body)
            customDisplay = d
            customUi = b ?: d
        } else {
            customDisplay = null
            customUi = null
        }
    }
    val displayFamily = customDisplay ?: if (editorial) Merriweather else SpaceGrotesk
    val uiFamily = customUi ?: if (editorial) PtSans else Urbanist

    // Seed the default board the first time Custom mode is opened.
    LaunchedEffect(settings.mode, settings.layoutReady) {
        if (settings.mode == "Custom" && !settings.layoutReady) {
            val seeded = settings.copy(layout = defaultBoardLayout(), layoutReady = true)
            settings = seeded
            saveSettings(context, seeded)
        }
    }

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
                        RipeCloud.pushPrefs(uid, settings)
                    } else {
                        val merged = settings.copy(
                            theme = cp.theme?.takeIf { it.isNotEmpty() } ?: settings.theme,
                            font = cp.font?.takeIf { it.isNotEmpty() } ?: settings.font,
                            mode = cp.mode?.takeIf { it.isNotEmpty() } ?: settings.mode,
                            pollingMs = cp.pollingMs ?: settings.pollingMs,
                            name = cp.name ?: settings.name,
                            anim = cp.anim?.takeIf { it.isNotEmpty() } ?: settings.anim,
                            fHead = cp.fHead ?: settings.fHead,
                            fBody = cp.fBody ?: settings.fBody,
                            colors = if (cp.colors.isNotEmpty()) cp.colors else settings.colors,
                            layout = if (cp.layout.isNotEmpty()) cp.layout else settings.layout
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

    val doSignIn: (String, String) -> Unit = { email, pass ->
        cloudBusy = true; cloudMsg = "Signing in…"; cloudMsgBad = false
        RipeCloud.signIn(email, pass) { err ->
            cloudBusy = false
            cloudMsg = err ?: "Signed in."
            cloudMsgBad = err != null
        }
    }
    val doCreate: (String, String) -> Unit = { email, pass ->
        cloudBusy = true; cloudMsg = "Creating account…"; cloudMsgBad = false
        RipeCloud.createAccount(email, pass) { err ->
            cloudBusy = false
            cloudMsg = err ?: "Account created — you are signed in."
            cloudMsgBad = err != null
        }
    }
    val doGoogle: () -> Unit = {
        cloudBusy = true; cloudMsg = "Opening Google…"; cloudMsgBad = false
        scope.launch {
            RipeCloud.googleSignIn(context) { err ->
                cloudBusy = false
                cloudMsg = err ?: "Signed in."
                cloudMsgBad = err != null
            }
        }
    }
    val doSignOut: () -> Unit = {
        scope.launch { RipeCloud.googleSignOut(context) }
        RipeCloud.signOutNow()
        cloudMsg = "Signed out."; cloudMsgBad = false
        showAuth = true
    }

    CompositionLocalProvider(
        LocalDisplayFont provides displayFamily,
        LocalUiFont provides uiFamily
    ) {
        Box(modifier = Modifier.fillMaxSize().background(p.bg)) {
            if (showAuth) {
                RipeStartPage(
                    p = p,
                    cloudEmail = cloudEmail,
                    cloudBusy = cloudBusy,
                    cloudMsg = cloudMsg,
                    cloudMsgBad = cloudMsgBad,
                    onSignIn = doSignIn,
                    onCreate = doCreate,
                    onGoogle = doGoogle,
                    onGuest = { showAuth = false },
                    onContinue = { showAuth = false },
                    onSignOut = doSignOut
                )
            } else {
                FoodMonitorDashboard(
                    settings = settings,
                    cloudEmail = cloudEmail,
                    cloudLogs = cloudLogs,
                    follow = follow,
                    followedReading = followedReading,
                    onPublish = onPublish,
                    onOpenSettings = { showSettings = true },
                    onOpenAuth = { showAuth = true },
                    onLayoutChange = { newLayout ->
                        val updated = settings.copy(layout = newLayout, layoutReady = true)
                        settings = updated
                        saveSettings(context, updated)
                        cloudUid?.let { RipeCloud.pushPrefs(it, updated) }
                    }
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
                                RipeCloud.pushPrefs(uid, updated)
                            }
                            showSettings = false
                        },
                        onCloudSignOut = doSignOut,
                        onOpenAuth = { showSettings = false; showAuth = true },
                        onClose = { showSettings = false }
                    )
                }
            }
        }
    }
}

/**
 * Downloads a Google Fonts family at runtime (no bundled list) and caches the
 * TTF under cacheDir. The legacy CSS endpoint is used because a legacy
 * User-Agent makes Google serve a plain TTF url(). Returns null on any failure
 * so the caller falls back to the bundled Grotesk pairing.
 */
@OptIn(ExperimentalTextApi::class)
suspend fun loadGoogleFont(context: Context, family: String): FontFamily? = withContext(Dispatchers.IO) {
    try {
        val name = family.trim().replace(" ", "+")
        if (name.isEmpty()) return@withContext null
        val conn = URL("https://fonts.googleapis.com/css?family=$name").openConnection()
        (conn as HttpURLConnection).setRequestProperty("User-Agent", "Mozilla/4.0")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val css = conn.inputStream.bufferedReader().use { it.readText() }
        val m = Regex("url\\((https://[^)]+\\.ttf)\\)").find(css) ?: return@withContext null
        val file = File(context.cacheDir, "ripe_font_" + name.replace("+", "_") + ".ttf")
        if (!file.exists() || file.length() == 0L) {
            val dl = URL(m.groupValues[1]).openConnection()
            (dl as HttpURLConnection).connectTimeout = 10000
            dl.readTimeout = 15000
            dl.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        if (file.length() > 0L) {
            FontFamily(
                Font(file, FontWeight.Normal),
                Font(file, FontWeight.Bold)
            )
        } else null
    } catch (e: Exception) {
        null
    }
}

/** The GitHub-style, themed start / sign-in page (mirrors the web #auth page). */
@Composable
fun RipeStartPage(
    p: RipePalette,
    cloudEmail: String?,
    cloudBusy: Boolean,
    cloudMsg: String?,
    cloudMsgBad: Boolean,
    onSignIn: (String, String) -> Unit,
    onCreate: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
    onContinue: () -> Unit,
    onSignOut: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val signedIn = cloudEmail != null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.panel)
                .padding(24.dp)
        ) {
            Text("Ripe FR-1", fontFamily = LocalDisplayFont.current, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = p.ink, letterSpacing = (-0.4).sp)
            Text("Food spoilage monitor", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp, bottom = 18.dp))

            if (signedIn) {
                Text("Signed in as $cloudEmail", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink2)
                Text(
                    "Readings and look-and-feel are shared with every device on this account.",
                    fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
                CloudButton("Continue to dashboard", filled = true, enabled = true, p = p) { onContinue() }
                Spacer(modifier = Modifier.height(10.dp))
                CloudButton("Sign out", filled = false, enabled = !cloudBusy, p = p) { onSignOut() }
            } else {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    label = { Text("Email", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = p.panelHi, focusedContainerColor = p.panelHi,
                        unfocusedBorderColor = p.sunken, focusedBorderColor = p.warn,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, singleLine = true,
                    label = { Text("Password", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = p.panelHi, focusedContainerColor = p.panelHi,
                        unfocusedBorderColor = p.sunken, focusedBorderColor = p.warn,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                CloudButton("Sign in", filled = true, enabled = !cloudBusy, p = p) { onSignIn(email.trim(), pass) }
                Spacer(modifier = Modifier.height(10.dp))
                CloudButton("Create account", filled = false, enabled = !cloudBusy, p = p) { onCreate(email.trim(), pass) }
                Spacer(modifier = Modifier.height(10.dp))
                CloudButton("Continue with Google", filled = false, enabled = !cloudBusy, p = p) { onGoogle() }
                Spacer(modifier = Modifier.height(10.dp))
                CloudButton("Continue as guest", filled = false, enabled = true, p = p) { onGuest() }
            }

            if (cloudMsg != null) {
                Text(
                    cloudMsg, fontFamily = LocalUiFont.current, fontSize = 12.5.sp,
                    color = if (cloudMsgBad) p.spoiled else p.fresh,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** The default Custom-dashboard board (mirrors the web's 6-column layout). */
fun defaultBoardLayout(): List<BoardItem> = listOf(
    BoardItem("readout", 0, 0, 4, 2),
    BoardItem("verdict", 4, 0, 2, 1),
    BoardItem("chamber", 4, 1, 2, 1),
    BoardItem("trend", 0, 2, 3, 1),
    BoardItem("log", 3, 2, 3, 1),
    BoardItem("device", 0, 3, 2, 1),
    BoardItem("calib", 2, 3, 4, 1)
)

private val BOARD_DEFS = listOf(
    "readout" to "Live readout", "verdict" to "Verdict", "trend" to "VOC trend",
    "log" to "Status history", "device" to "Device", "chamber" to "Bench setup",
    "calib" to "Calibration"
)

@Composable
fun BoardTinyButton(label: String, p: RipePalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(p.sunken)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, fontFamily = LocalUiFont.current, fontSize = 11.5.sp, color = p.ink)
    }
}

private fun boardOverlap(a: BoardItem, b: BoardItem): Boolean =
    a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

/** Nudge a moved widget down until it no longer overlaps a neighbour. */
private fun resolveBoard(layout: List<BoardItem>, index: Int, moved: BoardItem): BoardItem {
    var cur = moved
    var guard = 0
    while (guard < 64 && layout.indices.any { it != index && boardOverlap(layout[it], cur) }) {
        cur = cur.copy(y = cur.y + 1)
        guard++
    }
    return cur
}

/** Panel entrance: fades in and rises, mirroring the web's [data-anim] motion. */
@Composable
fun Reveal(animOn: Boolean, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        targetValue = if (!animOn || shown) 1f else 0f,
        animationSpec = tween(durationMillis = 460),
        label = "revealA"
    )
    val rise by animateFloatAsState(
        targetValue = if (!animOn || shown) 0f else 26f,
        animationSpec = tween(durationMillis = 460),
        label = "revealY"
    )
    Box(modifier = Modifier.graphicsLayer { alpha = a; translationY = rise }) { content() }
}

/** One board widget's body (kept separate so the grid code stays readable). */
@Composable
fun BoardWidgetContent(
    id: String, p: RipePalette, rawValue: Int, percentage: Int, status: String,
    lastSeen: String, chipLabel: String, logs: List<LogEntry>, trend: List<Int>,
    floor: Int, warn: Int, spoiled: Int
) {
    when (id) {
        "readout" -> {
            Text(String.format(Locale.US, "%,d", rawValue), fontFamily = LocalDisplayFont.current, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text("$percentage% VOC", fontFamily = LocalDisplayFont.current, fontSize = 12.5.sp, color = p.muted)
        }
        "verdict" -> Text(status, fontFamily = LocalDisplayFont.current, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = when (status) { "WARNING" -> p.warn; "SPOILED" -> p.spoiled; else -> p.fresh })
        "trend" -> Canvas(modifier = Modifier.fillMaxWidth().height(46.dp)) {
            val vals = trend.takeLast(50)
            if (vals.size >= 2) {
                val mn = vals.min(); val mx = vals.max().coerceAtLeast(mn + 1)
                val dx = size.width / (vals.size - 1)
                val path = Path()
                vals.forEachIndexed { k, v ->
                    val x = dx * k
                    val y = size.height - (v - mn).toFloat() / (mx - mn).toFloat() * size.height
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = p.warn, style = Stroke(width = 2f))
            }
        }
        "log" -> {
            if (logs.isEmpty()) Text("No changes yet", fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted)
            else logs.take(3).forEach { e ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.time, fontFamily = LocalDisplayFont.current, fontSize = 11.5.sp, color = p.muted)
                    Text(e.status, fontFamily = LocalDisplayFont.current, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.ink)
                }
            }
        }
        "device" -> {
            Text(chipLabel, fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.ink)
            Text("Last seen $lastSeen", fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted)
        }
        "chamber" -> ChamberCard(p)
        "calib" -> {
            Text("floor $floor · warn $warn", fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted)
            Text("discard $spoiled", fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted)
        }
    }
}

/** The Custom dashboard board: drag a widget to move it, drag its corner to resize. */
@Composable
fun CustomBoardScreen(
    p: RipePalette,
    layout: List<BoardItem>,
    onLayoutChange: (List<BoardItem>) -> Unit,
    greetWord: String,
    greetName: String,
    clockText: String,
    signedInDash: Boolean,
    cloudEmail: String?,
    onOpenAuth: () -> Unit,
    onOpenSettings: () -> Unit,
    rawValue: Int,
    percentage: Int,
    status: String,
    lastSeen: String,
    chipLabel: String,
    chipColor: Color,
    logs: List<LogEntry>,
    trend: List<Int>,
    floor: Int,
    warn: Int,
    spoiled: Int
) {
    val present = layout.map { it.id }.toSet()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ripe FR-1", fontFamily = LocalDisplayFont.current, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(chipColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(chipLabel, fontFamily = LocalUiFont.current, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.muted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Account", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink, modifier = Modifier.clickable { onOpenAuth() }.padding(horizontal = 10.dp, vertical = 7.dp))
                        Text("Settings", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink, modifier = Modifier.clickable { onOpenSettings() }.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("$greetWord, $greetName", fontFamily = LocalDisplayFont.current, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text("$clockText  ·  " + if (signedInDash) "syncing as $cloudEmail" else "this device only", fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted)
            }
        }
        item {
            SettingsSection("CUSTOM BOARD", p)
            Text("Add widgets below, then drag to move them and drag a corner to resize.", fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted, lineHeight = 18.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BOARD_DEFS.filter { it.first !in present }.forEach { (id, label) ->
                    BoardTinyButton("+ " + label, p) { onLayoutChange(layout + BoardItem(id, 0, layout.size, 6, 1)) }
                }
                if (BOARD_DEFS.all { it.first in present }) {
                    Text("All widgets shown", fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Reset board", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink2, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onLayoutChange(defaultBoardLayout()) })
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            // Full drag + resize board: widgets are absolutely placed on a
            // 6-column grid and moved/resized with drag gestures (mirrors web).
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                val gap = 10.dp
                val cols = 6
                val cellW = (maxWidth - gap * (cols - 1)) / cols
                val cellH = 92.dp
                val cellWpx = with(LocalDensity.current) { (cellW + gap).toPx() }
                val cellHpx = with(LocalDensity.current) { (cellH + gap).toPx() }
                val rows = (layout.maxOfOrNull { it.y + it.h } ?: 1).coerceAtLeast(1)
                Box(modifier = Modifier.fillMaxWidth().height(cellH * rows + gap * (rows - 1))) {
                    layout.forEachIndexed { i, bi ->
                        key(bi.id) {
                            var drag by remember(bi) { mutableStateOf(Offset.Zero) }
                            var rez by remember(bi) { mutableStateOf(Offset.Zero) }
                            val bx = cellW * bi.x + gap * bi.x
                            val by = cellH * bi.y + gap * bi.y
                            val wDp = cellW * bi.w + gap * (bi.w - 1)
                            val hDp = cellH * bi.h + gap * (bi.h - 1)
                            Box(
                                modifier = Modifier
                                    .offset(x = bx, y = by)
                                    .width(wDp)
                                    .height(hDp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.panel)
                                    .pointerInput(bi) {
                                        detectDragGestures(
                                            onDrag = { change, delta -> change.consume(); drag += delta },
                                            onDragEnd = {
                                                val nx = ((bx.toPx() + drag.x) / cellWpx).roundToInt().coerceIn(0, cols - bi.w)
                                                val ny = ((by.toPx() + drag.y) / cellHpx).roundToInt().coerceAtLeast(0)
                                                val moved = resolveBoard(layout, i, bi.copy(x = nx, y = ny))
                                                drag = Offset.Zero
                                                if (moved != bi) onLayoutChange(layout.toMutableList().also { it[i] = moved })
                                            },
                                            onDragCancel = { drag = Offset.Zero }
                                        )
                                    }
                                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            BOARD_DEFS.firstOrNull { it.first == bi.id }?.second ?: bi.id,
                                            fontFamily = LocalDisplayFont.current, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                            color = p.muted, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f)
                                        )
                                        BoardTinyButton("✕", p) { onLayoutChange(layout.filterIndexed { j, _ -> j != i }) }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    BoardWidgetContent(
                                        id = bi.id, p = p, rawValue = rawValue, percentage = percentage,
                                        status = status, lastSeen = lastSeen, chipLabel = chipLabel,
                                        logs = logs, trend = trend, floor = floor, warn = warn, spoiled = spoiled
                                    )
                                }
                                // Drag this corner to resize the widget.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(topStart = 10.dp))
                                        .background(p.sunken)
                                        .pointerInput(bi) {
                                            detectDragGestures(
                                                onDrag = { change, delta -> change.consume(); rez += delta },
                                                onDragEnd = {
                                                    val nw = (bi.w + (rez.x / cellWpx).roundToInt()).coerceIn(1, cols - bi.x)
                                                    val nh = (bi.h + (rez.y / cellHpx).roundToInt()).coerceIn(1, 3)
                                                    rez = Offset.Zero
                                                    val resized = bi.copy(w = nw, h = nh)
                                                    if (resized != bi) onLayoutChange(layout.toMutableList().also { it[i] = resized })
                                                },
                                                onDragCancel = { rez = Offset.Zero }
                                            )
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                        for (k in 0..2) {
                                            val o = k * 5f + 2f
                                            drawLine(p.muted, Offset(size.width - o, size.height), Offset(size.width, size.height - o), 1.5f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
    onCloudSignOut: () -> Unit,
    onOpenAuth: () -> Unit,
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
    var testResult by remember { mutableStateOf<String?>(null) }
    // v1.1.0 look & feel
    var animSelection by remember(settings) { mutableStateOf(settings.anim) }
    var nameText by remember(settings) { mutableStateOf(settings.name) }
    var fHeadText by remember(settings) { mutableStateOf(settings.fHead) }
    var fBodyText by remember(settings) { mutableStateOf(settings.fBody) }
    var colorsState by remember(settings) { mutableStateOf(settings.colors) }
    var presetSelection by remember(settings) { mutableStateOf("Ripe") }
    var showColors by remember { mutableStateOf(false) }
    val signedIn = cloudEmail != null
    val scope = rememberCoroutineScope()
    val p = activePalette(settings)

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
                        SegmentedControl(listOf("Basic", "Advanced", "Custom"), modeSelection, p) { modeSelection = it }
                        SettingsHint(
                            when (modeSelection) {
                                "Basic" -> "Basic hides the numbers — just says whether the food is spoiled."
                                "Custom" -> "Custom shows the dashboard board — pick which widgets you want."
                                else -> "Advanced shows the live ADC readout, VOC trend, status history and calibration."
                            },
                            p
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("APPEARANCE", p)
                        SegmentedControl(listOf("Light", "Dark", "System", "Custom"), themeSelection, p) { themeSelection = it }
                        SettingsHint(if (themeSelection == "Custom")
                            "Custom uses the palette below and overrides Light and Dark."
                        else
                            "Stored on this device and shared with your account.", p)
                        Spacer(modifier = Modifier.height(12.dp))
                        SegmentedControl(listOf("Grotesk", "Editorial", "Custom"), fontSelection, p) { fontSelection = it }
                        SettingsHint(
                            when (fontSelection) {
                                "Editorial" -> "Editorial: Merriweather for headings and figures, PT Sans for body text."
                                "Custom" -> "Custom: downloads the Google Fonts families below at runtime and caches them."
                                else -> "Grotesk: Space Grotesk + Urbanist."
                            },
                            p
                        )
                        if (fontSelection == "Custom") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = fHeadText, onValueChange = { fHeadText = it }, singleLine = true,
                                label = { Text("Display font family", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = p.panelHi, focusedContainerColor = p.panelHi,
                                    unfocusedBorderColor = p.sunken, focusedBorderColor = p.warn,
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = fBodyText, onValueChange = { fBodyText = it }, singleLine = true,
                                label = { Text("Body font family", fontFamily = LocalUiFont.current, fontSize = 13.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = p.panelHi, focusedContainerColor = p.panelHi,
                                    unfocusedBorderColor = p.sunken, focusedBorderColor = p.warn,
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsHint("e.g. Playfair Display, Inter, Lora. Saved on save & connect.", p)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("COLOURS — CUSTOM THEME", p)
                        SegmentedControl(listOf("Ripe", "Midnight", "Paper", "Contrast"), presetSelection, p) {
                            presetSelection = it
                            colorsState = COLOR_PRESETS[it] ?: emptyMap()
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.sunken)
                                .clickable { showColors = !showColors }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (showColors) "▾" else "▸", fontFamily = LocalDisplayFont.current, fontSize = 12.sp, color = p.muted)
                            Spacer(modifier = Modifier.width(7.dp))
                            Text("Customise each colour", fontFamily = LocalDisplayFont.current, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        }
                        if (showColors) {
                            Spacer(modifier = Modifier.height(6.dp))
                            COLOR_TOKENS.forEach { (key, label) ->
                                ColorRow(label, colorsState[key] ?: RipeDefaultColors[key] ?: "#000000", p) { v ->
                                    colorsState = colorsState + (key to v)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Reset all colours", fontFamily = LocalUiFont.current, fontSize = 13.sp,
                                color = p.ink2, textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { colorsState = emptyMap() }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection("MOTION", p)
                        SegmentedControl(listOf("On", "Off"), animSelection, p) { animSelection = it }
                        SettingsHint("Panels rise in and figures count up. Choose Off to reduce motion.", p)
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
                        if (signedIn) {
                            Text(
                                "Signed in as $cloudEmail — readings and settings are shared with every device on this account.",
                                fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted,
                                lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            Text(
                                "Not signed in — sign in to share readings and settings across your devices.",
                                fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted,
                                lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        Text("DISPLAY NAME", fontFamily = LocalDisplayFont.current, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            value = if (signedIn) nameText else "Guest",
                            onValueChange = { nameText = it },
                            enabled = signedIn,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = p.panelHi,
                                focusedContainerColor = p.panelHi,
                                unfocusedBorderColor = p.sunken,
                                focusedBorderColor = p.warn,
                                disabledContainerColor = p.sunken,
                                disabledTextColor = p.muted,
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 15.sp, color = p.ink),
                            modifier = Modifier.fillMaxWidth()
                        )
                        SettingsHint(
                            if (signedIn) "Shown in the greeting — works for Google and email accounts."
                            else "Guests always show as “Guest”. Sign in to set a name.", p
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CloudButton(if (signedIn) "Manage account" else "Sign in", filled = true, enabled = !cloudBusy, p = p) { onOpenAuth() }
                            if (signedIn) CloudButton("Sign out", filled = false, enabled = !cloudBusy, p = p) { onCloudSignOut() }
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
                                        settings.copy(
                                            theme = themeSelection,
                                            font = fontSelection,
                                            mode = modeSelection,
                                            source = sourceSelection,
                                            host = hostIp,
                                            port = port,
                                            routing = routingSelection,
                                            pollingMs = pollMs,
                                            sync = syncSelection,
                                            anim = animSelection,
                                            name = if (signedIn) nameText.trim() else settings.name,
                                            fHead = if (fontSelection == "Custom") fHeadText.trim() else settings.fHead,
                                            fBody = if (fontSelection == "Custom") fBodyText.trim() else settings.fBody,
                                            colors = colorsState
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

/** One editable colour token: label + CSS text field + live swatch. */
@Composable
fun ColorRow(label: String, value: String, p: RipePalette, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontFamily = LocalUiFont.current, fontSize = 12.sp, color = p.muted,
            maxLines = 1, modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v
                val parsed = parseCssColor(v, Color.Transparent)
                if (parsed.alpha > 0f) onChange(v)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = p.panelHi,
                focusedContainerColor = p.panelHi,
                unfocusedBorderColor = p.sunken,
                focusedBorderColor = p.warn,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.ink),
            modifier = Modifier.width(122.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parseCssColor(value, Color.Gray))
        )
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
    onOpenSettings: () -> Unit,
    onOpenAuth: () -> Unit,
    onLayoutChange: (List<BoardItem>) -> Unit
) {
    var rawValue by remember { mutableStateOf(1398) }
    var avgValue by remember { mutableStateOf(1398) }
    var percentage by remember { mutableStateOf(7) }
    var status by remember { mutableStateOf("FRESH") }
    var offline by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf("—") }
    val p = activePalette(settings)
    val basic = settings.mode == "Basic"
    val animOn = settings.anim != "Off"
    val signedInDash = cloudEmail != null
    val greetName = if (!signedInDash) "Guest"
        else settings.name.trim().ifBlank { cloudEmail.orEmpty().substringBefore("@") }.ifBlank { "Guest" }
    val hourNow = remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    val greetWord = when {
        hourNow.value < 12 -> "Good morning"
        hourNow.value < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    var clockText by remember { mutableStateOf("") }
    val shownRaw by animateIntAsState(
        targetValue = rawValue,
        animationSpec = tween(durationMillis = if (animOn) 600 else 0),
        label = "raw"
    )
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

    // Live clock under the greeting (updates every second).
    LaunchedEffect(Unit) {
        while (true) {
            clockText = timeFormat.format(Date())
            delay(1000)
        }
    }

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

    if (settings.mode == "Custom") {
        CustomBoardScreen(
            p = p,
            layout = settings.layout,
            onLayoutChange = onLayoutChange,
            greetWord = greetWord,
            greetName = greetName,
            clockText = clockText,
            signedInDash = signedInDash,
            cloudEmail = cloudEmail,
            onOpenAuth = onOpenAuth,
            onOpenSettings = onOpenSettings,
            rawValue = if (animOn) shownRaw else rawValue,
            percentage = percentage,
            status = status,
            lastSeen = lastSeen,
            chipLabel = chipLabel,
            chipColor = chipColor,
            logs = logs,
            trend = trendData,
            floor = devFloor,
            warn = devWarn,
            spoiled = devSpoiled
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        item {
            // Header + plain greeting line (no card), with a live clock.
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp, top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ripe FR-1", fontFamily = LocalDisplayFont.current, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = p.ink, letterSpacing = (-0.2).sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(chipColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(chipLabel, fontFamily = LocalUiFont.current, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.muted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenAuth() }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text("Account", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text("Settings", fontFamily = LocalUiFont.current, fontSize = 13.sp, color = p.ink)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("$greetWord, $greetName", fontFamily = LocalDisplayFont.current, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    "$clockText  ·  " + if (signedInDash) "syncing as $cloudEmail" else "this device only",
                    fontFamily = LocalUiFont.current, fontSize = 12.5.sp, color = p.muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        item {
            if (basic) return@item
            Reveal(animOn) {
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
                            text = String.format("%,d", if (animOn) shownRaw else rawValue),
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
        }

        item {
            Reveal(animOn) {
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
        }
        
        item {
            if (basic) return@item
            Reveal(animOn) {
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
        }

        item {
            Reveal(animOn) {
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
        }

        item {
            Reveal(animOn) {
            // 5. Bench setup — the dashboard's one custom illustration (web #6)
            ChamberCard(p)
            Spacer(modifier = Modifier.height(24.dp))
            }
        }

        item {
            if (basic) return@item
            Reveal(animOn) {
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
