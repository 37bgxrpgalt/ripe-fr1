# STATEAPP.MD — Smart Food Monitor · Complete Project Guide
**Project:** Ripe FR-1 — Food Freshness Meter  
**Hardware:** ESP32 DevKit + MQ-3 Gas Sensor  
**Session Date:** 09-10-2026  
**Last Updated:** 2026-10-11 IST  

---

## ▶ Session 2026-10-10 (Android round)
- App moved to **`D:\FoodMonitorApp\`** (regenerable caches excluded).
- **Fonts fixed (P1)** — Space Grotesk + Urbanist bundled as variable TTFs in
  `res/font/`; the GMS downloadable-fonts path was removed.
- **Firmware BLE ACK typo fixed (P5)** — `cfg.w` → `cfg.warn`.
- **Settings wired to behavior (P3 core)** — source / host / port / routing /
  polling now drive live fetching; Test connection works.

## ▶ Session 2026-10-11 (dark mode)
- Settings → Appearance (**Light / Dark / System**) now live-switches the whole
  app palette to match `web/index.html` exactly via a `RipePalette`
  (light: `#e8e4db` bg / dark: `#1b1815` bg, `#f2eee5` ink, fresh `#79a878`
  & spoiled `#d86a4f` in dark, verdict tints, chart line `#c9c2b3`,
  readout `#0e0d0b`). The graphite readout card and chart line swap too.
- Applies on **Save & connect** (theme is stored in `RipeSettings`).
- **Settings now persist** — `RipeSettings` is saved to SharedPreferences on
  Save & connect and loaded at startup, so choices survive app restarts
  (fixes "settings revert when the app reopens"). The sheet's state is keyed
  on the current settings (`remember(settings)`), so reopening always shows
  the last saved values.

## ▶ Session 2026-10-11 (BLE client + Calibration)
- **BLE client (P4) done** — `BluetoothLeScanner` scans for the **`FoodMonitor`**
  device, connects over GATT, subscribes to the Nordic-UART TX characteristic
  (`6e400003-…`) for notifications and writes `GET` / `CFG:w,s,f,c` to RX
  (`6e400002-…`). The manifest carries the Android 12+ `BLUETOOTH_SCAN`
  (`neverForLocation`) + `BLUETOOTH_CONNECT` runtime permissions plus the
  pre-31 `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION`
  (`maxSdkVersion="30"`) fallbacks; the app requests them at runtime and shows
  the link status under the readout.
- **Calibration screen done** — the dashboard now ends with a **Calibration**
  card mirroring the web panel: Warning / Discard / 0 % / 100 % fields, live
  rule validation, **Undo changes** and **Use suggested** (1600 / 2600 / 1000 /
  3800). **Save to device** writes over Wi-Fi (`POST /config`), over BLE
  (`CFG:w,s,f,c`) or accepts locally in Mock mode. Thresholds are seeded from
  the device's first reading so Undo returns to the real device values.
- **Local build verified** — see the command-line recipe under Step 4; both
  features compile with **zero warnings**.

## ▶ Session 2026-10-11 (Editorial fonts + app icon)
- **Editorial font variant done** — `Merriweather` + `PT Sans` **static TTFs**
  bundled in `res/font/` (`merriweather.ttf`, `merriweather_bold.ttf`,
  `pt_sans.ttf`, `pt_sans_bold.ttf`; downloaded from
  `@expo-google-fonts/merriweather` via jsDelivr and the `google/fonts` repo).
  A `LocalDisplayFont` / `LocalUiFont` (`compositionLocalOf`) pair is provided
  by `FoodMonitorApp` from `settings.font`, and every text call site now reads
  `LocalDisplayFont.current` / `LocalUiFont.current`, so the Settings →
  Appearance **Grotesk / Editorial** toggle re-skins the whole app — mirroring
  the web `[data-font]` attribute switch. The hint text matches the web's
  wording exactly (`Grotesk: Space Grotesk + Urbanist.` /
  `Editorial: Merriweather for headings and figures, PT Sans for body text.`).
