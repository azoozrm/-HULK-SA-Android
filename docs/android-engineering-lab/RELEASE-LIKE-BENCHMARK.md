# HULK SA Android — Release-like Performance Measurement (Gate 2)

This document owns the Release-like performance measurement infrastructure required to
measure HULK SA with production-like characteristics on physical devices. It does not
change application source, behavior, the shipping Release identity, endpoints, ABI or
brand, and it does not adopt a generated Baseline Profile into production.

Repository authority, Git mutation rules and protected product contracts remain in the
root `AGENTS.md`. Lab execution and evidence rules remain in
`docs/android-engineering-lab/README.md`.

## 1. Components

- `:macrobenchmark` — isolated Macrobenchmark test module (`com.android.test`,
  `targetProjectPath = ":app"`). It contains:
  - `StartupBenchmarks.coldStartupToTvMain` — deterministic cold-start measurement
    (`StartupTimingMetric`, `StartupMode.COLD`);
  - `FirstEntryNavigationBenchmarks.firstEntryNavigationFromTvMain` — deterministic
    first-entry navigation measurement (`FrameTimingMetric`, `StartupMode.WARM`, fixed
    D-pad input sequence);
  - `BaselineProfileGenerator.generateBaselineProfile` — Baseline Profile generation
    path (`BaselineProfileRule`).
- `:app` `benchmark` build type — a non-debuggable, Release-derived measurement target.

## 2. Measurement target contract

`benchmark` is created with `initWith(release)`, so it preserves the Release
optimization contract:

| Property | Value |
|---|---|
| Derived from | `release` build type |
| `isMinifyEnabled` | `true` (R8) |
| `isShrinkResources` | `true` |
| `isDebuggable` | `false` |
| `isProfileable` | `true` (manifest `<profileable android:shell="true" />`) |
| `signingConfig` | `debug` signing config (stable lab signer; see note below) |
| `applicationId` | `sa.hulksa.player.benchmark` (isolated measurement identity/data) |
| `versionName` | shipping version name + `.benchmark` suffix |

The shipping Release identity is unchanged: `applicationId = sa.hulksa.player`,
shipping version code/name, endpoints, ABI set and brand are not modified, and the
Release build type keeps its existing signing behavior (unsigned when release signing
properties are not supplied). Production signing is never used here.

The `debug` signing config is a local lab test signer, but for the persistent
`sa.hulksa.player.benchmark` engineering instance it is stable owner state whose continuity
must be preserved: the installed package can only be refreshed with a same-signer
`adb install -r`. It is not a disposable per-round key, and it must not be deleted,
regenerated or replaced merely to make a test pass. See
`docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md` for the governing
preservation contract.

The `debug` build type is intentionally **not** used as the measurement target.

## 3. Commands (Contabo lab)

Build the measurement APKs:

```sh
hulk-build-run ./gradlew --no-daemon --console=plain --max-workers=2 \
  :app:assembleBenchmark \
  :macrobenchmark:assembleBenchmark
```

Verify the Baseline Profile packaging path (asserts the non-debuggable benchmark APK
packages `assets/dexopt/baseline.prof` / `baseline.profm`):

```sh
hulk-build-run ./gradlew --no-daemon --console=plain \
  :app:verifyBenchmarkBaselineProfilePackaging
```

Run the benchmarks against the connected physical device:

```sh
hulk-build-run ./gradlew --no-daemon --console=plain --max-workers=2 \
  :macrobenchmark:connectedBenchmarkAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=sa.hulksa.player.macrobenchmark.StartupBenchmarks,sa.hulksa.player.macrobenchmark.FirstEntryNavigationBenchmarks"
```

Benchmark JSON and Perfetto traces are written under
`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/`.

## 4. Baseline Profile path (Gate 3)

Generation and packaging are separate and neither is auto-adopted:

1. Generate on a physical device:

   ```sh
   hulk-build-run ./gradlew --no-daemon --console=plain --max-workers=2 \
     :macrobenchmark:connectedBenchmarkAndroidTest \
     "-Pandroid.testInstrumentationRunnerArguments.class=sa.hulksa.player.macrobenchmark.BaselineProfileGenerator"
   ```

   The generated `baseline-prof.txt` is written to the connected test additional-output
   directory.

2. Review the generated profile. Adoption is a separate, explicit, owner-approved Gate 3
   decision; nothing under `app/src/**/baselineProfiles/` is added in this round.

3. To package a reviewed profile, place it at
   `app/src/benchmark/baselineProfiles/baseline-prof.txt` (the benchmark variant source
   set) and rebuild. Android Gradle Plugin merges source-set baseline profiles with
   library-provided profiles and packages them as `assets/dexopt/baseline.prof`. The
   `verifyBenchmarkBaselineProfilePackaging` task asserts the packaging.

## 5. Physical qualification notes

- The physical acceptance device is a Xiaomi Mi Box 4 (`MIBOX4`, API 28, `armeabi-v7a`).
- The `<profileable>` manifest element is recognized from API 29. On API 28 the platform
  does not expose the profileable-by-shell hook, so the profileable declaration cannot be
  independently exercised at the platform level on this device. The non-debuggable target
  is still measurable: Macrobenchmark produced full Perfetto traces and timing metrics.
- The benchmark target process is traced (for example the `launching: sa.hulksa.player.benchmark`
  slice appears in the captured traces).

## 6. Non-goals

- No application source, player or UI behavior change.
- No shipping application ID, version, endpoint, ABI or brand change.
- No production signing, tag, release or merge.
- No Baseline Profile adoption into production.
