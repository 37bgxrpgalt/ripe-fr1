/* ===========================================================================
 *  SMART FOOD SPOILAGE MONITOR — ESP32 + MQ-3
 * ---------------------------------------------------------------------------
 *  Reads an MQ-3 gas sensor (alcohol / VOC vapours from spoiling food) and
 *  serves a Material Design 3 dashboard locally, with FOUR connectivity
 *  modes so you are never stuck without a network:
 *
 *    MODE 1 — Wi-Fi (STA)     Joins your router; dashboard at http://<ip>
 *    MODE 2 — Access Point    Router unavailable? The ESP32 becomes its own
 *                             hotspot; dashboard at http://192.168.4.1
 *                             (phone connects directly to "FoodMonitor-XXXX")
 *    MODE 3 — BLE UART        Nordic-UART-style service, always advertised
 *                             when ENABLE_BLE=1, so readings are available
 *                             to any BLE terminal app with zero Wi-Fi.
 *    MODE 4 — USB (serial)     Plug the ESP32 straight into a PC's USB port
 *                             and run web/usb_server.ps1, which bridges the
 *                             COM port to the dashboard — needs NO Wi-Fi and
 *                             NO Bluetooth at all (see section 8b below).
 *
 *  NO WI-FI AT ALL?  Set ENABLE_WIFI 0 in config.h — the device becomes
 *  BLE-only (fast boot, no router, no hotspot) and the hosted dashboard can
 *  read it over Bluetooth (Settings -> Data source -> Bluetooth in the page).
 *
 *  NO WI-FI AND NO BLUETOOTH EITHER?  Use the USB cable. The firmware also
 *  streams the same JSON over the serial port, and web/usb_server.ps1 (plain
 *  Windows PowerShell, nothing to install) serves the dashboard and bridges
 *  /data + /config to the COM port. Pick "USB (PC)" in the page's Settings.
 *
 *  ENDPOINTS
 *    GET  /        -> Material 3 dashboard (embedded HTML, no external assets)
 *    GET  /data    -> {"value":1450,"raw":..,"avg":..,"pct":..,"status":"FRESH",...}
 *    POST /config  -> {"warn":1600,"spoiled":2600,"floor":1000,"ceil":3800}
 *                     thresholds persist in NVS flash (survive reboots)
 *
 *  BLE UART COMMANDS (write to RX char 6e400002-..., notifications on TX
 *  char 6e400003-..., service 6e400001-b5a3-f393-e0a9-e50e24dcca9e)
 *    "GET"             -> one JSON snapshot (notify)
 *    "CFG:w,s,f,c"     -> set thresholds, replies {"ok":true/false,...}
 *    "PING"            -> "PONG"
 *    (the device also pushes the JSON snapshot every REFRESH_MS)
 *
 *  HARDWARE
 *    ESP32 DevKit
 *     +- GPIO 34 (ADC1, input-only) <- voltage divider <- MQ-3 module AO
 *        MQ-3 VCC -> 5 V,  MQ-3 GND -> GND
 *
 *    IMPORTANT: MQ-3 AO can swing to ~5 V and GPIO 34 is NOT 5 V tolerant.
 *    Use a divider: 10 k in series + 10 k to GND (x0.5, max 2.5 V), or
 *    20 k series + 10 k to GND (x0.67). Keep it on ADC1 (GPIO 32-39 ADC2
 *    pins do not work while Wi-Fi is on).
 *
 *    WARM-UP: 24-48 h heater burn-in on first ever use, then 2-5 minutes
 *    before calibration/measurement readings.
 *
 *  LIBRARIES: none beyond the ESP32 Arduino core (WiFi, WebServer,
 *  Preferences, BLE all ship with it).
 * =========================================================================== */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>                 // NVS flash persistence for thresholds
#include <esp_arduino_version.h>

#include "config.h"                      // <-- edit Wi-Fi + pins + defaults here
#include "web_page.h"                    // generated from web/index.html
#include "favicon_svg.h"                 // generated from web/favicon.svg