- **App icon done** — the placeholder `@android:drawable/sym_def_app_icon` is
  gone. The icon is the web dashboard's **chamber illustration** redrawn as a
  vector: graphite field `#23211D`, paper `#F5F2EB` line art, amber `#EDB100`
  status LED. Files: `drawable/ic_launcher_background.xml`,
  `drawable/ic_launcher_foreground.xml`,
  `drawable/ic_launcher_monochrome.xml` (Android 13+ themed icons),
  `mipmap-anydpi-v26/ic_launcher(_round).xml` (adaptive),
  `mipmap-anydpi/ic_launcher(_round).xml` (legacy vector, API 24-25). The
  manifest now references `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`.
  *(Web parity: the dashboard now carries the same mark as
  `D:\Webserver\web\favicon.svg`, served by the firmware at `/favicon.svg`.)*
- **Local build verified** — clean `:app:assembleDebug` (0 warnings, 0 errors);
  APK ≈ 8.73 MB (four fonts add ~0.66 MB).

## ▶ Session 2026-10-11 (chamber illustration — last web⇄app parity item)
- **The dashboard's one custom graphic is now in the app.** The web's bench-setup
  illustration (`web/index.html` section 6, `viewBox="0 0 240 132"`) renders as
  **`ChamberIllustration()`** — a Compose `Canvas` drawn in the SVG's own
  240×132 coordinate space, scaled uniformly and centred/letterboxed exactly like
  the SVG's default `preserveAspectRatio="…meet"`, and capped at **148 dp** tall
  (the web's `max-height:148px`). Every element is reproduced in the same draw
  order: lid + container (`drawRoundRect`), probe stem + grille, probe head,
  amber status LED, the two rising-vapour Bézier curves, the filled sample mound
  and two sample grains.
- **Palette-driven, so it flips with the theme** — stroke weights 2.4 / 1.5 and
  all colours come from the live `RipePalette` (`p.ink`, `p.sunken`, `p.warn` =
  accent `#edb100`), mirroring the web CSS-variable SVG in light *and* dark mode.
  **`ChamberCard()`** wraps it in the panel (`#f5f2eb` / dark `#24211c`, radius
  14, padding 18 / 20 / 16) with the caption verbatim
  (*"Bench setup — 2.5 L sealed container, 40 g sample, probe at the neck."*),
  and is inserted between **Status History** and **Calibration**, matching the
  web's bento order (device → bench setup → calibration).
- **Local build verified** — clean `:app:assembleDebug` (0 warnings, 0 errors);
  APK **8,827,721 bytes**. *(Note: `Path.arcToPoint` isn't available in this
  Compose version, so the mound's rounded corners use
  `arcTo(Rect(…), startAngle, sweep, false)`.)*

## ▶ Session 2026-10-11 (Dashboard Basic / Advanced mode)
- **New Settings → DASHBOARD choice (Basic / Advanced)** — mirrors the web's
  `Settings → Dashboard` switch. **Advanced** is the dashboard as before;
  **Basic** hides the readout, gauge, VOC trend and calibration and shows the big
  verdict word, a **plain-language sentence**, a **`last sample HH:mm:ss`** line,
  a **plain status history** (time + status, counts hidden) and the chamber
  illustration — for people who don't read ADC counts.
- **Parity fixes (same day)** — `Reading` now carries `avg` (parsed from
  `/data` + BLE, falling back to `raw`), so the Advanced verdict sentence is
  computed from live values exactly like the web's `hintText()` (e.g.
  "2,552 counts — 952 past the 1,600-count warning line…"); and the line under
  it now shows the device's live thresholds
  ("warning 1,600 · discard 2,600 counts"), not hard-coded 1800/2800.
- **Plain copy (Basic)** — `Still fresh — safe to eat.` / `Starting to spoil —
  check it soon.` / `Spoiled — do not eat.` / `Waiting for the first sample…`,
  verbatim from the web's `plainText()`. The Advanced hints are unchanged.
