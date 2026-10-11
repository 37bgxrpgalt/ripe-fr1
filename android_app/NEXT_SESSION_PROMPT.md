# NEXT SESSION PROMPT — Smart Food Monitor (Ripe FR-1)

---

## ▶ Session 2026-10-10 — what changed

- App **moved** from `C:\Users\reyansh\Desktop\Simple food detector\FoodMonitorApp`
  to **`D:\FoodMonitorApp`** (regenerable caches excluded) on this PC.
- **Priority 1 done** — Space Grotesk + Urbanist are now **bundled variable
  TTFs** in `app/src/main/res/font/` (`space_grotesk.ttf`, `urbanist.ttf`); the
  GMS downloadable-fonts path, the `ui-text-google-fonts` dependency and
  `font_certs.xml` were removed. Fonts render offline on any device/emulator.
- **Priority 5 done** — firmware BLE ACK typo `cfg.w` → `cfg.warn`.
- **Priority 3 (core) done** — Settings now drive the app: Data source
  (Mock / ESP32 / BLE placeholder), ESP32 host+port, routing (Via local server /
  Direct from browser), polling 1/2/5 s; live `GET /data` (or `/proxy/data`)
  fetching and a working **Test connection** button.
- **Settings persistence done** — `RipeSettings` saved to SharedPreferences on
  Save & connect and loaded at startup; fixing "settings revert when the app
  reopens". (DataStore can replace SharedPreferences later if wanted.)
- **BLE client (P4) done 2026-10-11** — scans for the `FoodMonitor` device with
  `BluetoothLeScanner`, connects over GATT, subscribes to the Nordic-UART TX
  characteristic for notifications and writes `GET` / `CFG:w,s,f,c` to RX.
  Android 12+ scan/connect runtime permissions plus pre-31 fallbacks are in the
  manifest.
- **Calibration screen done 2026-10-11** — a Calibration card at the end of the
  dashboard mirrors the web panel (thresholds, rule validation, Undo, Use
  suggested, Save to device over Wi-Fi / BLE / mock).
- **Local build verified** — Gradle 8.4 + AGP 8.2.2 build cleanly with **JDK 17**
  (the AS JBR is JDK 25, unsupported by Gradle 8.4):
  ```powershell
  $env:JAVA_HOME = 'C:\Users\reyansh\.jdks\jbr-17.0.14'
  & 'C:\Users\reyansh\.gradle\wrapper\dists\gradle-8.4-bin\1w5dpkrfk8irigvoxmyhowfim\gradle-8.4\bin\gradle.bat' `
      :app:assembleDebug --offline
  ```
- **Editorial font variant done 2026-10-11** — `Merriweather` + `PT Sans` static
  TTFs bundled in `res/font/` (`merriweather.ttf`, `merriweather_bold.ttf`,
  `pt_sans.ttf`, `pt_sans_bold.ttf`). A `LocalDisplayFont` / `LocalUiFont`
  `compositionLocalOf` pair is provided by `FoodMonitorApp` from `settings.font`,
  so the Settings → Appearance **Grotesk / Editorial** toggle re-skins the whole
  app (mirrors the web `[data-font]` switch). The font hint text now matches the
  web wording exactly.
- **App icon done 2026-10-11** — replaced the placeholder
  `@android:drawable/sym_def_app_icon` with an **adaptive icon** built from the
  web dashboard's chamber illustration: `drawable/ic_launcher_background.xml`
  (graphite `#23211D`), `drawable/ic_launcher_foreground.xml` (paper `#F5F2EB`
  line art + amber `#EDB100` LED), a `drawable/ic_launcher_monochrome.xml` layer
  for Android 13+ themed icons, `mipmap-anydpi-v26/ic_launcher(_round).xml`
  (adaptive) and `mipmap-anydpi/ic_launcher(_round).xml` (legacy vector for
  API 24-25). Manifest now points at `@mipmap/ic_launcher`. The web dashboard
  gained the matching tab icon too (`D:\Webserver\web\favicon.svg`, embedded in
  the firmware at `/favicon.svg`).
