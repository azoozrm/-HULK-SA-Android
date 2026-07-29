# Quality Lab maintenance

## Routine changes

1. Update an analyzer with a failing self-test first.
2. Preserve stable finding fingerprints unless the finding meaning changes.
3. Validate every JSON config against its schema.
4. Regenerate UI inventory and review the diff.
5. Run Python, Compatibility Lab, Gradle unit/lint, and the selected emulator tier.
6. Inspect uploaded reports and raw evidence; do not stop at workflow color.

## Baseline changes

Baseline updates are manual, protected, and require a reason, build SHA, reviewed Before/After/Diff,
and no P0/P1 finding. PR workflows reject `update_baseline=true`.

## Device matrix changes

Add risk-driven profiles to `matrix.json`, preserve explicit “simulation” labels, validate real dp,
then run them before marking verified. OEM labels are reserved for physical artifacts.

## Product fixes

Reproduce, retain Before, add a regression test, apply the smallest fix, run targeted and affected
matrix tests, retain After/Diff, and commit the product fix separately. Do not fold product fixes
into analyzer/workflow commits.

## Commands

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s qa/quality/tests -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s qa/compatibility/tests -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/tests -p 'test_*.py' -v
python3 qa/quality/release/logo_integrity.py
sha256sum -c qa/canonical/canonical-source.sha256
git diff --check
```

