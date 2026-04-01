param(
    [int]$Width = 1920,
    [int]$Height = 1080,
    [int]$Fps = 30,
    [switch]$NoAudio,
    [string]$VideoFile
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$MockBridge = Join-Path $ScriptDir "mock_bridge.py"

# Try WSL Python first, fall back to Windows Python
$pyArgs = @($MockBridge, "--width", $Width, "--height", $Height, "--fps", $Fps)
if ($NoAudio) { $pyArgs += "--no-audio" }
if ($VideoFile) { $pyArgs += @("--video-file", $VideoFile) }

Write-Host "Starting mock bridge..." -ForegroundColor Cyan

# Check if Python3 exists on Windows
$winPython = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $winPython) { $winPython = Get-Command python -ErrorAction SilentlyContinue }

if ($winPython) {
    # Check ffmpeg
    $ff = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if (-not $ff -and -not $VideoFile) {
        Write-Host "ffmpeg not found on Windows. Trying WSL..." -ForegroundColor Yellow
    } else {
        & $winPython.Source @pyArgs
        exit $LASTEXITCODE
    }
}

# Fall back to WSL
Write-Host "Using WSL Python..." -ForegroundColor Yellow
$wslPath = ($MockBridge -replace '\\','/') -replace '^(\w):','/mnt/$1'
$wslPath = $wslPath.ToLower() -replace '/mnt/(\w)','/mnt/$1'
$wslArgs = $pyArgs | ForEach-Object {
    if ($_ -eq $MockBridge) { $wslPath } else { $_ }
}
wsl -d Ubuntu-24.04 -- python3 @wslArgs
