# HULK SA Android — DeepSeek Evidence Contract

Version 2 · owner decision 2026-11-09, Asia/Riyadh.
One complete reviewer ZIP; full archive SHA-256 in the handoff, without a separate checksum file.

This is the durable evidence and delivery contract for the normal OpenCode/DeepSeek executor.
It binds every executor and reviewer, regardless of model. It owns evidence records, archives,
integrity, bounded access and handoff; it grants no source, capture, device, Git or release authority.
Read it with `TASK-EXECUTION-CONTRACT.md`, root instructions and applicable physical/lab contracts.

## 1. Evidence exists before a round is reported complete

Retain originals in `/srv/hulk-android/reports/YYYY/<task-id>/`. Local correction rounds use stable
subdirectories such as `rounds/r01/`; preserve the same task/worktree throughout the section.
Do not overwrite earlier evidence or reuse a round ID. Record UTC timestamps with timezone;
attribute owner decisions using their stated date/time/timezone when available, without guessing.

The reviewer inspects source/diff, provenance and raw applicable evidence. Executor PASS prose alone
is insufficient. Source reasoning, executor-run checks, coordinator review and owner observations
remain distinct. Missing/unexecuted states stay NOT RUN/BLOCKED, never inferred PASS.

## 2. Minimal complete local-round packet

| File/record | Required content |
| --- | --- |
| TASK.md | Exact bounded packet, current phase, authority and capture policy |
| TASK.env | Sanitized live identities, branch/worktree, base HEAD, overlap/status and surfaces |
| INTENDED-DIFF.md | Planned files/purpose, source cause/owner and protected state before editing |
| CANDIDATE.json | Candidate ID, base HEAD, dirty state, diff hash and source-manifest hash |
| SOURCE-MANIFEST.sha256 | Complete frozen tracked plus authorized new source/material-input inventory; record method/exclusions |
| candidate.diff / final.diff | Reviewed diff tied to the candidate; retain staged/unstaged/new-file attribution |
| RESULT.md | Phase/verdict, changes, actual checks, limitations and next boundary |
| RUNTIME-EVIDENCE.md | Only applicable exercised behavior, steps, raw evidence paths and limits |
| OWNER-ACCEPTANCE.md | Append-only accepted/rejected/pending decisions, exact candidate and reviewed scope |
| MANIFEST.sha256 | Hashes of every durable file in the exported packet, excluding itself |

Applicable additions: sanitized command/test/build logs, APK inspection/hashes, install and launch
logs, actual device/window matrix, non-capture hierarchy/diagnostics, before/reference/after source
values and final Git/PR/CI records when publishing. A source/documentation task records runtime,
APK and Android builds as NOT RUN — NOT APPLICABLE; it does not fabricate empty “PASS” logs.

When a check ran, include its actual sanitized command/log, exit status, timestamp and original
machine-readable report. For JVM/JUnit include all XML suites for the declared focused/full run,
or the complete original report from which the aggregate can be independently recounted. Keep
relevant failed attempts and retries with separate input/run identities. A summary or BUILD SUCCESSFUL
line cannot replace the reports supporting a claimed test total. Package applicable lint/static/
Compatibility/build reports and source/APK/device provenance; do not run unrelated checks merely
to fill the ZIP. Capture/device/build evidence outside authority stays honestly NOT RUN.

A local round can be complete without a commit/PR. Its identity is the immutable recorded candidate,
not a false clean-HEAD label. Technical PASS applies only to the declared phase and mandatory criteria.

## 3. Bind source, artifacts and observations

Record base/local HEAD AND clean/dirty state. Include authorized new source/config/resource files
and material build inputs; omit outputs, caches and secrets. Record exclusions and the inventory
method. A changed-files-only checksum list is a separate inventory, not a complete SOURCE-MANIFEST.
Inventory tracked plus authorized new source/material inputs; record exclusions, build options,
toolchain/config identities and any other material inputs needed to attribute the result. The ZIP
contains changed/new file bytes and its immutable base identity; it need not copy the entire unchanged
repository. Keep the reviewed diff/new-file set and source manifest immutable for that round.

Every result identifies:
- criterion/state, actor, method/actual command, timestamp and PASS/FAIL/NOT RUN/BLOCKED;
- candidate ID and base HEAD; source manifest/diff hash;
- variant/APK hash/package/version/signer when applicable;
- device model/package/window/input method and interaction when applicable;
- raw evidence location and precise observed result, including anything untested.

Changed-files hashes alone do not prove the complete build inputs. When reusing a locally built APK
at publication, also retain a build-input manifest and prove its inputs/options match the committed
tree. Preserve original dirty-build attribution; do not relabel it clean-HEAD. If inputs differ, rebuild
affected artifacts/checks. Installed APK byte comparisons prove artifact identity, not visual approval.

Do not log credentials, signed/private playback URLs, access codes, session data, signing keys or
private account payloads. Sanitization must not remove a failure or falsify a result. If unsafe raw
material exists, preserve it only in restricted quarantine according to applicable security rules;
export a sanitized record and document the omission without exposing the secret.

## 4. Runtime, screenshots and owner evidence

Use only the approved surface and bounded technical interactions needed for the change.
Follow the physical contract; never uninstall, clear data, reset authentication or change signer.
Do not change network/display/font settings or unlock a device without applicable authorization.

Captures are OFF unless explicitly enabled for the current task. Without authorization, screenshots,
recordings, frame extraction, montages and image comparisons are NOT RUN — CAPTURE NOT AUTHORIZED.
Their absence alone does not block a routine round. Source/tests/hierarchy dumps cannot establish
visual correctness. Owner-supplied photos or direct device observations remain valid owner evidence
for their stated scope; accepting them does not grant new capture authority.

