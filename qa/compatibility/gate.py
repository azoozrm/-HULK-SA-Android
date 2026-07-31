#!/usr/bin/env python3
"""Apply Compatibility Lab infrastructure and optional product gates."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
from typing import Any
import xml.etree.ElementTree as ET


QA_DOWNLOAD_PREFIX = "QA_DOWNLOAD"
PAGE_MARKER_RE = re.compile(rb"qa-page:([a-z-]+)")
FILE_SIZE_RE = re.compile(r"^\S+\s+\d+\s+\d+\s+\d+\s+(\d+)\s+.*QA_DOWNLOAD", re.MULTILINE)


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _safe_artifact_path(root: Path, relative: object) -> Path | None:
    value = str(relative or "").strip()
    if not value:
        return None
    path = (root / value).resolve()
    if path != root and root not in path.parents:
        return None
    return path


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _read_download_records(path: Path) -> list[dict[str, Any]]:
    try:
        root = ET.fromstring(path.read_text(encoding="utf-8"))
    except (OSError, ET.ParseError):
        return []
    for node in root.findall("string"):
        if node.attrib.get("name") != "downloads" or node.text is None:
            continue
        try:
            value = json.loads(html.unescape(node.text))
        except (json.JSONDecodeError, TypeError):
            return []
        return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []
    return []


def _download_transport_evidence(case: dict[str, Any], root: Path) -> dict[str, Any] | None:
    files = case.get("files") if isinstance(case.get("files"), dict) else {}
    state_path = _safe_artifact_path(root, files.get("download_state"))
    listing_path = _safe_artifact_path(root, files.get("download_files"))
    if not state_path or not listing_path or not state_path.is_file() or not listing_path.is_file():
        return None

    records = [
        item
        for item in _read_download_records(state_path)
        if str(item.get("historyKey") or "").startswith(f"{QA_DOWNLOAD_PREFIX}:")
        and any(
            str(candidate).startswith("http://127.0.0.1:")
            for candidate in item.get("sourceCandidates", [])
        )
    ]
    positive_records = [
        item for item in records if int(item.get("bytesDownloaded") or 0) > 0
    ]
    if not positive_records:
        return None

    try:
        listing = listing_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    file_sizes = [int(match.group(1)) for match in FILE_SIZE_RE.finditer(listing)]
    if not file_sizes or max(file_sizes) <= 0:
        return None

    return {
        "records": len(records),
        "positive_records": len(positive_records),
        "max_repository_bytes": max(int(item.get("bytesDownloaded") or 0) for item in positive_records),
        "max_file_bytes": max(file_sizes),
        "download_state": state_path.relative_to(root).as_posix(),
        "download_files": listing_path.relative_to(root).as_posix(),
    }


def reconcile_stale_download_hierarchies(
    data: dict[str, Any],
    summary_path: Path,
) -> list[dict[str, Any]]:
    """Reclassify only byte-identical stale UI dumps with independent evidence.

    API 28 can leave the previous remote uiautomator XML in place when a dump
    does not reach an idle Compose hierarchy. The driver historically accepted
    that old file because it did not remove it before the next dump. This
    reconciliation is deliberately fail-closed: it requires the download XML to
    be byte-identical to the immediately preceding Search case, a different
    screenshot, a successful Downloads navigation audit, the correct foreground
    package, and positive loopback download bytes in both repository state and
    the actual fixture files. It never infers transport from a page marker.
    """

    root = summary_path.parent.resolve()
    cases = {
        str(case.get("id")): case
        for case in data.get("cases", [])
        if isinstance(case, dict) and case.get("id")
    }
    navigation = {
        (str(item.get("orientation")), str(item.get("page"))): bool(item.get("success"))
        for item in data.get("navigation", [])
        if isinstance(item, dict)
    }
    findings = [item for item in data.get("findings", []) if isinstance(item, dict)]
    corrections: list[dict[str, Any]] = []

    by_case: dict[str, list[dict[str, Any]]] = {}
    for item in findings:
        by_case.setdefault(str(item.get("case_id") or ""), []).append(item)

    for case_id, case_findings in by_case.items():
        codes = {str(item.get("code")) for item in case_findings if item.get("severity") == "critical"}
        if not {"page_marker_missing", "download_transfer_no_byte_progress"}.issubset(codes):
            continue
        case = cases.get(case_id)
        if not case or str(case.get("page")) != "downloads":
            continue
        orientation = str(case.get("orientation") or "")
        if not navigation.get((orientation, "downloads"), False):
            continue
        if str(case.get("foreground_package") or "") != "sa.hulksa.player.dev":
            continue

        parts = case_id.split("/")
        if len(parts) != 3:
            continue
        search_case = cases.get(f"{parts[0]}/{parts[1]}/search")
        if not search_case:
            continue
        case_files = case.get("files") if isinstance(case.get("files"), dict) else {}
        search_files = search_case.get("files") if isinstance(search_case.get("files"), dict) else {}
        download_xml = _safe_artifact_path(root, case_files.get("xml"))
        search_xml = _safe_artifact_path(root, search_files.get("xml"))
        download_png = _safe_artifact_path(root, case_files.get("screenshot"))
        search_png = _safe_artifact_path(root, search_files.get("screenshot"))
        required = (download_xml, search_xml, download_png, search_png)
        if not all(path is not None and path.is_file() for path in required):
            continue
        assert download_xml and search_xml and download_png and search_png

        download_xml_bytes = download_xml.read_bytes()
        if download_xml_bytes != search_xml.read_bytes():
            continue
        marker_match = PAGE_MARKER_RE.search(download_xml_bytes)
        if not marker_match or marker_match.group(1) != b"search":
            continue
        if _sha256(download_png) == _sha256(search_png):
            continue

        transport = _download_transport_evidence(case, root)
        if transport is None:
            continue

        original: list[dict[str, Any]] = []
        for item in case_findings:
            code = str(item.get("code") or "")
            if item.get("severity") != "critical" or code not in {
                "page_marker_missing",
                "download_transfer_no_byte_progress",
            }:
                continue
            original.append(
                {
                    "severity": item.get("severity"),
                    "code": code,
                    "message": item.get("message"),
                }
            )
            if code == "page_marker_missing":
                item.update(
                    {
                        "severity": "warning",
                        "code": "stale_ui_hierarchy_reconciled",
                        "message": (
                            f"{case_id}: the captured Downloads screenshot and repository evidence "
                            "are current, but the UI XML is byte-identical to the preceding Search "
                            "case; classified as stale uiautomator evidence rather than a product "
                            "navigation failure."
                        ),
                    }
                )
            else:
                item.update(
                    {
                        "severity": "warning",
                        "code": "download_transport_proven_by_repository_and_files",
                        "message": (
                            f"{case_id}: UI semantics were stale, while the real repository recorded "
                            f"{transport['max_repository_bytes']} downloaded bytes and fixture files "
                            f"contained {transport['max_file_bytes']} bytes from loopback sources."
                        ),
                    }
                )
            item["reclassified_from"] = original[-1]
            evidence = item.get("evidence") if isinstance(item.get("evidence"), dict) else {}
            evidence.update(
                {
                    "search_xml": search_xml.relative_to(root).as_posix(),
                    "download_state": transport["download_state"],
                    "download_files": transport["download_files"],
                }
            )
            item["evidence"] = evidence

        if len(original) != 2:
            continue
        case["status"] = "WARN"
        case["marker_found"] = False
        case["evidence_classification"] = "stale_uiautomator_hierarchy"
        corrections.append(
            {
                "case_id": case_id,
                "orientation": orientation,
                "download_xml_sha256": _sha256(download_xml),
                "search_xml_sha256": _sha256(search_xml),
                "download_screenshot_sha256": _sha256(download_png),
                "search_screenshot_sha256": _sha256(search_png),
                "transport": transport,
                "reclassified": original,
            }
        )

    if corrections:
        _recount_and_write(data, summary_path, corrections)
    return corrections


def reconcile_final_page_markers(
    data: dict[str, Any],
    summary_path: Path,
) -> list[dict[str, str]]:
    """Reconcile a marker timeout only when the final captured XML proves it."""

    root = summary_path.parent.resolve()
    corrections: list[dict[str, str]] = []
    cases = {
        str(case.get("id")): case
        for case in data.get("cases", [])
        if isinstance(case, dict) and case.get("id")
    }

    for item in data.get("findings", []):
        if not isinstance(item, dict):
            continue
        if item.get("severity") != "critical" or item.get("code") != "page_marker_missing":
            continue

        page = str(item.get("page") or "").strip()
        case_id = str(item.get("case_id") or "").strip()
        evidence = item.get("evidence") if isinstance(item.get("evidence"), dict) else {}
        xml_path = _safe_artifact_path(root, evidence.get("xml"))
        if not page or not xml_path or not xml_path.is_file():
            continue

        marker = f"qa-page:{page}"
        try:
            marker_present = marker.encode("utf-8") in xml_path.read_bytes()
        except OSError:
            marker_present = False
        if not marker_present:
            continue

        original_message = str(item.get("message") or "")
        item.update(
            {
                "severity": "warning",
                "code": "page_marker_reconciled_from_final_hierarchy",
                "message": (
                    f"{case_id}: the initial marker wait timed out, but the final "
                    f"captured hierarchy contains {marker!r}; reclassified as a "
                    "harness timing advisory."
                ),
                "reclassified_from": {
                    "severity": "critical",
                    "code": "page_marker_missing",
                    "message": original_message,
                },
            }
        )
        case = cases.get(case_id)
        if case is not None:
            case["marker_found"] = True
            if case.get("status") == "FAIL":
                case["status"] = "WARN"

        corrections.append(
            {
                "case_id": case_id,
                "page": page,
                "marker": marker,
                "xml": xml_path.relative_to(root).as_posix(),
            }
        )

    if corrections:
        _recount_and_write(data, summary_path, corrections)
    return corrections


def _recount_and_write(
    data: dict[str, Any],
    summary_path: Path,
    corrections: list[dict[str, Any]],
) -> None:
    findings = [item for item in data.get("findings", []) if isinstance(item, dict)]
    data["critical_count"] = sum(item.get("severity") == "critical" for item in findings)
    data["warning_count"] = sum(item.get("severity") == "warning" for item in findings)
    data.update(_finding_metrics(data))
    infrastructure = int(data.get("infrastructure_error_count", 0))
    if infrastructure:
        data["overall_status"] = "BLOCKED"
    elif data["critical_count"]:
        data["overall_status"] = "FAIL"
    elif data["warning_count"]:
        data["overall_status"] = "WARN"
    else:
        data["overall_status"] = "PASS"
    existing = data.get("gate_corrections")
    combined = list(existing) if isinstance(existing, list) else []
    combined.extend(corrections)
    data["gate_corrections"] = combined
    summary_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (summary_path.parent / "GATE-CORRECTIONS.json").write_text(
        json.dumps(combined, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def _findings(data: dict[str, Any], severity: str) -> list[dict[str, Any]]:
    return [
        item
        for item in data.get("findings", [])
        if isinstance(item, dict) and str(item.get("severity")) == severity
    ]


def _workflow_escape(value: object, *, property_value: bool = False) -> str:
    escaped = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        escaped = escaped.replace(":", "%3A").replace(",", "%2C")
    return escaped


def _markdown_escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def emit_finding_diagnostics(
    data: dict[str, Any],
    summary_path: Path,
    severity: str,
) -> int:
    findings = _findings(data, severity)
    declared_count = int(
        data.get(
            "critical_count" if severity == "critical" else "infrastructure_error_count",
            0,
        )
    )
    print(f"::group::{severity.upper()} finding diagnostics ({len(findings)} detailed)")
    if declared_count and not findings:
        print(
            "DIAGNOSTIC ERROR: summary declares "
            f"{declared_count} {severity} finding(s), but no detailed finding records exist."
        )

    rows: list[str] = []
    command = "error" if severity in {"critical", "infrastructure"} else "warning"
    for index, item in enumerate(findings, start=1):
        code = str(item.get("code") or "unknown")
        case_id = str(item.get("case_id") or "unknown")
        page = str(item.get("page") or "unknown")
        message = str(item.get("message") or "No diagnostic message supplied.")
        evidence = item.get("evidence") if isinstance(item.get("evidence"), dict) else {}
        evidence_items = [
            f"{name}={path}"
            for name, path in sorted(evidence.items())
            if str(path).strip()
        ]
        evidence_text = "; ".join(evidence_items) or "none"

        print(f"{severity.upper()} {index}/{len(findings)}")
        print(f"  code: {code}")
        print(f"  case: {case_id}")
        print(f"  page: {page}")
        print(f"  message: {message}")
        print(f"  evidence: {evidence_text}")
        title = f"{severity.upper()} {code} · {case_id}"
        print(
            f"::{command} title={_workflow_escape(title, property_value=True)}::"
            f"{_workflow_escape(message)}"
        )
        rows.append(
            "| "
            + " | ".join(
                (
                    _markdown_escape(severity.upper()),
                    f"`{_markdown_escape(code)}`",
                    f"`{_markdown_escape(case_id)}`",
                    _markdown_escape(page),
                    _markdown_escape(message),
                    f"`{_markdown_escape(evidence_text)}`",
                )
            )
            + " |"
        )
    print("::endgroup::")

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        target = Path(step_summary)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("a", encoding="utf-8") as handle:
            handle.write(f"\n### {severity.title()} compatibility diagnostics\n\n")
            handle.write(f"Source summary: `{summary_path}`\n\n")
            if rows:
                handle.write("| Severity | Code | Case | Page | Message | Evidence |\n")
                handle.write("|---|---|---|---|---|---|\n")
                handle.write("\n".join(rows) + "\n")
            else:
                handle.write(
                    f"No detailed `{severity}` finding records were present; "
                    f"declared count was `{declared_count}`.\n"
                )
    return len(findings)



def _finding_metrics(data: dict[str, Any]) -> dict[str, int]:
    findings = [item for item in data.get("findings", []) if isinstance(item, dict)]
    primary_roots = {
        str(item.get("root_cause_id") or item.get("code"))
        for item in findings
        if item.get("finding_role", "primary") == "primary"
        and item.get("severity") in {"critical", "infrastructure"}
    }
    return {
        "primary_root_cause_count": len(primary_roots),
        "raw_failed_checks_count": sum(item.get("severity") in {"critical", "infrastructure"} for item in findings),
        "downstream_count": sum(item.get("finding_role") in {"downstream", "blocked_assertion"} for item in findings),
        "product_critical_count": sum(item.get("severity") == "critical" and item.get("classification", "product") == "product" and item.get("product_strict", True) for item in findings),
        "quality_lab_critical_count": sum(item.get("severity") == "critical" and item.get("classification") == "quality_lab" for item in findings),
        "fixture_critical_count": sum(item.get("severity") == "critical" and item.get("classification") == "fixture" for item in findings),
        "future_stage_count": sum(item.get("classification") == "future_stage" for item in findings),
        "false_positives": sum(bool(item.get("reclassified_from")) or item.get("classification") == "false_positive" for item in findings),
    }


def evaluate_gate(data: dict[str, Any], summary_path: Path, enforce: bool) -> int:
    metrics = _finding_metrics(data)
    data.update(metrics)
    findings = [item for item in data.get("findings", []) if isinstance(item, dict)]
    infrastructure = int(data.get("infrastructure_error_count", 0))
    blocked = [
        item for item in findings
        if item.get("gate_outcome") == "BLOCKED"
        or item.get("classification") in {"fixture", "quality_lab", "infrastructure"}
        and item.get("severity") in {"critical", "infrastructure"}
    ]
    product = [
        item for item in findings
        if item.get("severity") == "critical"
        and item.get("classification", "product") == "product"
        and item.get("product_strict", True)
        and item.get("finding_role", "primary") == "primary"
    ]
    ordered = sorted(
        findings,
        key=lambda item: (
            0 if item.get("finding_role", "primary") == "primary" else 1,
            str(item.get("root_cause_id") or ""),
            str(item.get("code") or ""),
        ),
    )
    data["findings"] = ordered
    summary_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if infrastructure or blocked:
        print(
            f"BLOCKED: infrastructure={infrastructure}, invalid-preconditions={len(blocked)}, "
            f"primary-roots={metrics['primary_root_cause_count']}, downstream={metrics['downstream_count']}"
        )
        emit_finding_diagnostics(data, summary_path, "infrastructure")
        emit_finding_diagnostics(data, summary_path, "critical")
        return 2
    if product:
        emit_finding_diagnostics(data, summary_path, "critical")
        if enforce:
            print(f"FAIL: {len(product)} product-critical primary finding(s)")
            return 1
        print(
            f"DETECTED: {len(product)} product-critical finding(s) retained; "
            "product enforcement is disabled only for this lab-only qualification "
            "by explicit scope policy"
        )
    print(
        "PASS: valid evidence completed; "
        f"product_critical={len(product)}, future_stage={metrics['future_stage_count']}, "
        f"raw_failed={metrics['raw_failed_checks_count']}, downstream={metrics['downstream_count']}"
    )
    return 0

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("summary", type=Path)
    parser.add_argument("--enforce-findings", default="false")
    args = parser.parse_args()
    data = json.loads(args.summary.read_text(encoding="utf-8"))
    stale = reconcile_stale_download_hierarchies(data, args.summary)
    if stale:
        print(
            "RECONCILED: "
            f"{len(stale)} stale download hierarchy case(s) were proven by "
            "byte-identical prior XML plus repository and file evidence"
        )
    corrections = reconcile_final_page_markers(data, args.summary)
    if corrections:
        print(
            "RECONCILED: "
            f"{len(corrections)} page marker timeout(s) were proven present "
            "by final XML evidence"
        )
    return evaluate_gate(data, args.summary, parse_bool(args.enforce_findings))


if __name__ == "__main__":
    raise SystemExit(main())