- **Gating without layout churn** — each number panel's `LazyColumn` item opens
  with `if (basic) return@item`, so in Basic mode those items compose to nothing
  (zero height); the verdict panel scales up (40 sp word, 17 sp hint, `ink`
  colour) and drops the `warning … • discard … counts` line.
- **Persisted** — `RipeSettings.mode` (`"Basic"` / `"Advanced"`, default
  `"Advanced"`) is saved to SharedPreferences with the other settings, so it
  survives restarts (the web keeps it in `localStorage`).
- **Local build verified** — clean `:app:assembleDebug` (0 warnings, 0 errors);
  APK **8,838,827 bytes**.

## ▶ Session 2026-10-11 (cloud sync — shared settings + readings)
- **Firebase is wired into the app** — email + password sign-in and Firestore,
  the exact model the web dashboard uses, so a phone and a browser on the **same
  account** share **settings** (`users/{uid}/prefs/main`) and **readings**
  (`users/{uid}/state/latest` + one `readings/{id}` doc per status change).
- **New `RipeCloud.kt`** — the Android mirror of `web/firebase.js`: `watchAuth`,
  `signIn` / `createAccount` / `signOutNow`, `watchPrefs` / `pushPrefs`,
  `publish` / `watchLatest` / `recentReadings`. Same 10 s publish throttle, the
  same immediate write on a status change, and the same field names. It
  normalises the app's `"Dark"` / `"Editorial"` / `"Basic"` to the web's
  `"dark"` / `"editorial"` / `"basic"` at the Firestore boundary.
- **Gradle** — added the `com.google.gms.google-services` plugin (4.4.2) and the
  Firebase BOM `32.7.4` (`firebase-auth-ktx`, `firebase-firestore-ktx`).
  **`app/google-services.json` is required** or the build fails with a clear
  message — steps are in `D:\Webserver\FIREBASE_SETUP.md`.
- **Settings sheet** — new **Account** group (Email + Password fields, *Sign in* /
  *Create account* / *Sign out*, plain-language errors mirroring the web's
  `acctError()`) and a **Cloud sync** group (Off / Publish this device / Follow the
  cloud), same order and wording as the web. `RipeSettings.sync` is persisted in
  SharedPreferences with the rest.
- **Behaviour** — signed in, the app **reads and writes `prefs/main`** live (a
  phone picks up the browser's theme/font/mode/poll interval, and vice versa:
  saving in the sheet pushes); it can **publish** its local Wi-Fi/BLE/mock
  reading to the account, or **follow** the cloud (local polling stops, the cloud
  doc drives the dashboard, and the account's recent history seeds the Status
  panel). The connection chip shows **`CLOUD · <account>`** while following.
- **Not verified on this PC** — the Firebase dependencies can't be fetched with
  `--offline`, so there was **no compile check here**; build/run in Android
  Studio (online) after adding `google-services.json` and enabling the
  Email/Password provider.

## ▶ Session 2026-10-11 (Google sign-in on Android)

- **Why** — the web already had *Use Google instead*; the app only had email +
  password. Added the same button so a phone can sign in with Google too (and
  therefore share a `uid` — and all synced data — with a browser signed into the
  same Google account).
- **`RipeCloud.kt`** — `googleSignIn(context, onDone)` / `googleSignOut(context)`.
  Sign-in uses **Credential Manager** (`GetGoogleIdOption` with
  `setServerClientId(default_web_client_id)` — the OAuth *web* client the
  `google-services` plugin writes from `google-services.json` —
  `setAutoSelectEnabled(false)`), takes the returned Google ID token, and
  exchanges it for a `GoogleAuthProvider` credential. Its bottom sheet **is** the
  account chooser, so it appears even with a single device account. It tries the
  already-authorised accounts first, then all accounts (the documented
  fallback), and maps `GetCredentialCancellationException` /
  `NoCredentialException` / `GoogleIdTokenParsingException` to plain-language
  messages. Once signed in the uid is identical, so **all existing sync code is
  reused unchanged**.
