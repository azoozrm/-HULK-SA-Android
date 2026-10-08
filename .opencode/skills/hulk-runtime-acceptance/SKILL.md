---
name: hulk-runtime-acceptance
description: Use during HULK SA Android visual implementation and before claiming completion, to bind the approved screen specification to final-source tests, bounded runtime verification, and an honest evidence verdict. Screen capture is OFF unless the owner explicitly authorizes it for the current task.
---

# HULK runtime acceptance

This skill grants no authority. Read the applicable live AGENTS.md,
AGENTS.override.md, engineering-lab README, physical-instance contract,
TASK-EXECUTION-CONTRACT.md, DEEPSEEK-PROMPT-CONTRACT.md,
DEEPSEEK-EVIDENCE-CONTRACT.md, subsystem contracts,
and the current task packet. Read referenced contracts explicitly from
`docs/android-engineering-lab/`; a filename does not load its contents.
They govern execution. For UI, explicitly read
`docs/design/shared-ui/HULK-SHARED-UI-DESIGN-CONTRACT.md` and its active source reference.
Read the declared LOCAL_ROUND or SECTION_PUBLISH phase; this aid cannot activate publication.

## Start
1. Verify all task live fields, task worktree, branch, base HEAD, overlapping
   ownership, action authority, device availability, and required state matrix.
2. Trace each screen through the actual router to its source owner.
3. Identify existing state, callback, stable identity, and focus owners.
4. Record the intended presentation diff before editing.
5. Preserve behavior. Replace the approved visible composition.

## Review source
Review the complete diff before building and after the final correction.
Check scope, state ownership, keys, callbacks, Compose effects, RTL, focus,
adaptive behavior, temporary code, and accidental unrelated edits.
Do not weaken tests or acceptance criteria to obtain a pass.

## Validate
Run the task's focused tests, affected regressions, compilation, and applicable
static/Compatibility V2 checks through the live serialized lab workflow.
Do not run data-clearing instrumentation against persistent owner packages.
Do not change baseline images without explicit baseline approval.

## Bind source to runtime
Record candidate ID, base HEAD, clean/dirty state, source-manifest/diff hashes,
build command, material inputs/options, APK hash, package, version,
signer verification, device identity, and installation result.
A dirty iteration build is not an exact-HEAD build.
Bind final evidence to the immutable frozen candidate; an uncommitted LOCAL_ROUND is valid.
At publication prove material input/option equality to the committed tree and retain the original
dirty-build attribution, or rebuild affected outputs. A later commit does not relabel an old build.
Follow the live physical-instance contract; never improvise package refresh.

## Inspect
Capture policy, default OFF: screenshots, screen recordings, transition-frame
capture, frame extraction, montage generation and screenshot comparison are
enabled only when the owner explicitly requests capture for the current task.
Never infer capture authorization from a UI change, ADB access, device
installation, or the existence of capture tools; ADB use alone is not capture
authorization.

Without capture authorization:
- do not run the capture → open images → compare → fix → rebuild → recapture loop;
- do not block a routine task only because captures are missing;
- report automated visual validation as `NOT RUN — CAPTURE NOT AUTHORIZED`;
- leave visual review to the owner on the actual devices; record PENDING,
  ACCEPTED or REJECTED separately for the exact candidate/scope.

When capture is explicitly authorized, for every required screen/state:
- perform the recorded interaction;
- capture the full screen and relevant transition frames;
- open the actual image attachments;
- compare them with the approved composition;
- record specific visible findings;
- fix in-scope defects and repeat build/refresh/capture/inspection.

For states selected by the task, review the applicable source and authorized
runtime evidence. Capture-based inspection applies only when capture is ON.
Check composition, hierarchy, spacing, typography, artwork, control placement,
categories, focus, selected-versus-focused, RTL, long Arabic/Latin/mixed text,
clipping, safe edges, loading, empty, error, missing artwork, Back/return,
deep scroll, and required adaptive states.

A capture file existing is not proof of inspection.
A passing build is not proof of visual correctness or universal device coverage.
Owner acceptance is attributed only after the owner actually confirms it.

## Evidence
For each artifact record:
criterion/state ID; source SHA; APK SHA-256; package/variant; device/window;
locale/font scale; interaction; result; reviewer; timestamp. Capture path and
SHA-256 plus image-open record apply only to captures the owner explicitly
authorized; otherwise record automated visual validation as
`NOT RUN — CAPTURE NOT AUTHORIZED`.
Protect credentials and owner-private data.

## Finish
Re-read the task packet. Recheck remote HEAD and the final complete diff.
Every mandatory criterion must have evidence.
Missing captures alone do not block a routine task whose capture policy is OFF;
report its owner visual review separately: default PENDING until reviewed;
ACCEPTED or REJECTED requires an actual owner decision for the exact candidate/scope.
Under an authorized capture policy, missing required capture states remain
BLOCKED or NOT RUN.
Report PASS only when all mandatory technical criteria pass; PASS is technical
completion, not owner visual acceptance or universal device coverage.
Otherwise report FAIL, BLOCKED, or STOP with the exact reason.
Never declare merge, release, or production readiness from this skill.
Complete the declared phase under TASK-EXECUTION-CONTRACT:
- LOCAL_ROUND (default): finish relevant correction/check/build/approved refresh,
  bounded verification and durable evidence for owner review in the same task/worktree.
  Preserve successful work; no automatic commit, push, PR create/update or CI per correction.
- SECTION_PUBLISH: only with current publication authority after exact-section acceptance/
  completion, or an explicitly requested standalone publication. Run final applicable gates,
  freeze/bind source, make one coherent commit/normal push/one PR and follow configured CI
  to final. Path-filtered CI is NOT RUN; docs-only work needs no Android build/install.
Diagnose and fix only in-scope failures without bypassing checks or expanding scope;
honor real blockers and STOP conditions. Report owner visual acceptance separately.
