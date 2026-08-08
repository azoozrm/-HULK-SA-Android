# HULK SA Android — Current v1.0 Execution Plan

**Checkpoint date:** 2026-08-08  
**Purpose:** authoritative current execution checkpoint after completion of the adaptive/responsive qualification phase.  
**Repository:** `azoozrm/-HULK-SA-Android`  
**Official branch:** `phase-3-v0.9.3.0-adaptive-foundation`  
**Official checkpoint SHA:** `3ae0f744a2fbef54c769c0f5e43716da3a0b8b30`  
**Application ID:** `sa.hulksa.player`  
**Current version:** `0.9.3.20` / `versionCode 64`

> This checkpoint does not replace the permanent engineering requirements in `HULK-SA-OFFICIAL-PROJECT-PLAN.md`. It records the current truth after the later rescue, Compatibility Lab V2, adaptive qualification, and physical-device work. Older audit documents that still mention `0.9.3.18 / 62` are historical evidence, not the current execution state.

## 1. Closed phase — adaptive and responsive qualification

Status: **COMPLETED / CLOSED**.

The adaptive UI phase is closed unless a new defect is reproduced on a real device or by deterministic test evidence.

Validated scope includes:

- Login layout qualification for phone and television windows.
- Phone portrait and landscape main navigation.
- Tablet adaptive layout coverage in Compatibility Lab V2.
- Foldable coverage in Compatibility Lab V2.
- Android TV / Google TV navigation rail qualification.
- Home portrait Hero spacing correction.
- Compact-TV safe-area correction for Xiaomi-class logical viewports.
- Phone / tablet touch layouts and TV D-pad layouts kept as separate navigation behaviors.

Physical visual validation completed during the qualification phase on:

- Samsung Galaxy phone.
- Sony Android / Google TV.
- TCL Android / Google TV.
- Xiaomi receiver.

Protected rule after closure:

- Do not reopen broad adaptive redesign.
- One newly confirmed problem = one isolated branch and one PR.
- Do not change already validated surfaces while fixing an unrelated defect.

## 2. Current source and build state

Status: **QUALIFIED FOUNDATION**.

Current product source is the direct canonical Gradle project on the official branch.

Current release identity:

- `applicationId = sa.hulksa.player`
- `versionName = 0.9.3.20`
- `versionCode = 64`
- Required ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Forbidden ABI: legacy `x86`
- Release build uses R8 and resource shrinking.

The ordinary build gate covers Kotlin compilation, JVM unit tests, lint, Debug build, Release/R8 build, package/version verification, and ABI verification.

## 3. Signing state

Status: **SIGNING PATH QUALIFIED; FINAL OFFICIAL-HEAD CANDIDATE STILL REQUIRED FOR RC**.

The protected Signed Release Qualification workflow has successfully produced and verified a signed `0.9.3.20 / 64` candidate from the PR #105 product head using the approved protected signing environment.

Before Release Candidate, run the same signing qualification from the exact final official source SHA selected for RC and preserve:

- exact source SHA;
- package/version evidence;
- certificate/signature verification;
- ABI evidence;
- APK/AAB checksums.

Never change the signing key, application ID, certificate identity, or upgrade path merely to make CI pass.

## 4. Next mandatory gate — final exact-head qualification

Status: **NEXT**.

Before opening feature work, qualify the current official branch after the adaptive phase closure:

1. Run the final Compatibility Lab V2 Full Matrix on the exact official head.
2. Require deterministic product gates to pass without weakening thresholds or hiding failures.
3. Inspect artifacts, screenshots, hierarchy evidence, checksums, crash/ANR evidence, and profile completion.
4. Produce the final signed Release candidate from the exact qualified head.

This is a release qualification pass, not a new adaptive-development phase.

## 5. Install and upgrade qualification

Status: **REQUIRED BEFORE RC**.

Required evidence:

- Clean install of the signed Release APK.
- Launch and login smoke after clean install.
- Upgrade over a trusted previously installed stable HULK SA APK without uninstalling or clearing data.
- Same application ID and signer continuity.
- No unintended data loss.
- Successful launch after upgrade.
- R8/minified Release runtime smoke.

Static certificate/package comparison is useful but does not replace a real install-over test.

## 6. Production End-to-End qualification

Status: **REQUIRED BEFORE RC**.

Use protected real-service credentials only. Do not expose credentials, signing materials, or sensitive service data in logs, screenshots, PR text, or artifacts.

Required journeys:

- Real login and session validation.
- Home and catalog loading.
- Live / Movies / Series / Search.
- Movie details and series / episode flow.
- Live channel, movie, and episode playback.
- Audio / subtitle / track behavior where available.
- Favorites and history / continue-watching behavior.
- Download enqueue, progress, pause, resume, retry, completion, removal, and integrity.
- Logout and login again.

