# STATE — Smart Food Spoilage Monitor (Ripe FR‑1)

Working log so work can continue from any machine. Update this file after any
code change. Paths are written with the root as **`E:\ (or D:\)`** because this
project also runs on another computer where the root is `E:\Webserver\`; on
this PC the root is `D:\Webserver\` (it was moved here from the flaky `E:\`
removable drive — see caveats). So `E:\ (or D:\)Webserver\...` means
`E:\Webserver\...` or `D:\Webserver\...` on the relevant machine.

Last updated: 2026-10-11: **Firebase cloud sync added to the web dashboard** (household Google account; web first). Earlier today: pushed to GitHub (`37bgxrpgalt/ripe-fr1`); README refreshed; GitHub Pages prep.

---

## What was asked

1. Check all folders on `E:\`.
2. In the `webserver` folder: make the web server able to connect to a **real
   ESP32** (not only simulate).
3. Add **Settings**.
4. Add **dark / light mode**.
5. Add an option to choose between the **mock server** and the **ESP32**
   connection.
6. Connect without Wi-Fi using **Bluetooth (BLE)**.
7. Remove the **Light/Dark** control from the main page; keep it only in Settings.
8. Add **Merriweather** + **PT Sans** as an alternative typeface pair with a
   switch between the two pairings.

---

## What changed (by file)

### `web/index.html` (dashboard UI — single source of truth)
- **Theme (dark/light)**: added `[data-theme=dark]` token block in CSS (same warm
  hue ~40, one accent, no cool grays) plus a `--display-bg` token so the graphite
  readout stays dark in both themes. An inline `<head>` script applies the saved
  theme before first paint (no flash). The header has **no** theme control — the
  theme is chosen only in **Settings → Appearance** (Light / Dark / System).
  Sparkline canvas colours switch with the theme.
- **Settings sheet**: new modal (`#scrim`) opened by the header **Settings**
  button. Groups:
  - *Appearance* — Light / Dark / System (follows OS) **and a typeface pair**:
    Grotesk (Space Grotesk + Urbanist) or Editorial (Merriweather + PT Sans).
  - *Data source* — **Mock server**, **ESP32 (Wi‑Fi)** or **Bluetooth (BLE)**.
  - *ESP32 address* — host/IP, port, and route: **Via local server** (proxy,
    no CORS) or **Direct from browser** (needs firmware CORS).
  - *Bluetooth* — shown only for the BLE source; **Pair / connect now** opens the
    Web Bluetooth chooser (Chrome/Edge on `http://localhost`).
  - *Polling* — 1 s / 2 s / 5 s.
  - Buttons: **Test connection**, **Save & connect**; Esc / scrim click closes.
- **Settings persist** in `localStorage` under key `ripe.settings`
  (defaults: `theme:system, font:grotesk, source:mock, transport:proxy,
  host:192.168.1.57, port:80, pollMs:2000`).
- **Data source switching**: `endpointUrl()` builds the request URL per source.
  Mock → `data` / `config` (relative). ESP32 proxy → `proxy/data` / `proxy/config`
  on the local server. ESP32 direct → `http://host:port/data`. If the page is
  served by the ESP32 itself, relative URLs are used automatically. Changing
  source resets history and re-polls.
- Connection chip / banner text now name the active source (Mock / ESP32 / BLE /
  Demo) and the poll interval is shown in the footer.
- **First run auto-fill**: if nothing is stored, the page reads `/server-info`
  and adopts the host/port the local server was started with
  (`-Esp32Host` / `--esp32`); `--mode esp32` also pre-selects the ESP32 source.
- ESP32 JSON has no `t` timestamp, so the UI now falls back to browser local
  time for "last sample".

### `web/mock_server.py` (Python local server)
- Now a mock sensor **and** a real-ESP32 bridge. New endpoints:
  - `GET /server-info` — `{mode, esp32Host, esp32Port, server, port}`
  - `GET /proxy/data?host=&port=` — reads the real ESP32 `/data`
  - `POST /proxy/config?host=&port=` — writes thresholds to the real ESP32
  - `GET /data`, `POST /config` unchanged (mock); when started with
    `--mode esp32`, `/data` also forwards to the ESP32.
- CORS + `OPTIONS` added so a `file://` page can call the server too.
- `log_message` made robust (a 404 used to crash the log formatter and print a
  traceback); `/data` and `/proxy/data` remain quiet.
- Args: `--esp32 <ip>`, `--esp32-port <n>`, `--mode mock|esp32`, `--port <n>`
  (also env `ESP32_HOST`, `ESP32_PORT`, `MODE`, `PORT`).

