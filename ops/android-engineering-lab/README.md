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
never use production signing, and refuse generic diagnostic operation on all four protected
identities: `sa.hulksa.player`, `sa.hulksa.player.dev`, `sa.hulksa.player.preview` and
`sa.hulksa.player.benchmark`. The two owner-persistent packages may only be selected
explicitly by a dedicated state-preserving refresh/preflight path under
`docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`, never by a generic
diagnostic probe.

Explicit physical-device selection is mandatory when device identity matters: `HULK_ADB_SERIAL`
is honored only while that serial is present in adb `device` state, and zero or multiple
connected devices fail closed instead of silently choosing the first device.

Privacy contract: the diagnostics never persist credentials or sensitive UI/account data.
Editable-field text values are never emitted, and raw UI XML / screenshots are not captured
by default.

- `apk-inspect.sh <apk>`: deterministic identity evidence — package, versionCode/versionName,
  signer SHA-256, debuggable, profileable declaration, ABI set, packaged baseline profile
  state, and `apk_kind` (`target-app` vs `test-apk`).
- `physical-preflight.sh --package <pkg> --state-preserving [--apk <apk>] [--expect-*]`:
  read-only identity preflight before a physical action. Compares selected serial/device
  identity against an owner-approved surface, target and candidate APK package/version/signer,
  installed presence/version/signer (pulled base APK inspected with `apk-inspect.sh`), supplied
  source/worktree commit, and any `--temporary-setting NAME=RESTORE` contract. Fails closed on
  any mismatch; refuses production; never installs, clears, changes settings or launches.
  For the persistent `sa.hulksa.player.preview` / `sa.hulksa.player.benchmark` roles the full
  owner-approved identity proof set is mandatory: expected surface, expected package, expected
  candidate qualification versionCode/versionName, expected signer, candidate APK with
  non-empty package/version/signer/SHA-256, installed package identity/signer continuity,
  source commit, expected source commit and the state-preserving declaration.
  `physical_preflight.py` holds the pure judgment and fixture tests.
- `runtime-capture.sh <test-package> <outdir> [--launch <component>] [--capture-ui]`: bounded
  runtime packet for an explicit TEST package only — package identity, foreground/focused
  window, compilation state, bounded dumpsys, bounded package-scoped logcat (credential lines
  filtered), plus device identity. UI hierarchy and screenshot are captured **only** with the
  explicit `--capture-ui` opt-in (use solely when no login/credential UI is present).
- `focus-dpad-probe.sh <test-package> <outdir> [--component <c>] [--keys DOWN,UP,...]`:
  repeatable focused-node evidence before and after a requested key sequence. Raw UI XML is
  pulled to a temporary file, parsed for non-sensitive focus identity only, then deleted;
  editable-field text is never retained. Reports `destination_proven` only when the readable
  focus label changes.
- `perfetto-analyze.sh <benchmarkData.json> [--traces <dir>] [--target-compilation <file>]`:
  bounded benchmark JSON/trace analysis. Keeps the self-instrumenting test APK context
  compilation mode separate from the target-app compilation state. Reports
  `trace_processor=BLOCKED` rather than guessing when no qualified trace processor exists.
- `lib-lab.sh`: shared SDK/adb resolution with explicit device selection, redaction, package
  classification and generic-diagnostic guards.
- `uiautomator_focus.py`, `benchmark_analysis.py`: importable, unit-tested parsers.
- `tests/run-tests.sh`: deterministic tests for the diagnostics tools (no device/SDK needed).

Installing or replacing machine files under `/usr/local/bin` or `/etc/hulk-android` is a LAB
MAINTENANCE mutation and requires task authority. Do not execute repository scripts with sudo merely
because they exist here.
