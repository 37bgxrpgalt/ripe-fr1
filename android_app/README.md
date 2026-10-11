# Ripe FR‑1 — Android app

The native Android dashboard for the **Ripe FR‑1** food‑freshness meter. It mirrors
the web dashboard 1:1 (Braun‑inspired warm‑gray UI, Space Grotesk + Urbanist,
dark mode, Basic / Advanced view) and adds:

- **Wi‑Fi / BLE / mock** data sources,
- **Settings that follow the account** — theme, typeface, Basic/Advanced and the
  poll interval sync through Firebase,
- **Cloud sync** — one device *publishes* its readings, others *follow*,
- **Email + password and Google sign‑in** (via Credential Manager).

| | |
|---|---|
| Package | `com.example.foodmonitor` |
| App name | **Ripe FR‑1** |
| Min SDK | 24 (Android 7) |
| Compile / target SDK | 34 |
| Kotlin / AGP | 1.9.22 / 8.2.2 |
| UI | Jetpack Compose (BOM 2024.02.00), Material 3 |

---

## Download & install (no build needed)

1. Open the repo's **Releases** page (right‑hand sidebar of the GitHub repo) and
   download the latest **APK** asset (for v1.0.0: **`RipeFR1-v1.0.0.apk`**).
2. On the phone, allow *Install unknown apps* for whichever app you use to open
   the file (browser / Files).
3. Tap the APK to install. Debug APKs are self‑signed, so Android may warn about
   an "unknown developer" — that is expected for sideloaded builds.

---

## Build from source

1. Open **this folder** (`android_app/`) in **Android Studio** (Hedgehog or newer)
   and allow the Gradle sync to finish.
2. **Add your own Firebase config.** In the [Firebase console](https://console.firebase.google.com/)
   create (or open) a project, add an **Android** app with package name
   `com.example.foodmonitor`, download `google-services.json`, and place it at
   `app/google-services.json`. Without this file the build fails with a clear
   "File google-services.json is missing" message.
3. *(Optional, for Google sign‑in)* Register your signing **SHA‑1** under
   *Project settings → your Android app → Add fingerprint*, then re‑download
   `google-services.json`.
4. Run ▶ on a device or emulator that has **Google Play services**.

Command line (with the bundled Gradle wrapper):

```bash
./gradlew :app:assembleDebug        # macOS / Linux
gradlew.bat :app:assembleDebug      # Windows
# output: app/build/outputs/apk/debug/app-debug.apk
```

For a release APK, add a `signingConfig` in `app/build.gradle.kts` and run
`:app:assembleRelease`.

---

## Secrets — what is *not* here

- **`app/google-services.json`** is intentionally **not** committed. It is public
  config, but keeping it out means the repo carries no project‑specific keys; any
  `api_key` in it would be readable by anyone who could already build the app.
  Supply your own to build (step 2 above).
- **Wi‑Fi credentials** live in the **firmware**
  (`esp32/food_spoilage_monitor/config.h`), which is git‑ignored. This folder
  contains none.
- Keystores (`*.jks`, `*.keystore`) are git‑ignored.

---

## BLE device name

The app scans for the BLE name **`FoodMonitor`**, which is exactly what the
firmware advertises (`BLE_DEVICE_NAME` in
`esp32/food_spoilage_monitor/config.h`). Do not rename one without the other.
The Nordic‑UART service/characteristic UUIDs live in `MainActivity.kt`
(`object BleUart`).

---

## Layout

```
android_app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/foodmonitor/
│       │   ├── MainActivity.kt     ← the whole UI (dashboard, settings, BLE, cloud)
│       │   └── RipeCloud.kt        ← Firebase auth + Firestore sync
│       └── res/                    ← fonts, launcher icon, strings
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat + gradle/wrapper/
└── STATEAPP.MD                     ← app working log
```

See the root [`README.md`](../README.md) for the whole project and
[`FIREBASE_SETUP.md`](../FIREBASE_SETUP.md) for the cloud setup.
