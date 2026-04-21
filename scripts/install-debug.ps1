param(
    [string]$Device,
    [switch]$BuildFirst
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"

function Get-ConnectedDevices {
    $lines = & adb devices
    $devices = @()
    foreach ($line in $lines) {
        if ($line -match "^\s*([^\s]+)\s+device\s*$") {
            $devices += $Matches[1]
        }
    }
    return $devices
}

Push-Location $repoRoot
try {
    & "$repoRoot\dev-shell.ps1" -Quiet

    if ($BuildFirst -or -not (Test-Path -LiteralPath $apkPath)) {
        & "$repoRoot\scripts\build-debug.ps1"
    }

    $devices = Get-ConnectedDevices
    if ($devices.Count -eq 0) {
        throw "No adb devices connected. Run: adb devices"
    }

    if ([string]::IsNullOrWhiteSpace($Device)) {
        if ($devices.Count -gt 1) {
            throw "Multiple devices connected. Pass -Device <ip:port or serial>."
        }
        $Device = $devices[0]
    }

    Write-Host "Installing to: $Device"
    & adb -s $Device install -r $apkPath
} finally {
    Pop-Location
}