### `web/mock_server.ps1` (Windows local server, no installs)
- Same new endpoints and CORS/OPTIONS as the Python server.
- Params: `-Esp32Host`, `-Esp32Port`, `-Mode mock|esp32`, `-Port`.

### `esp32/food_spoilage_monitor/food_spoilage_monitor.ino` (firmware)
- Added CORS headers (`Access-Control-Allow-Origin: *`, methods, headers) on
  `/data`, `/config`, 404, and an `HTTP_OPTIONS` handler. This lets the browser
  talk to the ESP32 **directly** (Settings → ESP32 → Direct from browser).

### `esp32/food_spoilage_monitor/web_page.h`
- Regenerated from `web/index.html` via `tools/embed_page.ps1` (46.1 KB; **52.7 KB**
  after the BLE + typeface update below). Regenerated again for the favicon link
  (see the 2026-10-11 update) — `web_page.h` is byte-identical to `index.html`.

### Docs (done 2026-10-09)
- `README.md` — file tree, dashboard-features bullets, API table (`/server-info`,
  `/proxy/*`, CORS note), and section 7 rewritten as "Running the UI — mock
  sensor or a real ESP32" with both CLI examples.
- `design.md` — new section 7 "Dark mode" with the full light/dark token table,
  rules kept (warm hue ~40, one accent, readout stays dark).
- `E:\SMART_FOOD_Project_How_To_Run.txt` — appended step 6 (Settings, dark mode,
  real-ESP32 walk-through, port-8000-blocked workaround, new endpoints).

---

## Update — 2026-10-09 (later): BLE as a source + typeface option

### Header control removed
- The header **Light/Dark** toggle button is gone; the theme is chosen only in
  **Settings → Appearance** (Light / Dark / System), as requested.

### Bluetooth (BLE) as a first-class data source
- `web/index.html`:
  - **Settings → Data source** now has three options: **Mock server**,
    **ESP32 (Wi‑Fi)**, **Bluetooth (BLE)**. New pref `source:'ble'`
    (validated in `loadPrefs`).
  - When `source==='ble'`, `pollHTTP()` is a no-op (readings arrive as BLE
    notifications). `resetStream()` shows “BLE · …” / “Bluetooth — not paired”.
  - New **Bluetooth** settings group with a **Pair / connect now** button and
    live status; **Save & connect** triggers pairing when the source is BLE.
  - `bleSupported()` = `navigator.bluetooth && window.isSecureContext`.
    Clear messages when Web Bluetooth is unavailable or the page is not in a
    secure context (needs Chrome/Edge from `http://localhost`).
  - `bleConnect()` rewritten: sets `state.bleOK/bleName`, handles
    `gattserverdisconnected`, updates the chip and settings hint; a 2 s
    heartbeat (`PING`) also runs a 6 s watchdog that flags a quiet link as stale.
  - Device block button relabelled **Pair / connect now**; shown whenever
    `navigator.bluetooth` exists.
- `esp32/.../food_spoilage_monitor.ino`:
  - New `MODE_BLE` net mode; `modeName()` returns `"BLE ONLY"`.
  - `buildJson()` reports `ip:"ble://FoodMonitor"` and RSSI 0 in BLE-only mode.
  - **`ENABLE_WIFI 0`** support: skips Wi‑Fi STA/AP entirely (fast BLE-only boot);
    `setup()`, `loop()` guards; compile-time `#error` if both Wi‑Fi and BLE off.
- `esp32/.../config.h`: added `#define ENABLE_WIFI 1` (set 0 for a BLE-only
  device with no router or hotspot).

### Typeface pairings
- `web/index.html`: added **Merriweather** (display) and **PT Sans** (UI) to the
  Google Fonts link. New pref `font:'grotesk'|'editorial'`; a
  `html[data-font=editorial]` CSS block swaps only the two font tokens
  (same palette/scale). The pre-paint `<head>` script now also applies
  `data-font` (no flash). **Settings → Appearance** gained a **Grotesk /
  Editorial** segment that applies live and persists.
- `design.md` / `README.md`: documented the alternative pairing.

### Files touched this round
- `web/index.html` — BLE source, header-toggle removal, typeface option.
- `esp32/.../food_spoilage_monitor.ino`, `config.h` — `MODE_BLE`, `ENABLE_WIFI`.
- `esp32/.../web_page.h` — regenerated (52.7 KB).
- `README.md`, `design.md`, `STATE.md` — this log.

---

