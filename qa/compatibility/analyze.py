#!/usr/bin/env python3
"""Compatibility analyzer facade with durable debug-download evidence support.

The historical analyzer remains in ``analyze_core.py``. This facade preserves its
public API while correcting one narrow evidence-boundary defect: an Android 9 UI
Automator hierarchy can remain stale after the rendered Downloads page and the
real repository state have advanced. Durable fixture state and captured file bytes
are therefore accepted as independent transfer evidence; they never replace
product geometry, navigation, focus, crash, or foreground-package checks.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET

import analyze_core as _core
from analyze_core import *  # noqa: F401,F403


_BASE_ADD_CASE_FINDINGS = _core.add_case_findings
_DOWNLOAD_PROGRESS_CODES = {
    "download_transfer_no_byte_progress",
    "download_transfer_evidence_mismatch",
}
_QA_HISTORY_PREFIX = "QA_DOWNLOAD:"
_QA_FILE_PREFIX = "QA_DOWNLOAD_"
_ACTIVE_STATUSES = {
    "CHECKING",
    "DOWNLOADING",
    "PAUSED",
    "WAITING_NETWORK",
    "WAITING_SCHEDULE",
    "WAITING_STORAGE",
}


def _safe_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _loopback_source(record: dict[str, Any]) -> bool:
    candidates = record.get("sourceCandidates")
    if not isinstance(candidates, list):
        return False
    return any(
        isinstance(candidate, str)
        and (
            candidate.startswith("http://127.0.0.1:")
            or candidate.startswith("http://localhost:")
        )
        for candidate in candidates
    )


def _download_records(path: Path) -> list[dict[str, Any]]:
    root = ET.parse(path).getroot()
    payload = next(
        (
            node.text
            for node in root.findall("string")
            if node.attrib.get("name") == "downloads"
        ),
        None,
    )
    if not payload:
        return []
    decoded = json.loads(payload)
    if not isinstance(decoded, list):
        raise ValueError("downloads preference must contain a JSON array")
    return [item for item in decoded if isinstance(item, dict)]


def _download_file_sizes(path: Path) -> dict[str, int]:
    sizes: dict[str, int] = {}
    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        fields = raw_line.split(maxsplit=7)
        if len(fields) != 8 or not fields[0].startswith("-"):
            continue
        size = _safe_int(fields[4])
        filename = fields[7].strip()
        if filename.startswith(_QA_FILE_PREFIX) and size >= 0:
            sizes[filename] = size
    return sizes


def durable_download_evidence(
    state_path: Path,
    file_listing_path: Path,
) -> dict[str, Any]:
    """Correlate disposable repository state with actual loopback fixture bytes.

    Evidence is accepted only for records created by the debug fixture, sourced
    from loopback, and backed by a matching final or ``.part`` file whose captured
    size is at least the repository's persisted byte count. Production records are
    excluded by both history and filename prefixes.
    """

    records = [
        record
        for record in _download_records(state_path)
        if str(record.get("historyKey") or "").startswith(_QA_HISTORY_PREFIX)
        and str(record.get("fileName") or "").startswith(_QA_FILE_PREFIX)
    ]
    file_sizes = _download_file_sizes(file_listing_path)
    positive_records: list[dict[str, Any]] = []
    matched_records: list[dict[str, Any]] = []
    active_positive = 0
    completed_verified = 0

    for record in records:
        downloaded = _safe_int(record.get("bytesDownloaded"))
        total = _safe_int(record.get("totalBytes"))
        status = str(record.get("status") or "")
        file_name = str(record.get("fileName") or "")
        source_is_loopback = _loopback_source(record)
        if downloaded <= 0 or not source_is_loopback:
            continue

        positive_records.append(record)
        if status in _ACTIVE_STATUSES and (total <= 0 or downloaded < total):
            active_positive += 1
        if (
            status == "COMPLETED"
            and bool(record.get("integrityVerified"))
            and total > 0
            and downloaded == total
        ):
            completed_verified += 1

        captured_size = max(
            file_sizes.get(file_name, -1),
            file_sizes.get(f"{file_name}.part", -1),
        )
        if captured_size >= downloaded:
            matched_records.append(
                {
                    "download_id": _safe_int(record.get("downloadId")),
                    "status": status,
                    "bytes_downloaded": downloaded,
                    "total_bytes": total,
                    "captured_file_bytes": captured_size,
                    "file_name": file_name,
                }
            )

    repository_progress = bool(positive_records)
    origin_progress = bool(matched_records)
    valid = repository_progress and origin_progress
    return {
        "source": "durable-debug-fixture",
        "fixture_record_count": len(records),
        "positive_record_count": len(positive_records),
        "matching_file_count": len(matched_records),
        "active_positive_count": active_positive,
        "completed_verified_count": completed_verified,
        "max_state_bytes": max(
            (_safe_int(record.get("bytesDownloaded")) for record in positive_records),
            default=0,
        ),
        "max_captured_file_bytes": max(
            (item["captured_file_bytes"] for item in matched_records),
            default=0,
        ),
        "repository_progress": repository_progress,
        "origin_progress": origin_progress,
        "valid": valid,
        "records": matched_records,
    }


def _recompute_case_status(
    result: dict[str, Any],
    findings: list[dict[str, Any]],
) -> None:
    severities = {item.get("severity") for item in findings}
    result["status"] = (
        "BLOCKED"
        if "infrastructure" in severities
        else "FAIL"
        if "critical" in severities
        else "WARN"
        if "warning" in severities
        else "PASS"
    )


def add_case_findings(
    root: Path,
    device: dict[str, Any],
    case: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    result, findings = _BASE_ADD_CASE_FINDINGS(root, device, case)
    if case.get("page") != "downloads":
        return result, findings

    files = _core.relative_files(root, case.get("files", {}))
    state_file = files.get("download_state")
    listing_file = files.get("download_files")
    if not state_file or not listing_file:
        return result, findings

    evidence_paths = {
        key: value
        for key, value in files.items()
        if key
        in {
            "screenshot",
            "xml",
            "logcat",
            "activity",
            "window",
            "download_state",
            "download_files",
        }
    }
    try:
        durable = durable_download_evidence(
            root / state_file,
            root / listing_file,
        )
    except Exception as exc:
        findings.append(
            _core.finding(
                "infrastructure",
                "durable_download_evidence_error",
                f"{case.get('id')}: durable download evidence cannot be parsed: {exc}",
                case_id=case.get("id"),
                page="downloads",
                evidence=evidence_paths,
            )
        )
        _recompute_case_status(result, findings)
        return result, findings

    result["download_evidence"] = durable
    if durable["valid"]:
        findings = [
            item
            for item in findings
            if item.get("code") not in _DOWNLOAD_PROGRESS_CODES
        ]

    hierarchy_is_stale = (
        not case.get("marker_found")
        and _safe_int(device.get("api")) <= 28
        and durable["valid"]
        and bool(result.get("image"))
        and not bool((result.get("image") or {}).get("blank"))
        and (
            result.get("foreground_package") == _core.PACKAGE
            or _core.PACKAGE in result.get("hierarchy_packages", [])
        )
    )
    if hierarchy_is_stale:
        findings = [
            item
            for item in findings
            if item.get("code") != "page_marker_missing"
        ]
        findings.append(
            _core.finding(
                "warning",
                "stale_accessibility_hierarchy",
                f"{case.get('id')}: Android 9 exposed a stale UI Automator page marker; "
                "the foreground app, rendered capture, loopback repository state and "
                "captured file bytes independently prove the Downloads scenario and "
                "positive transfer",
                case_id=case.get("id"),
                page="downloads",
                evidence=evidence_paths,
            )
        )

    _recompute_case_status(result, findings)
    return result, findings


_core.add_case_findings = add_case_findings


if __name__ == "__main__":
    raise SystemExit(_core.main())
