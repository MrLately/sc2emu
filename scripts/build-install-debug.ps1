param(
    [string]$Device,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & "$repoRoot\dev-shell.ps1" -Quiet
    if ($Clean) {
        & "$repoRoot\scripts\build-debug.ps1" -Clean
    } else {
        & "$repoRoot\scripts\build-debug.ps1"
    }
    & "$repoRoot\scripts\install-debug.ps1" -Device $Device
} finally {
    Pop-Location
}
