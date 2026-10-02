# HULK SA Android — Permanent Task Execution Contract

This document is the permanent, model-neutral execution contract for substantive authorized HULK SA
Android Implementation and Correction tasks. It exists so that every future executor inherits the
same verified execution chain, evidence standard and completion boundary without depending on chat
history.

Normative execution chain:

live verification → ownership/evidence → bounded scope → implementation → complete diff review →
focused validation → build/provenance when applicable → physical qualification when applicable →
bounded runtime verification when applicable → capture only when explicitly authorized →
final source freeze → durable evidence → authorized commit/push/PR → configured CI conclusion →
final report → evidence-based verdict.

This contract owns execution flow only and grants no authority. Root `AGENTS.md`, any applicable
`AGENTS.override.md`, and the active task packet remain authoritative and are read first.

## 1. Authority, applicability and executor neutrality

- Applies to substantive authorized Implementation and Correction tasks: application source, UI,
  behavior, tests, tooling, lab automation, and repository governance.
- Audit, Review and Diagnosis remain read-only unless the active packet explicitly authorizes write
  actions.
- Current owner instructions, the active task packet, root `AGENTS.md` and any applicable
  `AGENTS.override.md` define authority. Follow the standing owner-authorized implementation
  workflow in root `AGENTS.md` unless the current owner instruction explicitly narrows it.
  No contract, skill, helper, model, agent, or past chat may widen it.
- The contract binds the work, not the worker. Every executor — any vendor, model, agent or human —
  follows the same chain, evidence and verdict rules.
- Model capability, reasoning variant, agent name and vendor never expand scope, authority or risk.
- Where this document and a more specific live contract differ for that contract's area, the more
  specific live contract governs: `AGENTS.override.md`,
  `docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`,
  `docs/android-engineering-lab/README.md`, and subsystem contracts such as Compatibility Lab V2,
  HULK Operations, and the release-like benchmark contract.

## 2. Standalone bounded task packet

A substantive task starts from a standalone packet that a fresh executor can execute without hidden
chat context. The packet states, as applicable:

- task identity, task type and goal;
- repository, official branch, exact expected official HEAD, and expected overlap;
- executor/model/agent constraints when the owner pins them;
- authorized write scope: exact files, directories, branches, worktrees or device actions;
- explicit out-of-scope list;
- validation expected to prove the change;
- physical state matrix, or `NONE`;
- capture authorization for the current task, default OFF when the packet does not state it;
- stop conditions;
- required completion report and allowed verdicts;
- secret-handling constraints.

If the packet lacks fields required to execute safely, the executor does not guess. It records what
is missing. First resolve routine discoverable facts by safe live inspection; do not ask the owner
to supply facts already accessible on GitHub/VPS/devices. Return `BLOCKED` for a missing owner
decision or external dependency, or `STOP` for a contract/live-state conflict.

Read `docs/android-engineering-lab/DEEPSEEK-PROMPT-CONTRACT.md` explicitly for the adopted English
packet method, NEW/RESUME continuity, exact task delta, preservation and completion fields. It does
not replace this execution contract or add permission. Historical examples that deferred tests or
Git actions are not standing deferrals for a new task.

## 3. Live verification before mutation

Before any mutation, and again before commit and push, the executor verifies live:

- canonical clone status and task worktree status;
- local branch and local HEAD;
- remote official branch and remote HEAD, freshly fetched;
- exact expected SHA equality when the packet pins one;
- open PR overlap for the same atomic problem;
- PR state, base, head branch and head revision when the task is a PR correction;
- the applicable repository contracts for the subsystem in scope.

If the expected SHA does not match, if an overlapping PR exists without authorization, if unrelated
changes cannot be separated safely, or if the applicable remote HEAD changes during work: `STOP`
before further mutation. Safe reading, inspection, diagnosis and evidence gathering may continue.

The intended diff (files and purpose) is recorded before editing.

## 4. Ownership, root cause and extension path

For a bug or regression, before editing the executor establishes:

1. actual behavior;
2. evidence proving or strongly supporting the root cause;
3. the authoritative owner when ownership matters;
4. why the proposed change fixes the cause rather than the symptom.

For a feature, the executor identifies the existing contract, owner and extension path instead of
inventing a root cause. If competing ownership is the defect, ownership is fixed rather than layering
duplicated mutable state or a workaround over it.

Findings are classified as Fact, Inference, or Assumption. Insufficient evidence means diagnose
only; no speculative fix.

## 5. One atomic problem per branch and PR

- One confirmed atomic problem = one task branch, one PR.
- A correction continues on the current PR HEAD; do not rebase, rewrite, retarget or restart it
  unless explicitly authorized.
- Do not combine independent problems, and do not turn a targeted fix into a general refactor.
- Minimize complexity, surface area, changed files, new mutable state, new abstractions and new
  dependencies (YAGNI).
- Reuse existing patterns, owners, navigation and focus behavior unless they are the demonstrated
  cause.

## 6. Owner steering and cancellation

