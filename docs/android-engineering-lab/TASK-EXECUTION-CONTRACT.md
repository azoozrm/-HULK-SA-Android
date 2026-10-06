# HULK SA Android — Permanent Task Execution Contract

Revised 2026-10-06 · local iteration and accepted-section publication.

This is the model-neutral execution contract for substantive authorized Implementation/Correction
work. It owns execution phases and completion boundaries; it grants no authority. Read root
`AGENTS.md`, applicable override and current task packet first. Read referenced contracts explicitly.

## 1. Authority and scope

Current owner instructions define the result and permitted source, checks, device and Git actions.
No contract, skill, model or historical prompt can expand them. OpenCode/DeepSeek is the normal
executor; ChatGPT/Sol coordinates and independently reviews. Audit/Review/Diagnosis stay read-only
unless specifically authorized. Model capability does not expand scope or risk.

More specific live contracts own their subject: lab resources, physical packages/state, compatibility,
Operations, player/download ownership and release qualification. This file does not duplicate their
changing package/version/device values. Capture and protected-action boundaries remain unchanged.

## 2. Two explicit phases

| Phase | Work and completion boundary | Git/CI |
| --- | --- | --- |
| LOCAL_ROUND (default for iterative section work) | Small source correction, relevant local checks, applicable state-preserving builds/install, bounded verification, durable evidence and owner review | No automatic commit, push, PR create/update or CI |
| SECTION_PUBLISH | Review accumulated accepted scope, final relevant regression gates, immutable source/artifact binding, one coherent publication, same section PR and configured CI conclusion | Normal commit/fast-forward push/one PR only under current publication authority |

LOCAL_ROUND retains successful local changes in the same task worktree/branch. Do not create a new
branch/PR or run the full publication pipeline for each small fix. Local technical PASS means that
round's mandatory criteria have evidence, not that a PR exists or owner visual acceptance is complete.

The owner’s acceptance/completion instruction for the exact section activates the standing authorized
publication workflow when that authority is already in session. Do not ask again for authorized steps.
Without that decision, finish local work/evidence and report its review boundary; do not publish early.
A later instruction may explicitly allow earlier Git publication or narrow checks.

An explicitly requested standalone documentation/governance task may use SECTION_PUBLISH directly,
with one branch/PR and no Android builds or device contact. Changes remain documentation-only.

## 3. Standalone task packet

Follow `DEEPSEEK-PROMPT-CONTRACT.md`. A packet identifies:
- NEW or RESUME, task/section/round and LOCAL_ROUND or SECTION_PUBLISH;
- owner-observed problem/result and exact element/property or inseparable behavior;
- repository, verified official/base and relevant PR identities;
- authorized scope, protected accepted work and any superseded instructions;
- candidate/evidence identity and current local/remote/dirty state;
- focused checks and relevant regression criteria;
- affected approved physical surfaces or NONE, and capture OFF unless explicitly enabled;
- publication trigger/current authority, stop conditions and completion boundary.

Resolve discoverable facts through safe current inspection. Do not invent SHAs, paths, package state,
measurements or results, or ask the owner for accessible evidence. A missing owner decision/external
dependency is BLOCKED; a live-state/authority conflict is STOP. No unfilled task template is sent.

For UI work explicitly read `../design/shared-ui/HULK-SHARED-UI-DESIGN-CONTRACT.md` and the relevant
accepted source/boards. All substantive work explicitly reads `DEEPSEEK-EVIDENCE-CONTRACT.md`.

## 4. Live verification before mutation

Verify canonical/task checkout status, local branch/HEAD, remote official HEAD, exact pinned SHA,
open-task/PR overlap and applicable live contracts. A PR correction also verifies state/base/head.
Record intended files/purpose before editing. Recheck relevant live state before commit/push.

If pinned HEAD mismatches, applicable remote changes, unrelated overlapping edits cannot be separated,
an unapproved PR owns the same work, or a protected contract would change without authority: STOP
before mutation. Reading/diagnosis may continue. Do not reset, rebase or absorb another actor's work.

For a resumed local section, uncommitted changes are expected: identify them by the previous round's
candidate manifest/diff and preserve valid accepted work. Do not require a clean checkout, discard
changes or silently start from the official branch merely because no commit/PR exists.

## 5. Ownership and bounded implementation

For a defect, establish actual behavior, source/evidence-backed cause, authoritative owner and why the
change corrects it. For a feature, identify the existing contract/owner/extension path instead.
Separate Fact, Inference and Assumption; insufficient evidence means diagnosis, not speculative code.

