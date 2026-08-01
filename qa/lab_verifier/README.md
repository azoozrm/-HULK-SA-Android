# Independent Lab Verifier

This verifier is an independent evidence consumer. It never imports or calls the Compatibility Lab runtime driver, analyzer, gate, marker injector, or fixture lifecycle implementation.

It validates raw XML, focus logs, logcat, window/activity state, marker provenance, origin/repository boundaries, screenshot geometry metadata, checksums, and commit/APK provenance. Missing mandatory evidence is `BLOCKED`; report-only mode ignores only proven product findings and still fails lab, fixture, infrastructure, provenance, or evidence invalidity.

## Local verification

```bash
python3 -m unittest discover -s qa/lab_verifier/tests -v
python3 qa/lab_verifier/cli.py replay \
  --corpus qa/lab_verifier/corpus \
  --repeat 8 \
  --out verifier-results
```

The corpus contains known PASS, PRODUCT, LAB, FIXTURE, INFRASTRUCTURE, BLOCKED, navigation-target, start-state, callback, origin/repository-boundary, density, and downstream-only controls. Outputs must remain byte-for-byte identical across replays.