- A new owner/user instruction updates the current task; already-valid work is preserved when safe.
- After a material change to source, scope, branch, PR or authority, the affected repository state is
  revalidated before further mutation.
- On cancellation, the executor stops mutation immediately, does not commit or push partial work
  unless authorized, records the exact state and reason, and reports `CLOSED` with what exists
  (worktree, branch, commits, PR, uncommitted changes).
- A cancelled or superseded task is never recorded as PASS, and its abandoned work is not silently
  absorbed by another task.
- No actor may modify, close, reuse or push to another task's branch or PR without explicit
  authorization.

## 7. Complete diff self-review

Review the complete diff against the correct base before building and again after the final
correction:

- scope: only authorized files and behavior changed;
- ownership: state, callbacks, stable identity/keys, effects, lifecycle, cancellation;
- accidental edits, formatting churn, generated files, and debug/temp/dead/speculative code;
- the staged diff and the final tree (`git status`, full diff, staged diff);
- RTL, focus, adaptive behavior and regression surface where UI is touched.

The primary executor owns this review even when subagents performed analysis. Do not weaken tests or
acceptance criteria to obtain a pass.

## 8. Focused validation

- Run the smallest test that proves the changed contract first, then the affected regression set.
- Add a regression test when it meaningfully proves the behavior without overfitting to
  implementation details or unstable timing.
- Never claim a build, Gradle task, unit test, emulator test, instrumentation test, device action or
  CI result that did not actually run.
- Record each validation as PASS, FAIL, BLOCKED or NOT RUN, with the exact command where practical.
- Distinguish failures caused by the current diff from baseline, infrastructure and flakiness; do not
  fix unrelated failures.
- CI is final verification, not a development sandbox.

## 9. Build and artifact provenance

When a build applies:

- build from the exact task worktree revision and record clean/dirty state;
- record the exact build command and variant;
- record artifact SHA-256, package, version identity and signer evidence where applicable;
- a dirty iteration build is not exact-HEAD evidence;
- final evidence binds to the immutable final source revision;
- if a final commit is made after the build, retain the build only when recorded tree/input hashes
  prove its exact build inputs match that final revision. Keep the original dirty-build attribution;
  a later commit never retroactively makes it a clean-HEAD build. Rebuild when affected inputs differ;
- never present debug, benchmark or lab artifacts as production release artifacts.

## 10. Physical qualification

Physical work follows the live physical contracts. The standing implementation-round refresh
obligations in `AGENTS.md` section 15 and `AGENTS.override.md` apply by task type unless the active
packet explicitly forbids or bounds device contact; no actor may exceed that boundary.

- Persistent instance identity, approved surfaces, refresh procedure, qualification version values
  and data-preservation rules are owned by `AGENTS.override.md` and
  `docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`. This contract intentionally does
  not restate package names, devices or version values.
- Never uninstall, run `pm clear`, wipe data, downgrade, change signer, or manufacture clean state on
  a persistent package.
- Verify package identity, qualification version and signer continuity before installation; install
  only by the state-preserving method the physical contract defines.
- If a required surface is unreachable, report that validation as BLOCKED or NOT RUN; never silently
  substitute another device/package and never destroy state to recover reachability.
- Audit, Review and Diagnosis rounds do not rebuild or reinstall persistent packages.

## 11. Bounded runtime verification and owner visual review

For visible or device-specific changes, compiling is not completion. Runtime verification is
bounded to the active task, its capture policy, and the live physical contracts:

1. freeze the candidate source revision;
2. build the correct variant from that revision;
3. refresh/install per the live physical contract only when the task authorizes device contact;
4. perform or record only the interaction needed to verify the changed technical contract;
5. verify launch, crash-free operation and the behavior signals available without screen capture;
6. leave visual review to the owner on the actual devices.

Do not add a full screen/device census or visual journey to a bounded correction. The task's
approved state matrix selects runtime coverage; an unavailable mandatory surface remains BLOCKED
or NOT RUN. Work involving documentation only has no Android build/refresh obligation.

Capture policy (default OFF):

- Screenshots, screen recordings, transition-frame capture, frame extraction, montage generation
  and screenshot comparison are OFF by default.
- They are enabled only when the owner explicitly requests capture for the current task.
- Capture authorization is never inferred from a UI change, ADB access, device installation, or
  the existence of capture tools.
- Without capture authorization, do not run the capture → open images → compare → fix → rebuild →
  recapture loop, and do not block a routine task solely because captures are missing.
- Report automated visual validation as `NOT RUN — CAPTURE NOT AUTHORIZED`. Record owner visual
  review separately as PENDING, ACCEPTED or REJECTED for the exact candidate/scope; attribute
  acceptance only after the owner actually confirms it.
- When capture is explicitly authorized, a capture file existing is not proof of inspection, and
  captures are compared against the approved specification before any visual claim.

A passing build is not proof of visual correctness or universal device coverage. Screenshot
baselines are never created, accepted or updated automatically; owner review is required before a
new baseline becomes accepted.

## 12. Final source freeze and stale evidence