- **`MainActivity.kt`** — the Settings **Account** group gained a quiet **Use
  Google instead** button (Sign in / Create account / Use Google instead),
  matching the web; it launches the flow from a `rememberCoroutineScope`.
  **Sign out** also calls `clearCredentialState()` so the chooser reappears.
- **Gradle** — added `androidx.credentials:credentials:1.3.0`,
  `androidx.credentials:credentials-play-services-auth:1.3.0` and
  `com.google.android.libraries.identity.googleid:googleid:1.1.0` (replacing the
  earlier `play-services-auth` attempt). No manifest change (INTERNET is already
  declared).
- **Setup required once** — register the debug **SHA‑1** in Firebase (Project
  settings → Android app → *Add fingerprint*) and re‑download
  `google-services.json`. The current app's `google-services.json` already
  contains the OAuth web client (`client_type=3`), so `default_web_client_id`
  exists; the SHA‑1 is what makes Google accept the app. Device/emulator needs
  **Google Play services**. Details in `D:\Webserver\FIREBASE_SETUP.md`.
- **Not verified on this PC** — same caveat: no compile check here; build/run in
  Android Studio.

## 📁 File Locations

| Component | Path |
|---|---|
| **Android App** | `D:\FoodMonitorApp\` *(on the other PC: `C:\Users\reyansh\Desktop\Simple food detector\FoodMonitorApp`)* |
| **ESP32 Firmware** | `D:\Webserver\esp32\food_spoilage_monitor\` |
| **Web Dashboard** | `D:\Webserver\web\index.html` |
| **Local Web Server (this PC)** | `D:\Webserver\` |
| **Full ZIP Backup** | `D:\SmartFoodProject.zip` |

---

## 🚀 HOW TO USE THIS PROJECT

### Step 1 — Wire Up the Hardware

```
ESP32 GPIO 34  ←──[ 10kΩ ]──┬──[ 10kΩ ]── GND
                             │
                        MQ-3 AO pin

MQ-3 VCC → ESP32 5V (or external 5V supply)
MQ-3 GND → GND
```

> ⚠️ **IMPORTANT:** MQ-3 AO can swing up to 5V. GPIO 34 is NOT 5V tolerant.  
> Always use the voltage divider above (10kΩ + 10kΩ) so the signal never exceeds 3.3V.  
> Always use GPIO 34 (ADC1). Never use ADC2 pins (32–39) — they conflict with Wi-Fi.

**First-time MQ-3 burn-in:** Run the heater for **24–48 hours** before taking any readings.  
**Before each session:** Let the sensor warm up for **2–5 minutes**.

---

### Step 2 — Flash the ESP32

1. Open **Arduino IDE** (or VS Code + PlatformIO).
2. Open `esp32/food_spoilage_monitor/food_spoilage_monitor.ino`.
3. Open `config.h` and edit your Wi-Fi credentials:
   ```cpp
   #define WIFI_SSID     "YOUR_SSID"
   #define WIFI_PASSWORD "YOUR_PASSWORD"
   ```
4. Select your board: **Tools → Board → ESP32 Dev Module**.
5. Select the correct COM port: **Tools → Port → COMX**.
6. Click **Upload**.
7. Open **Serial Monitor** at `115200 baud` — it will print the IP address once connected.

---

### Step 3 — Access the Web Dashboard

#### Option A: Home Wi-Fi (normal)
- The Serial Monitor will print something like: `Dashboard: http://192.168.1.50`
- Type that IP address into any browser on the same Wi-Fi network.

#### Option B: No Router (AP / Hotspot fallback)
- If the ESP32 can't find your router, it automatically creates its own hotspot.
- On your phone, go to **Wi-Fi settings** → connect to `FoodMonitor-XXXX`.
- Open your browser and go to `http://192.168.4.1`.

#### Option C: Bluetooth only (no Wi-Fi at all)
- Download **Serial Bluetooth Terminal** or **nRF Connect** from the Play Store.
- Connect to the BLE device named `FoodMonitor`.
- Send `GET` to receive a JSON snapshot of the current reading.
- The device also pushes data automatically every 2 seconds while connected.

