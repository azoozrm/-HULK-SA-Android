---
name: hulk-runtime-acceptance
description: Use during HULK SA Android visual implementation and before claiming completion, to bind the approved screen specification to final-source tests, physical captures, visual inspection, and an honest evidence verdict.
---

# HULK runtime acceptance

This skill grants no authority. Read the applicable live AGENTS.md,
AGENTS.override.md, engineering-lab README, physical-instance contract,
subsystem contracts, and the current task packet. They govern execution.

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
Record source SHA and dirty state, build command, APK hash, package, version,
signer verification, device identity, and installation result.
A dirty iteration build is not an exact-HEAD build.
Final evidence must use the immutable final source revision.
Follow the live physical-instance contract; never improvise package refresh.

## Inspect
For every required screen/state:
- perform the recorded interaction;
- capture the full screen and relevant transition frames;
- open the actual image attachments;
- compare them with the approved composition;
- record specific visible findings;
- fix in-scope defects and repeat build/refresh/capture/inspection.

Check composition, hierarchy, spacing, typography, artwork, control placement,
categories, focus, selected-versus-focused, RTL, long Arabic/Latin/mixed text,
clipping, safe edges, loading, empty, error, missing artwork, Back/return,
deep scroll, and required adaptive states.

A capture file existing is not proof of inspection.
A passing build is not proof of visual correctness.

## Evidence
For each artifact record:
criterion/state ID; source SHA; APK SHA-256; package/variant; device/window;
locale/font scale; interaction; capture path and SHA-256; image-open record;
visible findings; result; reviewer; timestamp.
Protect credentials and owner-private data.

## Finish
Re-read the task packet. Recheck remote HEAD and the final complete diff.
Every mandatory criterion must have evidence.
Missing required states remain BLOCKED or NOT RUN.
Report PASS only when all mandatory criteria pass.
Otherwise report FAIL, BLOCKED, or STOP with the exact reason.
Never declare merge, release, or production readiness from this skill.