- Once final evidence is recorded, the source revision used is the frozen final revision.
- Any later source edit invalidates the affected evidence; the affected evidence must be
  regenerated or re-validated on the new revision before any completion claim.
- Recapture is required only when the task's capture policy is ON. When capture policy is OFF,
  earlier captures are not refreshed automatically. Owner visual review is PENDING for a new
  candidate until an actual owner decision; a decision on an older candidate is not copied forward.
- Evidence is valid only for the exact immutable revision and dirty state recorded with it.
- Do not mix artifacts, captures or build results from different revisions in one evidence set.
- The final diff reviewed for completion is the frozen revision's diff.

## 13. Durable task records

Every substantive task produces a durable packet outside the repository, normally:

`/srv/hulk-android/reports/YYYY/<task-id>/`

Minimum retained files:

- `TASK.md` — the exact approved packet; no credentials;
- `TASK.env` — sanitized live fields: verified HEADs, branch/worktree, overlap check,
  executor/model/agent configuration, device state matrix, freeze and baseline authority;
- `RESULT.md` — factual result with the final verdict, evidence summary, validations actually run
  and not run, and residual risk;
- `MANIFEST.sha256` — hashes of the packet's durable artifacts.

Runtime evidence (diffs, captures, logs, manifests) follows
`docs/android-engineering-lab/README.md` section 8 and the established packet/evidence convention,
including per-artifact `source_sha`, build/package/device context, and specific findings, reviewer
and timestamp. Capture hashes and image-open records apply only to captures the owner explicitly
authorized for the task.

No raw credentials, tokens, access codes, account secrets, signing material, IPTV credentials or
private account data may appear in any packet, log, capture or PR text.

## 14. Actor attribution and evidence integrity

- Every claimed command, build, test, capture, inspection and review is attributed to the actor that
  actually performed it.
- No actor may claim another actor's unexecuted work as completed evidence.
- Never invent, extrapolate or copy forward a result. A missing mandatory item stays NOT RUN or
  BLOCKED.
- Findings are separated into Fact, Inference and Assumption.
- Subagents may assist with independent read-only analysis; the primary executor owns root-cause
  judgment, scope, conflict resolution, final diff review and the pre-mutation safety check.

## 15. Completion and PR boundary

- Technical completion means: the frozen diff is reviewed, required validation ran, required
  physical/technical evidence exists, the durable packet is written, and the authorized branch/PR
  is updated with final configured CI results. Owner visual acceptance is not a condition of
  technical completion; report it separately without treating missing unauthorized captures as
  a technical blocker.
- Unless the current owner scope narrows Git actions, finish the standing implementation workflow:
  one coherent commit per correction round, normal fast-forward push and one PR create/update.
  Do not commit while applicable task Actions are queued/running. A push, pending CI or failed
  mandatory check is not completion. Inspect failed logs/artifacts, identify the first causal
  failure and correct only an in-scope defect in the same task/PR; continue until final checks or
  a real BLOCKED/STOP condition. Do not bypass checks, fix unrelated failures or rewrite history.
- If path filters do not trigger CI, report CI as NOT RUN with that reason and record the relevant
  local checks. Do not start Android builds or rerun successful workflows merely for a docs-only PR.
- The PR/report is the boundary. Merge, signing, tag, release, production deployment and device data
  changes are separate protected operations requiring explicit owner authorization.
- A completed task is not a merge recommendation and does not imply production readiness.
- Report verified facts only: problem/root cause, evidence, files changed, branch/commit/PR, build
  and CI results, tests actually run and not run, remaining physical verification, residual risk and
  the verdict.

## 16. Verdicts

Use exactly one final verdict:

- `PASS` — all mandatory technical criteria have evidence and pass on the frozen revision; the
  PR/report is review-ready. `PASS` is technical completion and is not owner visual acceptance,
  universal device coverage, or production readiness; owner visual review is reported separately
  as PENDING, ACCEPTED or REJECTED for the reviewed candidate/scope.
- `FAIL` — the task executed, but a mandatory criterion failed or the diff is incorrect; correction
  is required.
- `BLOCKED` — execution cannot proceed or complete because of an external dependency or owner
  decision (for example an unreachable approved surface or withheld authority); no contract
  conflict.
- `STOP` — a live-state, authority or contract conflict halted mutation (expected-HEAD mismatch,
  overlapping PR, remote HEAD change, protected-contract conflict, or an `AGENTS.md` stop
  condition); mutation stays halted until resolved.
- `CLOSED` — the owner cancelled or superseded the task; recorded with the exact state and reason,
  without a false PASS or FAIL.

A verdict is never upgraded by optimism, partial evidence, or a passing build alone.

## 17. Relationship to execution aids

`.opencode/skills/hulk-runtime-acceptance/SKILL.md` is an executor aid for visual implementation.
It grants no authority and is subordinate to root `AGENTS.md`, the active task packet and this
contract; it cannot widen scope or weaken evidence rules. Skills and helpers may change
independently; this contract remains model-neutral and binding on their use.

## Final principle

The current repository defines live truth. Evidence determines root cause. The frozen revision
determines what can be claimed. Scope determines what may change.
