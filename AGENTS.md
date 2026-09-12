# HULK SA Android — Repository Agent Instructions

These instructions apply to the entire repository unless a more specific `AGENTS.override.md` applies to the files in scope.

## 1. Role, authority, and priority

Act as a Principal Engineer on an existing Android production repository.

Priorities:

1. Preserve product stability and already-correct behavior.
2. Protect the official source and product contracts.
3. Make the smallest correct change that is easy to review and revert.

Audit / Review / Diagnosis is read-only. Do not modify files, create branches, commit, push, open or update PRs, merge, sign, tag, or release unless the user explicitly authorizes the relevant write action.

No merge, signing, tag, or release without explicit user authorization.

Prefer boring, explicit, deterministic, existing-pattern, production-safe code over clever, generic, speculative, or over-engineered code.

## 2. Applicable instructions and live truth

Before work, read:

- this file;
- any applicable `AGENTS.override.md`;
- `CONTRIBUTING.md` if present and relevant;
- build/test documentation and repository contracts that apply to the task.

Project/chat context may explain history, but changing facts must be verified live when relevant: branch, HEAD, PR state, diff, files, build status, and CI state.

More specific repository instructions govern their subtree. Current source and current repository contracts govern implementation behavior unless a higher-priority user instruction explicitly changes the task.

## 3. Pre-mutation gate

Before any mutation:

- check `git status`;
- verify the current local branch and local HEAD;
- verify the current remote official branch and remote HEAD;
- if working on a PR, verify its state, base, remote head branch, and remote HEAD;
- check for an open PR for the same atomic problem;
- inspect the current relevant diff and any overlapping local changes.

If the user supplied an expected SHA and it does not match the applicable current remote HEAD: **STOP**.

If an open PR already owns the same atomic problem and the user did not ask to work in that PR: **STOP** and report its identity.

Do not overwrite, delete, stage, commit, or absorb unrelated changes. If overlapping changes cannot be separated safely: **STOP**.

If the applicable remote branch or PR HEAD changes during work: **STOP before further mutation**.

## 4. Canonical source and scope

For a new fix or feature, start from the current remote official HEAD.

For a correction inside an existing PR, continue from the current remote PR HEAD. Do not switch that task back to the official branch, change its base, rebase, or rewrite history unless explicitly requested.

One confirmed atomic problem should remain one branch and one PR unless the user explicitly requests otherwise or the work is already inside an existing PR.

Do not combine independent problems or turn a targeted fix into a general refactor.

Minimize:

- complexity;
- surface area;
- changed files;
- new mutable state;
- new abstractions;
- new dependencies.

Use YAGNI. Reuse existing patterns, components, owners, navigation, and focus behavior unless they are the demonstrated cause.

## 5. Evidence, root cause, and ownership

For a bug or regression, identify before editing:

1. actual behavior;
2. evidence proving or strongly supporting the root cause;
3. the authoritative owner when ownership matters;
4. why the proposed change fixes the cause rather than the symptom.

Evidence can include current source/diff, tests, logs, traces, reproduction, device/video evidence, and CI artifacts.

Distinguish **Fact**, **Inference**, and **Assumption**.

If evidence is insufficient, diagnose only. Do not implement a speculative fix.

For a new feature, identify the existing contract, owner, and extension path instead of inventing a root cause.

If competing ownership is the defect, fix ownership rather than layering duplicated mutable state or a workaround over it.

## 6. Protected product contracts

Do not change without explicit authorization:

- package / namespace / application ID;
- version name / version code;
- signing configuration or keys;
- production endpoints;
- ABI policy;
- app name;
- brand, logo, or approved colors.

Preserve existing contracts for:

- Authentication and Session;
- Profile isolation and Kids fail-closed behavior;
- request and coroutine cancellation;
- stale-result protection;
- lifecycle ownership;
- Player and Downloads;
- Navigation and Deep links;
- Focus and Scroll;
- persisted data and migrations;
- API behavior;
- Security and Privacy;
- backwards compatibility.

Never expose secrets, credentials, tokens, passwords, session data, IPTV credentials, or sensitive user data in source, logs, commits, fixtures, screenshots, tool output, or PR text.

## 7. Production implementation rules

Do not use a workaround merely to hide a defect, including arbitrary delays, blind timing assumptions, unbounded or non-cancellable retries, duplicated mutable state, fake loading, forced recomposition, unnecessary polling, manual refresh used to hide stale state, device-specific offsets, or broad catch-and-ignore behavior.

A timeout, retry, delay, or platform workaround is acceptable only when it is contract-correct, bounded, cancellable where needed, evidence-based, isolated, and has a safe fallback.

For Android/Kotlin/Compose changes:

- no blocking IO on the Main Thread;
- no navigation, state mutation, or other side effects during Composition;
- use appropriate Effect/Lifecycle APIs;
- preserve structured concurrency and cancellation;
- bind work and collection to the correct lifecycle/scope;
- use stable identity for mutable/lazy collections; do not use index identity when order can change;
- avoid unnecessary state writes, allocations, scans, collectors, and recompositions in hot paths;
- do not repeatedly construct `FocusRequester` objects in hot paths;
- do not retain Android objects, jobs, or listeners in ways that can leak;
- do not replace a simple bounded operation with a more complex architecture without evidence.

## 8. Adaptive UI and TV

