# Targeted Compatibility Retest Policy

This policy is mandatory for PR #57 and Issue #74.

## Rules

1. Never rerun a device job that already concluded `success` for the same source SHA.
2. Diagnose failures from the failed device job logs and artifacts before any rerun.
3. Classify every failure as one of:
   - product defect
   - lab contract defect
   - infrastructure defect
4. Apply the smallest fix that addresses the proven cause.
5. Rerun only the exact failed job with `rerun_workflow_job`.
6. If the exact job fails again, repeat diagnosis on that same device and do not touch successful devices.
7. Keep PR #57 draft and unmerged until all failed-device evidence passes.
8. Do not alter logo, colors, icon work, or approved visual identity.

## Current failed-device queue

1. Nexus 9 API 28: Downloads marker/progress evidence contract.
2. Android TV 720p API 36: Live and Downloads D-pad focus coverage.

## Current diagnosis

Nexus 9 captures show real download bytes in persisted state/files while the semantic hierarchy can expose a stale page marker on API 28. The lab must remain fail-closed, but may accept persisted byte evidence when it proves positive transfer and the app process owns the visible hierarchy.

Android TV 720p requires proof that vertical row navigation scrolls the lazy list before requesting focus on an off-screen target. Horizontal focus movement remains direct.
