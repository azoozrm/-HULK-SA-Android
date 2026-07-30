# HULK SA Quality Source of Truth

## Decision

The Quality Engineering Lab branch is based on:

- Product branch: `phase-3-v0.9.3.0-adaptive-foundation`
- Product base SHA: `55ee9a136d3557a97daa9b9c2a4821de75108652`
- Repository default branch: `main`
- Default-branch SHA at discovery: `9b425c26b984ff735119dc2a22e0deb97058f2c7`

`main` is the GitHub default but is not the current HULK SA product source. The
project owner explicitly identifies the phase-3 branch as official, and the
v0.9.3.17–v0.9.3.18 canonicalization, durable-download, compatibility, and
signing sequence was merged there.

## Discovery evidence

- Discovery date: 2026-07-29 UTC.
- Pull requests: 58 total; 20 open, 34 merged, 4 closed without merge.
- Remote branches: 62.
- Git tags: none.
- GitHub Releases: none.
- GitHub Actions workflows returned by the API: 63.
- PR #57 is draft and unmerged.
- PR #58 is merged to `main`; it changes only a guarded manual-signing
  dispatch anchor and does not make `main` the product source.

The complete PR record and changed-file lists are stored in
`qa/quality/pr-inventory.json` and summarized in `docs/quality/PR-INVENTORY.md`.

## Compatibility Lab lineage

The official branch contains Compatibility Lab tree
`af7ecde05458ecf947fec36825d374f9cee7d0c6`. Its latest valid official run is
run 55 (`30383727238`) at `ca4c86b561d7bd3f99de0ff6f11b322231c7a01b`.
The product and Compatibility Lab paths are byte-identical between that SHA and
the official head `55ee9a`.

PR #57 contains a newer Compatibility Lab tree
`549083129578e150c88de4064b55d09747a8e309` at
`ad2d0995a3e451d537d3917833fcdc2ce186b9f6`, but its 25-file diff also contains:

- five product/test files under `app/`;
- three release/build workflows;
- runtime-host and signing qualification changes;
- TV layout and durable-download product fixes;
- eight Compatibility Lab files.

PR #57 therefore cannot be used as the Quality Lab base without importing
unapproved product changes. The lab-only delta may be extracted in a dedicated,
reviewable commit. Any required debug-only semantics markers must be isolated
from product layout fixes. No PR is merged, closed, or retargeted by this work.

## Integrity rules

1. Product changes are compared against `55ee9a`.
2. PR #57 remains a draft reference and runtime-fix candidate.
3. Compatibility functionality is imported only by explicit path review.
4. Logo assets are protected by `qa/quality/release/approved-logo-assets.sha256`.
5. A baseline is valid only when its build SHA, workflow run, artifact digests,
   and device geometry are recorded.
6. Physical/OEM/ARM claims require physical evidence and cannot be inferred
   from x86_64 emulators.

