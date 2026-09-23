# Android Engineering Lab Operations

This directory contains non-secret, repository-tracked automation for the HULK SA Android
engineering lab.

It does not contain credentials, IP addresses, caches, active task state or product behavior.

## Qualified helpers

- `hulk-task-new`: creates one isolated task worktree after a clean canonical clone and exact
  remote official-HEAD gate.
- `hulk-task-status`: reports canonical and optional task worktree state.
- `hulk-task-close`: removes only a clean registered task worktree and deletes its local branch
  only when it has no unique commit and no remote branch.
- `lab-health.sh`: read-only health summary for toolchain, disk, memory, GitHub and KVM mode.
- `git-hooks/pre-commit`: blocks commits from the canonical clone.
- `git-hooks/pre-push`: blocks direct pushes to the official branch.

## Diagnostics (Gate 3A.5)

Bounded, read-only helpers for durable evidence. They never write inside the repository,
never use production signing, and refuse to operate on the protected packages
`sa.hulksa.player` and `sa.hulksa.player.dev`.

- `apk-inspect.sh <apk>`: deterministic identity evidence — package, versionCode/versionName,
  signer SHA-256, debuggable, profileable declaration, ABI set, packaged baseline profile
  state, and `apk_kind` (`target-app` vs `test-apk`).
- `runtime-capture.sh <test-package> <outdir> [--launch <component>]`: bounded runtime packet
  for an explicit TEST package only — package identity, foreground/focused window, compilation
  state, bounded dumpsys, bounded package-scoped logcat (credential lines filtered), UI
  hierarchy and screenshot, plus device identity.
- `focus-dpad-probe.sh <test-package> <outdir> [--component <c>] [--keys DOWN,UP,...]`:
  repeatable focused-node evidence before and after a requested key sequence. Reports
  `destination_proven` only when the readable focus label changes.
- `perfetto-analyze.sh <benchmarkData.json> [--traces <dir>] [--target-compilation <file>]`:
  bounded benchmark JSON/trace analysis. Keeps the self-instrumenting test APK context
  compilation mode separate from the target-app compilation state. Reports
  `trace_processor=BLOCKED` rather than guessing when no qualified trace processor exists.
- `lib-lab.sh`: shared SDK/adb resolution, redaction and package guards.
- `uiautomator_focus.py`, `benchmark_analysis.py`: importable, unit-tested parsers.
- `tests/run-tests.sh`: deterministic tests for the diagnostics tools (no device/SDK needed).

Installing or replacing machine files under `/usr/local/bin` or `/etc/hulk-android` is a LAB
MAINTENANCE mutation and requires task authority. Do not execute repository scripts with sudo merely
because they exist here.