## Update — 2026-10-09 (late): settings survive + async PowerShell proxy

Two TODO items closed. No UI re-design — functional only.

### Server-side `/settings` (address survives a browser)
- `web/mock_server.py` + `web/mock_server.ps1` now implement:
  - `GET /settings` — `{"ok":true,"host":…,"port":…}` (empty host until saved).
  - `POST /settings` — body `{"host":…,"port":…}` → validated (host non-empty,
    port 1–65535) and written to **`web/settings.json`** (atomic: temp + rename;
    PS: tmp + `Move-Item -Force`). The running default `esp32Host/esp32Port`
    updates too, so `/server-info` reflects it.
  - Startup precedence: **command line > environment > settings.json > defaults**
    (Python: env `ESP32_HOST`/`ESP32_PORT` before the file; PowerShell:
    `-Esp32Host`/`-Esp32Port` then env then file).
  - `web/settings.json` is runtime-created on first Save — delete it to forget
    the address.
- `web/index.html`:
  - `initFromServer()` (first run, empty `localStorage`) now fetches
    `/server-info` **and** `/settings` in parallel and adopts the stored
    address; `--mode esp32` / `-Mode esp32` still preselects the ESP32 source.
  - `saveSettings()` best-effort **POSTs** host/port to `/settings`
    (`persistServerSettings()`, 4 s timeout, failures ignored — the save still
    lands in `localStorage`).
  - New static hint in Settings → ESP32 address: "The last saved address is
    remembered next to the server (settings.json)…".

### PowerShell proxy is now async (never blocks)
- `web/mock_server.ps1` rewritten accept loop:
  - Requests are accepted with `HttpListener.GetContextAsync()` and polled
    (`AsyncWaitHandle.WaitOne(100)`); every proxy call runs on a shared
    `System.Net.Http.HttpClient` (4 s timeout) as a background task that is
    reaped between accepts (`Complete-PendingProxies`).
  - Result: a slow/unreachable ESP32 no longer blocks `/data`, `/`, or other
    clients, and the server can **proxy to itself**.
  - Added the `http://127.0.0.1:<port>/` listener prefix — HttpListener matches
    the Host header, so proxying via `127.0.0.1` without it returned 400.
  - `Invoke-WebRequest`-based sync proxy removed (superseded by HttpClient).
- The Python server was already threaded, so it never blocked; it only gained
  the `/settings` endpoints.

### Files touched this round
- `web/mock_server.py` — `/settings` GET/POST, `settings.json` load/save.
- `web/mock_server.ps1` — `/settings` GET/POST, async HttpClient proxy,
  `GetContextAsync` accept loop, `127.0.0.1` prefix.
- `web/index.html` — `initFromServer` dual-fetch, `persistServerSettings()`,
  address hint, 4 s settings-fetch timeout.
- `esp32/.../web_page.h` — regenerated after the `index.html` edits (53.5 KB,
  byte-identical embedded page).
- `README.md`, `STATE.md` — this log. (Paths in this log were briefly written
  as `D:\Webserver\`; since the project also runs on another computer with root
  `E:\Webserver\`, they are now written as `E:\ (or D:\)Webserver\...` — see the
  top note.)

---

## Update — 2026-10-11: matching tab icon (favicon)

The Android app got a custom launcher icon (redrawn from the dashboard's chamber
illustration); this round gives the **web dashboard the same mark** so the two
mirror each other.

### Files touched this round
- `web/favicon.svg` — **new**. The chamber mark (graphite field `#23211D`, paper
  line art `#F5F2EB`, amber `#EDB100` LED), 64×64, self-contained.
- `web/index.html` — added one line in `<head>`:
  `<link rel="icon" type="image/svg+xml" href="/favicon.svg">` (nothing else
  changed; the payload diff vs the previous embed is exactly that line).
- `tools/embed_page.ps1` / `tools/embed_page.py` — now emit **both** headers:
  `web/index.html → web_page.h` (`MAIN_page`) and
  `web/favicon.svg → favicon_svg.h` (`FAVICON_svg`). The PowerShell version was
  also fixed to **read via `[IO.File]::ReadAllText`** — `Get-Content -Raw` under
  Windows PowerShell 5.1 decoded UTF-8 as the ANSI codepage and corrupted the
  embedded non-ASCII characters (the new `.ps1` now produces a byte-identical
  payload, verified).
