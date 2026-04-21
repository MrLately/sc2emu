# DiscoPilot Release Checklist

Use this checklist before tagging or sharing an APK build.

## 1) Repo Hygiene

- [ ] `.gitignore` excludes build artifacts, local tooling, and local SDK config.
- [ ] No generated files are staged (`app/build`, `.gradle`, `.tooling`, logs).
- [ ] `README.md` reflects current feature set and commands.
- [ ] `MILESTONES.md` statuses are up to date.
- [ ] No personal paths/IPs are present in committed docs or defaults.

## 2) Build + Install

- [ ] `.\scripts\build-debug.ps1` succeeds.
- [ ] `.\scripts\install-debug.ps1 -Device <target>` succeeds.
- [ ] App launches on target device without immediate crash.
- [ ] `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` succeeds.

## 3) Bench Regression (Prop Off)

- [ ] Engine start and discovery transition to ready.
- [ ] Reconnect button recovers session from forced discovery failure.
- [ ] Video renders (no immediate black-screen crash).
- [ ] HUD fields update (`SPD`, `ALT`, `DST`, `ZT`, `PLN`, `PHN`).
- [ ] Safety banner appears on forced stale/discovery conditions.
- [ ] Bench panel opens and logs state transitions.
- [ ] Export Logs writes a JSON file and shows saved location.

## 4) Mission Regression (No Flight Required)

- [ ] Planner opens, map centers on pilot location when available.
- [ ] Add/undo/clear/save/load function correctly.
- [ ] Preview mode cycles: generated / anchors / both.
- [ ] Pattern controls affect route generation:
  - [ ] Grid width/lane/start-side
  - [ ] Corridor width/pass mode
  - [ ] Orbit radius/turns/direction
- [ ] Long-press waypoint editor works.
- [ ] Final waypoint shows linear landing cone when selected.
- [ ] Dry-run start/pause/resume/reset behaves correctly.
- [ ] Mission control bar badges show (`PLAY/PAUSE/STOP`, `LIN/SPR`, `WP`).

## 5) Flight-Safety Guardrails

- [ ] Takeoff requires confirmation dialog.
- [ ] Risky commands are blocked when reconnecting/offline/stale.
- [ ] Abort mission returns control to manual override state.
- [ ] Geofence-sensitive preflight checks return clear messages.

## 6) Release Artifacts

- [ ] APK path captured: `app\build\outputs\apk\debug\app-debug.apk`.
- [ ] Release notes include:
  - major changes
  - known limitations
  - test status (bench/flight)

## 7) Public Repo Readiness

- [ ] `LICENSE` exists and matches intended project licensing.
- [ ] `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md` are present.
- [ ] GitHub issue templates and PR template are present.
- [ ] GitHub CI workflow runs tests/lint/build on PRs.