Keep one atomic problem or explicitly owner-defined section bundle on one task branch and one PR.
Within that accepted section scope, related corrections remain local rounds on the same branch.
Do not accumulate unrelated refactors, independent products or new capabilities under that label.

Make the smallest maintainable correction; reuse existing owners/components and preserve shared
defaults. No fake data, timing sleeps, duplicated state, new dependencies or broad architecture change
without demonstrated need and authorized scope.

## 6. Owner steering, pause and cancellation

Steering updates the active task while preserving valid work/evidence. Revalidate affected state after
material changes. Preserve accepted neighbors; supersede only conflicting requirements.

Pausing for owner review leaves the complete local candidate/evidence available; it is not permission
to commit/push or clear the worktree. An accepted part does not automatically accept all remaining work.

Cancellation stops mutation and records exact local/branch/PR state as CLOSED. No silent commit,
publish, destructive cleanup or absorption of abandoned work. Do not touch another task's ownership.

## 7. Complete diff review

Review the current-round delta AND accumulated section diff before builds and completion.
Check scope, callbacks, stable identity, state/effects/lifecycle/cancellation, RTL/adaptive/focus,
new/untracked source, generated/temp/dead code, unrelated edits and staged/unstaged state.
At publication review the full final/staged diff and tree against the verified section base.
The primary executor owns this review; helpers cannot expand scope or weaken acceptance criteria.

## 8. Proportionate local validation

Run the smallest check that proves the changed contract, then affected regressions. A behavioral
regression test must prove meaningful behavior, not mirror the implementation. Do not add tests for
every reversible visual detail or run the entire suite after each unchanged small layout correction.

A local Android round must still establish that its actual source can build safely and that relevant
changed behavior is verified before installing. Use the applicable focused test/compile/static checks
for the task, then required Preview/Benchmark assembly. Record what ran and what was deferred.

Reuse valid unchanged evidence; broaden/repeat checks only for new changes, failures or unresolved
concerns. Full applicable unit/lint/Compatibility/static gates belong at section publication unless
a changed contract or current packet requires them earlier. Current explicit task deferrals remain
bounded to that task; the local workflow is not a permanent exemption from final regression gates.

Record PASS/FAIL/BLOCKED/NOT RUN and actual commands. Classify baseline/infrastructure/flaky/current
diff failures. Fix only in-scope causal defects; do not weaken tests, hide failures or push to diagnose.

## 9. Freeze and local candidate provenance

Freeze each candidate before evidence/build/install. For an uncommitted round retain base HEAD,
dirty state, reviewed diff/new-file inventory, SOURCE-MANIFEST.sha256 and CANDIDATE.json.
The candidate ID identifies the frozen content; HEAD alone never identifies a dirty candidate.

Build from that exact worktree content. Record command, inputs/options, variant, artifact hash and
applicable package/version/signer. Outputs/caches/secrets are not source inventory. No source edits
during a frozen build/verification set. Later edits invalidate affected evidence.

Final publication may reuse a local APK/check only when complete material input/option hashes prove
it matches the committed source. Preserve its original dirty-build attribution. Verify all authorized
new build inputs as well as tracked inputs. Rebuild affected outputs when any input changed.
Documentation-only commits do not force identical Android APKs to be rebuilt/reinstalled.

Never present lab/Preview/Benchmark artifacts as signed production-release qualification.

## 10. Physical refresh and bounded runtime

Follow `AGENTS.override.md` and `PHYSICAL-ENGINEERING-INSTANCES.md` for package identity, current
qualification overrides, approved surfaces, dual refresh and state preservation. Local candidates
are valid refresh inputs when frozen/proven; a commit or PR is not a prerequisite.

Do not install after required validation/build failure or replace the last known-good copy.
Verify package/version/signer and record hashes before state-preserving install; launch and verify
no immediate crash/stale update overlay, preserving established authentication/data.

Never uninstall, pm clear, wipe, downgrade, change signer or manufacture clean persistent state.
An unreachable required surface remains BLOCKED/NOT RUN; no silent substitute device/package.
Audit/Review/Diagnosis and documentation-only tasks do not rebuild/reinstall persistent packages.

Bound runtime to changed technical criteria on the selected authorized surfaces. No full device census
or lengthy visual journey for every correction. Use actual non-capture behavior signals and retain
precise limits. Source/unit/build success is not device behavior or universal window PASS.

