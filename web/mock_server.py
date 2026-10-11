#!/usr/bin/env python3
"""
Smart Food Spoilage Monitor — local server: mock sensor + real ESP32 bridge.

Serves web/index.html and a JSON API with two backends:

  mock   (default)  simulated MQ-3 — random walk + periodic spoilage event
  esp32             forwards to a real ESP32 so the UI shows live readings

Because the browser and the ESP32 are different origins, the ESP32 is reached
through /proxy/* on this server (no CORS problems, works on plain HTTP).

Usage:
    python web/mock_server.py                          # mock sensor, :8000
    python web/mock_server.py --esp32 192.168.1.57     # pre-fill device IP
    python web/mock_server.py --mode esp32 --esp32 192.168.1.57
    PORT=9000 python web/mock_server.py

Endpoints:
    GET  /                         the dashboard
    GET  /server-info              defaults for the Settings panel
    GET  /data                     mock reading (live when --mode esp32)
    POST /config                   mock threshold write
    GET  /settings                 stored ESP32 address (settings.json)
    POST /settings                 {"host":...,"port":...} -> saved next to the
                                   server so the address survives a browser
    GET  /proxy/data?host=&port=   live reading from the ESP32
    POST /proxy/config?host=&port= write thresholds to the ESP32

Standard library only — no pip installs required.
"""

import json
import os
import random
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
PORT = int(os.environ.get("PORT", 8000))
SETTINGS_PATH = os.path.join(ROOT, "settings.json")   # persisted ESP32 address

# --- server defaults: command line > environment > settings.json > built-in --
def _load_file_settings():
    """Read the address last saved from the dashboard (settings.json)."""
    try:
        with open(SETTINGS_PATH, "r", encoding="utf-8") as f:
            d = json.load(f)
        if not isinstance(d, dict):
            return {}
        return {k: d[k] for k in ("host", "port")
                if k in d and d[k] not in (None, "")}
    except (OSError, ValueError):
        return {}

def _save_file_settings(host, port):
    """Persist the ESP32 address next to the server (survives a browser)."""
    tmp = SETTINGS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"host": host, "port": int(port)}, f)
    os.replace(tmp, SETTINGS_PATH)

FILE_SETTINGS = _load_file_settings()
DEFAULT_ESP32_HOST = os.environ.get("ESP32_HOST", "") or FILE_SETTINGS.get("host", "")
DEFAULT_ESP32_PORT = int(os.environ.get("ESP32_PORT", FILE_SETTINGS.get("port", 80)))
MODE = os.environ.get("MODE", "mock")            # "mock" | "esp32"

# --- simulated device state -------------------------------------------------
CFG = {"warn": 1600, "spoiled": 2600, "floor": 1000, "ceil": 3800}
START = time.time()
value = 1150.0          # simulated ADC reading
phase = 0               # drives a periodic "spoilage event"


def status_of(v: int) -> str:
    if v >= CFG["spoiled"]:
        return "SPOILED"
    if v >= CFG["warn"]:
        return "WARNING"
    return "FRESH"


def read_sensor() -> dict:
    """Random-walk the reading; periodically simulate a spoilage spike."""
    global value, phase
    phase += 1
    event = phase % 40
    target = 3100 - abs(event - 17) * 90 if 8 < event < 26 else 1150.0
    value += (target - value) * 0.12 + random.uniform(-45, 45)
    value = max(700.0, min(4000.0, value))

    v = int(value)
    span = CFG["ceil"] - CFG["floor"]
    pct = max(0, min(100, round((v - CFG["floor"]) / span * 100))) if span else 0
    return {
        "value": v, "raw": v, "avg": v, "pct": pct, "status": status_of(v),
        "thr_warn": CFG["warn"], "thr_spoiled": CFG["spoiled"],
        "cal_floor": CFG["floor"], "cal_ceil": CFG["ceil"],
        "uptime_s": int(time.time() - START),
        "rssi": -55, "mode": "MOCK SERVER", "ip": "localhost",
    }


# --- ESP32 bridge -----------------------------------------------------------
def forward(kind, host, port, body=None):
    """Call http://host:port/<kind> and return (status, bytes). Never raises."""
    host = (host or "").strip()
    if not host:
        return 400, json.dumps({"ok": False, "error": "No ESP32 host set - open Settings."}).encode()
    url = "http://%s:%d/%s" % (host, int(port), kind)
    req = urllib.request.Request(url, data=body, method="GET" if body is None else "POST")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=4) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:            # the ESP32 answered with an error
        try:
            return e.code, e.read()
        except Exception:
            return e.code, b'{"ok":false,"error":"ESP32 returned an error"}'
    except Exception as e:                          # unreachable / wrong IP / timeout
        return 502, json.dumps(
            {"ok": False, "error": "ESP32 %s:%s unreachable (%s)" % (host, port, e)}
        ).encode()


