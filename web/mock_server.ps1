<#
  Smart Food Spoilage Monitor — local server (mock sensor + real ESP32 bridge).

  Serves web/index.html plus a JSON API with two backends:

    mock   (default)  simulated MQ-3 — random walk + periodic spoilage event
    esp32             forwards to a real ESP32 so the UI shows live readings

  The ESP32 is reached through /proxy/* on this server, so the browser never
  hits a CORS problem and no firmware change is required to use it. Proxy
  calls run on background HttpClient tasks, so one slow/unreachable ESP32 can
  never block the dashboard or other clients (self-proxy works too).

  Usage:
      powershell -ExecutionPolicy Bypass -File web\mock_server.ps1
      powershell -ExecutionPolicy Bypass -File web\mock_server.ps1 -Esp32Host 192.168.1.57
      powershell -ExecutionPolicy Bypass -File web\mock_server.ps1 -Mode esp32 -Esp32Host 192.168.1.57
      # then open http://localhost:8000

  Endpoints:
      GET  /                         the dashboard
      GET  /server-info              defaults for the Settings panel
      GET  /data                     mock reading (live when -Mode esp32)
      POST /config                   mock threshold write
      GET  /settings                 stored ESP32 address (settings.json)
      POST /settings                 {"host":...,"port":...} -> saved next to
                                     the server so the address survives a browser
      GET  /proxy/data?host=&port=   live reading from the ESP32 (async)
      POST /proxy/config?host=&port= write thresholds to the ESP32 (async)

  Stop with Ctrl+C.
#>
param(
    [string]$Esp32Host = '',
    [int]$Esp32Port = 80,
    [ValidateSet('mock', 'esp32')][string]$Mode = 'mock',
    [int]$Port = 0
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$htmlPath = Join-Path $root 'index.html'
$settingsPath = Join-Path $root 'settings.json'
if (-not $Port) { $Port = if ($env:PORT) { [int]$env:PORT } else { 8000 } }
$prefix = "http://localhost:$Port/"

# --- persisted server settings (settings.json): the address survives browsers --
$stored = @{}
if (Test-Path -LiteralPath $settingsPath) {
    try {
        $s = Get-Content -Raw -LiteralPath $settingsPath | ConvertFrom-Json
        if ($s.host) { $stored.host = [string]$s.host }
        if ($s.port) { $stored.port = [int]$s.port }
    } catch { }
}
# precedence: -Esp32Host / -Esp32Port param > env > settings.json > defaults
${script:Esp32HostDefault} = if ($Esp32Host) { $Esp32Host }
    elseif ($env:ESP32_HOST) { $env:ESP32_HOST }
    elseif ($stored.host) { $stored.host } else { '' }
${script:Esp32PortDefault} = if ($env:ESP32_PORT) { [int]$env:ESP32_PORT }
    elseif ($stored.port) { $stored.port } else { $Esp32Port }

# --- simulated device state --------------------------------------------------
$cfg = @{ warn = 1600; spoiled = 2600; floor = 1000; ceil = 3800 }
$sim = @{ value = 1150.0; phase = 0; started = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }

function Get-StatusOf([double]$v) {
    if ($v -ge $cfg.spoiled) { 'SPOILED' }
    elseif ($v -ge $cfg.warn) { 'WARNING' }
    else { 'FRESH' }
}

function Get-SensorData {
    # Random walk with a periodic simulated "spoilage event".
    $sim.phase = ($sim.phase + 1) % 40
    $e = $sim.phase
    $target = if ($e -gt 8 -and $e -lt 26) { 3100 - [math]::Abs($e - 17) * 90 } else { 1150.0 }
    $sim.value += ($target - $sim.value) * 0.12 + (Get-Random -Minimum (-45.0) -Maximum 45.0)
    $sim.value = [math]::Max(700.0, [math]::Min(4000.0, $sim.value))

    $v = [int]$sim.value
    $span = $cfg.ceil - $cfg.floor
    $pct = if ($span -gt 0) { [int][math]::Round(($v - $cfg.floor) * 100.0 / $span) } else { 0 }
    $pct = [math]::Max(0, [math]::Min(100, $pct))

    [ordered]@{
        value        = $v
        raw          = $v
        avg          = $v
        pct          = $pct
        status       = Get-StatusOf $v
        thr_warn     = $cfg.warn
        thr_spoiled  = $cfg.spoiled
        cal_floor    = $cfg.floor
        cal_ceil     = $cfg.ceil
        uptime_s     = [int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - $sim.started)
        rssi         = -55
        mode         = 'MOCK SERVER'
        ip           = 'localhost'
    }
}

# --- ESP32 bridge (async) -----------------------------------------------------
# Every proxy request spawns an HttpClient task that is reaped between
# accepts, so a slow ESP32 (4 s timeout) never blocks anything else and the
# server can even proxy to itself.
$httpClient = New-Object System.Net.Http.HttpClient
$httpClient.Timeout = [TimeSpan]::FromSeconds(4)
$pending = New-Object System.Collections.ArrayList    # { ctx, task, host, port }

function Send-ProxyAsync($ctx, [string]$path, [string]$body) {
    $q = $ctx.Request.QueryString
    $h = if ($q['host']) { $q['host'] } else { $Esp32HostDefault }
    $p = if ($q['port']) { [int]$q['port'] } else { $Esp32PortDefault }
    $target = "http://${h}:$p$path"
    if ($body) {
        $contentObj = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, 'application/json')
        $task = $httpClient.PostAsync($target, $contentObj)
    } else {
        $task = $httpClient.GetAsync($target)
    }
    [void]$pending.Add([pscustomobject]@{ ctx = $ctx; task = $task; host = $h; port = $p })
    $h; return   # nothing is written here - the reap loop answers the browser
}

function Complete-PendingProxies {
    # Send the response for every finished proxy call. Returns $true when
    # something was written (so the main loop keeps polling without sleeping).
    $written = $false
    $done = New-Object System.Collections.ArrayList
    foreach ($item in $pending) {
        if (-not $item.task.IsCompleted) { continue }
        [void]$done.Add($item)
        $written = $true
        $code = 502
        $content = '{"ok":false,"error":"ESP32 request failed."}'
        try {
            $resp = $item.task.Result
            $code = [int]$resp.StatusCode
            $content = $resp.Content.ReadAsStringAsync().Result
        } catch {
            $content = (@{ ok = $false; error = "ESP32 $($item.host):$($item.port) unreachable - request failed" } | ConvertTo-Json -Compress)
        }
        try { Send-Bytes $item.ctx $code ([Text.Encoding]::UTF8.GetBytes($content)) 'application/json' } catch { }
    }
    foreach ($item in $done) { [void]$pending.Remove($item) }
    return $written
}

# --- responses ---------------------------------------------------------------
function Send-Json($ctx, [int]$code, $obj) {
    $bytes = [Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Compress -Depth 4))
    Send-Bytes $ctx $code $bytes 'application/json'
}

function Send-Bytes($ctx, [int]$code, [byte[]]$bytes, [string]$type) {
    $ctx.Response.StatusCode = $code
    $ctx.Response.ContentType = $type
    $ctx.Response.Headers.Add('Cache-Control', 'no-store')
    # CORS so a page opened from file:// or another port can call this server
    $ctx.Response.Headers.Add('Access-Control-Allow-Origin', '*')
    $ctx.Response.Headers.Add('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    $ctx.Response.Headers.Add('Access-Control-Allow-Headers', 'Content-Type')
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()
}

# --- request bodies -----------------------------------------------------------
function Read-RawBody($ctx) {
    $reader = New-Object IO.StreamReader($ctx.Request.InputStream, $ctx.Request.ContentEncoding)
    $b = $reader.ReadToEnd(); $reader.Close()
    return $b
}
function Read-JsonBody($ctx) {
    try { return (Read-RawBody $ctx) | ConvertFrom-Json } catch { return @{} }
}

# --- one request --------------------------------------------------------------
function Process-Request($ctx) {
    $path = $ctx.Request.Url.AbsolutePath
    $method = $ctx.Request.HttpMethod

    if ($method -eq 'OPTIONS') {
        Send-Bytes $ctx 204 ([byte[]]@()) 'text/plain'
    }
    elseif ($path -eq '/settings' -and $method -eq 'GET') {
        Send-Json $ctx 200 ([ordered]@{
            ok = $true; host = $Esp32HostDefault; port = $Esp32PortDefault; server = 'powershell'
        })
    }
    elseif ($path -eq '/settings' -and $method -eq 'POST') {
        $in = Read-JsonBody $ctx
        $addrHost = [string]$in.host
        try { $addrPort = [int]$in.port } catch { $addrPort = 0 }
        if (-not $addrHost) { Send-Json $ctx 400 @{ ok = $false; error = 'Host must not be empty.' }; return }
        if ($addrPort -lt 1 -or $addrPort -gt 65535) { Send-Json $ctx 400 @{ ok = $false; error = 'Port must be 1-65535.' }; return }
        $tmp = "$settingsPath.tmp"
        (@{ host = $addrHost; port = $addrPort } | ConvertTo-Json -Compress) |
            Set-Content -LiteralPath $tmp -NoNewline -Encoding UTF8
        Move-Item -LiteralPath $tmp -Destination $settingsPath -Force
        ${script:Esp32HostDefault} = $addrHost
        ${script:Esp32PortDefault} = $addrPort
        Write-Host "  [settings] ESP32 address saved: ${addrHost}:$addrPort"
        Send-Json $ctx 200 @{ ok = $true; host = $addrHost; port = $addrPort }
    }
    elseif ($path -eq '/server-info' -and $method -eq 'GET') {
        Send-Json $ctx 200 ([ordered]@{
            mode = $Mode; esp32Host = $Esp32HostDefault; esp32Port = $Esp32PortDefault
            server = 'powershell'; port = $Port
        })
    }
    elseif ($path -eq '/data' -and $method -eq 'GET') {
        if ($Mode -eq 'esp32') { [void](Send-ProxyAsync $ctx '/data' $null) }
        else { Send-Json $ctx 200 (Get-SensorData) }
    }
    elseif ($path -eq '/proxy/data' -and $method -eq 'GET') {
        [void](Send-ProxyAsync $ctx '/data' $null)
    }
    elseif (($path -eq '/config' -or $path -eq '/proxy/config') -and $method -eq 'POST') {
        $body = Read-RawBody $ctx
        if ($path -eq '/proxy/config') { [void](Send-ProxyAsync $ctx '/config' $body); return }

        try { $in = $body | ConvertFrom-Json } catch { $in = $null }

        $w = if ($in -and $null -ne $in.warn)    { [int]$in.warn    } else { $cfg.warn }
        $s = if ($in -and $null -ne $in.spoiled) { [int]$in.spoiled } else { $cfg.spoiled }
        $f = if ($in -and $null -ne $in.floor)   { [int]$in.floor   } else { $cfg.floor }
        $c = if ($in -and $null -ne $in.ceil)    { [int]$in.ceil    } else { $cfg.ceil }

        if (-not ($f -ge 0 -and $c -le 4095 -and $f -lt $w -and $w -lt $s -and $s -lt $c)) {
            Send-Json $ctx 400 @{ ok = $false; error = 'Rule: floor < warning < spoiled < ceil (0-4095).' }
        } else {
            $cfg.warn = $w; $cfg.spoiled = $s; $cfg.floor = $f; $cfg.ceil = $c
            Write-Host "  [cfg] saved: warn=$w spoiled=$s floor=$f ceil=$c"
            Send-Json $ctx 200 ([ordered]@{ ok = $true; warn = $w; spoiled = $s; floor = $f; ceil = $c })
        }
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
                '.py'   { 'text/plain; charset=utf-8' }
                '.md'   { 'text/plain; charset=utf-8' }
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

# --- HTTP listener ------------------------------------------------------------
# Single thread, but proxy work happens on background HttpClient tasks, so the
# accept loop never blocks on the ESP32. GetContextAsync is polled and finished
# proxy calls are answered between request arrivals.
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
# also accept Host: 127.0.0.1 (HttpListener matches the Host header, so
# Http://localhost:PORT proxied to itself via 127.0.0.1 needs this prefix)
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "Smart Food Spoilage Monitor - local server"
Write-Host "  Dashboard : http://localhost:$Port"
Write-Host "  JSON API  : http://localhost:$Port/data   (mode: $Mode)"
if ($Esp32HostDefault) {
    Write-Host "  ESP32     : http://${Esp32HostDefault}:$Esp32PortDefault  (via /proxy/data, async)"
} else {
    Write-Host "  ESP32     : not set - choose Settings > Data source > ESP32"
}
Write-Host "  Ctrl+C to stop."

try {
    while ($listener.IsListening) {
        $accept = $null
        try { $accept = $listener.GetContextAsync() } catch { break }
        # wait for the next request; meanwhile answer any finished proxy calls
        while (-not $accept.AsyncWaitHandle.WaitOne(100)) {
            if (-not (Complete-PendingProxies)) { Start-Sleep -Milliseconds 25 }
        }
        try { $ctx = $accept.Result } catch { break }
        try {
            Process-Request $ctx
        } catch {
            try { Send-Json $ctx 500 @{ ok = $false; error = $_.Exception.Message } } catch { }
        }
        # reap anything that finished while we served the request
        [void](Complete-PendingProxies)
    }
}
finally {
    $httpClient.Dispose()
    $listener.Stop()
    $listener.Close()
    Write-Host "Server stopped."
}