/* Stream JSON snapshots over the USB cable (MODE 4). Declared here, right
   after config.h, because the build-time sanity check below needs it. Set
   ENABLE_SERIAL_JSON to 0 in config.h for a CSV-only Arduino Serial Plotter. */
#ifndef ENABLE_SERIAL_JSON
  #define ENABLE_SERIAL_JSON 1
#endif

#if ENABLE_BLE
  #include <BLEDevice.h>
  #include <BLEServer.h>
  #include <BLEUtils.h>
  #if ESP_ARDUINO_VERSION_MAJOR < 3     // Bluedroid needs an explicit CCCD;
    #include <BLE2902.h>                 // NimBLE (core 3.x) creates it itself
  #endif
#endif

#if !ENABLE_WIFI && !ENABLE_BLE && !ENABLE_SERIAL_JSON
  #error "ENABLE_WIFI, ENABLE_BLE and ENABLE_SERIAL_JSON are all 0 - there is no way to read the sensor."
#endif

/* ===========================================================================
 *  1. THRESHOLDS (defaults; overridden by NVS if previously saved)
 *  Rule:  CAL_FLOOR < THRESH_WARNING < THRESH_SPOILED < CAL_CEIL
 * =========================================================================== */
struct Thresholds {
  int warn;
  int spoiled;
  int floor;
  int ceil;
};
Thresholds cfg = {DEF_THRESH_WARNING, DEF_THRESH_SPOILED, DEF_CAL_FLOOR, DEF_CAL_CEIL};

Preferences prefs;

/* ===========================================================================
 *  2. SAMPLING STATE (circular moving-average buffer)
 * =========================================================================== */
int  window[AVG_WINDOW] = {0};
int  wIndex = 0, wCount = 0;
long wSum = 0;

volatile int latestRaw        = 0;
unsigned long lastSample      = 0;
unsigned long lastPush        = 0;
unsigned long lastWifiTry     = 0;

enum NetMode { MODE_STA, MODE_AP, MODE_BLE };
NetMode netMode = MODE_STA;

/* ===========================================================================
 *  3. SENSOR SAMPLING + STATUS
 * =========================================================================== */
int readRawADC() {
  long burst = 0;
  for (int i = 0; i < ADC_SAMPLES_PER_READ; i++) burst += analogRead(MQ3_PIN);
  return (int)(burst / ADC_SAMPLES_PER_READ);
}

void pushSample(int v) {
  if (wCount == AVG_WINDOW) wSum -= window[wIndex];   // evict oldest
  else                      wCount++;
  window[wIndex] = v;
  wSum += v;
  wIndex = (wIndex + 1) % AVG_WINDOW;
}

int smoothed() { return wCount ? (int)(wSum / wCount) : 0; }

/* Status comes from the SMOOTHED value so the badge never flickers. */
const char* statusFor(int avg) {
  if (avg >= cfg.spoiled) return "SPOILED";
  if (avg >= cfg.warn)    return "WARNING";
  return "FRESH";
}

int percentFor(int avg) {
  long span = cfg.ceil - cfg.floor;
  if (span <= 0) return 0;
  int p = (int)((long)(avg - cfg.floor) * 100 / span);
  return constrain(p, 0, 100);
}

/* ===========================================================================
 *  4. JSON BUILDER — shared by the HTTP /data endpoint and BLE pushes
 * =========================================================================== */
const char* modeName() {
  if (netMode == MODE_BLE) return "BLE ONLY";
  #if ENABLE_BLE
    return (netMode == MODE_STA) ? "WI-FI + BLE" : "AP + BLE";
  #else
    return (netMode == MODE_STA) ? "WI-FI" : "AP";
  #endif
}

