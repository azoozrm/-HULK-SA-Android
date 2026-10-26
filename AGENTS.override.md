# HULK SA Android — Owner Device Installation Override

This override supplements root `AGENTS.md` and makes the owner's current physical-installation
policy durable across chats, task packets, models and executors.

It supersedes only the former mandatory Preview/Benchmark dual-refresh and persistent-instance
wording. All other repository governance, write-safety, protected product, signing, merge, release
and stop-condition rules remain unchanged.

Read the full specific contract before any physical build/install/instrumentation action:

`docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`

## Default owner-device package

The single default owner-device target is the original production package:

- Package: `sa.hulksa.player`
- Role: the owner's real HULK SA application and daily viewing/engineering copy.
- Source identity: applicationId `sa.hulksa.player`, versionCode `68`, versionName `1.0.0`.
- Production signer certificate SHA-256:
  `144e0548da502aa8bd060a9a280a98bb9dc6763387bf8b20ea100b5fed652fe0`
  (`CN=HULK SA Base Stable`).
- State: persistent owner login, session, profiles and data must survive every update.

`sa.hulksa.player.preview`, `sa.hulksa.player.benchmark`, `sa.hulksa.player.dev` and any other
alternate-ID application are **not** default owner-device targets. Do not install a new Preview,
Benchmark, Debug or alternate-ID application on an owner daily device without a new explicit owner
request.

## Approved physical surfaces

1. Xiaomi Mi Box 4 / `MIBOX4`
   - primary persistent TV runtime/qualification surface;
   - the original `sa.hulksa.player` owner state is the state to preserve.

2. Samsung Galaxy A06 / `SM-A065F` / device `a06`
   - persistent mobile/compact-phone qualification surface;
   - the original `sa.hulksa.player` owner state is the state to preserve;
   - transport address/ADB port is dynamic runtime state and is not part of this contract.

Pre-existing engineering packages on a surface are not automatically removed. Do not delete a
pre-existing package on another device merely because it is no longer the default target.

## In-place original update

At the end of every Android implementation round that reaches a valid, testable state, unless the
active task explicitly forbids physical refresh:

1. Build the original release variant from the exact frozen task worktree through the existing
   configured, authorized production-signing workflow:
   `:app:assembleRelease` with applicationId `sa.hulksa.player`, the source version identity and
   the installed production signing identity.
2. Before installation verify:
   - the candidate contains the frozen implementation;
   - exact package `sa.hulksa.player` and source versionCode/versionName;
   - full signing-certificate continuity with the installed original;
   - non-debuggable release-like properties, R8/resource shrinking and APK SHA-256.
3. Install only with `adb install -r`.
4. Launch once and verify:
   - no required-update block;
   - existing login/session remains present;
   - the original package is the running application;
   - no additional task-added application remains.

An uncommitted LOCAL_ROUND candidate is a valid input when its base HEAD, dirty state, complete
source/diff hashes and artifact provenance are recorded under the task execution and evidence
contracts. Do not claim a dirty build is a clean-HEAD build.

For the original package, NEVER:

- uninstall;
- run `pm clear`;
- wipe storage, SharedPreferences, databases or account/session state;
- downgrade;
- change the signer or application ID;
- install an unsigned or debug-signed replacement over it;
- run instrumentation/orchestrator configuration that can clear target-package data;
- expose, extract, print or persist credentials from stored state.

If production signing or the required signer verification is unavailable, keep the last known-good
original installed and report the exact blocker. Do not substitute a debug signer or an alternate
package. If a build or required validation fails, do not force-replace the last known-good copy.

## Narrow task-added cleanup exception

The two packages `sa.hulksa.player.preview` and `sa.hulksa.player.benchmark` installed on
`MIBOX4` by the `live-arabic-commentary-r01` task are task-added copies, not owner data. The owner
explicitly authorized their exact removal in the correction that adopted this policy.

Removal is allowed only when the active task packet proves the exact packages were added by that
task and explicitly authorizes cleanup:

- verify exact package names and installation evidence first;
- remove only those exact package names, never a wildcard or prefix;
- never remove `sa.hulksa.player`;
- never remove a pre-existing engineering package on another device;
- preserve all source, APK and report evidence on the server.

## Conflict rule

For owner-device package identity, in-place installation and refresh behavior, this contract is the
specific Android Engineering Lab authority and supersedes older Preview/Benchmark dual-refresh
wording or historical chat instructions. Repository Git/write protections, data protection,
signing/release authority and all other root `AGENTS.md` rules remain unchanged. This contract
grants no merge, tag, public release or deployment authority.