## 11. Capture and owner review

Screenshots, recordings, transition frames, extraction, montage and screenshot comparisons are OFF
unless explicitly requested for the current task. UI changes, ADB access, installation and missing
captures do not grant capture permission.

With capture OFF, no capture/open/compare/rebuild loop. Record automated visual validation as
NOT RUN — CAPTURE NOT AUTHORIZED; missing unauthorized captures alone is not a blocker.
Owner visual/behavior review is separate: PENDING until an actual exact-candidate decision,
then ACCEPTED or REJECTED with reviewed scope. Do not copy acceptance to a later changed candidate.

Authorized capture requirements need actual bytes, hashes and inspection findings. A file/path is not
proof of image inspection. Screenshot baselines are not automatically created/accepted/updated.
Changing network/display/font settings or unlocking devices requires applicable separate authority.

## 12. Evidence after every local round

Follow `DEEPSEEK-EVIDENCE-CONTRACT.md` for canonical originals, minimum records, candidate/APK/state
binding, complete downloadable ZIP, external SHA-256, archive/manifest verification, bounded hulkapp
read/traverse access and final filename/path/size/hash handoff.

Retain focused round records and append owner decisions; do not overwrite earlier results.
Missing/inaccessible evidence remains explicit. No actor may claim another actor's unexecuted work.
Protect accepted/open-task evidence; do not erase it to save build space.
No secrets/private account material in source, prompts, reports, fixtures, logs, archives or PRs.

## 13. Accepted-section publication

SECTION_PUBLISH proceeds after the owner accepted the exact section scope or expressly requested a
standalone publication task. Recheck remote/overlap/local state and freeze accumulated source.
Review accepted/rejected/pending decisions and ensure rejected work is corrected, not absorbed.

Run final gates appropriate to the accumulated diff:
- Android behavior/source: applicable full unit/regression, lint/compilation and Compatibility V2/static
  validation, and the required artifact/physical provenance verification;
- documentation-only: full documentation diff, references, scope/consistency, evidence/archive checks
  and relevant repository documentation checks; Android builds/devices are NOT APPLICABLE.

Do not hard-code historical test counts or claim old runs validate new inputs. Retain valid evidence
where exact input equality is proven. Any final correction creates a new candidate and requires
affected verification and owner review when it changes accepted visible behavior.

After final local gates, one coherent section commit, normal fast-forward push and one section PR.
No experimental commits, automatic per-round push/CI, direct official-branch push or history rewrite.
Do not commit while applicable task CI is queued/running. Check remote HEAD again before push.

Follow triggered configured checks to final conclusion; inspect the first causal failure and correct
only an in-scope defect in the same task/PR. Pending/failed mandatory CI is not publication completion.
No bypass, unneeded reruns or unrelated fixes. Path-filtered CI is NOT RUN with its actual reason;
do not trigger Android workflows for a docs-only task solely to manufacture a green badge.

Technical publication, owner visual acceptance and protected merge/release remain separate.
Merge, signing, tags, release, production deployment and device data changes require explicit authority.
Section acceptance plus publication is not universal device or release readiness.

## 14. Verdicts and reporting

Use one technical verdict with the declared phase:
- PASS: all mandatory criteria for that phase pass on the frozen candidate; LOCAL_ROUND does not
  claim a commit/PR/CI, and SECTION_PUBLISH includes its required final Git/CI boundary.
- FAIL: an executed mandatory criterion failed or the diff is incorrect.
- BLOCKED: an external dependency/missing owner decision prevents the required work.
- STOP: live-state, authority, overlap or repository-contract conflict halts mutation.
- CLOSED: owner cancelled/superseded; record exact state, not a false PASS.

Report cause/uncertainty, changed files, phase/candidate/base/dirty identity, actual checks/build/install,
archive handoff, owner decision, publication status, limits and next boundary.
Distinguish local technical verification, owner acceptance, Git publication and release qualification.

## 15. Execution aids

`.opencode/skills/hulk-runtime-acceptance/SKILL.md` and other aids remain subordinate to the active
packet and live contracts. Their historical commit/push wording does not widen LOCAL_ROUND into
SECTION_PUBLISH. Models/configuration are not changed by this contract.

## Final principle

Accepted source is preserved. Frozen inputs determine valid evidence. Local rounds remain local;
accepted sections publish once under current authority. Scope determines what may change.