size_t buildJson(char* buf, size_t len) {
  int avg = smoothed();
  int rssi = 0;                                     // 0 = no Wi-Fi link (AP/BLE)
  String ip = "ble";
  #if ENABLE_BLE
    ip = String("ble://") + BLE_DEVICE_NAME;        // e.g. ble://FoodMonitor
  #endif
  #if ENABLE_WIFI
    if (netMode == MODE_STA) {
      rssi = WiFi.RSSI();
      ip = WiFi.localIP().toString();
    } else if (netMode == MODE_AP) {
      ip = WiFi.softAPIP().toString();
    }
  #endif
  return snprintf(buf, len,
    "{\"value\":%d,\"raw\":%d,\"avg\":%d,\"pct\":%d,\"status\":\"%s\","
    "\"thr_warn\":%d,\"thr_spoiled\":%d,\"cal_floor\":%d,\"cal_ceil\":%d,"
    "\"uptime_s\":%lu,\"rssi\":%d,\"mode\":\"%s\",\"ip\":\"%s\"}",
    latestRaw, latestRaw, avg, percentFor(avg), statusFor(avg),
    cfg.warn, cfg.spoiled, cfg.floor, cfg.ceil,
    (unsigned long)(millis() / 1000), rssi, modeName(), ip.c_str());
}

/* ===========================================================================
 *  5. THRESHOLD PERSISTENCE + VALIDATION
 * =========================================================================== */
void loadCfg() {
  prefs.begin("spoil", true);
  cfg.warn    = prefs.getInt("warn",    DEF_THRESH_WARNING);
  cfg.spoiled = prefs.getInt("spoiled", DEF_THRESH_SPOILED);
  cfg.floor   = prefs.getInt("floor",   DEF_CAL_FLOOR);
  cfg.ceil    = prefs.getInt("ceil",    DEF_CAL_CEIL);
  prefs.end();
  // repair an invalid stored set rather than booting with a broken gauge
  if (!(cfg.floor >= 0 && cfg.floor < cfg.warn && cfg.warn < cfg.spoiled &&
        cfg.spoiled < cfg.ceil && cfg.ceil <= 4095)) {
    cfg = {DEF_THRESH_WARNING, DEF_THRESH_SPOILED, DEF_CAL_FLOOR, DEF_CAL_CEIL};
  }
}

bool applyCfg(int w, int s, int f, int c, String* err) {
  if (!(f >= 0 && c <= 4095 && f < w && w < s && s < c)) {
    *err = "Rule violated: floor < warning < spoiled < ceil (0-4095).";
    return false;
  }
  cfg = {w, s, f, c};
  prefs.begin("spoil", false);
  prefs.putInt("warn", w);    prefs.putInt("spoiled", s);
  prefs.putInt("floor", f);   prefs.putInt("ceil", c);
  prefs.end();
  return true;
}

/* tolerant "key":value extractor — accepts any key order / extra fields */
int jsonInt(const String& body, const char* key, int fallback) {
  String k = String("\"") + key + "\":";
  int i = body.indexOf(k);
  if (i < 0) return fallback;
  return body.substring(i + k.length()).toInt();
}

/* ===========================================================================
 *  6. HTTP ROUTES
 * =========================================================================== */
WebServer server(80);

/* CORS: lets the dashboard poll this device from another origin
   (Settings -> Data source -> ESP32 -> "Direct from browser").            */
void corsHeaders() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
}
void handleOptions() {
  corsHeaders();
  server.send(204, "text/plain", "");
}

void handleData() {
  char json[384];
  buildJson(json, sizeof(json));
  corsHeaders();
  server.send(200, "application/json", json);
}

void handleConfig() {
  if (server.method() == HTTP_OPTIONS) { handleOptions(); return; }
  if (server.method() != HTTP_POST) {
    corsHeaders();
    server.send(405, "application/json", "{\"ok\":false,\"error\":\"POST only\"}");
    return;
  }
  String body = server.arg("plain");   // raw JSON body from the dashboard
  String err;
  int w = jsonInt(body, "warn",    cfg.warn);
  int s = jsonInt(body, "spoiled", cfg.spoiled);
  int f = jsonInt(body, "floor",   cfg.floor);
  int c = jsonInt(body, "ceil",    cfg.ceil);

  if (!applyCfg(w, s, f, c, &err)) {
    corsHeaders();
    server.send(400, "application/json",
                "{\"ok\":false,\"error\":\"" + err + "\"}");
    return;
  }
  corsHeaders();
  server.send(200, "application/json", "{\"ok\":true}");
  Serial.printf("[cfg] saved  warn=%d spoiled=%d floor=%d ceil=%d\n", w, s, f, c);
}

