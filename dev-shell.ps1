param(
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolingRoot = Join-Path $repoRoot ".tooling"

function Add-PathOnce {
    param([string]$PathToAdd)
    if (-not (Test-Path -LiteralPath $PathToAdd)) { return }
    $segments = $env:Path -split ";"
    if ($segments -notcontains $PathToAdd) {
        $env:Path = "$PathToAdd;$env:Path"
    }
}

$jdkHome = Join-Path $toolingRoot "jdk\jdk-17.0.18+8"
$gradleHome = Join-Path $toolingRoot "gradle\gradle-8.7"
$gradleBin = Join-Path $gradleHome "bin"
$sdkRoot = Join-Path $toolingRoot "android-sdk"
$platformTools = Join-Path $sdkRoot "platform-tools"

$missing = @()
foreach ($required in @($jdkHome, $gradleBin, $sdkRoot, $platformTools)) {
    if (-not (Test-Path -LiteralPath $required)) {
        $missing += $required
    }
}

if ($missing.Count -gt 0) {
    Write-Error ("Missing required tooling path(s):`n- " + ($missing -join "`n- "))
}

$env:JAVA_HOME = $jdkHome
$env:GRADLE_HOME = $gradleHome
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

Add-PathOnce (Join-Path $jdkHome "bin")
Add-PathOnce $gradleBin
Add-PathOnce $platformTools

if (-not $Quiet) {
    Write-Host "Environment configured for this PowerShell session:" -ForegroundColor Green
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
    Write-Host "GRADLE_HOME=$env:GRADLE_HOME"
    Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
    Write-Host ""
    Write-Host "Quick commands:"
    Write-Host "  .\gradlew.bat assembleDebug"
    Write-Host "  adb devices"
    Write-Host "  adb -s <device> install -r app\build\outputs\apk\debug\app-debug.apk"
}
