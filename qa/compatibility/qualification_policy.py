#!/usr/bin/env python3
"""Fail-closed classification and root/downstream reconciliation for Compatibility Lab reports.

This policy consumes raw analyzer output but does not execute the emulator driver,
marker injector, or analyzer. It rebuilds gate semantics from explicit evidence.
"""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import re
from typing import Any, Iterable

PRIMARY = "primary"
DOWNSTREAM = "downstream_blocked"
VALID_CLASSIFICATIONS = {"product", "quality_lab", "fixture", "infrastructure", "blocked", "future_stage"}
FATAL_ALWAYS = {"quality_lab", "fixture", "infrastructure", "blocked"}
DOWNLOAD_CODES = {
    "download_action_audit_error",
    "download_action_audit_incomplete",
    "missing_mandatory_download_action_evidence",
    "tv_download_action_not_executed",
    "tv_download_action_unreachable",
    "tv_download_navigation_focus_mismatch",
    "tv_download_action_precondition_incomplete",
    "tv_download_row_navigation_incomplete",
    "start_focus_not_established",
    "navigation_target_mismatch",
    "harness_selector_failure",
    "action_callback_not_executed",
    "ui_state_not_updated",
    "static_focus_graph_contradiction",
}
TRANSIENT_RETRY_CODES = {
    "emulator_boot_failure",
    "adb_disconnected",
    "system_service_unavailable",
    "artifact_download_failure",
}
ASSERTION_CODES = {
    "navigation_target_mismatch",
    "start_focus_not_established",
    "action_callback_not_executed",
    "ui_state_not_updated",
    "tv_download_action_not_executed",
    "tv_download_action_unreachable",
}


def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def _slug(value: object) -> str:
    return re.sub(r"[^a-z0-9_.:-]+", "-", str(value or "unknown").strip().lower()).strip("-") or "unknown"


def _finding(
    code: str,
    classification: str,
    message: str,
    root: str,
    *,
    case_id: str | None = None,
    page: str | None = None,
    role: str = PRIMARY,
    evidence: dict[str, str] | None = None,
    raw_check_id: str | None = None,
) -> dict[str, Any]:
    severity = "infrastructure" if classification == "infrastructure" else "critical"
    gate_outcome = "FAIL_PRODUCT" if classification == "product" and role == PRIMARY else "BLOCKED"
    return {
        "severity": severity,
        "code": code,
        "message": message,
        "case_id": case_id,
        "page": page,
        "evidence": evidence or {},
        "classification": classification,
        "root_cause_id": root,
        "finding_role": role,
        "product_strict": classification == "product" and role == PRIMARY,
        "gate_outcome": gate_outcome,
        "raw_check_id": raw_check_id,
    }


def _key_event(check: dict[str, Any]) -> dict[str, Any] | None:
    events = check.get("key_events")
    if not isinstance(events, list):
        return None
    for event in events:
        if isinstance(event, dict) and not event.get("success", True):
            return event
    return None


def graph_contradictions(checks: Iterable[dict[str, Any]]) -> dict[str, list[str]]:
    """Detect a static harness graph assigning one observed edge to multiple targets."""
    groups: dict[tuple[str, str, str], dict[str, list[str]]] = {}
    for check in checks:
        if check.get("success"):
            continue
        event = _key_event(check)
        if not event:
            continue
        current = str(event.get("focused_before") or check.get("initial_target") or "")
        key = str(event.get("key") or "")
        observed = str(event.get("actual_target") or check.get("actual_target") or "")
        expected = str(event.get("expected_target") or check.get("expected_target") or "")
        if not current or not key or not observed or not expected:
            continue
        bucket = groups.setdefault((current, key, observed), {})
        bucket.setdefault(expected, []).append(str(check.get("id") or "unknown"))
    result: dict[str, list[str]] = {}
    for (current, key, observed), expected_map in groups.items():
        check_ids = sorted({item for values in expected_map.values() for item in values})
        if len(check_ids) < 2:
            continue
        signature = f"{current}|{key}|{observed}"
        result[signature] = check_ids
    return result