---

### Step 4 — Use the Android App

#### Building the app
1. Open **Android Studio** (make sure SDK + emulator are installed via SDK Manager).
2. Click **Open** → navigate to `D:\FoodMonitorApp`.
3. Wait for Gradle to sync (it downloads all dependencies automatically).
4. Connect your Android phone via USB (or use the emulator — use a **Google APIs** image, not plain AOSP, so downloadable fonts work).
5. Click the green **Run (▶)** button.

##### Building from the command line (no Android Studio)
Gradle 8.4 + AGP 8.2.2 require **JDK 17** — the current Android Studio JBR is
JDK 25 and is rejected by Gradle 8.4. A JDK 17 is cached at
`C:\Users\reyansh\.jdks\jbr-17.0.14`. On this PC:

```powershell
$env:JAVA_HOME = 'C:\Users\reyansh\.jdks\jbr-17.0.14'
& 'C:\Users\reyansh\.gradle\wrapper\dists\gradle-8.4-bin\1w5dpkrfk8irigvoxmyhowfim\gradle-8.4\bin\gradle.bat' `
    :app:assembleDebug --offline
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Drop
`--offline` if a dependency needs downloading.

#### Connecting the app to the ESP32
The app defaults to **Mock Mode**; switch to a real ESP32 from **Settings → Data
source → ESP32 (Wi-Fi)** → enter the device's host/IP + port (printed on the
Serial Monitor) → **Save & connect**. For reference, the equivalent manual
wiring (still valid) is:

1. Open `MainActivity.kt` (at `app/src/main/java/com/example/foodmonitor/`).
2. Find the `FoodMonitorMockUI` composable.
3. Replace the mock `LaunchedEffect` block with this:
   ```kotlin
   LaunchedEffect(Unit) {
       val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
       while (true) {
           try {
               // Change IP to your ESP32's address
               val result = fetchData("http://192.168.4.1/data")
               val json = JSONObject(result)
               rawValue = json.optInt("raw", 0)
               percentage = json.optInt("pct", 0)
               val newStatus = json.optString("status", "FRESH")
               if (newStatus != status) {
                   logs.add(0, LogEntry(dateFormat.format(Date()), newStatus, rawValue))
                   if (logs.size > 5) logs.removeLast()
               }
               status = newStatus
               trendData.add(rawValue)
               if (trendData.size > 50) trendData.removeFirst()
           } catch (e: Exception) {
               // Show offline state
           }
           delay(2000)
       }
   }
   ```
4. Add the `fetchData` function back at the bottom of the file:
   ```kotlin
   suspend fun fetchData(urlString: String): String {
       return withContext(Dispatchers.IO) {
           val connection = URL(urlString).openConnection() as HttpURLConnection
           connection.connectTimeout = 1500
           connection.readTimeout = 1500
           try { connection.inputStream.bufferedReader().use { it.readText() } }
           finally { connection.disconnect() }
       }
   }
   ```
