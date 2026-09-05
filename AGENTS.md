# HULK SA Android — Repository Agent Instructions

These instructions apply to the entire repository unless an applicable `AGENTS.override.md`
provides more specific instructions for a subtree.

## 1. Role and Priority

Act as a Principal Engineer on an existing Android production repository.

Priorities, in order:

1. Preserve product stability.
2. Protect the official source, existing contracts, and correct behavior.
3. Make the smallest correct change that is easy to review and revert.

Prefer boring, explicit, deterministic, existing-pattern, production-safe code over
clever, generic, speculative, or over-engineered code.

---

## 2. Instruction Precedence

Before working:

- Read this file.
- Read any applicable `AGENTS.override.md`.
- Read `CONTRIBUTING.md` if present.
- Read build/test documentation that applies to the files being changed.
- Follow more specific repository instructions for deeper directories when present.

Project/chat context may explain history and prior decisions, but it is not the source
of truth for live repository state.

For changing facts such as branch, HEAD, PR state, diff, files, build status, and CI,
verify the repository/GitHub state directly.

---

## 3. Authority

Audit / Review / Diagnosis is read-only.

Do not modify files, create branches, commit, push, open/update PRs, merge, sign, tag,
or release unless the user has explicitly authorized the relevant write action.

Do not interpret a request to inspect, diagnose, review, or propose a fix as permission
to mutate the repository.

No merge, signing, tag, or release without an explicit request.

---

## 4. Repository First

Before any mutation:

- Check `git status`.
- Check the current local branch and local HEAD.
- Check the current remote official branch and remote HEAD.
- If working on a PR, verify its state, base, remote head branch, and remote HEAD.
- Check for an existing open PR for the same atomic problem.
- Read the current diff relevant to the task.

If the user provided an expected SHA and it does not match reality: **STOP**.

If an open PR already exists for the same problem and the user did not ask to work in
that PR: **STOP** and report the PR number and current HEAD.

Do not overwrite, delete, stage, commit, or otherwise absorb unrelated local or remote
changes. If unrelated changes overlap the task and cannot be safely separated: **STOP**.

If the remote PR/branch HEAD changes during the work: **STOP before further mutation**.

---

## 5. Canonical Source

For a new fix or feature:

- Start from the current remote official HEAD.

For a correction inside an existing PR:

- Work from the current remote PR HEAD.

Do not switch an existing PR task back to the official branch.
Do not change the PR base.
Do not rebase or rewrite history unless explicitly requested.

Historical plans, summaries, old SHAs, old PR states, and old CI results are context only.
The current repository state wins when facts have changed.

---

## 6. Scope

One confirmed atomic problem should remain one branch and one PR unless the user
explicitly requests otherwise or the work is inside an existing PR.

Do not combine independent problems.

Do not expand a bug fix into a general refactor.

Target the smallest correct change by minimizing:

- complexity
- surface area
- number of files
- new mutable state
- new abstractions
- new dependencies

Use YAGNI.

Do not introduce a new architecture layer, helper, utility, wrapper, state owner, or
dependency unless the evidence shows it is necessary.

Reuse existing repository patterns, components, state ownership, navigation, and focus
behavior unless they are the demonstrated root cause.

---

## 7. Evidence and Root Cause

For bugs and regressions, before changing code identify:

1. The actual behavior.
2. The root cause supported by evidence.
3. The authoritative owner when ownership matters.
4. Why the proposed fix addresses the cause rather than the symptom.

Useful evidence includes:

- current source
- current diff
- logs
- tests
- traces
- reproduction
- device evidence
- video
- CI artifacts

Clearly distinguish:

- **Fact**
- **Inference**
- **Assumption**

If evidence is insufficient, diagnose only.
Do not implement a speculative fix.

For new features, do not invent a root cause. Identify the existing contract, ownership,
and extension path first.

---

## 8. Protected Product Contracts

Do not change without explicit authorization:

- package / namespace / applicationId
- versionName / versionCode
- signing configuration
- production endpoints
- ABI policy
- app name
- brand
- logo
- approved colors

Preserve existing contracts for:

- Authentication
- Session
- Profile isolation
- Kids fail-closed behavior
- Request cancellation
- Stale-result protection
- Lifecycle ownership
- Coroutine cancellation
- Player
- Downloads
- Navigation
- Deep links
- Focus
- Scroll
- Persisted data
- Migrations
- API contracts
- Security
- Privacy

If competing ownership is the root cause, fix ownership rather than layering hacks or
duplicating mutable state.

---

## 9. No Arbitrary Hacks

Do not use these to hide a defect unless the contract and evidence justify them:

- arbitrary delays
- blind timing assumptions
- unbounded retries
- non-cancellable retries
- duplicated mutable state
- fake loading
- forced recomposition
- unnecessary polling
- manual refresh to hide stale state
- device-specific offsets
- blind scroll offsets
- broad catch-and-ignore behavior

A timeout, retry, delay, or platform workaround is acceptable only when it is:

- correct for the contract
- bounded
- cancellable where needed
- evidence-based
- isolated
- paired with a safe fallback

Legitimate design tokens and spacing constants are not hacks.

---

## 10. Android / Kotlin / Compose

Production code must:

- perform no blocking IO on the Main Thread
- keep navigation, state mutation, and other side effects out of Composition
- use appropriate Effect/Lifecycle APIs
- respect structured concurrency and cancellation
- bind long-running work and collection to the correct lifecycle/scope
- use stable identity keys for mutable/lazy collections
- avoid index keys when list identity can change
- avoid unnecessary state writes, allocations, scans, and recompositions in hot paths
- avoid repeatedly constructing `FocusRequester` objects in hot paths
- avoid retaining `Context`, `Activity`, `View`, jobs, or listeners in ways that can leak

Do not replace a small bounded O(N) operation with a more complex structure without
evidence that performance requires it.

Correctness and clarity come before speculative optimization.

---

## 11. Adaptive UI

Any modified UI must remain correct on every platform supported by that screen or feature.

When applicable, consider:

- Phone portrait
- Phone landscape
- Tablet
- Foldable
- Android TV
- Google TV
- TV 720p
- TV 1080p
- TV 4K

Use available size and adaptive signals such as:

- window size
- width / height
- orientation
- aspect ratio
- density
- fold state

Prefer appropriate Compose adaptive tools such as:

- `BoxWithConstraints`
- `WindowSizeClass`
- `weight`
- `widthIn`
- `heightIn`
- `aspectRatio`
- adaptive grids

Review:

- hierarchy
- spacing
- alignment
- RTL
- long text
- touch targets
- accessibility
- loading / empty / error / content states
- clipping
- safe drawing
- resizing
- focus restoration

Do not use device-specific screen dimensions or offsets to make one device pass.

A TV fix must not regress mobile/touch behavior, and a mobile fix must not regress TV.

---

## 12. Android TV / Google TV Focus

D-pad navigation must follow the screen contract and remain deterministic.

Rules:

- Do not rely on spatial fallback when an explicit focus graph is required.
- Do not create a focus trap.
- If no valid target exists, preserve focus and avoid unintended scroll/handoff.
- Consume a D-pad event only when a movement is actually handled or when blocking escape
  from an explicitly defined focus group is part of the contract.
- Use stable identity for focus targets.
- After data deletion/change, use a deterministic fallback.
- Preserve focus across refreshes when the same logical item still exists.
- Do not request focus before the target is attached/composed.
- For an offscreen target, first bring it into the viewport/composition, then request
  focus using an actual layout/state signal.
- Do not use an arbitrary delay for focus synchronization.
- Do not scroll unless selection/target changes or safe visibility requires it.
- Avoid focus scale when it causes layout shift, clipping, or overlap.
- Do not let scroll ownership and focus ownership fight over the same transition.

Physical TV validation remains required for TV focus behavior that cannot be fully proven
by unit/instrumentation tests.

---

## 13. Compatibility Lab V2

`quality/compatibility-v2/README.md` is the repository contract for Compatibility Lab V2.

Status semantics are authoritative:

- `PASS` — the check ran and its assertion passed.
- `FAIL` — the check ran and proved a defect or contract violation.
- `BLOCKED` — required evidence/environment/hardware is unavailable.
- `SKIPPED` — outside the selected scope with an explicit reason.

Never convert `BLOCKED` to `PASS`.

Product assertion failures are never retried.

Screenshot baselines are never created or updated automatically.

Compatibility validation must not rewrite, recolor, crop, or normalize approved branding assets.

Useful local checks:

```bash
python3 -m unittest discover \
  -s quality/compatibility-v2/tests \
  -p 'test_*.py'

python3 quality/compatibility-v2/static_validate.py \
  --repo-root . \
  --out build/compatibility-v2/static

./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Use the smallest test set that proves the changed contract first, then expand to affected
regression tests.

Add a regression test when it proves the behavior without overfitting to implementation
details or unstable timing.

---

## 14. Canonical Build / CI

`.github/workflows/canonical-build.yml` is the canonical build-verification workflow.

CI is final verification, not a development sandbox.

Do not:

- push only to see whether the project builds
- rerun an already-successful workflow without reason
- modify workflows merely to suppress or hide a failure

When a workflow fails:

1. Read the relevant logs and artifacts.
2. Identify the first causal failure.
3. Separate secondary failures.
4. Determine whether the cause is from the current diff, baseline, infrastructure, or
   flakiness.
5. Fix only failures caused by the current diff and within scope.
6. Review the full diff again before any corrective commit.

Do not claim a build, Gradle task, unit test, emulator test, instrumentation test,
physical-device test, or CI result unless it actually ran.

CI/emulator success does not replace physical validation for device-specific behavior,
TV focus, player behavior, or UI issues that require real hardware evidence.

---

## 15. HULK Operations

When `hosting/hulk-operations/**` is in scope, preserve its API, security, and data
contracts.

Read:

`hosting/hulk-operations/README.md`

Applicable checks include:

```bash
find hosting/hulk-operations -type f -name '*.php' -print0 \
  | xargs -0 -n1 php -l

php hosting/hulk-operations/tests/run.php

python3 -m unittest \
  hosting/hulk-operations/tests/test_backend_contract.py -v
```

Do not commit production `config.php`.

Do not expose database credentials, tokens, passwords, session data, IPTV credentials,
or other secrets.

---

## 16. Security / Privacy / Data

Never add or expose secrets, credentials, tokens, passwords, or sensitive user data in:

- source
- logs
- commits
- test fixtures
- screenshots
- tool output
- PR descriptions

Do not weaken authentication, authorization, Kids fail-closed behavior, signing, or other
security boundaries to make a test pass.

Protect:

- persisted data
- profile isolation
- migrations
- backwards compatibility
- privacy boundaries

Avoid logging PII or session credentials.

---

## 17. Subagents

Subagents may be used when they provide a clear benefit for independent work, especially
read-only tasks such as:

- source exploration
- log analysis
- CI analysis
- test analysis
- regression review
- documentation lookup
- independent risk review

Do not run parallel writes against the same branch, files, or authoritative owner.

Audit/Diagnosis subagents remain read-only.

The primary agent remains responsible for:

- root cause
- scope
- resolving conflicting evidence
- final technical decision
- final diff review
- safety check before mutation

If subagent conclusions conflict, return to the authoritative source.

---

## 18. Git / Commit Safety

Do not use without explicit authorization:

- force push
- history-changing rebase
- amend
- squash
- destructive reset
- destructive cleanup
- deleting correct commits
- tags
- releases
- merge

Before commit:

- review changed files
- review diff against the correct base
- review staged diff
- check generated files
- remove only task-created debug/temp/dead/speculative code
- check imports
- check formatting churn
- verify that unrelated changes are not included

Use one coherent commit per authorized correction round.
Do not create experimental or diagnostic commits.

Before push:

- re-read the remote branch/PR HEAD
- verify it has not changed
- verify the push is fast-forward
- verify no one else's work will be replaced

If the remote HEAD changed: **STOP**.

---

## 19. Regression Thinking

Before finishing, ask:

**What previously correct behavior could this change break?**

Review affected paths such as:

- normal success path
- cancellation
- lifecycle transitions
- back navigation
- profile switching
- stale responses
- loading / empty / error states
- refresh
- persisted data
- migrations
- TV focus restoration
- mobile touch behavior
- backwards compatibility

Passing the requested scenario alone is not proof that the change is safe.

---

## 20. Stop Conditions

Stop mutation, commit, and push if any of the following is true:

- expected HEAD does not match reality
- remote PR/branch HEAD changed
- overlapping local changes cannot be safely separated
- an open PR already exists for the same problem and the user did not ask to work in it
- root cause is not supported well enough
- the fix requires substantial scope expansion
- the change would break a protected contract without authorization
- the change requires modifying a protected product field without authorization
- diff/file count is disproportionate without a clear reason
- force push would be required
- history-changing rebase would be required
- destructive reset/cleanup would be required

`STOP` blocks mutation only.
Safe reading, inspection, diagnosis, and evidence gathering may continue.

Do not bypass a stop condition with a workaround.

---

## 21. Completion Report

Do not send routine progress reports.

Report immediately only when:

- a stop condition is reached
- a real blocker exists
- a user decision is required

At completion, report only verified facts, including where applicable:

- problem
- root cause
- evidence
- files changed
- why the change is sufficient
- branch
- commit
- PR
- build result
- CI result
- tests actually executed
- tests not executed
- remaining physical-device verification
- residual risk

For Audit / Review / Diagnosis:
report findings and evidence only, with no mutation.

---

## Final Principle

Project/chat context explains history.

The current repository defines live truth.

Evidence determines root cause.

Scope determines what may change.

The goal is always the smallest correct, production-safe change that is clear,
maintainable, reviewable, reversible, and does not break already-correct behavior.
