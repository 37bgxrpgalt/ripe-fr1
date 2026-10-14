# Ripe FR-1 v1.1.1

**Install:** download **`Ripe_FR-1-v1.1.1.apk`** below and open it on your Android
phone. You'll need to allow **"Install unknown apps"** for your browser / file
manager the first time.

## USB (PC) mode — no Wi-Fi, no Bluetooth

A fourth way to reach the dashboard: plug the ESP32 into a PC with a **USB cable**.

- New **`web/usb_server.ps1`** — a small Windows PowerShell bridge that serves the
  dashboard offline and talks to the device over the **COM port at 115200 baud**.
  Nothing to install; it runs in any browser.
- New **USB (PC)** data source in Settings; the dashboard labels itself `USB · SERIAL`.
- The bridge also parses the legacy `raw,avg,status` CSV lines, so **no reflash is
  needed** on the current firmware.
- Firmware now also streams the same JSON over Serial that it already sends over
  BLE, and accepts `GET` / `PING` / `CFG:warn,spoiled,floor,ceil` (gated by
  `ENABLE_SERIAL_JSON`, default on).
- **USB mode is web/PC only.** The Android app still uses Wi-Fi, BLE and cloud sync.
- To use it: copy `usb_server.ps1`, `index.html` and `favicon.svg` to the PC, run
  the script, and open the URL it prints.

## Settings, redesigned

- Settings is now a **floating panel** — *Appearance · Settings · Connections ·
  Account · About This App* — with the site still visible behind it.
- A **profile block** (avatar, username, email) is pinned at the bottom.
- The panel keeps a **constant size** as you switch options; long sections scroll
  inside it. On phones it becomes an **accordion** with a back button.

## Instant apply — the Save button is gone

- Removed **"Save & connect"**. Every option now applies the moment you choose it
  (the ESP32 address applies as you type).
- Removed the top **Account** button — the account now lives under
  **Settings → Account**.

## Profile picture

- Pick a picture from your device's images; it's downscaled and **stored with your
  account**, so it follows you across devices.
- It shows inline in the greeting — e.g. *"Good evening, (photo) reyansh"*.

## Android app

- The same Settings redesign: accordion sections, back button, **instant apply**.
- **Profile picture** picker, with the avatar shown in the greeting.
- Profile picture synced through the account.
- Top Account button removed.
- **Version 1.1.1** (`versionCode 2`).

## Firmware / docs

- `esp32/food_spoilage_monitor/`: serial-JSON support (`ENABLE_SERIAL_JSON`); the
  embedded web page was regenerated from the UI source.
- README: *four connectivity modes*, and a new *§7 Option C — USB cable*.

---

> **Note:** this is a **debug-signed sideload** build (not a Play Store release).
> Package: `com.example.foodmonitor`

**Full changelog:** https://github.com/37bgxrpgalt/ripe-fr1/compare/v1.1.0...f7b00d6
