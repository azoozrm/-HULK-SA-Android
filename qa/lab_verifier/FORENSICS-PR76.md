# PR #76 Freeze and Forensics Record

Frozen on 2026-08-01 before the clean qualification branch was created.

## Git state

- Base branch: `phase-3-v0.9.3.0-adaptive-foundation`
- Base SHA: `7a28b217bae94cb22ee01ea1a9d381c79c019d83`
- PR #76 head branch: `fix/quality-lab-tv-preconditions-20260801`
- PR #76 actual head SHA: `dfbfdc3c7b50ccc136a99084f07006f99e4c4514`
- PR #76 merge SHA: `97e1364e040c104c852a21059738b51404136135`
- PR #76 state: open draft, 27 commits, 15 changed files
- PR #57 head SHA: `c291fa7df2f3ee3a04d020cd831f02073a44d514`
- PR #57 merge SHA: `f93e46b9f0b2383293d0e8274ecccf238918242a`
- PR #57 state: open draft, 47 commits, 7 changed files

The PR #76 description was stale at freeze time: it named `0d5efbbef9982665f69dd96a2053299654d77a14` as the current clean head while GitHub reported `dfbfdc3c7b50ccc136a99084f07006f99e4c4514`.

## Latest runtime run

Workflow run `30716444068` (`HULK SA Quality UI Gate`) completed with failure.

- Phone/tablet jobs completed their device gate.
- Android TV 720p, 1080p, and 4K failed the enforcement step.
- Aggregate report construction and final UI enforcement failed.
- Compose/instrumentation job was skipped.

## Reviewed artifact ZIP digests

- Aggregate: `57c8ff7463266f7b3093c9a2a61b490cfe4ee49ce1134c823ba1fc1485445e5b`
- Android TV 720p: `36cd4d7883dbd2208a567c28e927697c2317a5b67b251e9491006aaaf70fa354`
- Android TV 1080p: `13fbe7bdb1824a7eca5f579d2b83940f4c7c6753e22a1007e4b72e830c8dd1a6`
- Android TV 4K: `5d76bed82c9d618e7a5ecb252895ada425ae31e5610cc754cdea554b93b6360b`
- Nexus 9 API 28: `ee2e77ecd775172c447c21234703f3520e9b16fa44e2cf166a0ac689c53c00a4`

These match the SHA-256 digests published by GitHub Actions for the artifacts.

## Proven classifications

### Lab failures

1. **Circular validation**: internal self-validation and analyzer tests were used as the primary proof for the same runtime path.
2. **Stale PR provenance claim**: PR body head SHA did not match the actual GitHub head.
3. **Root/downstream explosion**: TV 1080p and 4K each reported 35 critical failed checks, with 20 marked downstream, yet the repeated checks remained product-critical in the raw finding list.
4. **Start-state attribution**: a navigation precondition failure generated repeated `navigation_target_mismatch`, `tv_download_action_unreachable`, `tv_download_action_not_executed`, and `ui_state_not_updated` product findings under one root.
5. **Skipped independent runtime layer**: Compose/instrumentation was skipped in the latest run; green static/self-validation jobs therefore did not validate runtime correctness.

### Product findings supported by independent raw evidence

The Base build repeatedly showed loopback origin body bytes while repository progress remained zero, including Nexus 9 API 28 and all TV artifacts. This is a product-boundary signal only when fixture/provenance/start-state checks are independently valid. It must not be conflated with the TV focus precondition failures.

### Fixture and infrastructure

- The reviewed run recorded zero infrastructure retries and no ADB/emulator infrastructure root in the inspected summaries.
- The loopback origin produced bytes, so the reviewed download-origin fixture was active for the boundary finding.
- Fixture correctness as a whole was not independently established because HULK SA itself was the only runtime oracle.

### Blocked / not verified

- Android TV callback/action conclusions downstream of the first invalid focus target are blocked, not independent product roots.
- Harness non-interference was not proven.
- Full runtime correctness of marker provenance was not independently proven.
- The nine-device matrix did not prove the lab itself because it shared the same runtime driver, classifier, and gate.

## Required break in the circular proof

The clean branch introduces an independent verifier, fixed artifact corpus, positive/negative controls, fault injection, and a standalone deterministic Android fixture app. No module imports the Compatibility Lab runtime driver, analyzer, gate, injector, or product fixture lifecycle.