- **Chamber illustration done 2026-10-11** — the web dashboard's one custom
  graphic (`index.html` section 6, `viewBox="0 0 240 132"`) now renders in the app
  as `ChamberIllustration()` (a `Canvas` drawn in the SVG's 240×132 space, scaled
  uniformly / letterboxed and capped at 148 dp) wrapped by `ChamberCard()`. It is
  palette-driven (`p.ink` / `p.sunken` / `p.warn`) so it flips with light/dark,
  and sits between Status History and Calibration to match the web's order. This
  closes the last web⇄app UI parity item; the software side is feature-complete.
- **Basic / Advanced dashboard mode done 2026-10-11** — new **Settings →
  Dashboard** choice mirroring the web (`index.html` `[data-mode=basic]` +
  `plainText()`/`applyMode()`). **Basic** hides the readout, gauge, VOC trend and
  calibration and shows the verdict word + a plain-language sentence (“Still
  fresh — safe to eat.” / “Starting to spoil — check it soon.” / “Spoiled — do
  not eat.”), a **`last sample`** line, a **plain status history** (no counts) and
  the chamber illustration. Persisted in SharedPreferences (`RipeSettings.mode`)
  on the app, `localStorage` on the web.
- **Parity fixes done 2026-10-11** — `Reading.avg` is parsed and drives the same
  live verdict sentence as the web; the threshold line uses the device's live
  `thr_warn`/`thr_spoiled`.
- **Still open:** nothing on the app side — remaining work is on-device
  verification (BLE pair/notify, `/config` round-trip) and the ESP32 flash.

---

## 🗂️ Context

You are continuing development of the **Ripe FR-1 Smart Food Spoilage Monitor**, a project that uses an **ESP32 + MQ-3 gas sensor** to detect food spoilage. The system consists of:

1. **ESP32 firmware** — reads the MQ-3, hosts a local web server, and advertises BLE UART
2. **Web dashboard** — "Ripe FR-1" aesthetic (Braun / Teenage Engineering inspired), warm gray palette, bento-grid layout, dark mode
3. **Android app** — Jetpack Compose (Material You 3), mirrors the web dashboard layout and design exactly

Read the full session log and project guide at:
```
D:\FoodMonitorApp\STATEAPP.MD   (on the other PC:
C:\Users\reyansh\Desktop\Simple food detector\FoodMonitorApp\STATEAPP.MD)
```

---

## 📁 Key File Locations

