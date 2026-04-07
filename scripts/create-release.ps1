<#
.SYNOPSIS
    Creates a GitHub Release with the AAB and bridge binary attached.

.DESCRIPTION
    - Reads version from secrets/version.properties
    - Creates a GitHub release with tag v<versionName>
    - Uploads:
      - The release AAB from app/build/outputs/bundle/release/
      - The bridge binary from build-bridge-arm64/ (renamed to openautolink-headless-arm64)
    - Requires: gh CLI authenticated

.EXAMPLE
    # After building both:
    .\scripts\bundle-release.ps1
    bash scripts/build-bridge-wsl.sh
    .\scripts\create-release.ps1
#>
param(
    [string]$Notes = "",
    [switch]$Draft,
    [switch]$NoBridge   # Skip attaching bridge binary
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

# Read version
$versionFile = Join-Path $repoRoot 'secrets\version.properties'
if (-not (Test-Path $versionFile)) {
    throw "Version file not found: $versionFile. Run bundle-release.ps1 first."
}

$version = @{}
Get-Content $versionFile | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') {
        $version[$Matches[1]] = $Matches[2]
    }
}

$tag = "v$($version['versionName'])"
Write-Host "[release] Creating release: $tag"

# Find AAB
$aabDir = Join-Path $repoRoot 'app\build\outputs\bundle\release'
$aab = Get-ChildItem -Path $aabDir -Filter '*.aab' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $aab) {
    Write-Warning "No AAB found in $aabDir — run bundle-release.ps1 first"
}

# Find bridge binary
$bridgeBinary = Join-Path $repoRoot 'build-bridge-arm64\openautolink-headless-stripped'
$bridgeAsset = Join-Path $repoRoot 'build-bridge-arm64\openautolink-headless-arm64'
$hasBridge = $false
if (-not $NoBridge -and (Test-Path $bridgeBinary)) {
    # Rename for the release asset name the app expects
    Copy-Item -Path $bridgeBinary -Destination $bridgeAsset -Force
    $hasBridge = $true
    Write-Host "[release] Bridge binary: $bridgeAsset"
} elseif (-not $NoBridge) {
    Write-Warning "No bridge binary found at $bridgeBinary — run build-bridge-wsl.sh first"
}

# Build asset list
$assets = @()
if ($aab) { $assets += $aab.FullName }
if ($hasBridge) { $assets += $bridgeAsset }

if ($assets.Count -eq 0) {
    throw "No assets to upload. Build the AAB and/or bridge first."
}

# Create release
$ghArgs = @('release', 'create', $tag, '--title', $tag)
if ($Draft) { $ghArgs += '--draft' }
if ($Notes) {
    $ghArgs += '--notes'
    $ghArgs += $Notes
} else {
    $ghArgs += '--generate-notes'
}
$ghArgs += $assets

Write-Host "[release] gh $($ghArgs -join ' ')"
& gh @ghArgs

Write-Host ""
Write-Host "[release] Release $tag created with $($assets.Count) asset(s)"