## 7. Reliability qualification

Status: **REQUIRED BEFORE RC**.

Qualify behavior under:

- Process death and recreation.
- Device reboot.
- Background restrictions.
- Network interruption and recovery.
- Wi-Fi-only constraints.
- Slow / unavailable service responses.
- Storage-low and full-disk conditions.
- Download retry and recovery.
- Repeated player open/close cycles.
- Long playback / download soak.
- Crash, ANR, memory-growth, and black-screen detection.

Durable Downloads already use WorkManager/foreground execution; the remaining work is runtime qualification, not a redesign without evidence.

## 8. Visual regression and accessibility

Status: **REQUIRED BEFORE RC**.

- Create human-approved visual baselines for the critical screens and device classes.
- Never auto-approve or auto-regenerate baselines on failure.
- Cover Login, Home, Live, Movies, Series, My List, Search, Downloads, Settings, details, player surfaces, navigation states, and focus states.
- Cover important font scales and RTL behavior.
- Perform accessibility checks for touch targets, readable text, focus order, TalkBack where applicable, keyboard/IME behavior, and D-pad visibility.

## 9. Performance and Baseline Profile qualification

Status: **NOT YET IMPLEMENTED AS A COMPLETE RELEASE GATE**.

Before v1.0:

- Add Macrobenchmark coverage for cold/warm startup and critical navigation/scroll/player journeys.
- Establish measured performance budgets from Release-like builds.
- Add or qualify Baseline Profiles for critical paths.
- Record device/build SHA/iterations with the result artifact.
- Do not use emulator heuristic jank warnings as the production performance SLA.

## 10. 64-bit, 16 KB, package and architecture release checks

Status: **64-bit / ABI POLICY PRESENT; 16 KB FINAL GATE STILL REQUIRED**.

Before RC verify on final APK/AAB:

- exact required ABI set;
- no legacy `x86`;
- native library parity;
- 64-bit compliance;
- Android 16 KB page-size compatibility for packaged native libraries;
- APK/AAB integrity and hashes.

## 11. Security, transport and privacy review

Status: **REQUIRED BEFORE RC**.

The current product still supports an HTTP Xtream-compatible production endpoint and cleartext media/API transport where required by the service. Credentials can therefore be present in request URLs for compatible Xtream flows.

Before public v1.0:

- document and verify the production transport policy;
- minimize credential exposure in logs and diagnostics;
- verify no credentials or secrets are included in build artifacts or CI evidence;
- confirm network-security configuration is intentional and as narrow as service compatibility permits;
- perform Privacy / Data Safety / pre-launch review.

Do not silently change the production endpoint or protocol during this review; any transport change requires its own explicit compatibility decision and qualification.

## 12. Monitoring and release operations

Status: **REQUIRED FOR v1.0 ROLLOUT**.

Prepare privacy-respecting production monitoring for at least:

- crashes;
- ANRs;
- device / Android-version context;
- startup health;
- playback failures;
- download failures;
- memory/performance regressions.

Prepare release notes, staged rollout policy, and rollback plan before public release.

## 13. Release Candidate gate

A v1.0 Release Candidate may be declared only when all applicable gates below are green or explicitly accepted with documented rationale:

1. Exact-head canonical build/lint/unit gates.
2. Final Compatibility Lab V2 Full Matrix.
3. Signed Release APK/AAB from the exact qualified head.
4. Certificate/package/version/ABI/16 KB verification.
5. Clean install and upgrade/install-over qualification.
6. Signed R8 runtime smoke.
7. Production E2E.
8. Physical ARM/OEM validation.
9. Reliability / process / reboot / network / storage qualification.
10. Visual regression baselines and accessibility checks.
11. Performance / Macrobenchmark / Baseline Profile evidence.
12. Security, privacy, Data Safety and pre-launch review.
13. No unresolved release-blocking P0/P1 defect.
14. Release notes, staged rollout and rollback plan prepared.

## 14. v1.0 and post-v1.0 rule

Large new features remain frozen until v1.0 stability gates are complete.

Examples deferred until after v1.0 include EPG, cross-device synchronization, profiles, parental controls, voice search, Picture-in-Picture, Cast, alerts, whole-season downloads, and other major feature expansion.

## 15. Immediate execution order

The official execution sequence from this checkpoint is:

`Close adaptive phase → Final exact-head Full Matrix → Final signed candidate → Install/Upgrade → Production E2E → Physical/Reliability → Visual/Accessibility → Performance/Baseline Profiles → 16 KB/Security/Privacy → RC → v1.0`

No public Release, tag, or major feature work is authorized by this checkpoint itself.