void startWeb() {
  server.on("/", HTTP_GET, []() { server.send_P(200, "text/html", MAIN_page); });
  // Tab icon, linked from the dashboard head. Browsers also probe /favicon.ico;
  // serve the same SVG there so the tab never shows a blank/default icon.
  server.on("/favicon.svg", HTTP_GET, []() { server.send_P(200, "image/svg+xml", FAVICON_svg); });
  server.on("/favicon.ico", HTTP_GET, []() { server.send_P(200, "image/svg+xml", FAVICON_svg); });
  server.on("/data", HTTP_GET, handleData);
  server.on("/config", HTTP_POST, handleConfig);
  server.on("/config", HTTP_OPTIONS, handleOptions);
  server.onNotFound([]() {
    if (server.method() == HTTP_OPTIONS) { handleOptions(); return; }
    corsHeaders();
    server.send(404, "text/plain", "Not found - try / or /data");
  });
  server.begin();
}

/* ===========================================================================
 *  7. Wi-Fi: STA first, SoftAP fallback when the router is unreachable
 * =========================================================================== */
bool connectSTA() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);                       // stable link = snappy UI
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("Connecting to Wi-Fi \"%s\"", WIFI_SSID);
  unsigned long t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < WIFI_TIMEOUT_MS) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    netMode = MODE_STA;
    Serial.print("Dashboard: http://");
    Serial.println(WiFi.localIP());
    return true;
  }
  return false;
}

void startAP() {
  netMode = MODE_AP;
  char name[24];
  snprintf(name, sizeof(name), "FoodMonitor-%04X",
           (uint16_t)(ESP.getEfuseMac() >> 32));
  WiFi.mode(WIFI_AP);
  WiFi.softAP(name);                          // open AP — no router needed
  Serial.println("Router unreachable -> starting own access point.");
  Serial.printf("Join Wi-Fi \"%s\", then open\n", name);
  Serial.print("Dashboard: http://");
  Serial.println(WiFi.softAPIP());
}

void maintainWiFi() {
  if (netMode != MODE_STA) return;            // AP mode is self-sustaining
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - lastWifiTry < 10000) return; // non-blocking retry every 10 s
  lastWifiTry = millis();
  Serial.println("[WiFi] lost - reconnecting...");
  WiFi.reconnect();
}

/* ===========================================================================
 *  8. BLE UART (Nordic UART service) — works with zero Wi-Fi
 * =========================================================================== */
#if ENABLE_BLE
#define NUS_SERVICE "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define NUS_RX      "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // write  (phone->ESP32)
#define NUS_TX      "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // notify (ESP32->phone)

BLEServer*      bleServer   = NULL;
BLECharacteristic* txChar   = NULL;
volatile bool   bleClient  = false;
volatile bool   bleWantPush = false;                     // set by callbacks,
volatile int    cfgW = 0, cfgS = 0, cfgF = 0, cfgC = 0;  // consumed in loop()
volatile bool   cfgPending = false;
volatile bool   cfgWanted  = false;                      // "GET" request

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* p) override {
    bleClient = true;
    Serial.println("[BLE] client connected");
  }
  void onDisconnect(BLEServer* p) override {
    bleClient = false;
    Serial.println("[BLE] client disconnected");
    p->startAdvertising();                    // ready for the next phone
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  /* Runs in the BT task - only latch plain values here, apply them in loop(). */
  void onWrite(BLECharacteristic* c) override {
    auto raw = c->getValue();                 // String or NimBLEAttValue
    String cmd(raw.c_str(), raw.length());
    cmd.trim();
    if (cmd == "GET") {
      cfgWanted = true;
    } else if (cmd == "PING") {
      bleWantPush = true;                     // replies via the JSON path below
      txChar->setValue((uint8_t*)"PONG", 4);
      txChar->notify();
    } else if (cmd.startsWith("CFG:")) {
      int w, s, f, ce;
      if (sscanf(cmd.c_str(), "CFG:%d,%d,%d,%d", &w, &s, &f, &ce) == 4) {
        cfgW = w; cfgS = s; cfgF = f; cfgC = ce;
        cfgPending = true;
      }
    }
  }
};

