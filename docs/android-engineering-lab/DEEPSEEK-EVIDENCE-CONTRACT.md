# HULK SA Android — DeepSeek Evidence Contract

Version 1 · prepared 2026-10-06.

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
| SOURCE-MANIFEST.sha256 | Frozen authorized source inventory, including new source files |
| candidate.diff / final.diff | Reviewed diff tied to the candidate; retain staged/unstaged/new-file attribution |
| RESULT.md | Phase/verdict, changes, actual checks, limitations and next boundary |
| RUNTIME-EVIDENCE.md | Only applicable exercised behavior, steps, raw evidence paths and limits |
| OWNER-ACCEPTANCE.md | Append-only accepted/rejected/pending decisions, exact candidate and reviewed scope |
| MANIFEST.sha256 | Hashes of every durable file in the exported packet, excluding itself |

Applicable additions: sanitized command/test/build logs, APK inspection/hashes, install and launch
logs, actual device/window matrix, non-capture hierarchy/diagnostics, before/reference/after source
values and final Git/PR/CI records when publishing. A source/documentation task records runtime,
APK and Android builds as NOT RUN — NOT APPLICABLE; it does not fabricate empty “PASS” logs.

A local round can be complete without a commit/PR. Its identity is the immutable recorded candidate,
not a false clean-HEAD label. Technical PASS applies only to the declared phase and mandatory criteria.

## 3. Bind source, artifacts and observations

Record base/local HEAD AND clean/dirty state. Include authorized new source/config/resource files
and material build inputs; omit outputs, caches and secrets. Record exclusions and the inventory
method. Keep the reviewed diff/new-file set and source manifest immutable for that round.

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

## 5. Complete downloadable archive and external checksum

DeepSeek prepares the transfer package; the owner downloads and sends it to the reviewer.
A “complete packet” means all records needed to review that round and every referenced essential
dependency, not an indiscriminate copy of the whole lab.

1. Freeze the safe export directory and finalize RESULT/acceptance/status records.
2. Copy required evidence to a self-contained staging tree, or include a complete artifact index
   with actually reachable reviewer access. Do not leave essential dangling VPS-only references.
3. Deduplicate large APKs across local rounds in a protected content-addressed artifact store.
   If the reviewer cannot access that store, include the necessary APK/evidence in the final export.
   Do not rebuild/reinstall solely to make an archive or attach identical APKs to every small round.
4. Generate MANIFEST.sha256 from the final relative file paths; exclude the manifest itself and
   the enclosing ZIP/external sidecar. All referenced exported files must be included.
5. Create one ZIP with a single task/round root and stable relative names. No absolute paths,
   traversal components, duplicate entries, unsafe symlinks or unexplained encrypted entries.
6. Reopen/test the ZIP (including CRC), verify every manifest entry from archive bytes, and verify
   completeness/no unexpected files. File existence or successful zip creation alone is insufficient.
7. Compute SHA-256 over the final ZIP bytes. Write a sibling `<archive-name>.zip.sha256` containing
   the full digest and basename. This external sidecar validates the downloaded container.
8. Verify the sidecar against the final archive. Revalidate both after any archive modification.
   A container checksum and an inner manifest are different checks; neither substitutes for the other.

For local rounds, use a small complete focused packet; at section publication provide the final
candidate/acceptance index and required preserved evidence. Do not run a full audit/device census
or package the full repository merely because a correction is small.

## 6. Bounded hulkapp access

The final ZIP, external checksum and intended download path must be readable by `hulkapp`, with
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
- exact external checksum filename/path;
- actual ZIP byte count and full SHA-256;
- manifest entry count and the archive/manifest/sidecar validation result;
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