def classify_download_actions(entries: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        orientation = str(entry.get("orientation") or "unknown")
        checks = [item for item in entry.get("checks", []) if isinstance(item, dict)]
        if entry.get("error"):
            findings.append(_finding(
                "download_action_audit_error",
                "infrastructure",
                f"{orientation} / downloads: {entry.get('error')}",
                f"download-actions:{orientation}:infrastructure",
                page="downloads",
            ))
            continue

        contradictions = graph_contradictions(checks)
        contradictory_ids = {check_id for values in contradictions.values() for check_id in values}
        for signature, check_ids in sorted(contradictions.items()):
            current, key, observed = signature.split("|", 2)
            root = f"download-actions:{orientation}:focus-graph:{_slug(current)}:{_slug(key)}:{_slug(observed)}"
            findings.append(_finding(
                "static_focus_graph_contradiction",
                "quality_lab",
                f"{orientation} / downloads: the harness assigned {key} from {current} to multiple expected targets, while runtime consistently reached {observed}; checks={', '.join(check_ids)}",
                root,
                page="downloads",
                raw_check_id=check_ids[0],
            ))
            for check_id in check_ids[1:]:
                findings.append(_finding(
                    "downstream_blocked",
                    "quality_lab",
                    f"{orientation} / downloads / {check_id}: blocked by the static focus-graph contradiction",
                    root,
                    page="downloads",
                    role=DOWNSTREAM,
                    raw_check_id=check_id,
                ))

        for check in checks:
            if check.get("success") or str(check.get("id") or "") in contradictory_ids:
                continue
            check_id = str(check.get("id") or "unknown")
            reason = str(check.get("reason") or check.get("precondition_failure") or "contract failed")
            source = str(check.get("source") or "QUALITY_LAB").upper()
            precondition = bool(check.get("precondition_established"))
            key_confirmed = bool(check.get("key_press_confirmed"))
            expected_action = str(check.get("expected_action") or "")
            evidence = {str(k): str(v) for k, v in (check.get("evidence") or {}).items() if str(v)}
            root = f"download-actions:{orientation}:{_slug(check_id)}"

            if source == "INFRASTRUCTURE" or reason.startswith("INFRASTRUCTURE_FAILURE"):
                classification = "infrastructure"
                code = "download_action_infrastructure_failure"
            elif source == "FIXTURE" or reason.startswith("HARNESS_SELECTOR_FAILURE"):
                classification = "fixture"
                code = "harness_selector_failure"
            elif source in {"QUALITY_LAB", "LAB"} or reason.startswith("LAB_FOCUS_GRAPH_MODEL_MISMATCH"):
                classification = "quality_lab"
                code = "focus_graph_model_mismatch"
            elif not precondition:
                classification = "blocked"
                code = "start_state_not_established"
            elif expected_action and not key_confirmed:
                classification = "blocked"
                code = "action_key_not_confirmed"
            elif reason.startswith("ACTION_CALLBACK_NOT_EXECUTED"):
                classification = "product"
                code = "action_callback_not_executed"
            elif reason.startswith("UI_STATE_NOT_UPDATED"):
                classification = "product"
                code = "ui_state_not_updated"
            elif reason.startswith("NAVIGATION_TARGET_MISMATCH"):
                classification = "product"
                code = "navigation_target_mismatch"
            else:
                classification = "blocked"
                code = "download_action_unclassified_failure"

            findings.append(_finding(
                code,
                classification,
                f"{orientation} / downloads / {check_id}: {reason}",
                root,
                page="downloads",
                evidence=evidence,
                raw_check_id=check_id,
            ))
    return findings


def _normalize_existing(item: dict[str, Any]) -> dict[str, Any]:
    result = deepcopy(item)
    classification = str(result.get("classification") or "").strip().lower()
    severity = str(result.get("severity") or "critical")
    if classification not in VALID_CLASSIFICATIONS:
        classification = "infrastructure" if severity == "infrastructure" else "product"
    role = str(result.get("finding_role") or PRIMARY)
    if role in {"downstream", "blocked_assertion"}:
        role = DOWNSTREAM
    elif role != DOWNSTREAM:
        role = PRIMARY
    code = str(result.get("code") or "unknown")
    case_key = str(result.get("case_id") or result.get("page") or "global")
    if code in {"download_origin_repository_boundary_mismatch", "download_transfer_no_byte_progress"}:
        root = f"download-boundary:{case_key}"
    else:
        root = str(result.get("root_cause_id") or f"{code}:{case_key}")
    result.update({
        "classification": classification,
        "finding_role": role,
        "root_cause_id": root,
        "product_strict": classification == "product" and role == PRIMARY,
        "gate_outcome": "FAIL_PRODUCT" if classification == "product" and role == PRIMARY else "BLOCKED" if severity in {"critical", "infrastructure"} else "RECORDED",
    })
    return result


def reconcile_findings(findings: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    primary_roots: set[str] = set()
    seen_exact: set[tuple[str, str, str]] = set()
    for raw in findings:
        if not isinstance(raw, dict):
            continue
        item = _normalize_existing(raw)
        exact = (str(item.get("root_cause_id")), str(item.get("code")), str(item.get("raw_check_id") or item.get("case_id") or ""))
        if exact in seen_exact:
            continue
        seen_exact.add(exact)
        root = str(item["root_cause_id"])
        if item["finding_role"] == PRIMARY:
            if root in primary_roots:
                item["finding_role"] = DOWNSTREAM
                item["product_strict"] = False
                item["gate_outcome"] = "BLOCKED"
            else:
                primary_roots.add(root)
        result.append(item)
    return sorted(result, key=lambda item: (0 if item["finding_role"] == PRIMARY else 1, str(item["root_cause_id"]), str(item.get("code"))))


def provenance_findings(summary: dict[str, Any]) -> list[dict[str, Any]]:
    provenance = summary.get("provenance") if isinstance(summary.get("provenance"), dict) else {}
    required = ("source_head_sha", "base_sha", "tested_commit_sha", "merge_sha", "lab_apk_sha256", "workflow_run_id", "workflow_run_attempt")
    missing = [key for key in required if not str(provenance.get(key) or "").strip()]
    if missing:
        return [_finding(
            "provenance_incomplete",
            "blocked",
            f"mandatory provenance fields are missing: {', '.join(missing)}",
            "provenance:mandatory",
        )]
    return []


def normalize_summary(summary: dict[str, Any]) -> dict[str, Any]:
    data = deepcopy(summary)
    existing = [item for item in data.get("findings", []) if isinstance(item, dict) and str(item.get("code")) not in DOWNLOAD_CODES]
    rebuilt_download = classify_download_actions(data.get("download_actions", []))
    all_findings = existing + rebuilt_download + provenance_findings(data)
    reconciled = reconcile_findings(all_findings)
    data["findings"] = reconciled
    data["critical_count"] = sum(item.get("severity") == "critical" for item in reconciled)
    data["warning_count"] = sum(item.get("severity") == "warning" for item in reconciled)
    infrastructure_roots = [item for item in reconciled if item.get("classification") == "infrastructure" and item.get("finding_role") == PRIMARY]
    data["infrastructure_invalidity_count"] = len(infrastructure_roots)
    data["infrastructure_error_count"] = sum(str(item.get("code") or "").lower() in TRANSIENT_RETRY_CODES for item in infrastructure_roots)
    data["primary_root_cause_count"] = sum(item.get("finding_role") == PRIMARY and item.get("severity") in {"critical", "infrastructure"} for item in reconciled)
    data["raw_failed_checks_count"] = sum(item.get("severity") in {"critical", "infrastructure"} for item in reconciled)
    data["downstream_count"] = sum(item.get("finding_role") == DOWNSTREAM for item in reconciled)
    data["product_critical_count"] = sum(item.get("classification") == "product" and item.get("finding_role") == PRIMARY and item.get("severity") == "critical" for item in reconciled)
    data["quality_lab_critical_count"] = sum(item.get("classification") == "quality_lab" and item.get("finding_role") == PRIMARY for item in reconciled)
    data["fixture_critical_count"] = sum(item.get("classification") == "fixture" and item.get("finding_role") == PRIMARY for item in reconciled)
    data["blocked_root_count"] = sum(item.get("classification") == "blocked" and item.get("finding_role") == PRIMARY for item in reconciled)
    if data["infrastructure_invalidity_count"] or data["quality_lab_critical_count"] or data["fixture_critical_count"] or data["blocked_root_count"]:
        data["overall_status"] = "BLOCKED"
    elif data["product_critical_count"]:
        data["overall_status"] = "FAIL"
    elif data["warning_count"]:
        data["overall_status"] = "WARN"
    else:
        data["overall_status"] = "PASS"
    data["qualification_policy"] = {
        "schema_version": 1,
        "normalized_sha256": hashlib.sha256(canonical_bytes(reconciled)).hexdigest(),
    }
    return data


def gate_decision(summary: dict[str, Any], enforce_findings: bool) -> tuple[int, str]:
    data = normalize_summary(summary)
    invalid = data["infrastructure_invalidity_count"] + data["quality_lab_critical_count"] + data["fixture_critical_count"] + data["blocked_root_count"]
    if invalid:
        return 2, "BLOCKED"
    if enforce_findings and data["product_critical_count"]:
        return 1, "FAIL_PRODUCT"
    return 0, "PASS"


def retry_allowed(code: str) -> bool:
    normalized = str(code).strip().lower()
    return normalized in TRANSIENT_RETRY_CODES and normalized not in ASSERTION_CODES
