# HULK SA Android Engineering Lab

This document is the repository contract for the permanent HULK SA Android engineering lab.
Repository authority, Git mutation rules, protected product contracts, testing rules and stop
conditions remain in the root `AGENTS.md`. This contract owns only lab execution, resource,
filesystem, evidence and rebuild behavior.

## 1. Current qualified architecture

- Permanent provider class: Contabo Cloud VPS 4.
- Qualified lab mode: **Mode B — Hybrid Lab**.
- Engineering host identity: `hulksa-android-lab-01`.
- Engineering user: `hulkapp`.
- Canonical repository: `/srv/hulk-android/repos/HULK-SA-Android`.
- Task worktrees: `/srv/hulk-android/worktrees/<task-id>`.
- Reports: `/srv/hulk-android/reports/YYYY/<task-id>`.
- Evidence: `/srv/hulk-android/evidence/`.
- Artifacts: `/srv/hulk-android/artifacts/`.
- Android SDK: `/opt/android-sdk`.
- OpenCode releases: `/opt/opencode/<version>`.

Mode B is selected because the Contabo guest does not expose nested KVM to the guest:
`/dev/kvm` is absent and CPU virtualization flags are not passed through. This does not block
source work, builds, static validation, unit tests, lint, APK generation, reports, evidence
processing, GitHub operations, OpenCode or DeepSeek.

Do not install Android system images merely to prove slow software emulation can boot. Runtime or
emulator qualification must use an owner-approved external execution surface when the active task
actually requires it.

## 2. Authority and actors

The current user instruction defines the task goal, task type, scope, expected SHA/PR constraints
and protected-action authority.

- ChatGPT / Sol controls scope, verifies live GitHub state, issues the bounded task packet, reviews
  diffs/evidence/results and decides the next action.
- OpenCode / DeepSeek executes the authorized task on the lab and must not widen scope or bypass a
  stop condition.
- The owner supplies credentials interactively and explicitly authorizes protected operations such
  as merge, signing, tag and release.
- Repository contracts remain authoritative for product and Git behavior.

No actor may claim another actor's unexecuted work as completed evidence.

## 3. Canonical clone and task worktrees

The canonical clone is a clean source anchor, not a task-editing workspace.

For a new implementation task:

1. Verify the current remote official HEAD.
2. Verify the canonical clone is clean.
3. Verify no open PR already owns the same atomic problem.
4. Create one task worktree from the exact verified official HEAD.
5. Run OpenCode / DeepSeek only inside that task worktree.
6. Keep one atomic problem on one branch and one PR.
7. Before commit or push, re-read the applicable remote HEAD and apply `AGENTS.md` stop rules.

For an existing PR correction, start from the verified current PR HEAD instead of the official
branch. Do not rebase or rewrite history unless explicitly authorized.

Qualified helper commands on the lab:

- `hulk-task-new <task-id> <branch> <expected-head>`
- `hulk-task-status [task-id]`
- `hulk-task-close <task-id>`

Versioned source for these helpers lives under `ops/android-engineering-lab/`.

## 4. GitHub and credentials

Git uses a machine-specific SSH key. GitHub CLI uses its own authorized GitHub credential when API
or PR operations are in scope.

Credentials are owner-entered and must never appear in source, task prompts, shell history, reports,
evidence archives, commits or PR text.

The canonical repository remote uses SSH. Direct push to the official branch is locally guarded and
remains prohibited by repository policy even if credentials technically permit it.

## 5. OpenCode and DeepSeek

OpenCode is installed as a pinned standalone release. DeepSeek inference is remote through the
configured provider credential; the VPS does not host the model.

Do not run OpenCode `/init` in this repository. The repository already has authoritative
`AGENTS.md` instructions.

A normal repository task starts with:

1. the current task packet;
2. root `AGENTS.md`;
3. live Git state and applicable remote HEAD;
4. this lab contract only when lab/runtime/maintenance behavior is relevant;
5. subsystem contracts only when the active task enters that subsystem.

