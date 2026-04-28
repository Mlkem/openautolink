<#
.SYNOPSIS
    ADB Reverse Tunnel Relay -- laptop-side relay for tunneling ADB through the OpenAutoLink app.

.DESCRIPTION
    This script listens on two ports:
    - Port 6556: Waits for the OpenAutoLink app to connect OUT from the car head unit
    - Port 15555: Local ADB endpoint -- run `adb connect localhost:15555` to use

    Traffic flows: adb (laptop) -> :15555 -> relay -> tunnel -> head-unit:5555

    The app must be configured to connect to this laptop's IP on port 6556.
    Since the head unit can't accept incoming connections (firewall/AP isolation),
    the app initiates the outbound connection to this relay.

.PARAMETER TunnelPort
    Port the app connects to (default: 6556)

.PARAMETER AdbPort
    Local port for ADB to connect to (default: 15555)

.EXAMPLE
    .\adb-relay.ps1
    # Then in the app Debug tab: enter this laptop's IP, tap "Connect Tunnel"
    # Then: adb connect localhost:15555
#>
param(
    [int]$TunnelPort = 6556,
    [int]$AdbPort = 15555
)

$ErrorActionPreference = "Stop"

function Write-Status($msg) {
    Write-Host "[relay] $msg" -ForegroundColor Cyan
}

function Write-OK($msg) {
    Write-Host "[relay] $msg" -ForegroundColor Green
}

function Write-Err($msg) {
    Write-Host "[relay] $msg" -ForegroundColor Red
}

# Show local IPs so user knows what to enter in the app
Write-Status "=== OpenAutoLink ADB Reverse Tunnel Relay ==="
Write-Status ""
Write-Status "Local IP addresses:"
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -ne "127.0.0.1" -and $_.PrefixOrigin -ne "WellKnown" } |
    ForEach-Object { Write-Status "  $($_.InterfaceAlias): $($_.IPAddress)" }
Write-Status ""
Write-Status "Tunnel port (app connects here): $TunnelPort"
Write-Status "ADB port (adb connect localhost:$AdbPort): $AdbPort"
Write-Status ""

while ($true) {
    try {
        # Step 1: Listen for the app's outbound connection
        Write-Status "Waiting for app to connect on port $TunnelPort..."
        $tunnelListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $TunnelPort)
        $tunnelListener.Start()

        $appClient = $tunnelListener.AcceptTcpClient()
        $tunnelListener.Stop()
        $appEndpoint = $appClient.Client.RemoteEndPoint
        Write-OK "App connected from $appEndpoint"

        # Step 2: Listen for ADB connection
        Write-Status "Waiting for ADB on port $AdbPort..."
        Write-Status "  Run: adb connect localhost:$AdbPort"
        $adbListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $AdbPort)
        $adbListener.Start()

        $adbClient = $adbListener.AcceptTcpClient()
        $adbListener.Stop()
        Write-OK "ADB connected"

        # Step 3: Bridge the two connections
        Write-Status "Bridging ADB <-> tunnel (Ctrl+C to stop)..."

        $appStream = $appClient.GetStream()
        $adbStream = $adbClient.GetStream()

        $appClient.Client.NoDelay = $true
        $adbClient.Client.NoDelay = $true

        # Use async read/write to bridge bidirectionally
        $bufSize = 32768
        $buf1 = New-Object byte[] $bufSize
        $buf2 = New-Object byte[] $bufSize

        $totalBytes = 0

        # Start async reads for both directions
        $appRead = $appStream.BeginRead($buf1, 0, $bufSize, $null, $null)
        $adbRead = $adbStream.BeginRead($buf2, 0, $bufSize, $null, $null)

        while ($appClient.Connected -and $adbClient.Connected) {
            # Check app->adb direction
            if ($appRead.IsCompleted) {
                $n = $appStream.EndRead($appRead)
                if ($n -le 0) { Write-Status "App disconnected"; break }
                $adbStream.Write($buf1, 0, $n)
                $adbStream.Flush()
                $totalBytes += $n
                $appRead = $appStream.BeginRead($buf1, 0, $bufSize, $null, $null)
            }

            # Check adb->app direction
            if ($adbRead.IsCompleted) {
                $n = $adbStream.EndRead($adbRead)
                if ($n -le 0) { Write-Status "ADB disconnected"; break }
                $appStream.Write($buf2, 0, $n)
                $appStream.Flush()
                $totalBytes += $n
                $adbRead = $adbStream.BeginRead($buf2, 0, $bufSize, $null, $null)
            }

            # Small sleep to avoid busy-wait, but keep latency low
            Start-Sleep -Milliseconds 1
        }

        $kb = [math]::Round($totalBytes / 1024, 1)
        Write-Status "Session ended -- $kb KB forwarded"

    } catch {
        Write-Err "Error: $_"
    } finally {
        # Cleanup
        if ($appClient) { try { $appClient.Close() } catch {} }
        if ($adbClient) { try { $adbClient.Close() } catch {} }
        if ($tunnelListener) { try { $tunnelListener.Stop() } catch {} }
        if ($adbListener) { try { $adbListener.Stop() } catch {} }
    }

    Write-Status ""
    Write-Status "Restarting relay (waiting for next app connection)..."
    Write-Status ""
}
