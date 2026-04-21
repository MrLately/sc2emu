# Contributing to DiscoPilot

Thanks for contributing.

## Ground Rules

- Treat this as flight-control software: safety regressions are priority issues.
- Keep changes focused and explain operational impact.
- Do not commit local secrets, device IPs, or personal logs.

## Local Setup

```powershell
cd <repo-root>
.\dev-shell.ps1
```

## Before Opening a PR

1. Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

2. Run prop-off bench checks using `RELEASE_CHECKLIST.md`.
3. Include test notes in your PR description:
   - What you changed
   - What you tested
   - What you did not test

## Safety-Critical Change Expectations

For changes affecting takeoff, land, RTH, mission start/pause/abort, reconnect, or command gating:

- Explain old behavior and new behavior.
- Describe failure modes considered.
- Provide bench evidence (logs/screenshots where possible).

## Code Style

- Kotlin/Android code should stay readable and explicit.
- Prefer small cohesive functions over large mixed-responsibility blocks.
- Keep user-facing messages clear and operationally meaningful.

## PR Scope

- Prefer one topic per PR.
- Avoid unrelated formatting-only edits in functional PRs.

## Reporting Security Issues

Use `SECURITY.md` and do not open public issues for exploitable details.
