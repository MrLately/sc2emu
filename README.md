# DiscoPilot

DiscoPilot is an Android app that controls a Parrot Disco directly over network links (Wi-Fi or ZeroTier) without relying on the stock SC2 app flow.

## Project Status

- Bench-tested workflows are implemented and actively used.
- Flight testing should be treated as in-progress until contributors report validated outdoor results for their exact setup.

## Safety First

- This software can control a real aircraft.
- Always perform prop-off bench checks before live tests.
- Keep a controlled test area and visual line of sight.
- Verify link health, telemetry freshness, and mission preflight before takeoff.
- Use at your own risk.

## Features

- Direct control engine:
  - discovery handshake
  - uplink control commands
  - telemetry parsing
- Live H264 preview
- Flight HUD and controls:
  - dual virtual sticks
  - takeoff/land
  - RTH
  - attitude indicator
- Mission planning:
  - points, grid, corridor, orbit
  - waypoint long-press editor
  - linear/circular landing options
  - preview modes
  - dry-run simulation
  - mission save/load
- Safety and diagnostics:
  - reconnect-aware command gating
  - bench lock guardrail
  - mission preflight validation
  - safety banners
  - bench/event logs
  - JSON diagnostics export

## Local Dev Setup (Windows PowerShell)

```powershell
cd <repo-root>
.\dev-shell.ps1
```

## Build and Install

```powershell
# Build debug APK
.\scripts\build-debug.ps1

# Clean + build debug APK
.\scripts\build-debug.ps1 -Clean

# Install APK to one connected adb device
.\scripts\install-debug.ps1

# Install APK to a specific device
.\scripts\install-debug.ps1 -Device <device_ip:port>

# Build + install in one command
.\scripts\build-install-debug.ps1 -Device <device_ip:port>
```

APK output:

`app\build\outputs\apk\debug\app-debug.apk`

## Manual Build

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb -s <device_ip:port> install -r app\build\outputs\apk\debug\app-debug.apk
```

## Contribution

- Start with `CONTRIBUTING.md`.
- Run unit tests + lint + debug build before opening a PR.
- Keep safety behavior changes explicit in PR descriptions.

## Documents

- Milestones: `MILESTONES.md`
- Release checks: `RELEASE_CHECKLIST.md`
- Security policy: `SECURITY.md`
- Code of conduct: `CODE_OF_CONDUCT.md`

## Legal

- This project is community-maintained and is not affiliated with or endorsed by Parrot.
- License: MIT (see `LICENSE`).
