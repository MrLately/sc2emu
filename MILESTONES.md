# DiscoPilot Milestones

## v0.4 Safety/Control (No Flight Needed)
- [x] v0.4.1 Command gating hardening: block risky commands during reconnect/not-ready states.
- [x] v0.4.2 Mission preflight validator: structural checks, altitude/segment sanity, geofence-aware checks.
- [x] v0.4.3 Manual override on abort/failure: mission stop, sticks reset, controls handback.
- [x] v0.4.4 Reconnect state model in UI (`reconnecting`, `discovery`, `ready`) with command lockouts.
- [x] v0.4.5 Bench-test checklist/log panel for prop-off validation.

## v0.5 Planner Completion (No Flight Needed)
- [x] v0.5.1 Linear landing cone marker on final waypoint.
- [x] v0.5.2 Pattern settings UI for Grid/Corridor/Orbit.
- [x] v0.5.3 Generated-route preview controls (anchors vs generated path clarity).
- [x] v0.5.4 Save/load full pattern parameters.
- [x] v0.5.5 Dry-run mission preview mode.

## v0.6 Telemetry/HUD Parity (No Flight Needed)
- [x] v0.6.1 Final HUD fields and stale-data indicators.
- [x] v0.6.2 Mission status badges parity (`PLAY/PAUSE/STOP`, `LIN/SPR`, waypoint execution).
- [x] v0.6.3 Safety alert banners (link, battery, telemetry, mission errors).
- [x] v0.6.4 Log export bundle for troubleshooting.

## v0.7 Release/Collab Readiness
- [x] v0.7.1 GitHub-ready cleanup and `.gitignore`.
- [x] v0.7.2 Build/install docs and helper scripts.
- [x] v0.7.3 Regression checklist for release cuts.

## v0.8 Safety Workflow Enhancements (Bench First)
- [x] v0.8.1 Bench Lock guardrail (block takeoff while grounded).
- [x] v0.8.2 Command event log stream for troubleshooting and confidence.
- [x] v0.8.3 Include bench lock + command log in diagnostics export bundle.

## v0.9 Public Launch Readiness
- [x] v0.9.1 Add open-source governance docs (`LICENSE`, `CONTRIBUTING`, `SECURITY`, `CODE_OF_CONDUCT`).
- [x] v0.9.2 Add GitHub issue templates and PR template.
- [x] v0.9.3 Add CI workflow for unit tests, lint, and debug build.
- [x] v0.9.4 Remove personal defaults from planner map fallback.
- [ ] v0.9.5 First public tag/release notes after outdoor flight validation.