- `esp32/food_spoilage_monitor/favicon_svg.h` — **new**, auto-generated.
- `esp32/food_spoilage_monitor/food_spoilage_monitor.ino` — `#include
  "favicon_svg.h"` and two routes in `startWeb()`:
  `GET /favicon.svg` and `GET /favicon.ico` both `send_P(200, "image/svg+xml",
  FAVICON_svg)`. (No other firmware change.)
- `web/mock_server.ps1` — `.svg → image/svg+xml` in the static-file type switch.
- `web/mock_server.py` — pinned `.svg` (+ `.html`/`.js`/`.json`) in the handler's
  `extensions_map`, since Windows' `mimetypes` DB often lacks `image/svg+xml`.
- `README.md` — `/favicon.svg` row added to the API table.
- `esp32/.../web_page.h` — regenerated (54.9 KB; +1 line vs before).

### Verified on this PC (PowerShell server, `:8012`)
- `GET /favicon.svg` → `200 image/svg+xml`, 836 bytes.
- `GET /` → `200 text/html`, 54,677 bytes, and contains the `rel="icon"` link.
- `GET /data` → `200 application/json` (unchanged).
- `web_page.h` payload and `favicon_svg.h` payload are **byte-identical** to
  `web/index.html` and `web/favicon.svg` respectively.
- The regenerated `web_page.h` differs from the previous one by **exactly one
  line** (the `<link rel="icon" …>`).

### Still to do
- Flash the firmware (the new `/favicon.*` routes + `favicon_svg.h`) and confirm
  the browser tab shows the mark at `http://<esp32-ip>/`.
- Re-run `tools/embed_page.ps1` after any future `web/index.html` **or**
  `web/favicon.svg` edit.

---

## Update — 2026-10-11: Dashboard **Basic / Advanced** mode

New **Settings → Dashboard** choice (**Basic** / **Advanced**), stored in
`localStorage` beside the theme and typeface — never on the device. **Advanced**
is the dashboard as before. **Basic** is for people who don't read ADC counts: it
hides the readout, gauge, VOC trend, device block and calibration, and shows the
big verdict word, a plain-language sentence, a **last-sample line**, a **plain
status history** (status + time, no counts) and the chamber illustration.

### Files touched this round
- `web/index.html` —
  - **Settings**: a new first group `<h3>Dashboard</h3>` with
    `<div class="seg" id="segMode">` (Basic / Advanced) and `#modeHint`.
  - **CSS**: a `[data-mode=basic]` block — hides the number panels
    (`.readout/.trend/.device/.calib`), widens the verdict to the full six
    columns, enlarges the word (`clamp(38px,8vw,54px)`), keeps `.log` full-width
    as a **plain history** (`.logList .v` counts hidden) and the illustration
    (`max-height:230px`), and hides `.vsince` / `.vthr` (no numbers) while
    showing `.vseen`. The `.verdict .vseen` "last sample HH:MM:SS" line carries
    the readout's own timestamp into Basic.
  - **JS**: `mode:'advanced'` in `PREF_DEFAULTS` (+ `loadPrefs` validation);
    `plainText(status)` and `applyMode()`; `state.last` keeps the last reading so
    toggling back to Advanced restores the numeric hint at once; `segMode` wired
    into `readSettingsForm` / `syncSettingsForm` / the seg click handler /
    `refreshSettingsHints`; `applyData` picks `plainText` vs the numeric
    `hintText` by mode; the no-flash `<head>` script now also sets `data-mode`.
- `esp32/.../web_page.h` — regenerated via `tools/embed_page.ps1` (57.3 KB;
  payload **byte-identical** to `web/index.html`).

### Verified on this PC (PowerShell server, `:8012`)
- Toggling **Basic** → `data-mode=basic`; `.readout`, `.trend`, `.device`,
  `.calib` all `display:none`; `#verdict`, `.log` and `.chamber` visible (rows
  1 / 2 / 3, full width); verdict word **54 px**; illustration `max-height:230px`;
  `.vsince`/`.vthr` hidden; hint shows **"Starting to spoil — check it soon."**
  (no numbers); the **status history** shows plain rows (time + status, the
  `.v` counts `display:none`); a **"last sample 13:17:32"** line appears; the
  choice is written to `localStorage`.
- Reload → the inline `<head>` script applies **Basic before first paint** (the
  readout is already hidden and the plain hint is set), so there is no flash of
  the advanced view.
- Toggling back to **Advanced** restores every panel and the numeric hint
  (e.g. "1,334 counts — 334 above the 1,000-count clean-air floor.").
- Browser console: **no errors**.

---

## Update — 2026-10-11: **Firebase cloud sync** on the web (household model)