class Handler(SimpleHTTPRequestHandler):
    # Windows' mimetypes DB often lacks .svg - pin it so /favicon.svg is served
    # with the right type (and rendered, not downloaded).
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".svg": "image/svg+xml",
        ".html": "text/html; charset=utf-8",
        ".js": "application/javascript",
        ".json": "application/json",
    }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    # ---- responses ---------------------------------------------------------
    def _headers(self, code, body, ctype="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        # CORS so a page opened from file:// (or another port) can call us too
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code, obj):
        self._headers(code, json.dumps(obj).encode())

    def _params(self):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        host = (q.get("host", [""])[0] or DEFAULT_ESP32_HOST)
        port = int(q.get("port", [str(DEFAULT_ESP32_PORT)])[0] or DEFAULT_ESP32_PORT)
        return host, port

    # ---- GET: /, /data, /settings, /server-info, /proxy/data ---------------
    def do_OPTIONS(self):
        self._headers(204, b"")

    def do_GET(self):
        path = self.path.split("?")[0]

        if path == "/server-info":
            self._json(200, {
                "mode": MODE if MODE in ("mock", "esp32") else "mock",
                "esp32Host": DEFAULT_ESP32_HOST,
                "esp32Port": DEFAULT_ESP32_PORT,
                "server": "python",
                "port": PORT,
            })
            return

        if path == "/settings":
            self._json(200, {"ok": True, "host": DEFAULT_ESP32_HOST,
                             "port": DEFAULT_ESP32_PORT, "server": "python"})
            return

        if path == "/data":
            if MODE == "esp32":
                code, body = forward("data", DEFAULT_ESP32_HOST, DEFAULT_ESP32_PORT)
                self._headers(code, body)
            else:
                self._json(200, read_sensor())
            return

        if path == "/proxy/data":
            host, port = self._params()
            code, body = forward("data", host, port)
            self._headers(code, body)
            return

        super().do_GET()

    # ---- POST: /settings, /config, /proxy/config --------------------------
    def do_POST(self):
        path = self.path.split("?")[0]

        if path == "/settings":
            global DEFAULT_ESP32_HOST, DEFAULT_ESP32_PORT
            try:
                length = int(self.headers.get("Content-Length", 0))
                raw = self.rfile.read(length) or b"{}"
                incoming = json.loads(raw)
            except (ValueError, json.JSONDecodeError):
                incoming = {}
            host = str(incoming.get("host", "")).strip()
            try:
                port = int(incoming.get("port", 0))
            except (TypeError, ValueError):
                self._json(400, {"ok": False, "error": "Port must be an integer."})
                return
            if not host:
                self._json(400, {"ok": False, "error": "Host must not be empty."})
                return
            if not (1 <= port <= 65535):
                self._json(400, {"ok": False, "error": "Port must be 1-65535."})
                return
            DEFAULT_ESP32_HOST, DEFAULT_ESP32_PORT = host, port
            try:
                _save_file_settings(host, port)
            except OSError as e:
                self._json(500, {"ok": False, "error": "Could not write settings: %s" % e})
                return
            self._json(200, {"ok": True, "host": host, "port": port})
            return

        if path not in ("/config", "/proxy/config"):
            self._json(404, {"ok": False, "error": "Not found - try / or /data"})
            return

        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length) or b"{}"
            incoming = json.loads(raw)
        except (ValueError, json.JSONDecodeError):
            raw, incoming = b"{}", {}

        if path == "/proxy/config":
            host, port = self._params()
            code, body = forward("config", host, port, raw)
            self._headers(code, body)
            return

        w = incoming.get("warn", CFG["warn"])
        s = incoming.get("spoiled", CFG["spoiled"])
        f = incoming.get("floor", CFG["floor"])
        c = incoming.get("ceil", CFG["ceil"])
        try:
            w, s, f, c = int(w), int(s), int(f), int(c)
        except (TypeError, ValueError):
            self._json(400, {"ok": False, "error": "Thresholds must be integers."})
            return

        if not (0 <= f < w < s < c <= 4095):
            self._json(400, {"ok": False,
                             "error": "Rule: floor < warning < spoiled < ceil (0-4095)."})
            return

        CFG.update(warn=w, spoiled=s, floor=f, ceil=c)
        self._json(200, {"ok": True, **CFG})

    def log_message(self, fmt, *args):  # quieter console, robust to HTTPStatus args
        try:
            line = fmt % args if args else fmt
        except Exception:
            line = str(fmt)
        if "/data" not in line:
            super().log_message(fmt, *args)


if __name__ == "__main__":
    import sys

    i = 1
    while i < len(sys.argv):                      # tiny argv parser, no deps
        if sys.argv[i] in ("--esp32", "--host") and i + 1 < len(sys.argv):
            DEFAULT_ESP32_HOST = sys.argv[i + 1]; i += 2
        elif sys.argv[i] == "--esp32-port" and i + 1 < len(sys.argv):
            DEFAULT_ESP32_PORT = int(sys.argv[i + 1]); i += 2
        elif sys.argv[i] == "--mode" and i + 1 < len(sys.argv):
            MODE = sys.argv[i + 1]; i += 2
        elif sys.argv[i] == "--port" and i + 1 < len(sys.argv):
            PORT = int(sys.argv[i + 1]); i += 2
        else:
            print("unknown argument:", sys.argv[i]); sys.exit(2)

    print("Smart Food Spoilage Monitor - local server")
    print("  Dashboard : http://localhost:%d" % PORT)
    print("  JSON API  : http://localhost:%d/data   (mode: %s)" % (PORT, MODE))
    if DEFAULT_ESP32_HOST:
        print("  ESP32     : http://%s:%d  (via /proxy/data)" % (DEFAULT_ESP32_HOST, DEFAULT_ESP32_PORT))
    else:
        print("  ESP32     : not set - choose Settings > Data source > ESP32")
    print("  Ctrl+C to stop.")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