| What | Path |
|---|---|
| **Android App root** | `D:\FoodMonitorApp\` *(on the other PC: `C:\Users\reyansh\Desktop\Simple food detector\FoodMonitorApp`)* |
| **Main UI file** | `D:\FoodMonitorApp\app\src\main\java\com\example\foodmonitor\MainActivity.kt` |
| **AndroidManifest** | `D:\FoodMonitorApp\app\src\main\AndroidManifest.xml` |
| **App build.gradle** | `D:\FoodMonitorApp\app\build.gradle.kts` |
| **ESP32 firmware** | `D:\Webserver\esp32\food_spoilage_monitor\food_spoilage_monitor.ino` |
| **Web dashboard source** | `D:\Webserver\web\index.html` (the design reference — 1200+ lines) |

---

## ✅ What Was Done Last Session

- Built a complete Android Studio project from scratch in `FoodMonitorApp/`
- Created full Ripe FR-1 UI in Jetpack Compose matching the web dashboard:
  - Graphite readout panel (dark background, big ADC number)
  - Verdict panel (green/yellow/red tinted, status word + hint)
  - VOC Trend sparkline (Compose Canvas)
  - Status history log (timestamped status-change events)
  - Device stats row
- Added a **Settings dialog** with SegmentedControl toggles for appearance, data source, ESP32 address, routing, and polling — styled exactly like the web version
- Created a **mock data generator** (coroutine, random ADC walk, drives UI live)
- Fixed 5 build errors: missing mipmap, wrong coroutine syntax, Alignment.Baseline, font placeholder, bad font cert
- Attempted to wire up **Space Grotesk + Urbanist** fonts via Compose Google Fonts API

---

## ✅ PRIORITY 1 — RESOLVED: Fonts (bundled TTFs, no GMS needed)

**RESOLVED 2026-10-10:** Space Grotesk + Urbanist are now **bundled variable
TTFs** in `app/src/main/res/font/` (`space_grotesk.ttf`, `urbanist.ttf`) and
wired with `Font(R.font.…)`. The `ui-text-google-fonts` dependency and
`font_certs.xml` were removed — fonts work offline on any device or emulator
image. The original diagnosis is kept below for reference.

**The Problem:**  
Space Grotesk and Urbanist are **not rendering** on the Android app — text still appears in the default system font. The Google Fonts API code is correctly written in `MainActivity.kt`, but the fonts fail to load silently.

**Likely causes to investigate (in order):**
1. **`font_certs.xml` certificate is wrong/malformed** — the file at `app/src/main/res/values/font_certs.xml` was written with a partially real cert. The Google Fonts provider will silently fall back to the system font if cert verification fails.
   - **Fix:** Replace `font_certs.xml` with the exact, up-to-date certs from the official Google Fonts in Compose documentation: https://developer.android.com/jetpack/compose/text/fonts#downloadable-fonts
   - Or download the official `font_certs.xml` from: https://github.com/android/compose-samples

2. **Emulator doesn't have Google Play Services** — the downloadable fonts API requires GMS. A plain AOSP emulator image will silently fail.
   - **Fix:** In Android Studio AVD Manager, make sure the emulator uses a **"Google APIs"** or **"Google Play"** system image, not the plain "Default" image.

3. **Device is offline during first launch** — fonts are downloaded from GMS on first use.
   - **Fix:** Ensure the device/emulator has internet access when running for the first time.

4. **Alternative: Use bundled .ttf files instead of downloadable fonts** — guaranteed to work offline, on any device, no cert issues:
   - Download `SpaceGrotesk-Bold.ttf`, `SpaceGrotesk-Regular.ttf` from https://fonts.google.com/specimen/Space+Grotesk
   - Download `Urbanist-Regular.ttf`, `Urbanist-Bold.ttf` from https://fonts.google.com/specimen/Urbanist
   - Rename all files to lowercase with underscores (Android requirement)
   - Place them in: `app/src/main/res/font/`
   - Replace the `GoogleFont.Provider` block in `MainActivity.kt` with:
     ```kotlin
     val SpaceGrotesk: FontFamily = FontFamily(
         Font(R.font.space_grotesk_regular, FontWeight.Normal),
         Font(R.font.space_grotesk_bold,    FontWeight.Bold)
     )
     val Urbanist: FontFamily = FontFamily(
         Font(R.font.urbanist_regular, FontWeight.Normal),
         Font(R.font.urbanist_bold,    FontWeight.Bold)
     )
     ```
   - Remove the import for `androidx.compose.ui.text.googlefonts.Font` and `GoogleFont`
   - This approach is **simpler and more reliable** — recommended if downloadable fonts keep failing

---

## 🟡 PRIORITY 2 — Connect App to Real ESP32

Currently the app runs a **mock data generator** (random ADC values, no real sensor).  
To connect to the real ESP32:

1. In `MainActivity.kt`, find the `FoodMonitorMockUI` composable
2. Replace the mock `LaunchedEffect` with a real HTTP fetch loop (see STATEAPP.MD Step 4 for the exact code)
3. The ESP32 serves data at `GET /data` — returns JSON like:
   ```json
   {"value":1450,"raw":1450,"avg":1448,"pct":22,"status":"FRESH",
    "thr_warn":1800,"thr_spoiled":2800,"uptime_s":312,"rssi":-55,"mode":"WI-FI","ip":"192.168.1.50"}
   ```
4. Connect phone to same Wi-Fi as ESP32 (or to ESP32's `FoodMonitor-XXXX` hotspot if no router)
5. Update IP address in `fetchData("http://192.168.4.1/data")` to match ESP32's actual IP from Serial Monitor

---

## 🟡 PRIORITY 3 — Wire Settings Dialog to App Behavior

The Settings dialog UI is built and opens correctly, but the toggle selections don't do anything yet.  
Needs to be wired up:
- **Data Source** toggle → switch between mock coroutine / HTTP fetch / BLE scan
- **ESP32 IP + Port** fields → save with `DataStore` / `SharedPreferences`, use in `fetchData()`
- **Polling interval** (1s/2s/5s) → change the `delay()` value in the data fetch loop
- **Theme** (Light/Dark/System) → apply a dark `ColorScheme` in `MaterialTheme`

---

## ✅ PRIORITY 4 — DONE: BLE Android client

Implemented 2026-10-11 in `MainActivity.kt` (class `BleClient`, object
`BleUart`). The ESP32 advertises a **Nordic UART BLE Service**:
- Service UUID: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- TX (notify) UUID: `6e400003-b5a3-f393-e0a9-e50e24dcca9e` ← receive JSON here
- RX (write) UUID: `6e400002-b5a3-f393-e0a9-e50e24dcca9e` ← send `GET`, `PING`, `CFG:w,s,f,c`

What the app does now:
1. Requests `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` at runtime on API 31+ (and
   `ACCESS_FINE_LOCATION` on older versions); the manifest carries the
   `neverForLocation` scan flag and `maxSdkVersion="30"` legacy entries.
2. Scans for the device named `FoodMonitor` using `BluetoothLeScanner`.
3. `connectGatt()` → `discoverServices()` → subscribes to the TX characteristic
   (CCCD `0x2902`) notifications.
4. Parses the JSON notification (same shape as `/data`) into `Reading` and
   pushes it through the shared `applyReading()` path (readout, verdict, trend,
   history, calibration seeding).
5. Sends `GET` on every poll for a fresh snapshot and `CFG:w,s,f,c` from the
   Calibration card.

Still to verify on real hardware (needs an ESP32 + phone): actual pairing, the
notification stream and the `CFG` acknowledgement.

---

## ✅ PRIORITY 5 — DONE: Firmware BLE ACK typo (`cfg.w` → `cfg.warn`)

In `food_spoilage_monitor.ino` around line 372 (inside `serviceBLE()`):
```cpp
// BUG: cfg.w should be cfg.warn
snprintf(ack, sizeof(ack),
  "{\"ok\":true,\"thr_warn\":%d,...}", cfg.w, cfg.spoiled, cfg.floor, cfg.ceil);