Chosen model: the whole household signs in with **one Google account**, so every
device gets the same `uid` and shares `users/{uid}/…`. One device on the ESP32's
Wi‑Fi runs **Publish this device**; everyone else runs **Follow the cloud**.

### New files
- `web/firebase-config.js` — the `firebaseConfig` from the Firebase console
  (project **`ripe-fr1`**). Public by design; access is governed by the Firestore
  rules + Authorized domains, not by hiding this file.
- `web/firebase.js` — ES-module SDK **v13.0.0** from the Firebase CDN
  (`firebase-app` / `firebase-auth` / `firebase-firestore`); exports
  `onUser, signIn, signOutNow, publish, addReading, follow, recentReadings`.
  Data model: `users/{uid}/state/latest` (one snapshot doc) +
  `users/{uid}/readings/{autoId}` (one doc per status change).

### `web/index.html`
- Loads the SDK **on demand** via a guarded `import('./firebase.js')`
  (`loadCloud()`), so the ESP32's offline page — which has no `firebase.js` and
  no internet — never touches Firebase. New pref `sync`, default **off**.
- **Settings → Account** (`Sign in with Google` / email / `Sign out`) and
  **Settings → Cloud sync** segment (`Off` / `Publish this device` /
  `Follow the cloud`).
- `cloudMaybePublish()` mirrors each local snapshot to Firestore (≥10 s between
  writes, plus immediately on every status change, which also appends a
  `readings` doc). Demo data is never published.
- **Follow** subscribes to `state/latest` with `onSnapshot`, stops local polling,
  renders the doc through the existing `applyData()`, loads history from
  `readings`, and runs a 30 s staleness watchdog ("Cloud data is stale").
- Footer line `Cloud · publishing/following · <email>`.
- Hosted-page fix: when the source is Mock **and** the page is not localhost, the
  "Disconnected" banner now explains there is no local server here and points at
  *Preview demo data* / *Follow the cloud* instead of "restart mock_server.ps1".

### Verified on this PC (PowerShell server `:8011`, browser tool)
- `GET /`, `/data`, `firebase.js`, `firebase-config.js` all `200`; mock stream live.
- `loadCloud()` imports the SDK from `gstatic`; `onUser` fires with `null`
  (signed out); **0 console errors/warnings**.
- Settings shows **Account** + **Cloud sync**; the hint updates per choice; saving
  **Follow** while signed out shows the guard message and the footer
  `Cloud · following · not signed in`; back to **Off** hides it.
- `web_page.h` regenerated (65 KB; payload **byte-identical** to `index.html`).

### Still unverified (needs a real Google account)
- The Google sign-in popup itself, a real publish → follow round-trip, and the
  Firestore security rules — test on the live Pages URL.

---

## How to run / test

Mock (default):
```powershell
python E:\ (or D:\)Webserver\web\mock_server.py --port 8010
#   or:  powershell -ExecutionPolicy Bypass -File E:\ (or D:\)Webserver\web\mock_server.ps1 -Port 8010
# open http://localhost:8010
```

Pointed at a real ESP32 (pre-selects ESP32 in Settings):
```powershell
python E:\ (or D:\)Webserver\web\mock_server.py --port 8010 --esp32 192.168.1.57
powershell -ExecutionPolicy Bypass -File E:\ (or D:\)Webserver\web\mock_server.ps1 -Port 8010 -Esp32Host 192.168.1.57
```
Then in the dashboard: **Settings → Data source → ESP32 (Wi‑Fi) → host/port →
Test connection → Save & connect**. Prefer "Via local server" unless the ESP32
was flashed with the updated CORS firmware.

No Wi-Fi at all? Choose **Settings → Data source → Bluetooth (BLE)** and press
**Pair / connect now** (Chrome/Edge, page opened via `http://localhost`). Set
`ENABLE_WIFI 0` in `config.h` and reflash for a BLE-only device.

> NOTE: port **8000 is blocked** on this PC (WinError 10013). Use 8010/8080/etc.
>
> Pressing **Save & connect** also writes the ESP32 address to `web/settings.json`
> next to the server, so a fresh browser picks it up. Delete that file to forget it.

---

## Verified (2026-10-09, this PC)

- Python server on `:8010`: `/data`, `/server-info`, `/proxy/data` all return
  valid JSON; `/proxy/data?host=localhost&port=8010` round-trips.
- PowerShell server on `:8011`: `/data`, `/server-info`, `/config` OK;
  `/proxy/data` + `/proxy/config` OK when the target is a **different** server
  (8011 → 8010). Self-proxy 8011 → 8011 returned 502 then (see caveat) —
  **superseded later this day** (late round below): self-proxy works now.
