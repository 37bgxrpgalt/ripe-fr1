<#
  Smart Food Spoilage Monitor — USB serial bridge (PC, no Wi-Fi, no Bluetooth).

  Use this when the ESP32 can only reach the PC through a USB cable: plug the
  board in, run this script, and open the address it prints. The script:

    * serves the dashboard (web\index.html) so nothing needs the internet;
    * opens the ESP32's COM port at 115200 baud;
    * forwards GET /data   -> the latest JSON snapshot the device streams;
    * forwards POST /config -> "CFG:w,s,f,c" down the cable (thresholds save
                               to the device's flash, exactly like BLE/Wi-Fi).

  In the dashboard pick Settings -> Data source -> USB (PC).

  Works with the current firmware (section 8b streams one JSON line every
  REFRESH_MS) AND with older firmware, which only prints the
  "raw,avg,status" CSV lines — those are parsed as a fallback.

  Usage:
      powershell -ExecutionPolicy Bypass -File web\usb_server.ps1
      powershell -ExecutionPolicy Bypass -File web\usb_server.ps1 -ComPort COM5
      powershell -ExecutionPolicy Bypass -File web\usb_server.ps1 -Port 8013
      powershell -ExecutionPolicy Bypass -File web\usb_server.ps1 -Demo   # no hardware
      # then open http://localhost:8013  (the script prints the exact URL)

  Endpoints:
      GET  /            the dashboard
      GET  /data        live reading bridged from the COM port
      POST /config      {"warn":..,"spoiled":..,"floor":..,"ceil":..}
      GET  /serial-info which COM port / baud / last line received
      GET  /server-info defaults for the Settings panel

  Stop with Ctrl+C.
#>
param(
    [string]$ComPort = '',
    [int]$Baud = 115200,
    [int]$Port = 0,
    [switch]$Demo
)
$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$htmlPath = Join-Path $root 'index.html'
if (-not $Port) { $Port = if ($env:PORT) { [int]$env:PORT } else { 8013 } }
$prefix = "http://localhost:$Port/"

# --- serial / device state ---------------------------------------------------
$sp          = $null        # System.IO.Ports.SerialPort once open
$rx          = ''           # bytes received but not yet split into a line
$last        = $null        # last full JSON snapshot from the device
$lastMs      = 0            # when $last was parsed
$lastLine    = ''           # last non-empty line seen (for /serial-info)
$portName    = ''           # the COM port actually in use
$cfgAck      = $null        # pending {"ok":..} reply to a CFG command
$csv         = $null        # fallback {raw,avg,status} from old-firmware CSV
$cfg         = @{ warn = 1600; spoiled = 2600; floor = 1000; ceil = 3800 }

# --- demo mode (no hardware): synthesise the same stream ---------------------
$sim = @{ value = 1150.0; phase = 0 }
$simLast = 0

function Get-SerialPorts {
    try { return @([System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object) }
    catch { if ($Demo) { return @() } else { throw } }
}

function Open-Bridge {
    if ($Demo) { $script:portName = 'DEMO'; Write-Host '  [serial] demo mode - no COM port is opened.'; return }
    $name = $ComPort
    if (-not $name) {
        $ports = Get-SerialPorts
        if ($ports.Count -eq 0) {
            Write-Host '  [serial] no COM ports found. Plug the ESP32 in, then rerun the script.'
            return
        }
        $name = $ports[0]
        if ($ports.Count -gt 1) {
            Write-Host "  [serial] several ports found ($($ports -join ', ')) - using $name (pass -ComPort to choose)."
        }
    }
    try {
        $p = New-Object System.IO.Ports.SerialPort $name, $Baud, 'None', 8, 'One'
        $p.ReadTimeout  = 200
        $p.WriteTimeout = 1000
        # Keep DTR/RTS low: some boards (CP210x/CH340) reset when they toggle.
        $p.DtrEnable = $false
        $p.RtsEnable = $false
        $p.Open()
        $script:sp = $p
        $script:portName = $name
        Write-Host "  [serial] opened $name at $Baud baud."
    } catch {
        $script:sp = $null
        Write-Host "  [serial] could not open ${name}: $($_.Exception.Message)"
        Write-Host '           Close any Serial Monitor / Arduino IDE / other bridge and retry.'
    }
}

# --- turn the raw stream into state ------------------------------------------
function Handle-Line([string]$line) {
    if (-not $line) { return }
    $script:lastLine = $line

    if ($line[0] -eq '{') {
        try { $o = $line | ConvertFrom-Json } catch { $o = $null }
        if ($o) {
            if ($null -ne $o.ok) { $script:cfgAck = $o; return }
            if ($null -ne $o.avg -or $null -ne $o.value) { Store-Snapshot $o; return }
        }
        return
    }

    # fallback for firmware that only prints the Plotter CSV: raw,avg,status
    if ($line -match '^\d+,\d+,(FRESH|WARNING|SPOILED)\s*$') {
        $parts = $line.Trim() -split ','
        $script:csv = @{ raw = [int]$parts[0]; avg = [int]$parts[1]; status = $parts[2] }
    }
}

function Store-Snapshot($o) {
    if ($null -ne $o.thr_warn -and $null -ne $o.thr_spoiled -and
        $null -ne $o.cal_floor -and $null -ne $o.cal_ceil) {
        $script:cfg.warn    = [int]$o.thr_warn
        $script:cfg.spoiled = [int]$o.thr_spoiled
        $script:cfg.floor   = [int]$o.cal_floor
        $script:cfg.ceil    = [int]$o.cal_ceil
    }
    $script:last = [ordered]@{
        value       = [int]$o.value
        raw         = [int]$o.raw
        avg         = [int]$o.avg
        pct         = [int]$o.pct
        status      = [string]$o.status
        thr_warn    = $script:cfg.warn
        thr_spoiled = $script:cfg.spoiled
        cal_floor   = $script:cfg.floor
        cal_ceil    = $script:cfg.ceil
        uptime_s    = if ($null -ne $o.uptime_s) { [int]$o.uptime_s } else { 0 }
        rssi        = if ($null -ne $o.rssi) { [int]$o.rssi } else { 0 }
        mode        = 'SERIAL'
        ip          = 'usb://' + $script:portName
    }
    $script:lastMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
}

function Pump-Serial {
    if ($Demo) { Pump-Demo; return }
    if (-not $sp -or -not $sp.IsOpen) { return }
    try { $chunk = $sp.ReadExisting() } catch { return }
    if (-not $chunk) { return }
    $script:rx += $chunk
    while ($true) {
        $i = $script:rx.IndexOf("`n")
        if ($i -lt 0) { break }
        $line = $script:rx.Substring(0, $i).TrimEnd("`r")
        $script:rx = $script:rx.Substring($i + 1)
        Handle-Line $line
    }
    if ($script:rx.Length -gt 8192) { $script:rx = '' }   # guard vs runaway binary
}

function Pump-Demo {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    if ($now - $script:simLast -lt 800) { return }
    $script:simLast = $now
    # random walk + a spoilage event every ~60 s (approximates the mock server)
    $script:sim.phase = ($script:sim.phase + 1) % 75
    $e = $script:sim.phase
    $target = if ($e -gt 20 -and $e -lt 55) { 3100 - [math]::Abs($e - 37) * 70 } else { 1150.0 }
    $script:sim.value += ($target - $script:sim.value) * 0.12 + (Get-Random -Minimum (-45.0) -Maximum 45.0)
    $script:sim.value = [math]::Max(700.0, [math]::Min(4000.0, $script:sim.value))
    $v = [int]$script:sim.value
    Handle-Line (@{ value = $v; raw = $v; avg = $v; pct = 0; status = 'FRESH'
                    thr_warn = $cfg.warn; thr_spoiled = $cfg.spoiled
                    cal_floor = $cfg.floor; cal_ceil = $cfg.ceil
                    uptime_s = 0; rssi = 0 } | ConvertTo-Json -Compress)
}

# --- HTTP helpers (same contract as mock_server.ps1) -------------------------
function Send-Bytes($ctx, [int]$code, [byte[]]$bytes, [string]$type) {
    $ctx.Response.StatusCode = $code
    $ctx.Response.ContentType = $type
    $ctx.Response.Headers.Add('Cache-Control', 'no-store')
    $ctx.Response.Headers.Add('Access-Control-Allow-Origin', '*')
    $ctx.Response.Headers.Add('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    $ctx.Response.Headers.Add('Access-Control-Allow-Headers', 'Content-Type')
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()
}
function Send-Json($ctx, [int]$code, $obj) {
    $bytes = [Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Compress -Depth 5))
    Send-Bytes $ctx $code $bytes 'application/json'
}
function Read-RawBody($ctx) {
    $reader = New-Object IO.StreamReader($ctx.Request.InputStream, $ctx.Request.ContentEncoding)
    $b = $reader.ReadToEnd(); $reader.Close()
    return $b
}

# --- data for the dashboard ---------------------------------------------------
function Get-LiveData {
    if ($last) { return $last }
    if ($csv) {
        $v = $csv.avg
        $span = $cfg.ceil - $cfg.floor
        $pct = if ($span -gt 0) { [int][math]::Round(($v - $cfg.floor) * 100.0 / $span) } else { 0 }
        $pct = [math]::Max(0, [math]::Min(100, $pct))
        return [ordered]@{
            value = $csv.raw; raw = $csv.raw; avg = $v; pct = $pct; status = $csv.status
            thr_warn = $cfg.warn; thr_spoiled = $cfg.spoiled
            cal_floor = $cfg.floor; cal_ceil = $cfg.ceil
            uptime_s = 0; rssi = 0; mode = 'SERIAL (CSV)'; ip = 'usb://' + $portName
        }
    }
    return $null
}

# --- one request --------------------------------------------------------------
function Process-Request($ctx) {
    $path = $ctx.Request.Url.AbsolutePath
    $method = $ctx.Request.HttpMethod

    if ($method -eq 'OPTIONS') {
        Send-Bytes $ctx 204 ([byte[]]@()) 'text/plain'
    }
    elseif ($path -eq '/data' -and $method -eq 'GET') {
        $d = Get-LiveData
        if ($d) { Send-Json $ctx 200 $d }
        else {
            Send-Json $ctx 503 ([ordered]@{
                ok = $false; mode = 'SERIAL'
                error = if ($sp -or $Demo) { 'Waiting for the first reading from the device.' }
                        else { 'No COM port is open - plug the ESP32 in and rerun web\usb_server.ps1.' }
            })
        }
    }
    elseif ($path -eq '/config' -and $method -eq 'POST') {
        try { $in = (Read-RawBody $ctx) | ConvertFrom-Json } catch { $in = $null }
        $w = if ($in -and $null -ne $in.warn)    { [int]$in.warn    } else { $cfg.warn }
        $s = if ($in -and $null -ne $in.spoiled) { [int]$in.spoiled } else { $cfg.spoiled }
        $f = if ($in -and $null -ne $in.floor)   { [int]$in.floor   } else { $cfg.floor }
        $c = if ($in -and $null -ne $in.ceil)    { [int]$in.ceil    } else { $cfg.ceil }

        if (-not ($f -ge 0 -and $c -le 4095 -and $f -lt $w -and $w -lt $s -and $s -lt $c)) {
            Send-Json $ctx 400 @{ ok = $false; error = 'Rule: floor < warning < spoiled < ceil (0-4095).' }
            return
        }
        if ($Demo) {
            $cfg.warn = $w; $cfg.spoiled = $s; $cfg.floor = $f; $cfg.ceil = $c
            Send-Json $ctx 200 ([ordered]@{ ok = $true; warn = $w; spoiled = $s; floor = $f; ceil = $c })
            return
        }
        if (-not $sp -or -not $sp.IsOpen) {
            Send-Json $ctx 503 @{ ok = $false; error = 'No COM port is open - cannot send the new thresholds.' }
            return
        }
        $script:cfgAck = $null
        try { $sp.WriteLine("CFG:$w,$s,$f,$c") } catch {
            Send-Json $ctx 500 @{ ok = $false; error = "Serial write failed: $($_.Exception.Message)" }
            return
        }
        Write-Host "  [cfg] sent CFG:$w,$s,$f,$c - waiting for the device ack..."
        $deadline = [DateTime]::UtcNow.AddMilliseconds(2500)
        while (-not $script:cfgAck -and [DateTime]::UtcNow -lt $deadline) {
            Pump-Serial
            if (-not $script:cfgAck) { Start-Sleep -Milliseconds 40 }
        }
        if ($script:cfgAck) {
            $cfg.warn = $w; $cfg.spoiled = $s; $cfg.floor = $f; $cfg.ceil = $c
            Write-Host "  [cfg] device confirmed: warn=$w spoiled=$s floor=$f ceil=$c"
            Send-Json $ctx 200 $script:cfgAck
        } else {
            # the write succeeded even if the ack was lost in the noise
            Send-Json $ctx 200 ([ordered]@{ ok = $true; warn = $w; spoiled = $s; floor = $f; ceil = $c
                                            note = 'sent to the device (no ack seen)' })
        }
    }
    elseif ($path -eq '/serial-info' -and $method -eq 'GET') {
        Send-Json $ctx 200 ([ordered]@{
            ok = [bool]($sp -or $Demo); demo = [bool]$Demo
            port = $portName; baud = $Baud
            ports = @(Get-SerialPorts)
            lastMsAgo = if ($lastMs) { [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $lastMs } else { $null }
            lastLine = $lastLine
        })
    }
    elseif ($path -eq '/server-info' -and $method -eq 'GET') {
        Send-Json $ctx 200 ([ordered]@{
            mode = 'usb'; server = 'powershell'; port = $Port
            comPort = $portName; baud = $Baud
        })
    }
    elseif ($path -eq '/settings' -and $method -eq 'GET') {
        Send-Json $ctx 200 @{ ok = $true; server = 'powershell'; mode = 'usb' }
    }
    elseif ($path -eq '/settings' -and $method -eq 'POST') {
        # the page remembers the ESP32 IP; USB mode has none, so just accept it
        Send-Json $ctx 200 @{ ok = $true }
    }
    elseif ($method -eq 'GET') {
        $file = if ($path -eq '/' -or $path -eq '') { $htmlPath } else { Join-Path $root ($path.TrimStart('/')) }
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            $ext = [IO.Path]::GetExtension($file).ToLowerInvariant()
            $type = switch ($ext) {
                '.html' { 'text/html; charset=utf-8' }
                '.svg'  { 'image/svg+xml' }
                '.css'  { 'text/css' }
                '.js'   { 'application/javascript' }
                '.json' { 'application/json' }
                default { 'application/octet-stream' }
            }
            Send-Bytes $ctx 200 ([IO.File]::ReadAllBytes($file)) $type
        } else {
            Send-Bytes $ctx 404 ([Text.Encoding]::UTF8.GetBytes('Not found - try / or /data')) 'text/plain'
        }
    }
    else {
        Send-Bytes $ctx 405 ([Text.Encoding]::UTF8.GetBytes('Unsupported method')) 'text/plain'
    }
}

# --- start -------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $htmlPath)) {
    Write-Host "WARNING: $htmlPath not found."
    Write-Host "         Copy web\index.html (and favicon.svg) next to this script so"
    Write-Host "         the bridge can serve the dashboard - /data still works without it."
    Write-Host ''
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
try { $listener.Start() } catch {
    Write-Host "Could not listen on port $Port : $($_.Exception.Message)"
    Write-Host "Pass a different one, e.g.  -Port 8014"
    exit 1
}

Open-Bridge

Write-Host 'Smart Food Spoilage Monitor - USB serial bridge'
Write-Host "  Dashboard : http://localhost:$Port   (Settings > Data source > USB (PC))"
Write-Host "  JSON API  : http://localhost:$Port/data"
if ($Demo) {
    Write-Host '  Device    : DEMO mode (no COM port)'
} elseif ($sp) {
    Write-Host "  Device    : $portName at $Baud baud"
} else {
    Write-Host '  Device    : not connected - plug in the ESP32 and rerun the script'
}
Write-Host '  Ctrl+C to stop.'

$lastPump = 0
try {
    while ($listener.IsListening) {
        $accept = $null
        try { $accept = $listener.GetContextAsync() } catch { break }
        # wait for the next request while draining the serial port
        while (-not $accept.AsyncWaitHandle.WaitOne(50)) {
            if ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $lastPump -ge 40) {
                $lastPump = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                Pump-Serial
            }
        }
        try { $ctx = $accept.Result } catch { break }
        try { Process-Request $ctx } catch {
            try { Send-Json $ctx 500 @{ ok = $false; error = $_.Exception.Message } } catch { }
        }
    }
}
finally {
    if ($sp) { try { $sp.Close(); $sp.Dispose() } catch { } }
    $listener.Stop()
    $listener.Close()
    Write-Host 'Bridge stopped.'
}
