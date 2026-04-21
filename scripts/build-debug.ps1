param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & "$repoRoot\dev-shell.ps1" -Quiet
    if ($Clean) {
        & "$repoRoot\gradlew.bat" clean assembleDebug
    } else {
        & "$repoRoot\gradlew.bat" assembleDebug
    }

    $apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path -LiteralPath $apkPath) {
        Write-Host "APK ready: $apkPath"
    } else {
        throw "Build finished but APK not found at $apkPath"
    }
} finally {
    Pop-Location
}