- Browser (`http://localhost:8010`): no console errors/warnings; mock stream
  live; Settings opens; Light/Dark/System switch applies (verified computed
  backgrounds of body / readout / verdict / inputs — light `#e8e4db` etc.,
  dark `#1b1815` etc.); Test connection OK; switching source to ESP32 + proxy
  + Save & connect switches the chip to "ESP32 · …" and polls `/proxy/data`;
  polling interval changes the footer.
- `web_page.h` regenerated after the final `index.html` edit and contains the
  new Settings markup and `data-theme=dark` block.
- First-run auto-fill verified: `--esp32 127.0.0.1 --esp32-port 8010` became the
  Settings host; `--mode esp32` booted with source = ESP32 and live readings.
- A mock server was running at `http://localhost:8010` for inspection in that
  round (historical note — no server is left running now).

### Verified — later 2026-10-09 (BLE source + typeface), `http://localhost:8010`
- Removed the header theme button; no `#themeBtn` remains. Theme switch via
  **Settings → Appearance → Dark** sets `data-theme="dark"` and persists
  (`ripe.settings.theme="dark"`); reverted to `system` afterwards.
- Typeface option: with clean storage the page boots
  `data-font="grotesk"` (`body` → Urbanist, `#rawNow` → Space Grotesk).
  Choosing **Editorial** sets `data-font="editorial"`, `body` → PT Sans,
  `#rawNow` → Merriweather, updates the hint, and persists across reload.
- No console errors/warnings in either state.
- `web_page.h` regenerated **after** the final `index.html` edit (52.7 KB) and
  includes the BLE source, typeface option and `data-theme=dark` block.
- Firmware edits are source-only (not compiled/flashed here).

### Verified — 2026-10-09 (late), PowerShell server on `:8011` (no Python on this PC)
- **Python is not installed on this PC right now** — only the MS Store stub.
  The Python server changes were code-reviewed but not executed; every new
  endpoint/behaviour below was verified on the PowerShell server instead.
- `/data`, `/server-info`, `/settings`, `/config`, `OPTIONS`, GET `/` all OK.
  `POST /settings` persists to `web/settings.json` (`{"host":…,"port":…}`);
  it survives a server restart (loaded at boot) and updates `/server-info`.
  Invalid input rejected: empty host, port 0/70000 → 400.
- **Async proxy:** with an unreachable target (`10.255.255.1:81`) in flight,
  `/data` answered in **16 ms** and `/server-info` in **19 ms**; the slow call
  returned its 502 after the 4 s HttpClient timeout. One slow ESP32 no longer
  blocks anything else.
- **Self-proxy works now:** `GET /proxy/data?host=127.0.0.1&port=8011` and
  `host=localhost` both return live mock readings (was 502/400 before this
  round). `POST /proxy/config?host=localhost&port=8011` round-trips and the
  thresholds show up in `/data` afterwards; invalid rules pass through as 400.
- Browser (`http://localhost:8011`, no console errors): first run with empty
  `localStorage` reports `Mock · MOCK SERVER` and prefills the ESP32 address
  group from the server-saved address when one exists; clearing `localStorage`
  and reloading adopts it again. Saving host/port in Settings writes
  `web/settings.json`. Switching to **ESP32 (Wi‑Fi) → Via local server** and
  pointing at the second server (`localhost:8012`) polls
  `/proxy/data?host=localhost&port=8012` every 2 s (~130 ms each) with the chip
  reading `ESP32 · MOCK SERVER`.
- `web_page.h` regenerated after the final `index.html` edit and is
  byte-identical to the embedded page (53.5 KB).
- Screenshot of the live dashboard (mock, `http://localhost:8010`) saved to
  `docs/dashboard-screenshot.png` for reference.