## 6. Resource policy

The VPS is intentionally serialized.

- One heavy workload at a time.
- Build wrapper uses the global heavy-work lock.
- Do not run parallel Gradle builds.
- Build scope target ceiling: MemoryHigh 4 GiB, MemoryMax 5.5 GiB, CPUQuota 350%.
- OpenCode scope ceiling as currently configured by `hulk-opencode-run`: MemoryHigh 1 GiB,
  MemoryMax 1.5 GiB, with no CPU quota on the OpenCode scope.
- Start qualified Gradle runs with at most two workers when lab resource pressure matters.
- Swap is an OOM safety net, not performance capacity. Sustained swap over 1 GiB is a warning;
  over 2 GiB invalidates performance conclusions and requires resource review.

Disk gates:

- warning at 75% used or below 25 GiB free;
- block new images at 80% used or below 20 GiB free;
- block new heavy work at 85% used or below 15 GiB free;
- critical cleanup at 90% used or below 8 GiB free;
- stop lab work at 95% used.

## 7. Qualified local validation surface

Contabo is qualified for:

- repository fetch/branch/worktree operations;
- Python Compatibility V2 tests;
- static validation;
- Gradle compilation;
- unit tests;
- lint when requested by the task;
- debug APK assembly;
- checksums, reports and evidence processing;
- GitHub PR/API operations when authorized.

The initial clean baseline proved:

- Compatibility Python tests: 177 PASS;
- static validation: 27/27 PASS;
- `:app:testDebugUnitTest`: PASS;
- `:app:assembleDebug`: PASS.

These baseline facts do not prove future diffs. Every task must run the validation required by its
own changed contract.

Local accelerated Android Emulator runtime is unavailable in Mode B. Emulator, instrumentation,
TV/player physical behavior and other runtime-only claims remain NOT RUN/BLOCKED unless actual
approved runtime evidence exists.

## 8. Reports and evidence

Every substantive task should produce a durable result packet outside the repository, normally:

`/srv/hulk-android/reports/YYYY/<task-id>/RESULT.md`

with a `MANIFEST.sha256`.

A result packet records, as applicable:

- task identity/type and timestamps;
- verified repository/branch/HEAD;
- worktree/branch;
- authority and scope;
- commands actually run;
- validations actually run and their PASS/FAIL/BLOCKED/SKIPPED state;
- validations not run and why;
- findings separated into fact/inference/assumption;
- changed files/diff/commit/push/PR when authorized;
- artifacts with SHA-256;
- failures/stop conditions;
- residual risk and next action.

Never invent a test result. Never place raw secrets in reports.

## 9. Retention and cleanup

Reports and SHA manifests are durable and never auto-deleted. Evidence linked to an open PR,
release or security incident is protected until explicit closure.

Cleanup must refuse directories marked `KEEP`, `OPEN-PR`, `SECURITY-HOLD` or covered by an
active task lock.

Caches, build outputs and ephemeral run data may be cleaned according to the lab retention policy,
but source, reports, protected evidence and secrets are never sacrificed to clear disk pressure.

## 10. Rebuild and machine drift

Non-secret reproducibility artifacts are versioned under `ops/android-engineering-lab/`.
Machine-specific credentials, IP addresses, host fingerprints, caches, live process IDs, active
locks and transient worktree state are never versioned.

Use `ops/android-engineering-lab/lab-health.sh` to compare the machine against the expected lab
shape. A future clean-rebuild qualification may expand the versioned bootstrap set, but it must not
retroactively claim an untested rebuild path.

## 11. Completion boundary

The lab is ready for normal Android engineering work when:

- canonical clone is clean;
- Git SSH and GitHub CLI are authenticated;
- OpenCode and DeepSeek are available;
- task worktree lifecycle is proven;
- local build/static/unit baseline is proven;
- resource/disk gates are healthy;
- Mode B limitation is recorded accurately.

Runtime evidence remains surface-specific. Missing local KVM must never be converted into a false
local-emulator PASS.