//                                    ^^^^^  ← should be cfg.warn
```
**Fixed 2026-10-10** in `D:\Webserver\esp32\food_spoilage_monitor\food_spoilage_monitor.ino`:
replace `cfg.w` with `cfg.warn`.

---

## 📐 Design Reference

The Android app must match the web dashboard (`D:\Webserver\web\index.html`) exactly:
- Background: `#e8e4db` (warm gray)
- Panel: `#f5f2eb`
- Graphite display: `#23211d`
- Accent: `#edb100` (yellow)
- Fresh: `#4c7a4d` (green), Warning: `#edb100` (yellow), Spoiled: `#b23a25` (red)
- Fonts: **Space Grotesk** for headings/numbers, **Urbanist** for body/descriptions (Grotesk pair, default); **Merriweather** + **PT Sans** for the Editorial pair — both bundled in `res/font/` and switched via Settings → Appearance
- Dark mode uses same hue ~40° warm grays (never cool/blue grays)

---

## 🧪 Testing Checklist Before Calling It Done

- [ ] Fonts render as Space Grotesk (headings) and Urbanist (body) — visually distinct from system default
- [ ] Mock mode: values cycle through FRESH → WARNING → SPOILED as the random number rises
- [ ] Settings dialog opens, all toggles work visually
- [ ] Real ESP32 mode: app shows live ADC reading matching the web dashboard
- [ ] BLE mode: app receives data when Wi-Fi is not available (client is implemented; needs a real ESP32 + phone to verify)
- [ ] Calibration card: Save writes thresholds to the device (Wi-Fi / BLE) and they survive a reboot
- [ ] App builds with 0 errors (the 129 red marks in the gutter are lint warnings, not errors — BUILD SUCCESSFUL is what matters)

---

*Prompt created: 09-10-2026 22:33 IST — paste this at the start of your next session*
