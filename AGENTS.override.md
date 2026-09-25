# HULK SA Android — Owner Physical Engineering Override

This override supplements root `AGENTS.md` and exists to make the owner's persistent physical
engineering-instance contract durable across chats, task packets, models and executors.

It overrides only conflicting Preview-only physical-refresh wording in root `AGENTS.md`.
All other repository governance, write-safety, protected product, signing, merge, release and
stop-condition rules remain unchanged.

Read the full specific contract before any physical engineering build/install/instrumentation action:

`docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`

## Persistent owner instances

The fixed persistent physical engineering packages are:

1. `sa.hulksa.player.benchmark`
   - primary semi-release physical development/testing instance;
   - owner-authenticated state/data is persistent and must be preserved;
   - current qualification identity: versionCode `67`, qualification versionName base `0.9.3.23`.

2. `sa.hulksa.player.preview`
   - owner's persistent Preview/daily engineering copy;
   - existing state/data must be preserved;
   - current qualification identity: versionCode `67`, qualification versionName base `0.9.3.23`.

`sa.hulksa.player.dev` is NOT the default persistent physical engineering instance.

## Mandatory dual refresh after successful implementation

At the end of every Android IMPLEMENTATION round that reaches a valid, testable state, unless the
active task explicitly forbids physical-device refresh, build and refresh BOTH persistent packages
from the exact same task worktree / PR HEAD.

Use the owner-approved qualification overrides:

`-PHULK_QUALIFICATION_VERSION_CODE=67`
`-PHULK_QUALIFICATION_VERSION_NAME=0.9.3.23`

Build the actual persistent variants:

- `:app:assemblePreview`
- `:app:assembleBenchmark`

Before installation verify package, version identity, signer continuity and required release-like
properties. Install only with `adb install -r`.

For BOTH persistent packages, never:

- uninstall;
- run `pm clear`;
- wipe storage / SharedPreferences / databases / session state;
- downgrade;
- change signer;
- silently install a stale source-baseline version;
- run instrumentation/orchestrator that can clear target-package data.

Launch both once after a successful refresh and verify no immediate crash and no stale Optional
Update overlay. Preserve existing authenticated/session state.

If a new build or required validation fails, do not force-replace the last known-good installed copy.

Any test that requires clean state must use a separate isolated disposable package/surface.

Audit/Review/Diagnosis rounds do not automatically rebuild/reinstall these packages unless the
active task specifically requires a safe physical action.

This owner contract remains in force until the owner explicitly changes it. A future authorized
qualification-version change must update this override and the dedicated contract in the same
governance round so subsequent conversations inherit the new values.
