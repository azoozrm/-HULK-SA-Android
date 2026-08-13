# HULK SA Compatibility Lab V2

Compatibility Lab V2 is a clean, isolated qualification system for the existing HULK SA application. It does not inject semantics, screens, endpoints, fixtures, delays, or measurement code into `app/src/main`.

## Status model

Every check reports one of:

- `PASS`: the check ran and its assertion passed.
- `FAIL`: the check ran and proved a defect or contract violation.
- `BLOCKED`: required verification could not be completed because evidence, credentials, an approved baseline, a real service, or physical hardware is unavailable.
- `SKIPPED`: the check is outside the selected execution scope, with an explicit reason.

`BLOCKED` is never converted to `PASS`. Product assertion failures are never retried. Screenshot baselines are never created or updated automatically.

## Layers

1. `static_validate.py`: package/version, Manifest, branding hashes, the complete phone/TV/banner/notification density matrix, production-marker absence, old-lab absence, endpoint identity, source hygiene.
2. JVM unit tests: adaptive window, input, layout, focus policy, downloads, state restoration and business logic.
3. Android instrumentation and UI Automator: real Activity launch, lifecycle, D-pad, system UI, screenshots, hierarchy and diagnostics.
4. Runtime evidence gate: requires the exact artifacts declared by the selected scope.
5. Visual regression: enabled only after a human-approved full-window baseline is committed. Until then it is explicitly `BLOCKED` in full qualification.
6. Physical verification: Xiaomi receiver, TCL TV, Galaxy phone, small phone, tablet and install-over remain `BLOCKED: PHYSICAL DEVICE REQUIRED` until signed evidence is attached.

## Local commands

```bash
python3 -m unittest discover -s quality/compatibility-v2/tests -p 'test_*.py'
python3 quality/compatibility-v2/static_validate.py --repo-root . --out build/compatibility-v2/static
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Runtime collection requires an already booted emulator and installed debug/test APKs:

```bash
bash quality/compatibility-v2/collect_runtime_evidence.sh phone-medium build/compatibility-v2/runtime/phone-medium
python3 quality/compatibility-v2/evidence_gate.py \
  --scope runtime --evidence-root build/compatibility-v2/runtime/phone-medium \
  --out build/compatibility-v2/runtime/phone-medium/gate
```

## Branding rule

The approved HULK SA logo and reviewed TV assets are read-only after approval. V2 verifies their SHA-256 values, exact Android density dimensions, distinct phone/TV launcher references, and `ContentScale.Fit`; it never rewrites, recolors, crops, or normalizes assets during validation.