## Caveats / notes
- **The project root is `E:\Webserver\` on the other computer and
  `D:\Webserver\` on this PC**; paths in this file use the **`E:\ (or D:\)`**
  shorthand (`E:\ (or D:\)Webserver\...`). On this PC the project moved off the
  flaky `E:\` removable drive onto the fixed `D:\` disk (see the E: entry
  below); on the other computer it stays on `E:\` and the same commands work.
- **Port 8000 is blocked on this PC** (WinError 10013). Tested on 8010/8011/8012.
- **Python is not installed on this PC** right now (only the MS Store stub
  `python.exe`), so `web/mock_server.py` — including its `/settings` endpoints —
  was **code-reviewed but never executed** here. Everything was verified on the
  PowerShell server instead. Re-check the Python routes on a machine with
  Python (see first TODO).
- **E: flakiness (why we moved):** `mock_server.ps1` twice disappeared from the
  `E:` drive after the script was started/killed (not AV — verified; benign
  probe scripts never vanished). It was recreated and re-verified before moving
  the project to the fixed internal disk `D:\`.
- **The PowerShell proxy is async since 2026-10-09** (`GetContextAsync` accept
  loop + shared `System.Net.Http.HttpClient` tasks). In earlier rounds it was
  single-threaded: it blocked other requests while proxying and could not proxy
  to **itself**. Both are fixed — a slow/unreachable ESP32 (4 s timeout) no
  longer blocks anything and self-proxy (8011 → 8011) returns live data.
- `web/settings.json` is a **runtime artifact**, created on the first Save. Test
  runs left `{"host":"10.20.30.40","port":90}` in it; **it was deleted for a
  clean handoff** and will be re-created on the next Save. Delete it any time to
  forget the saved ESP32 address.
- **The Android Studio app now lives at `D:\FoodMonitorApp\`** (moved here from
  the Desktop). It mirrors `web/index.html` (same palette/fonts) and reads the
  ESP32 directly or through this server's `/proxy/data`. Its own working log:
  `D:\FoodMonitorApp\STATEAPP.MD` + `NEXT_SESSION_PROMPT.md`. Belongs to the
  web-side log because the two are developed side by side.
  - **2026-10-11:** the app gained a real **BLE client** (scan `FoodMonitor` →
    GATT → Nordic-UART TX notifications; `GET`/`CFG:w,s,f,c` written to RX) and a
    **Calibration card** mirroring this dashboard's panel (saves via
    `POST /config`, over BLE, or locally in Mock mode). Both compile clean —
    still need real-hardware verification. Local build recipe (JDK 17) is in
    `STATEAPP.MD` under Step 4.
- `D:\Webserver\android_app/` (and `Android Application.zip`) are the old
  single-file Android sketches — superseded by the real Android Studio project
  at `D:\FoodMonitorApp\`.
- `esp32_test/` is a trivial serial-print sketch, unrelated to this project.

## TODO / follow-ups
- [ ] Re-verify `web/mock_server.py` end-to-end on a machine with Python:
      `/settings` GET/POST, `settings.json` load/save + startup precedence,
      threaded proxy. Code-reviewed here only — Python is not installed on this
      PC.
- [ ] Test against real ESP32 hardware: proxy route, then direct+CORS route.
- [ ] Flash `web_page.h` + the CORS firmware (Wi-Fi creds in `config.h`; keep
      `ENABLE_BLE 1`) and confirm the embedded page shows Settings + dark mode.
- [ ] Test **Bluetooth (BLE)** end-to-end with a real board: pair
      `FoodMonitor` from Chrome/Edge at `http://localhost`; confirm the chip
      reads **BLE · …** and live counts. (Not testable here — no BT hardware in
      this environment.)
- [ ] For a no-Wi-Fi device: set **`ENABLE_WIFI 0`** in `config.h`, reflash, and
      check it boots straight to `BLE ONLY`. Confirm `buildJson()` shows
      `ip:"ble://FoodMonitor"`, RSSI `—`.
- [x] Server-side `/settings` POST so the address survives a browser (done
      2026-10-09 — implemented in `mock_server.py` + `mock_server.ps1`, stored
      in `web/settings.json`; PowerShell path verified).
- [x] Make the PowerShell proxy async so it never blocks (done 2026-10-09 —
      `GetContextAsync` accept loop + shared HttpClient tasks; self-proxy and
      non-blocking verified).
- [ ] Re-run `tools\embed_page.ps1` after any future `web/index.html` **or**
      `web/favicon.svg` edit (it now regenerates both `web_page.h` and
      `favicon_svg.h`).
- [ ] Flash the firmware with the new `/favicon.svg` + `/favicon.ico` routes and
      confirm the browser tab icon on the device (`http://<esp32-ip>/`).
- [x] Dashboard **Basic / Advanced** mode (done 2026-10-11 — `Settings → Dashboard`,
      `[data-mode=basic]` CSS + `plainText`/`applyMode`; hides every number panel for
      a plain spoiled-or-not verdict + the chamber illustration). Verified on the
      PowerShell server: toggle both ways, persists on reload with no flash, console
      clean. `web_page.h` regenerated (57.3 KB).

## GitHub Pages hosting (prep, 2026-10-11)

