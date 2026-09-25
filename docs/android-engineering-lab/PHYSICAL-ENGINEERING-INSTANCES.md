# HULK SA Android — Persistent Physical Engineering Instances

This file is a repository contract for owner-approved physical development/testing instances.
It applies to Android implementation rounds that refresh physical engineering copies and to any
runtime/diagnostic task that could alter their installed state.

It remains in force until the owner explicitly changes it.

## 1. Authoritative persistent instances

Two installed packages are persistent owner engineering state and must be preserved across rounds:

### A. Benchmark engineering instance

- Package: `sa.hulksa.player.benchmark`
- Role: primary semi-release physical development/testing instance.
- Build family: release-derived benchmark variant, non-debuggable, R8/resource-shrunk, profileable.
- Current owner-approved qualification identity: versionCode `67`, qualification versionName base
  `0.9.3.23` (the Gradle benchmark suffix remains part of the installed variant name where applicable).
- State: persistent. Owner authentication/session/data may already exist and must survive future rounds.

This is the default physical instance for authenticated engineering/runtime journeys unless a task
explicitly requires a separate isolated clean fixture.

`sa.hulksa.player.dev` is NOT the default persistent physical engineering instance.

### B. Preview engineering instance

- Package: `sa.hulksa.player.preview`
- Role: owner's persistent daily Preview copy of the latest successful in-progress implementation.
- Build family: release-derived Preview variant, non-debuggable, R8/resource-shrunk.
- Current owner-approved qualification identity: versionCode `67`, qualification versionName base
  `0.9.3.23`.
- State: persistent. Existing owner login/session/data must be preserved.

### C. Persistent approved physical surfaces

The package contract above applies independently on every owner-approved persistent physical
qualification surface.

#### TV surface — Xiaomi Mi Box 4

- Model identity: Xiaomi `MIBOX4`.
- Role: primary persistent TV runtime/qualification surface.
- Persistent package state: the installed `sa.hulksa.player.benchmark` authenticated owner state is
  engineering state and must be preserved across rounds.
- Do not substitute another package or manufacture clean state on this surface for convenience.

#### Mobile surface — Samsung Galaxy A06

- Model: Samsung Galaxy A06 / `SM-A065F` / device `a06`.
- Role: persistent mobile/compact-phone qualification surface for future mobile runtime,
  navigation, IME, inset, touch and legibility qualification when the active task requires it.
- Persistent package: `sa.hulksa.player.benchmark`.
- Current installed qualification identity: versionCode `67`, versionName
  `0.9.3.23.benchmark`.
- State: owner-authenticated persistent engineering state established during Gate 4E1 and must be
  preserved for future mobile testing.
- The ADB/Tailscale transport address and port are dynamic runtime state and are deliberately not
  part of this repository contract.
- Future mobile qualification should reuse this Benchmark instance when appropriate rather than
  replacing it with `sa.hulksa.player.dev` or another disposable package.
- If a later implementation needs to refresh this Galaxy Benchmark for mobile validation, first
  verify exact package/version/signer continuity and use only same-package/same-signer
  state-preserving `adb install -r`. Never uninstall, clear, downgrade or reset authentication.
- If the Galaxy is temporarily unreachable, report that physical refresh/qualification as NOT RUN
  or BLOCKED as appropriate; do not silently substitute a different phone and do not destroy the
  persistent state to recover reachability.

## 2. Mandatory dual refresh after implementation

At the end of every Android IMPLEMENTATION round that reaches a valid, testable state, and unless
the task explicitly forbids physical-device refresh, refresh BOTH persistent packages from the exact
same task worktree / PR HEAD:

1. Build Preview:

   `./gradlew -PHULK_QUALIFICATION_VERSION_CODE=67 -PHULK_QUALIFICATION_VERSION_NAME=0.9.3.23 :app:assemblePreview`

2. Build Benchmark:

   `./gradlew -PHULK_QUALIFICATION_VERSION_CODE=67 -PHULK_QUALIFICATION_VERSION_NAME=0.9.3.23 :app:assembleBenchmark`

3. Verify each APK before installation:
   - exact expected package;
   - exact qualification versionCode/versionName contract;
   - non-debuggable where required by the variant;
   - R8/resource shrinking where required;
   - signer continuity with the already-installed package;
   - APK SHA-256 recorded in task evidence when physical refresh is part of completion evidence.

4. Install each package only with `adb install -r`.

5. Never uninstall, `pm clear`, wipe app data, downgrade, change signer, or replace a persistent
   package with a different application ID merely to make a test pass.

6. Launch each refreshed package once and verify:
   - package starts without immediate crash;
   - no stale Optional Update overlay appears because of an old qualification version;
   - existing authenticated/session state remains present where previously established.

7. If either new build or required validation fails, do not destroy or force-replace the last
   known-good installed copy. Stop that refresh path and report the exact failure.

The two refreshes are one standing completion obligation, not two independent product changes.
The persistent-surface section does not make every device mandatory for every task: use the physical
surface named by the active task, preserve all other persistent surfaces, and explicitly report any
required-but-unreachable surface rather than silently substituting or clearing it.

## 3. Data preservation is mandatory

The persistent Benchmark and Preview packages are not disposable fixtures.

For both packages, NEVER:

- uninstall;
- run `pm clear`;
- clear storage, SharedPreferences, databases or account/session state;
- run instrumentation/orchestrator configuration that clears the target package data;
- reset authentication to manufacture a clean test state;
- expose, extract, print or persist credentials from stored state.

A task that needs clean state must use an explicitly isolated disposable package/surface that is not
one of these persistent packages.

Before any instrumentation against either persistent package, prove that the exact invocation will
not clear target data. If that cannot be proven, do not run that instrumentation path.

## 4. Diagnosis / audit rounds

Audit, Review and Diagnosis rounds do not automatically rebuild or reinstall either package.

They may use an existing persistent package for read-only physical evidence only when the active
packet authorizes physical interaction and the evidence method preserves installed data.

Do not refresh packages merely because a diagnosis ran.

## 5. Version changes

The current fixed qualification identity is:

- versionCode `67`
- qualification versionName base `0.9.3.23`

Do not silently fall back to the Gradle source baseline version.
Do not change this physical qualification identity unless the owner explicitly authorizes a new one.
When the owner authorizes a new qualification identity, update this contract in the same governance
round so future chats/tasks inherit the new value.

## 6. Prompt / executor contract

Every future DeepSeek/OpenCode Android IMPLEMENTATION task packet that can reach physical refresh
must explicitly carry this contract or direct the executor to read it before device actions.

The executor must report, when refresh is performed:

- exact source/PR HEAD used;
- Preview APK package/version/hash and install result;
- Benchmark APK package/version/hash and install result;
- signer-continuity verification;
- confirmation that `adb install -r` was used;
- confirmation that no uninstall / `pm clear` / data wipe occurred;
- launch-smoke result for both packages;
- whether prior authenticated/session state remained available.

When a task names the persistent Galaxy mobile surface, also report whether the existing
`sa.hulksa.player.benchmark` state was reused/preserved and whether the exact original mobile
settings modified for the task (if any) were restored.

No raw credential values belong in prompts, logs, reports, fixtures or PR text.

## 7. Conflict rule

For physical engineering-instance ownership and refresh behavior, this contract is the specific
Android Engineering Lab authority and supersedes older Preview-only wording or historical chat
instructions that conflict with the dual-refresh requirement or persistent-surface ownership.

Repository Git/write protections, protected product contracts, signing/release authority and all
other root `AGENTS.md` rules remain unchanged.