void blePush(const char* payload, size_t n) {
  if (!txChar) return;
  txChar->setValue((uint8_t*)payload, n);
  txChar->notify();
}

void startBLE() {
  BLEDevice::init(BLE_DEVICE_NAME);
  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());

  BLEService* svc = bleServer->createService(NUS_SERVICE);

  txChar = svc->createCharacteristic(
      NUS_TX, BLECharacteristic::PROPERTY_NOTIFY);
  #if ESP_ARDUINO_VERSION_MAJOR < 3
    BLE2902* cccd = new BLE2902();            // Bluedroid: explicit CCCD
    cccd->setNotifications(true);
    txChar->addDescriptor(cccd);
  #else
    txChar->setNotify(true);                  // NimBLE: CCCD is automatic
  #endif

  BLECharacteristic* rxChar = svc->createCharacteristic(
      NUS_RX, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rxChar->setCallbacks(new RxCallbacks());

  svc->start();

  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(NUS_SERVICE);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.printf("[BLE] advertising as \"%s\" (UART service %s)\n",
                BLE_DEVICE_NAME, NUS_SERVICE);
}

void serviceBLE() {
  /* apply commands latched by the BT callbacks */
  if (cfgPending) {
    cfgPending = false;
    String err;
    char ack[96];
    if (applyCfg(cfgW, cfgS, cfgF, cfgC, &err)) {
      snprintf(ack, sizeof(ack),
               "{\"ok\":true,\"thr_warn\":%d,\"thr_spoiled\":%d,"
               "\"cal_floor\":%d,\"cal_ceil\":%d}", cfg.warn, cfg.spoiled, cfg.floor, cfg.ceil);
      Serial.printf("[BLE] cfg saved warn=%d spoiled=%d floor=%d ceil=%d\n",
                    cfg.w, cfg.spoiled, cfg.floor, cfg.ceil);
    } else {
      snprintf(ack, sizeof(ack), "{\"ok\":false,\"error\":\"%s\"}", err.c_str());
    }
    blePush(ack, strlen(ack));
  }
  if (cfgWanted) { cfgWanted = false; bleWantPush = true; }

  /* periodic snapshot every REFRESH_MS */
  if (bleClient && (bleWantPush || millis() - lastPush >= REFRESH_MS)) {
    bleWantPush = false;
    lastPush = millis();
    char json[384];
    size_t n = buildJson(json, sizeof(json));
    blePush(json, n);
  }
}
#endif // ENABLE_BLE

/* ===========================================================================
 *  8b. USB / SERIAL — the same snapshots over the USB cable (MODE 4)
 * ---------------------------------------------------------------------------
 *  Lets the dashboard run on a PC with NO Wi-Fi and NO Bluetooth at all: plug
 *  the ESP32 into the PC with a USB cable and run web/usb_server.ps1, which
 *  opens the COM port, serves the dashboard, and bridges /data + /config.
 *
 *  The line protocol mirrors the BLE UART one:
 *    device -> PC   one JSON snapshot per REFRESH_MS (same buildJson output)
 *    PC -> device   "GET" | "PING" | "CFG:w,s,f,c"  (replies on one line)
 *
 *  The plain "raw,avg,status" CSV lines are still printed every
 *  SAMPLE_INTERVAL_MS (Serial Plotter). The bridge understands both, so it
 *  even works with older firmware that only emits the CSV lines.
 * =========================================================================== */
#if ENABLE_SERIAL_JSON
static String        usbLine;
static unsigned long lastUsbPush = 0;

static void usbSendLine(const char* s) { Serial.println(s); }