Agreed with the user: publish the dashboard on **GitHub Pages** (static HTTPS),
*then* add Firebase sync. Prepared this round:

- `.gitignore` — ignores `esp32/food_spoilage_monitor/config.h` (Wi‑Fi creds),
  `web/settings.json`, and build/IDE/OS cruft.
- `esp32/food_spoilage_monitor/config.example.h` — masked copy of `config.h` so
  a cloner can rebuild without real credentials (real creds are never committed).
- `.github/workflows/pages.yml` — publishes **only `web/`** to Pages on a push to
  `main` (Source = "GitHub Actions"), keeping `web/index.html` the single source
  of truth (no duplicated copy).
- `web/index.html` — favicon link changed from `/favicon.svg` (absolute) to
  `favicon.svg` (relative) so it resolves both under a `/repo/` subpath and at
  the ESP32/localhost root. Verified on the PowerShell server (port 8012):
  resolves to `http://localhost:8012/favicon.svg`, live data flowing, 0 console
  errors. `web_page.h` regenerated (57.4 KB → payload byte-identical).

Key finding (in README §10): **a hosted HTTPS page cannot read the ESP32.** The
browser blocks `https://` → `http://192.168.1.57/data` as *mixed content*, and
the ESP32 has no TLS. So the Pages site follows the **cloud** (Firebase), while
publishing is done by the **Android app** or the local `http://localhost:8012`
page. Vercel / Netlify / Cloudflare do **not** change this (their functions run
in the cloud, off your LAN). The ESP32's own page is unaffected.

**Follow-up (2026-10-11):** the README was then refreshed — a **new live
screenshot** (captured 1281×1148 in light mode via the browser tool and written to
`docs/dashboard-screenshot.png`, replacing the Oct-9 one), an embedded preview and
**Live** URL at the top, an accurate file tree (git-ignored `config.h` /
`settings.json` marked *LOCAL ONLY*, plus `config.example.h`, `favicon.svg` and
`FIREBASE_SETUP.md` added), and §10 rewritten to be reader-facing and current (no
more "install Git on this PC", real repo URL).

**Pushed to GitHub** (2026-10-11): <https://github.com/37bgxrpgalt/ripe-fr1>
(public). `git` was installed and the `dubious ownership` warning cleared with
`git config --global --add safe.directory D:/Webserver`; local `main` ==
`origin/main` at `8333251`. **Not done: enabling Pages** — repo → Settings → Pages
→ Build and deployment → Source: **GitHub Actions**, after which the committed
workflow serves `web/` at <https://37bgxrpgalt.github.io/ripe-fr1/>.

## Resume here (next session)
1. Everything on the software side of this round is **done and verified** on the
   PowerShell server: `/settings` persistence, async non-blocking proxy,
   first-run address adoption, self-proxy, the favicon, and the **Basic /
   Advanced dashboard mode**. `web_page.h` is current (57.3 KB, after the
   Basic/Advanced update) and byte-identical to `web/index.html`;
   `favicon_svg.h` is byte-identical to `web/favicon.svg`.
2. Two things stay unverified from this PC: **real ESP32 hardware** (Wi-Fi
   proxy/direct + BLE) and **`web/mock_server.py` itself** (Python not installed
   here; code-reviewed only). Verify the Python routes on a machine with Python.
3. To preview: start the local server and open the dashboard (port 8000 is
   blocked — use 8010/8011):
   ```powershell
   python E:\ (or D:\)Webserver\web\mock_server.py --port 8010
   #   or:  powershell -ExecutionPolicy Bypass -File E:\ (or D:\)Webserver\web\mock_server.ps1 -Port 8010
   # open http://localhost:8010
   ```
4. `web/settings.json` was deleted for a clean handoff (re-created on the first
   Save & connect). At handoff no server is left running; if the screenshot-demo
   server is still up at `http://localhost:8010`, kill the `mock_server.ps1`
   process when done.
5. **Pushed to GitHub** — <https://github.com/37bgxrpgalt/ripe-fr1> (public;
   `main` == `origin/main` at `8333251`). Next: enable **GitHub Pages** (Settings
   → Pages → Source: **GitHub Actions**); the committed workflow then serves
   `web/` at <https://37bgxrpgalt.github.io/ripe-fr1/>. After that, start the
   **Firebase** work — web first, needs the `firebaseConfig` object.
6. Keep the locked design language (`design.md`): warm hue ~40, one accent
   `#edb100`, no gradients/cool grays/emoji. Re-run `tools\embed_page.ps1` after
   any future `web/index.html` edit.
