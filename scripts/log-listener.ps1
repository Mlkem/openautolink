<#
.SYNOPSIS
    Log Listener -- receives log stream from OpenAutoLink app (outbound mode).

.DESCRIPTION
    Listens on port 6555 for the app to connect outbound and stream logs.
    Use when the hotspot blocks inbound connections to the car.

    In the app Debug tab: enter this laptop's IP, tap "Connect Out".

.PARAMETER Port
    Port to listen on (default: 6555)

.EXAMPLE
    .\log-listener.ps1
    # Then in app Debug tab: enter laptop IP, tap "Connect Out"
#>
param(
    [int]$Port = 6555
)

$ErrorActionPreference = "Stop"

Write-Host "[log] === OpenAutoLink Log Listener ===" -ForegroundColor Cyan
Write-Host "[log] " -ForegroundColor Cyan
Write-Host "[log] Local IP addresses:" -ForegroundColor Cyan
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -ne "127.0.0.1" -and $_.PrefixOrigin -ne "WellKnown" } |
    ForEach-Object { Write-Host "[log]   $($_.InterfaceAlias): $($_.IPAddress)" -ForegroundColor Cyan }
Write-Host "[log] " -ForegroundColor Cyan
Write-Host "[log] Listening on port $Port -- enter this IP in the app" -ForegroundColor Cyan
Write-Host "[log] " -ForegroundColor Cyan

while ($true) {
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
        $listener.Start()
        Write-Host "[log] Waiting for app to connect on port $Port..." -ForegroundColor Cyan

        $client = $listener.AcceptTcpClient()
        $listener.Stop()
        $remote = $client.Client.RemoteEndPoint
        Write-Host "[log] App connected from $remote" -ForegroundColor Green
        Write-Host "[log] " -ForegroundColor Cyan

        $reader = New-Object System.IO.StreamReader($client.GetStream())

        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ($line -match "^#") { continue }  # skip keepalives
            # Color-code by severity
            if ($line -match " E/") {
                Write-Host $line -ForegroundColor Red
            } elseif ($line -match " W/") {
                Write-Host $line -ForegroundColor Yellow
            } elseif ($line -match " I/") {
                Write-Host $line -ForegroundColor White
            } elseif ($line -match "^===") {
                Write-Host $line -ForegroundColor Cyan
            } else {
                Write-Host $line -ForegroundColor DarkGray
            }
        }

        Write-Host "[log] App disconnected" -ForegroundColor Yellow
    } catch {
        Write-Host "[log] Error: $_" -ForegroundColor Red
    } finally {
        if ($client) { try { $client.Close() } catch {} }
        if ($listener) { try { $listener.Stop() } catch {} }
    }

    Write-Host "[log] " -ForegroundColor Cyan
    Write-Host "[log] Waiting for next connection..." -ForegroundColor Cyan
}