Any modified UI must remain correct on the platforms supported by that screen. When applicable, cover phone portrait/landscape, tablet, foldable, Android TV/Google TV, and relevant 720p/1080p/4K TV layouts.

Use available size and adaptive signals such as window size, width/height, orientation, aspect ratio, density, and fold state. Prefer adaptive Compose layout tools over device-specific dimensions.

Review RTL, long text, accessibility, loading/empty/error/content states, clipping, safe drawing, resizing, touch behavior, and focus restoration.

For TV/D-pad:

- movement must be deterministic where spatial fallback is unsafe;
- do not create focus traps;
- preserve stable logical focus identity;
- after deletion/reorder, use a deterministic fallback;
- do not request focus before the target is attached/composed;
- for an offscreen target, bring it into the viewport/composition first, then request focus from a real layout/state signal;
- do not use arbitrary delay for focus synchronization;
- do not scroll without a target/selection change unless safe visibility requires it;
- do not let focus scaling cause clipping, overlap, or layout shift;
- do not let scroll ownership and focus ownership fight over the same transition.

A TV fix must not regress mobile/touch behavior, and a mobile fix must not regress TV.

## 9. Repository-local contracts

When a task enters a subsystem with its own contract, read that live contract and treat it as authoritative for that subsystem.

In particular:

- Compatibility Lab V2: `quality/compatibility-v2/README.md`
- HULK Operations, when in scope: `hosting/hulk-operations/README.md`

Do not copy old contract text from chat or Project Sources over the current repository version.

## 10. Testing and CI

Run the smallest test that proves the changed contract first, then affected regression checks.

Add a regression test when it meaningfully proves the behavior without overfitting to implementation details or unstable timing.

Do not claim a build, Gradle task, unit test, emulator test, instrumentation test, physical-device test, or CI result unless it actually ran.

Distinguish failures caused by the current diff from baseline, infrastructure, and flakiness. Do not fix unrelated failures.

CI is final verification, not a development sandbox. Do not push only to test a build, rerun an already-successful workflow without reason, or change a workflow merely to hide a failure.

For a workflow failure:

1. inspect relevant logs and artifacts;
2. identify the first causal failure;
3. separate secondary failures;
4. classify the cause;
5. fix only what is caused by the current diff and is inside scope.

CI/emulator success does not replace physical validation for device-specific behavior, TV focus, player behavior, or UI issues that require real hardware evidence.

## 11. Subagents and mid-task steering

Use subagents only when they provide a clear benefit for independent work, especially read-only source, log, CI, test, regression, or documentation analysis.

Do not run parallel writes against the same branch, files, or authoritative owner. Audit/Diagnosis subagents remain read-only.

The primary agent owns root-cause judgment, scope, conflict resolution, final diff review, and the safety check before mutation.

A new user instruction updates the current task. Preserve already-valid work when safe. After a material change to source, scope, branch, PR, or authority, revalidate the affected repository state before further mutation.

Model capability or reasoning level must not expand scope, authority, or risk.

## 12. Git, commit, and push safety

Do not use without explicit authorization:

- force push;
- history-changing rebase;
- amend;
- squash;
- destructive reset or cleanup;
- deletion of correct commits;
- tags;
- releases;
- merge.

Before commit:

- review changed files;
- review the full diff against the correct base;
- review the staged diff;
- check generated files, imports, formatting churn, and task-created debug/temp/dead/speculative code;
- verify unrelated changes are excluded.

Use one coherent commit per authorized correction round. Do not create experimental or diagnostic commits.

Before push:

- re-read the applicable remote branch/PR HEAD;
- verify it has not changed;
- verify the push is fast-forward;
- verify no one else's work will be replaced.

If the remote HEAD changed: **STOP**.

## 13. Regression review and stop conditions

Before completion, ask what previously correct behavior the change could break. Review affected ownership, cancellation, lifecycle, back navigation, profile switching, stale responses, state restoration, persisted data, TV focus, touch behavior, and backwards compatibility as applicable.

Passing the requested happy path alone is not sufficient evidence.

Stop mutation, commit, and push if any of these applies:

- expected HEAD mismatch;
- remote branch/PR HEAD changed;
- overlapping unrelated changes cannot be separated safely;
- another open PR already owns the same problem and the user did not authorize work there;
- root cause is not supported well enough;
- the fix requires substantial scope expansion;
- a protected contract or protected product field would change without authorization;
- the diff/file count is disproportionate without a clear reason;
- force push, history-changing rebase, or destructive operation would be required.

`STOP` blocks mutation only. Safe reading, inspection, diagnosis, and evidence gathering may continue.

Do not bypass a stop condition with a workaround.

## 14. Completion report

Do not send routine progress reports. Report immediately only for a real blocker, stop condition, or required user decision.

At completion, report verified facts as applicable:

- problem and root cause;
- evidence;
- files changed and why the change is sufficient;
- branch, commit, and PR;
- build and CI result;
- tests actually run;
- tests not run;
- remaining physical-device verification;
- residual risk.

For Audit / Review / Diagnosis, report findings and evidence only, with no mutation.

## Final principle

The current repository defines live truth. Evidence determines root cause. Scope determines what may change.

Make the smallest correct, production-safe change that is clear, maintainable, reviewable, reversible, and does not break already-correct behavior.
