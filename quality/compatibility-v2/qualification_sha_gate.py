#!/usr/bin/env python3
"""Fail-closed exact-SHA contract for final HULK SA release qualification."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
OFFICIAL_BRANCH = "phase-3-v0.9.3.0-adaptive-foundation"
EXPECTED_WORKFLOWS = {
    "canonical": ".github/workflows/canonical-build.yml",
    "full": ".github/workflows/compatibility-v2-full.yml",
    "production": ".github/workflows/production-authenticated-smoke.yml",
}


class QualificationError(ValueError):
    pass


def validated_sha(label: str, value: str) -> str:
    normalized = value.strip().lower()
    if not SHA_PATTERN.fullmatch(normalized):
        raise QualificationError(f"{label} is not a full 40-character commit SHA")
    return normalized


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise QualificationError(f"Unable to read {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise QualificationError(f"{path} must contain a JSON object")
    return value


def run_id(run: dict[str, Any], label: str) -> int:
    value = run.get("id")
    if not isinstance(value, int) or value <= 0:
        raise QualificationError(f"{label} run has an invalid id")
    return value


def verify_run(
    *,
    label: str,
    run: dict[str, Any],
    expected_repo: str,
    expected_branch: str,
    expected_sha: str,
    expected_workflow: str,
) -> int:
    repository = run.get("repository")
    repo_name = repository.get("full_name") if isinstance(repository, dict) else None
    if repo_name != expected_repo:
        raise QualificationError(f"{label} run belongs to {repo_name!r}, expected {expected_repo!r}")
    if run.get("head_branch") != expected_branch:
        raise QualificationError(
            f"{label} run ref is {run.get('head_branch')!r}, expected Official Branch {expected_branch!r}"
        )
    actual_sha = validated_sha(f"{label} run head_sha", str(run.get("head_sha", "")))
    if actual_sha != expected_sha:
        raise QualificationError(f"{label} run SHA {actual_sha} does not match target {expected_sha}")
    path = str(run.get("path", "")).split("@", 1)[0]
    if path != expected_workflow:
        raise QualificationError(f"{label} run workflow is {path!r}, expected {expected_workflow!r}")
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        raise QualificationError(
            f"{label} run is not a completed success: status={run.get('status')!r}, conclusion={run.get('conclusion')!r}"
        )
    return run_id(run, label)


def artifact_list(payload: dict[str, Any], label: str) -> list[dict[str, Any]]:
    artifacts = payload.get("artifacts")
    if not isinstance(artifacts, list):
        raise QualificationError(f"{label} artifacts response does not contain an artifacts list")
    return [item for item in artifacts if isinstance(item, dict)]


def verify_artifact(
    artifacts: list[dict[str, Any]],
    *,
    label: str,
    expected_name: str,
    expected_run_id: int,
    expected_sha: str,
) -> None:
    matches = [item for item in artifacts if item.get("name") == expected_name]
    if len(matches) != 1:
        raise QualificationError(f"{label} evidence artifact {expected_name!r} is missing or ambiguous")
    artifact = matches[0]
    if artifact.get("expired") is not False:
        raise QualificationError(f"{label} evidence artifact {expected_name!r} is expired or unavailable")
    workflow_run = artifact.get("workflow_run")
    if not isinstance(workflow_run, dict):
        raise QualificationError(f"{label} artifact is missing workflow-run provenance")
    if workflow_run.get("id") != expected_run_id:
        raise QualificationError(f"{label} artifact is attached to a different workflow run")
    artifact_sha = validated_sha(
        f"{label} artifact workflow_run.head_sha", str(workflow_run.get("head_sha", ""))
    )
    if artifact_sha != expected_sha:
        raise QualificationError(f"{label} artifact SHA {artifact_sha} does not match target {expected_sha}")


def expected_full_runtime_artifacts(device_matrix: Path, run_id_value: int) -> list[str]:
    matrix = read_json(device_matrix)
    profiles = matrix.get("profiles")
    if not isinstance(profiles, list) or not profiles:
        raise QualificationError("Compatibility V2 device matrix has no profiles")
    ids: list[str] = []
    for profile in profiles:
        if not isinstance(profile, dict) or not isinstance(profile.get("id"), str) or not profile["id"]:
            raise QualificationError("Compatibility V2 device matrix contains an invalid profile id")
        ids.append(profile["id"])
    return [f"HULK-SA-COMPATIBILITY-V2-RUNTIME-{profile_id}-{run_id_value}" for profile_id in ids]


def verify_exact_qualification(
    *,
    expected_repo: str,
    expected_branch: str,
    target_sha: str,
    checkout_sha: str,
    execution_sha: str,
    execution_ref: str,
    official_head_start: str,
    official_head_end: str,
    canonical_run: dict[str, Any],
    canonical_artifacts: dict[str, Any],
    full_run: dict[str, Any],
    full_artifacts: dict[str, Any],
    production_run: dict[str, Any],
    production_artifacts: dict[str, Any],
    device_matrix: Path,
) -> None:
    target = validated_sha("target Official SHA", target_sha)
    expected_ref = f"refs/heads/{expected_branch}"
    if execution_ref != expected_ref:
        raise QualificationError(
            f"qualification execution ref is {execution_ref!r}, expected {expected_ref!r}"
        )

    comparisons = {
        "workflow execution SHA": execution_sha,
        "checked-out SHA": checkout_sha,
        "Official HEAD at qualification start": official_head_start,
        "Official HEAD after evidence validation": official_head_end,
    }
    for label, value in comparisons.items():
        actual = validated_sha(label, value)
        if actual != target:
            raise QualificationError(f"{label} {actual} does not match target {target}")

    canonical_id = verify_run(
        label="canonical build",
        run=canonical_run,
        expected_repo=expected_repo,
        expected_branch=expected_branch,
        expected_sha=target,
        expected_workflow=EXPECTED_WORKFLOWS["canonical"],
    )
    full_id = verify_run(
        label="Compatibility V2 Full Matrix",
        run=full_run,
        expected_repo=expected_repo,
        expected_branch=expected_branch,
        expected_sha=target,
        expected_workflow=EXPECTED_WORKFLOWS["full"],
    )
    production_id = verify_run(
        label="production authenticated smoke",
        run=production_run,
        expected_repo=expected_repo,
        expected_branch=expected_branch,
        expected_sha=target,
        expected_workflow=EXPECTED_WORKFLOWS["production"],
    )

    verify_artifact(
        artifact_list(canonical_artifacts, "canonical build"),
        label="canonical build",
        expected_name=f"HULK-SA-BUILD-EVIDENCE-{canonical_id}",
        expected_run_id=canonical_id,
        expected_sha=target,
    )

    full_items = artifact_list(full_artifacts, "Compatibility V2 Full Matrix")
    verify_artifact(
        full_items,
        label="Compatibility V2 Full Matrix binaries",
        expected_name=f"HULK-SA-COMPATIBILITY-V2-BINARIES-{full_id}",
        expected_run_id=full_id,
        expected_sha=target,
    )
    for expected_name in expected_full_runtime_artifacts(device_matrix, full_id):
        verify_artifact(
            full_items,
            label="Compatibility V2 Full Matrix runtime",
            expected_name=expected_name,
            expected_run_id=full_id,
            expected_sha=target,
        )

    verify_artifact(
        artifact_list(production_artifacts, "production authenticated smoke"),
        label="production authenticated smoke",
        expected_name=f"HULK-SA-PRODUCTION-AUTHENTICATED-SMOKE-{production_id}",
        expected_run_id=production_id,
        expected_sha=target,
    )


def verify_current_head(*, target_sha: str, checkout_sha: str, official_head: str) -> None:
    target = validated_sha("target Official SHA", target_sha)
    for label, value in (("checked-out SHA", checkout_sha), ("current Official HEAD", official_head)):
        actual = validated_sha(label, value)
        if actual != target:
            raise QualificationError(f"{label} {actual} does not match target {target}")


def write_signed_metadata(
    *,
    out: Path,
    source_sha: str,
    canonical_run_id: str,
    full_run_id: str,
    production_run_id: str,
) -> None:
    source = validated_sha("signed qualification source SHA", source_sha)
    for label, value in (
        ("canonical build run id", canonical_run_id),
        ("Full Matrix run id", full_run_id),
        ("production smoke run id", production_run_id),
    ):
        if not value.isdigit() or int(value) <= 0:
            raise QualificationError(f"{label} is invalid")
    payload = {
        "schema_version": 1,
        "qualification_kind": "hulk-sa-signed-release-exact-sha",
        "source_sha": source,
        "evidence": {
            "canonical_build": {"status": "PASS", "run_id": int(canonical_run_id)},
            "compatibility_v2_full": {"status": "PASS", "run_id": int(full_run_id)},
            "production_authenticated_smoke": {"status": "PASS", "run_id": int(production_run_id)},
            "signed_artifacts": {"status": "PASS"},
            "physical_devices": {
                "status": "BLOCKED",
                "reason": "PHYSICAL DEVICE REQUIRED; this workflow does not create or infer physical-device evidence",
            },
        },
        "release_readiness_status": "BLOCKED",
        "release_readiness_reason": "Physical-device qualification remains BLOCKED until exact-SHA physical evidence is attached and verified",
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    verify = subparsers.add_parser("verify-evidence")
    verify.add_argument("--expected-repo", required=True)
    verify.add_argument("--expected-branch", default=OFFICIAL_BRANCH)
    verify.add_argument("--target-sha", required=True)
    verify.add_argument("--checkout-sha", required=True)
    verify.add_argument("--execution-sha", required=True)
    verify.add_argument("--execution-ref", required=True)
    verify.add_argument("--official-head-start", required=True)
    verify.add_argument("--official-head-end", required=True)
    verify.add_argument("--canonical-run", type=Path, required=True)
    verify.add_argument("--canonical-artifacts", type=Path, required=True)
    verify.add_argument("--full-run", type=Path, required=True)
    verify.add_argument("--full-artifacts", type=Path, required=True)
    verify.add_argument("--production-run", type=Path, required=True)
    verify.add_argument("--production-artifacts", type=Path, required=True)
    verify.add_argument(
        "--device-matrix",
        type=Path,
        default=Path("quality/compatibility-v2/config/device-matrix.json"),
    )

    head = subparsers.add_parser("verify-head")
    head.add_argument("--target-sha", required=True)
    head.add_argument("--checkout-sha", required=True)
    head.add_argument("--official-head", required=True)

    metadata = subparsers.add_parser("write-signed-metadata")
    metadata.add_argument("--out", type=Path, required=True)
    metadata.add_argument("--source-sha", required=True)
    metadata.add_argument("--canonical-run-id", required=True)
    metadata.add_argument("--full-run-id", required=True)
    metadata.add_argument("--production-run-id", required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "verify-evidence":
            verify_exact_qualification(
                expected_repo=args.expected_repo,
                expected_branch=args.expected_branch,
                target_sha=args.target_sha,
                checkout_sha=args.checkout_sha,
                execution_sha=args.execution_sha,
                execution_ref=args.execution_ref,
                official_head_start=args.official_head_start,
                official_head_end=args.official_head_end,
                canonical_run=read_json(args.canonical_run),
                canonical_artifacts=read_json(args.canonical_artifacts),
                full_run=read_json(args.full_run),
                full_artifacts=read_json(args.full_artifacts),
                production_run=read_json(args.production_run),
                production_artifacts=read_json(args.production_artifacts),
                device_matrix=args.device_matrix,
            )
        elif args.command == "verify-head":
            verify_current_head(
                target_sha=args.target_sha,
                checkout_sha=args.checkout_sha,
                official_head=args.official_head,
            )
        else:
            write_signed_metadata(
                out=args.out,
                source_sha=args.source_sha,
                canonical_run_id=args.canonical_run_id,
                full_run_id=args.full_run_id,
                production_run_id=args.production_run_id,
            )
    except QualificationError as exc:
        print(f"FAIL: {exc}")
        return 1
    print("PASS: exact-SHA release qualification contract satisfied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
