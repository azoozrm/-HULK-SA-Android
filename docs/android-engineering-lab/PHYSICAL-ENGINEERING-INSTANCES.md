# HULK SA Android — Persistent Physical Application Contract

Revised by owner decision 2026-11-09, Asia/Riyadh.
This governs owner-device application identity, authorized in-place updates and data preservation.
It supersedes the former Preview/Benchmark persistent-instance and dual-refresh requirements.
It grants no Git, signing, version, release, capture or deployment authority.

## 1. One official signed application

The routine physical update/runtime target is the owner's existing final signed official app:

- Application ID: `sa.hulksa.player`.
- Build: release application, non-debuggable, with the installed owner's signing identity.
- Update: same package/same signer, in place, using `adb install -r` only.
- State: login/session, profiles, favorites, Continue Watching/history, preferences, downloads
  and all other existing persisted data must survive.

Do not install or refresh Preview, Benchmark, debug/dev, companion test/instrumentation or any
other package on owner devices as part of routine implementation/qualification. Another variant
or application ID is not a substitute for the official signed app. Do not install a second copy
to avoid signing, version, data or validation constraints. Leave existing secondary packages
untouched; this policy does not authorize uninstalling/clearing them.

Do not require a Benchmark/profileable variant for ordinary runtime checks. Measure only what
the authorized signed app and approved methods support, recording limits. Historical variant
infrastructure/examples do not authorize installation on owner devices.

## 2. Approved persistent surfaces

- TV: Xiaomi Mi Box 4 / `MIBOX4`.
- Phone: Samsung Galaxy A06 / `SM-A065F` / device `a06`.

Use only surfaces required and authorized by the active task, not every device on every round.
Verify device identity and the official installed package live. OS, transport address/port,
package version, signer and APK hashes are changing facts, not fixed historical values.
Do not copy old qualification versions into a new install decision.

If a required surface is unreachable or the expected official package is absent, report that
refresh/verification BLOCKED or NOT RUN with its reason. Do not substitute a device, install
a new package, reset authentication/pairing or manufacture clean state for convenience.

## 3. Authorized update procedure

Perform this only within current device-refresh authority, with an appropriate signed artifact
from the existing owner-authorized signing/artifact route:

1. Record the installed official package/version/signing certificate digest, APK identity where
   available, data directory/first-install time and bounded non-secret existing-state evidence.
   Do not extract credentials.
2. Freeze exact source: base/local HEAD, clean/dirty candidate, reviewed diff, complete source/
   material-input manifest, build options and artifact provenance. A proven frozen local
   candidate is valid; a commit/PR is not itself an install prerequisite.
   Bind it to the verified existing task worktree under TASK-EXECUTION-CONTRACT; corrections/new
   chats reuse that worktree and may not silently substitute another branch/candidate.
3. Complete required checks. Select a verified signed release APK tied to that candidate; retain
   actual run/checkout/source provenance when CI supplied it. An unsigned release, debug signer
   or unrelated old APK cannot substitute.
4. Verify application ID `sa.hulksa.player`, non-debuggable release properties, compatible ABI,
   actual version, APK SHA-256 and exact signer continuity with the installed app. The authorized
   version must permit a normal non-downgrade update and respect the product/runtime version
   contract. No silent version changes or stale-update-overlay workarounds.
5. Use `adb install -r` only; retain the command/result. No uninstall, clear, downgrade/test-only
   acceptance flags or package/signer change to force success.
6. Verify installed APK bytes equal the selected signed artifact; recheck package/version/signer,
   preserved data directory/first-install time where available and expected update time. Launch
   once, check immediate crash/stale-update behavior and verify existing owner state survives.
7. Perform bounded task-specific runtime checks with source/APK/device/time binding. Owner visual/
   behavior acceptance remains PENDING until an actual decision for that exact candidate.

On build/validation/identity/signature/version failure, retain the last known-good installed copy.
If no suitable signed artifact or authorized signing route is available, report required physical
refresh BLOCKED and continue independent authorized source/check/evidence work. Do not initiate
CI/signing/commit/push/PR/release to bypass a LOCAL_ROUND/source-only boundary. Existing session
authority persists; do not ask again for already-authorized steps.

## 4. Data, credentials and device settings

Never uninstall, `pm clear`, wipe storage/SharedPreferences/databases/downloads, reset login/profile
state, downgrade, change signer or extract/print/persist credentials. Do not run instrumentation/
orchestrator that can clear target data or install a test companion under routine owner-device
authority. Clean-state testing requires separate owner authority on a separate approved surface;
it never makes the owner's official application disposable.

Device network/display/font changes or unlocking need applicable authority. Captures, recordings,
frame extraction and image comparisons remain OFF without current-task authorization. Missing
unauthorized captures do not block routine technical work; report automated visual validation as
`NOT RUN — CAPTURE NOT AUTHORIZED`.

## 5. Source-only, review and documentation rounds

Audit/Review/Diagnosis/documentation do not automatically build/sign/install/launch the app.
A source-only task remains source-only. Record unperformed runtime checks honestly and preserve
valid local changes/evidence. This contract changes the permitted physical target, not the task
phase or owner-controlled signing/publication/merge/release boundary.

## 6. Packet and evidence requirements

Tasks that may refresh devices must explicitly direct the executor to read this contract and
specify the official signed app, approved surfaces, in-place update/data preservation and current
action authority. Do not carry forward Preview/Benchmark refresh commands or fixed old versions.

When refresh actually runs, include sanitized before/after package/state records, source/candidate/
input identity, signed artifact provenance/hash/inspection, signer continuity, actual install result,
installed-byte comparison and launch/runtime/preserved-state observations in the single complete
reviewer ZIP required by DEEPSEEK-EVIDENCE-CONTRACT. Report unreachable/not-run scope separately.
JVM/build/source/artifact identity is not hardware or visual PASS.

## 7. Precedence

This owner decision controls routine physical target/refresh over historical Preview/Benchmark,
dual-refresh, qualification-override and disposable-fixture instructions in older docs/prompts/aids.
Preserve historical evidence; do not rewrite old results. Root governance and all unrelated protected
fields, authority and STOP rules remain in force.
