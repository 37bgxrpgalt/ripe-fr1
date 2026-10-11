// config.example.h - copy this to config.h and fill in your own values.
// config.h is git-ignored; never commit real Wi-Fi credentials.

#pragma once
/* ===========================================================================
 *  config.h — user settings for the Smart Food Spoilage Monitor
 *  Edit this file before flashing. Nothing else needs to change.
 * =========================================================================== */

/* ---------------------------- 1. Wi-Fi ---------------------------------- */
// Set ENABLE_WIFI to 0 for a BLE-only device (no router, no hotspot needed):
// the ESP32 then skips Wi-Fi entirely and only advertises over Bluetooth.
#define ENABLE_WIFI      1
#define WIFI_SSID        "YOUR_WIFI_SSID"
#define WIFI_PASSWORD    "YOUR_WIFI_PASSWORD"
#define WIFI_TIMEOUT_MS  15000     // give the router 15 s before falling back

/* ---------------------------- 2. Modes ---------------------------------- */
// BLE stays available even while Wi-Fi runs (the ESP32 time-shares the radio).
// Set to 0 to free a little RAM / improve Wi-Fi range if you never use it.
#define ENABLE_BLE       1
#define BLE_DEVICE_NAME  "FoodMonitor"   // shows up in BLE scanner apps

/* ---------------------------- 3. Hardware -------------------------------- */
// MQ-3 AO -> voltage divider (10k series + 10k to GND = x0.5) -> GPIO 34.
// GPIO 34 is ADC1 (usable with Wi-Fi on) and is NOT 5 V tolerant.
// NEVER use GPIO 32-39 ADC2 pins while Wi-Fi is active.
#define MQ3_PIN              34
#define ADC_SAMPLES_PER_READ 16     // hardware burst-averaging reads
#define AVG_WINDOW           20     // moving-average window length
#define SAMPLE_INTERVAL_MS   500    // sample the sensor every 0.5 s
#define REFRESH_MS           2000   // push/refresh cadence (matches UI poll)

/* ---------------------------- 4. Defaults -------------------------------- */
// Calibrate, then either edit these or change them from the dashboard
// (Calibration card -> Save). Saved values persist in flash (NVS).
// Rule: CAL_FLOOR < THRESH_WARNING < THRESH_SPOILED < CAL_CEIL
#define DEF_THRESH_WARNING 1600
#define DEF_THRESH_SPOILED 2600
#define DEF_CAL_FLOOR      1000
#define DEF_CAL_CEIL       3800