When authorized captures are required, retain file hashes and actual image-open/inspection findings.
A path or existing image file is not proof the executor/model saw it. A newly edited candidate does
not inherit an older candidate's owner acceptance or capture PASS.

Owner review defaults to PENDING. Record ACCEPTED or REJECTED only from an actual owner decision,
with candidate/APK identity and scope. A partial acceptance preserves accepted neighbors while
listing remaining defects. Do not turn whole-section acceptance into universal device qualification.

## 5. One complete downloadable reviewer ZIP

DeepSeek/the actual executor prepares one self-contained ZIP from the first handoff of every
substantive round. The owner only downloads and forwards that one file. The reviewer must be able
to inspect the round's complete applicable evidence from its contents.

Include every essential referenced dependency/report/artifact needed to assess the result inside
this ZIP. A VPS-only path, external artifact index/store, previous ZIP reference or prose summary
does not replace required evidence bytes. Deduplicate within the archive and preserve canonical
originals separately. Include only relevant earlier evidence, not recursively nested old ZIPs or
an indiscriminate copy of the lab/repository. Include a necessary APK when artifact review requires
it; do not rebuild/reinstall solely for packaging.

Before declaring delivery complete:

1. Freeze the candidate and finalize TASK/identity/diff, RESULT, applicable acceptance/runtime
   records, original raw check reports/logs and artifact/provenance evidence from sections 2–4.
2. Prepare one safe task/round staging root with START-HERE/index, all required files and essential
   referenced evidence. Verify each required item is actually present; do not ask the owner to
   assemble missing pieces or download supplementary review files.
3. Check original focused/full reports against the declared aggregate and exact candidate/run.
   Preserve failures/retries. Recover retained valid evidence without rerunning unchanged checks
   merely for packaging. A report from different inputs cannot validate the new candidate.
4. Generate MANIFEST.sha256 over every exported regular file except itself, using relative paths.
   Include a runnable VERIFY.sh that validates hashes, exact coverage and applicable completeness
   checks. The enclosing ZIP is not part of its own inner manifest.
5. Create ONE ZIP with a single task/round root and stable relative paths. Reject absolute/traversal
   paths, duplicate entries, unsafe symlinks and encrypted entries.
6. Reopen the FINAL ZIP, test CRC, extract safely into an isolated directory, verify all hashes and
   exact file coverage, and check all required contents/essential references. Recount included test
   reports. Integrity PASS alone does not prove evidence completeness.
7. Verify actual bounded hulkapp read/traverse access to the final ZIP/download path under section 6.
   Final archive contents include the access proof for the intended export path; after creating the
   final ZIP, recheck actual account access without changing packaged evidence.
8. Compute SHA-256 over the FINAL ZIP bytes and report its full digest, filename, absolute path and
   exact byte count in the handoff text. Recheck/recompute after any archive change.

Do not create/deliver a checksum sidecar, supplementary evidence file or second ZIP for the same
handoff. The archive digest is outside the ZIP in the response text; the inner MANIFEST is inside.
These are distinct checks; neither replaces the other.

If essential evidence or export access is missing, recover it before handoff. If unavailable, list
the exact missing item and mark delivery BLOCKED/incomplete; do not announce a complete/PASS packet.
Keep implementation/check verdicts separate from evidence-delivery status and owner acceptance.
No fabrication, hidden failure or new device/capture/Git/signing authority follows from packaging.
Missing unauthorized captures and genuinely inapplicable checks are not evidence omissions.

## 6. Bounded hulkapp access

The final ZIP and intended download path must be readable by `hulkapp`, with
directory traversal through the required parents. Verify actual read/traverse access as that account
using its approved local execution route, rather than assuming ownership or mode bits suffice.

Prefer files owned by hulkapp or a specifically authorized group. Grant only necessary file read
and directory traversal on the exact task/export paths; preserve originals and restrictive defaults.
Never use recursive broad chown/chmod on `/srv/hulk-android`, mode 777, world-readable secrets,
public hosting, new accounts or authentication changes to make delivery convenient.

If access needs a privileged change outside current authority, retain the packet and report the
exact inaccessible path as BLOCKED. Do not falsely claim an account access check ran.
Download methods remain the existing authorized transport; this contract creates no public service.

## 7. Handoff to owner and reviewer

The final report supplies:
- exact archive filename and absolute VPS path;
- actual ZIP byte count and full SHA-256 in the response text, with no separate checksum file;
- manifest entry count, CRC/hash/exact-coverage result and required-content completeness result;
- verified hulkapp read/traverse result;
- phase, candidate/base/dirty identity, changed scope and owner acceptance state;
- Git/PR/CI identity only when actually published;
- missing evidence, inaccessible dependencies and runtime limits.

Do not require the owner to manually assemble the ZIP, generate checksums, repair permissions or
restate existing canonical evidence. When the coordinator already has GitHub/VPS access, inspect
there first. Ask for transfer only for evidence otherwise inaccessible or owner-only observations.

The reviewer checks downloaded archive SHA-256, ZIP safety/completeness and all manifest entries,
then compares the candidate to source and actual checks. An executor PASS is not independent review.
If a transfer is truncated/corrupt, keep the canonical originals and regenerate/retransfer the
affected export; never synthesize missing evidence or claim to read unavailable bytes.

## 8. Preservation and closure

Task records, manifests, accepted baselines and referenced evidence are durable; never auto-delete.
Protect evidence for open PRs, accepted sections, releases/security holds and active tasks.
Cache/output cleanup cannot remove sole artifact copies required for provenance or active review.
Closure records existing local changes/branches/PRs and outcome; it does not authorize deletion.

Section publication links final evidence, relevant rounds and exact owner acceptance. This contract
adds no automatic merge, release qualification, new model/provider setup or mandatory captures.