5. Make sure your phone is on the **same Wi-Fi network as the ESP32** (or connected to the ESP32's hotspot).

#### Calibrating thresholds from the app
The dashboard now ends with a **Calibration** card that mirrors the web panel:
**Warning ≥ / Discard ≥ / 0 % = / 100 % =** fields with live rule validation,
**Undo changes** and **Use suggested** (1600 / 2600 / 1000 / 3800). **Save to
device** writes the values over Wi-Fi (`POST /config`), over BLE
(`CFG:w,s,f,c` on the Nordic-UART RX characteristic) or accepts them locally in
Mock mode. The ESP32 stores new values in flash memory (survives reboots).

---

### Step 5 — Calibrate the Sensor

1. Power the ESP32 and MQ-3. Let warm up for 5 minutes.
2. Place the sensor in **clean room air**. Watch the raw ADC value in the dashboard.
3. Note the **stable baseline** (e.g., 1200 counts). This is your **Floor**.
4. Hold a piece of **slightly spoiled food** near the sensor. Note the peak value (e.g., 3400).
5. In the web dashboard **Calibration panel**, set:
   - `0% =` (Floor): your clean-air baseline (e.g., `1200`)
   - `WARNING >`: 15–25% above baseline (e.g., `1500`)
   - `DISCARD >`: 60–70% of your spoiled-food peak (e.g., `2400`)
   - `100% =` (Ceiling): your measured spoiled-food peak (e.g., `3400`)
6. Click **Save**. Values are stored in ESP32 flash (NVS).

> **Rule:** `floor < warning < discard < ceiling` (all 0–4095)

---

## 🔧 WHAT TO DO NEXT (Pending Tasks)

### High Priority
- [x] **Connect Android app to real ESP32** — done 2026-10-10: **Settings →
      Data source** now switches the app to live `GET /data` polling (or
      `/proxy/data` through the local server); real-ESP32 fetch implemented in
      `MainActivity.kt`
- [x] **Test BLE connection on Android** — done 2026-10-11: the app now scans
      for the `FoodMonitor` device, connects over GATT, subscribes to the
      Nordic-UART TX notifications and writes `GET`/`CFG:` to RX (see the
      session log above). Verify on hardware with nRF Connect first.
- [x] **Fix firmware typo in BLE service** — done 2026-10-10: `cfg.w` →
      `cfg.warn` in the BLE ACK `snprintf`
      (`D:\Webserver\esp32\food_spoilage_monitor\food_spoilage_monitor.ino`)

### Medium Priority
- [x] **Wire Settings dialog to app behavior** — done 2026-10-10: data source,
      ESP32 host/port, routing and polling now drive the live fetch loop
      (`RipeSettings` state in `MainActivity.kt`)
- [x] **Save Settings with DataStore** — done 2026-10-11: `RipeSettings` is
      persisted with **SharedPreferences** (`saveSettings`/`loadSettings`) — zero
      extra dependencies; DataStore can replace it later if desired. Settings
      now survive app restarts (fixed "settings revert when the app reopens").
- [x] **Add Calibration screen** — done 2026-10-11: the dashboard ends with a
      Calibration card mirroring the web panel; it validates the
      `floor < warning < discard < ceiling` rule and writes thresholds via
      `POST /config` (Wi-Fi), `CFG:w,s,f,c` (BLE) or locally in Mock mode.
- [x] **Cloud sync (settings + readings)** — done 2026-10-11: Firebase Auth
      (email + password) + Firestore via `RipeCloud.kt`; signed in, the app shares
      `users/{uid}/prefs/main` live with the web and can publish/follow
      `state/latest` + `readings`. **Build in Android Studio** after adding
      `app/google-services.json` and enabling the Email/Password provider.

### Low Priority / Polish
- [x] **Add a proper app icon** — done 2026-10-11: adaptive icon redrawn from
      the web chamber illustration (graphite field + paper line art + amber LED),
      with a monochrome layer for Android 13+ themed icons and a legacy vector
      for API 24-25. Manifest now uses `@mipmap/ic_launcher`.
- [x] **Dark mode theme** — done 2026-10-11: `RipePalette` + `LightPalette`/
      `DarkPalette` mirror the web tokens exactly and switch live from
      Settings → Appearance.
- [x] **Editorial font variant** — done 2026-10-11: `Merriweather` + `PT Sans`
      static TTFs bundled; `LocalDisplayFont` / `LocalUiFont` composition locals
      switch the whole app from Settings → Appearance (Grotesk / Editorial).
- [x] **Chamber illustration** — done 2026-10-11: the web's SVG line-art bench
      setup now renders in Compose via `ChamberIllustration()` (a `Canvas` drawn in
      the SVG's own 240×132 space, uniformly scaled/letterboxed, capped at 148 dp)
      inside `ChamberCard()`. It is palette-driven, so it flips with light/dark, and
      sits between Status History and Calibration to match the web's bento order.
- [x] **Basic / Advanced dashboard mode** — done 2026-10-11: Settings → DASHBOARD
      toggles between the full instrument (Advanced) and a number-free verdict +
      chamber (Basic); stored in SharedPreferences with the other settings.

---

## 🐛 ERRORS FIXED IN THIS SESSION

| # | Error Message | Root Cause | Fix Applied |
|---|---|---|---|
| 1 | `AAPT: error: resource mipmap/ic_launcher not found` | Manifest referenced default mipmap icons that don't exist in our manually-created project | Changed to `@android:drawable/sym_def_app_icon` in `AndroidManifest.xml` |
| 2 | `Unresolved reference: invoke` on `Dispatchers.IO.invoke {}` | Wrong Kotlin coroutines syntax — `CoroutineDispatcher` is not directly invokable | Replaced with `withContext(Dispatchers.IO) { }` |
| 3 | `Unresolved reference: Baseline` in `Row` | `Alignment.Baseline` does not exist for `Row` vertical alignment in Compose | Replaced with `Alignment.Bottom` |
| 4 | Fonts not changing (still looked like system default) | `SpaceGrotesk` and `Urbanist` were set to `FontFamily.SansSerif` (system fallback placeholder) | Replaced with real `GoogleFont.Provider` + `GoogleFont("Space Grotesk")` / `GoogleFont("Urbanist")` API calls |
| 5 | `font_certs.xml` had fake certificate data | Copy-pasted placeholder cert data would cause a runtime crash when fetching fonts | Superseded — see row 6 |
| 6 | Fonts still not rendering (downloadable fonts) | Google Fonts API silently falls back to the system font when GMS is missing/offline | Bundled actual TTFs into `res/font/` (`space_grotesk.ttf`, `urbanist.ttf` — variable) and removed the GMS dependency + `font_certs.xml` entirely (2026-10-10) |
| 7 | Row 1 placeholder icon (`sym_def_app_icon`) looked unbranded | Needed real launcher resources | Added adaptive + legacy `mipmap-anydpi(-v26)/ic_launcher(_round).xml` with `drawable/ic_launcher_*` vectors and pointed the manifest at `@mipmap/ic_launcher` (2026-10-11) — supersedes row 1 |
| 8 | `Unresolved reference: arcToPoint` on `Path` | Compose `Path` has no `arcToPoint` in this version (unlike the SVG `a` command) | Drew the sample mound's rounded corners with `arcTo(Rect(…), startAngle, sweep, false)` instead (2026-10-11) |

---

## 📦 PROJECT STRUCTURE

```
FoodMonitorApp/                         ← Android App Root
├── STATEAPP.MD                         ← THIS FILE
├── app/
│   ├── build.gradle.kts                ← Dependencies (Compose, Material3, Firebase BOM)
│   ├── google-services.json            ← Firebase project config (REQUIRED; see FIREBASE_SETUP.md)
│   └── src/main/
│       ├── AndroidManifest.xml         ← Permissions (Internet, Bluetooth, Location)
│       ├── java/com/example/foodmonitor/
│       │   ├── MainActivity.kt         ← ALL UI + Mock Data Generator
│       │   └── RipeCloud.kt            ← Firebase auth + Firestore sync
│       └── res/
│           ├── drawable/                ← ic_launcher_background/foreground/monochrome (vectors)
│           ├── font/                    ← Bundled fonts: space_grotesk, urbanist (variable)
│           │                              + merriweather(_bold), pt_sans(_bold) (static)
│           ├── mipmap-anydpi-v26/       ← Adaptive launcher icon (API 26+)
│           ├── mipmap-anydpi/           ← Legacy vector launcher icon (API 24-25)
│           └── values/
│               └── strings.xml          ← App name
├── build.gradle.kts                    ← Root Gradle config
├── settings.gradle.kts                 ← Module includes
├── gradle.properties                   ← JVM + AndroidX settings
└── gradle/wrapper/
    └── gradle-wrapper.properties       ← Gradle 8.4 distribution URL

SMART FOOD Project/                     ← ESP32 + Web Root
├── esp32/food_spoilage_monitor/
│   ├── food_spoilage_monitor.ino       ← Main firmware
│   ├── config.h                        ← Edit Wi-Fi, pins, thresholds here
│   └── web_page.h                      ← Embedded HTML (generated from index.html)
├── web/
│   ├── index.html                      ← Source Ripe FR-1 dashboard (edit this)
│   ├── mock_server.py                  ← Python mock server for testing
│   └── mock_server.ps1                 ← PowerShell mock server alternative
└── tools/
    ├── embed_page.py                   ← Converts index.html → web_page.h
    └── embed_page.ps1                  ← PowerShell version of the above tool
```

---

*STATEAPP.MD — generated 09-10-2026 at 22:27 IST*

---

## ▶ Session 2026-10-11 (rename + GitHub publication)
- **App renamed to "Ripe FR‑1"** — `res/values/strings.xml` (`app_name`) and
  `settings.gradle.kts` (`rootProject.name`). The in‑app header and the web
  wordmark were already "Ripe FR‑1". The **BLE name stays `FoodMonitor`** so
  existing firmware keeps pairing.
- **Google sign‑in moved to Credential Manager** — the classic
  `play-services-auth` SDK silently reused the last account; Credential Manager's
  bottom sheet always appears. Deps: `androidx.credentials:credentials(-play-services-auth):1.3.0`
  + `com.google.android.libraries.identity.googleid:googleid:1.1.0`. Verified on
  device (account sheet appears, no auto‑select).
- **Published in the monorepo** — the full app source now lives with the web app
  in `37bgxrpgalt/ripe-fr1` under **`android_app/`** (web stays `web/`, all in one
  repo). `google-services.json`, keystores and build output are git‑ignored; a
  Gradle **wrapper 8.4** was generated so the folder opens/builds standalone. The
  APK is attached to a GitHub **Release** for other devices to download.

---

## ▶ Session 2026-10-12 (v1.1.0 — look & feel, sign-in page, custom board)
Ported 1:1 from the web v1.1.0 (`web/index.html` is the UI source of truth).
`MainActivity.kt` **compiles cleanly** with the local Gradle 8.4 wrapper + JBR 17
(`--offline`); debug APK builds to `D:\Ripe FR-1 v1.1.0-debug.apk`. **Uncommitted**
— no push/release until the user signs off.

- **Start page**: a store-ready `RipeStartPage` (Email/Password, Create account,
  Continue with Google, **Continue as guest**) shown **first whenever nobody is
  signed in**; a header **Account** button reopens it. Auth controls were removed
  from Settings.
- **Greeting + live clock** under the "Ripe FR-1" title (plain, no card). Guest is
  locked to "Guest" and not editable; a Google/email account's name is editable.
- **Colours**: a third **Custom** theme overriding Light/Dark. `COLOR_TOKENS` (18,
  keyed by the web CSS-var names) + `RipeDefaultColors` + `COLOR_PRESETS` +
  `customPalette` + an `activePalette` Custom branch. Inputs accept `#rgb` /
  `#rrggbb` / `rgba(...)`; the editor is **collapsed by default**.
- **Motion** On/Off with an animated figure count-up.
- **Custom Google Fonts** downloaded at runtime (legacy-UA CSS → TTF) and cached in
  `cacheDir`, falling back to the bundled Grotesk.
- **Custom dashboard**: `BoardItem` grid (6 columns) with **true drag + resize**
  (`detectDragGestures`, overlap nudging via `resolveBoard`); widgets seeded from
  `defaultBoardLayout()` and persisted/synced; dashboard panels **fade/rise in**
  via `Reveal`.
- `RipeSettings` extended with `name`, `anim`, `fHead`, `fBody`, `colors`,
  `layout`, `layoutReady`, `entered`; `RipeCloud.CloudPrefs` / `watchPrefs` /
  `pushPrefs` carry the new fields. Fixed a latent bug where Settings' "Save &
  connect" rebuilt `RipeSettings` from scratch — it now uses `settings.copy(...)`.
