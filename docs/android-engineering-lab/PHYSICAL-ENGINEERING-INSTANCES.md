# HULK SA Android — Owner Device Physical Installation Contract

This file is the repository contract for owner-approved physical installation and qualification on
the owner's daily devices. It applies to Android implementation rounds that update the original app
and to any runtime/diagnostic task that could alter its installed state.

It remains in force until the owner explicitly changes it.

## 1. Authoritative owner package

The single default owner-device package is the original production application:

### A. Original HULK SA application

- Package: `sa.hulksa.player`
- Application ID: `sa.hulksa.player`
- Source identity: versionCode `68`, versionName `1.0.0`
- Build family: production release variant (`:app:assembleRelease`), non-debuggable,
  R8/resource-shrunk.
- Production signer certificate SHA-256:
  `144e0548da502aa8bd060a9a280a98bb9dc6763387bf8b20ea100b5fed652fe0`
  (`CN=HULK SA Base Stable`).
- State: persistent owner authentication, session, profiles and data. These must survive every
  in-place update.

### B. Non-default packages

`sa.hulksa.player.preview`, `sa.hulksa.player.benchmark`, `sa.hulksa.player.dev` and any other
alternate-ID application are not default owner-device targets. Do not install a new Preview,
Benchmark, Debug or alternate-ID application on an owner daily device without a new explicit owner
request.

Pre-existing engineering packages on a surface are not automatically removed. Do not delete a
pre-existing package on another device merely because it is no longer the default target.

## 2. Approved physical surfaces

### TV surface — Xiaomi Mi Box 4

- Model identity: Xiaomi `MIBOX4`.
- Role: primary persistent TV runtime/qualification surface.
- Package: the original `sa.hulksa.player`.
- Owner-authenticated state is persistent engineering state and must be preserved.

### Mobile surface — Samsung Galaxy A06

- Model: Samsung Galaxy A06 / `SM-A065F` / device `a06`.
- Role: persistent mobile/compact-phone qualification surface for runtime, navigation, IME, inset,
  touch and legibility qualification when the active task requires it.
- Package: the original `sa.hulksa.player`.
- State: owner-authenticated persistent state must be preserved.
- The ADB/Tailscale transport address and port are dynamic runtime state and are deliberately not
  part of this repository contract.
- If the Galaxy is temporarily unreachable, report physical refresh/qualification as NOT RUN or
  BLOCKED as appropriate; do not silently substitute another device or destroy persistent state to
  recover reachability.

## 3. In-place update procedure

At the end of every Android implementation round that reaches a valid, testable state, and unless
the active task explicitly forbids physical refresh, update the original package in place from the
exact same frozen task-worktree candidate. An uncommitted LOCAL_ROUND candidate is valid when its
base HEAD, dirty state, complete source/diff hashes and APK provenance are recorded under
TASK-EXECUTION-CONTRACT and DEEPSEEK-EVIDENCE-CONTRACT. Do not force a commit/push/PR before local
installation or describe a dirty build as clean-HEAD.

1. Build the original release variant with the existing configured, authorized production-signing
   workflow:

   `./gradlew -PHULK_QUALIFICATION_VERSION_CODE=68 -PHULK_QUALIFICATION_VERSION_NAME=1.0.0 :app:assembleRelease`

   The production signing properties/keystore are supplied only by the approved protected signing
   environment. Do not create, replace or extract a signing key.

2. Before installation verify:
   - the candidate contains the frozen implementation;
   - exact package `sa.hulksa.player`;
   - exact source versionCode `68` / versionName `1.0.0`;
   - full signer-certificate continuity with the installed original, including the expected
     certificate SHA-256;
   - non-debuggable release-like properties, R8/resource shrinking and APK SHA-256.
3. Install only with `adb install -r`.
4. Launch once and verify:
   - package starts without immediate crash;
   - no required-update block;
   - existing authenticated/session state remains present;
   - the original package is the running application;
   - no additional task-added application remains.

Do not force a version bump to clear an update gate. If the installed/live version or signer is
incompatible, stop and report the exact blocker.

## 4. Data preservation is mandatory

The original `sa.hulksa.player` package is not a disposable fixture.

For the original package, NEVER:

- uninstall;
- run `pm clear`;
- clear storage, SharedPreferences, databases or account/session state;
- run instrumentation/orchestrator configuration that clears the target package data;
- reset authentication to manufacture a clean test state;
- downgrade or change the signer;
- expose, extract, print or persist credentials from stored state.

A task that needs clean state must use an explicitly isolated, separately authorized disposable
package/surface that is not the owner's original package.

Before any instrumentation against the original package, prove that the exact invocation will not
clear target data. If that cannot be proven, do not run that instrumentation path.

## 5. Narrow task-added cleanup exception

The two packages `sa.hulksa.player.preview` and `sa.hulksa.player.benchmark` installed on `MIBOX4`
by `live-arabic-commentary-r01` were task-added copies, not owner data. The owner explicitly
authorized their exact removal in the correction that adopted this policy.

Removal is allowed only when the active task packet proves the exact packages were added by that
task and explicitly authorizes cleanup:

- verify exact package names and installation evidence first;
- remove only those exact package names, never a wildcard or prefix;
- never remove `sa.hulksa.player`;
- never remove a pre-existing engineering package on another device;
- preserve all source, APK and report evidence on the server.

## 6. Diagnosis / audit rounds

Audit, Review and Diagnosis rounds do not automatically rebuild or reinstall the original package.

They may use the existing original package for read-only physical evidence only when the active
packet authorizes physical interaction and the evidence method preserves installed data.

Do not update packages merely because a diagnosis ran.

## 7. Version and signer changes

The current source identity is:

- versionCode `68`
- versionName `1.0.0`
- package `sa.hulksa.player`
- production signer certificate SHA-256
  `144e0548da502aa8bd060a9a280a98bb9dc6763387bf8b20ea100b5fed652fe0`

Do not silently bump versions or fall back to an alternate identity. Do not change the production
signing key or certificate. A version/signer change requires explicit owner authorization and a
matching governance update in the same round.

## 8. Prompt / executor contract

Every future Android IMPLEMENTATION task packet that can reach physical installation must
explicitly carry this contract or direct the executor to read it before device actions.

The executor must report, when an in-place update is performed:

- exact source candidate/base HEAD and clean/dirty state, source/diff hashes and PR HEAD when
  present;
- original APK package/version/hash and install result;
- certificate identity and continuity with the installed original;
- confirmation that `adb install -r` was used;
- confirmation that no uninstall / `pm clear` / data wipe occurred;
- launch result, update-gate result and whether prior authenticated/session state remained
  available;
- any task-added package cleanup with exact package names and before/after inventory.

When a task names the Galaxy mobile surface, also report whether the existing original
`sa.hulksa.player` state was reused/preserved and whether any exact original mobile settings
modified for the task were restored.

No raw credential values belong in prompts, logs, reports, fixtures or PR text.

## 9. Conflict rule

For owner-device package identity, in-place installation and refresh behavior, this contract is the
specific Android Engineering Lab authority and supersedes older Preview/Benchmark dual-refresh
wording or historical chat instructions. Repository Git/write protections, data protection,
signing/release authority and all other root `AGENTS.md` rules remain unchanged. This contract
grants no merge, signing, tag, public release or deployment authority.