static void usbHandleLine(const String& line) {
  if (line.length() == 0) return;

  if (line.equalsIgnoreCase("GET")) {
    char json[384];
    buildJson(json, sizeof(json));
    usbSendLine(json);
    return;
  }
  if (line.equalsIgnoreCase("PING")) { usbSendLine("PONG"); return; }

  if (line.startsWith("CFG:")) {              // CFG:warn,spoiled,floor,ceil
    int v[4]; int n = 0; int idx = 4;
    while (n < 4) {
      int comma = line.indexOf(',', idx);
      String part = (comma < 0) ? line.substring(idx) : line.substring(idx, comma);
      v[n++] = part.toInt();
      if (comma < 0) break;
      idx = comma + 1;
    }
    if (n != 4) { usbSendLine("{\"ok\":false,\"error\":\"CFG:w,s,f,c\"}"); return; }

    String err;
    char ack[96];
    if (applyCfg(v[0], v[1], v[2], v[3], &err)) {
      snprintf(ack, sizeof(ack),
               "{\"ok\":true,\"thr_warn\":%d,\"thr_spoiled\":%d,"
               "\"cal_floor\":%d,\"cal_ceil\":%d}",
               cfg.warn, cfg.spoiled, cfg.floor, cfg.ceil);
      Serial.printf("[USB] cfg saved warn=%d spoiled=%d floor=%d ceil=%d\n",
                    cfg.warn, cfg.spoiled, cfg.floor, cfg.ceil);
    } else {
      snprintf(ack, sizeof(ack), "{\"ok\":false,\"error\":\"%s\"}", err.c_str());
    }
    usbSendLine(ack);
    return;
  }
}

static void serviceSerial() {
  /* inbound command lines */
  while (Serial.available()) {
    char c = (char)Serial.read();
    if (c == '\r') continue;
    if (c == '\n') { usbHandleLine(usbLine); usbLine = ""; }
    else if (usbLine.length() < 64) usbLine += c;
  }

  /* outbound snapshot, on the same cadence as the BLE push */
  if (millis() - lastUsbPush >= REFRESH_MS) {
    lastUsbPush = millis();
    char json[384];
    buildJson(json, sizeof(json));
    usbSendLine(json);
  }
}
#endif

/* ===========================================================================
 *  9. SETUP & LOOP
 * =========================================================================== */
void setup() {
  Serial.begin(115200);
  delay(200);

  analogReadResolution(12);                   // 0-4095 counts
  // GPIO 34 defaults to 11 dB attenuation (0-3.3 V). Force it if needed:
  // analogSetPinAttenuation(MQ3_PIN, ADC_11db);

  Serial.println();
  Serial.println("=== Smart Food Spoilage Monitor ===");
  Serial.println("Tip: warm up the MQ-3 2-5 min (24-48 h on first use).");

  loadCfg();
  Serial.printf("Thresholds: warn=%d spoiled=%d floor=%d ceil=%d\n",
                cfg.warn, cfg.spoiled, cfg.floor, cfg.ceil);

  /* connectivity: Wi-Fi STA -> AP fallback, or BLE-only when Wi-Fi is off */
  #if ENABLE_WIFI
    if (!connectSTA()) startAP();
    startWeb();
  #else
    netMode = MODE_BLE;                        /* not Wi-Fi; label is cosmetic */
    #if ENABLE_BLE
      Serial.println("Wi-Fi disabled (ENABLE_WIFI 0) - reading over BLE + USB.");
    #else
      Serial.println("Wi-Fi and BLE disabled - reading over USB serial.");
    #endif
  #endif

  #if ENABLE_BLE
    startBLE();
  #endif

  Serial.println("CSV (raw,avg,status):");
  lastSample  = millis();
  lastWifiTry = millis();
  lastPush    = millis();
}

void loop() {
  #if ENABLE_WIFI
    server.handleClient();                      // dashboard + JSON API
    maintainWiFi();
  #endif

  /* sample the sensor on its own cadence */
  if (millis() - lastSample >= SAMPLE_INTERVAL_MS) {
    lastSample = millis();
    int raw = readRawADC();
    pushSample(raw);
    latestRaw = raw;

    int avg = smoothed();
    Serial.printf("%d,%d,%s\n", raw, avg, statusFor(avg));  // Serial Plotter
  }

  #if ENABLE_BLE
    serviceBLE();
  #endif

  #if ENABLE_SERIAL_JSON
    serviceSerial();                            // USB cable (MODE 4)
  #endif
}
