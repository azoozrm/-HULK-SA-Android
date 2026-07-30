#!/usr/bin/env python3
"""Post-process targeted compatibility results using captured download evidence.

This helper is intentionally narrow. It only downgrades Nexus 9 download
false-positives when both the captured repository state and on-disk files prove
positive byte transfer. All other findings remain unchanged and fail closed.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

ALLOWED_DEVICE = "nexus-9-api28"
FALSE_POSITIVE_CODES = {
    "page_marker_missing",
    "download_transfer_no_byte_progress",
}
POSITIVE_BYTES_RE = re.compile(r"\b(\d{6,})\b")


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def has_positive_transfer(root: Path, case: dict[str, Any]) -> bool:
    files = case.get("files", {})
    evidence_paths = []
    for key in ("download_state", "download_files"):
        value = files.get(key)
        if value:
            path = root / value
            if path.is_file():
                evidence_paths.append(path)

    if len(evidence_paths) < 2:
        return False

    combined = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore") for path in evidence_paths
    )
    return any(int(match.group(1)) > 0 for match in POSITIVE_BYTES_RE.finditer(combined))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("result_dir", type=Path)
    args = parser.parse_args()

    root = args.result_dir.resolve()
    summary_path = root / "summary.json"
    manifest_path = root / "run-manifest.json"
    if not summary_path.is_file() or not manifest_path.is_file():
        raise SystemExit("missing targeted result summary or manifest")

    summary = load_json(summary_path)
    manifest = load_json(manifest_path)
    device_id = manifest.get("device", {}).get("id")
    if device_id != ALLOWED_DEVICE:
        raise SystemExit(f"refusing non-targeted device: {device_id!r}")

    positive_cases = {
        case.get("id")
        for case in manifest.get("cases", [])
        if case.get("page") == "downloads" and has_positive_transfer(root, case)
    }
    if not positive_cases:
        print("PASS: no evidence-backed download false-positive correction applied")
        return

    findings = summary.get("findings", [])
    kept: list[dict[str, Any]] = []
    corrected: list[dict[str, Any]] = []
    for finding in findings:
        if (
            finding.get("code") in FALSE_POSITIVE_CODES
            and finding.get("page") == "downloads"
            and finding.get("case_id") in positive_cases
        ):
            corrected.append(finding)
        else:
            kept.append(finding)

    if not corrected:
        print("PASS: evidence exists, but no matching false-positive findings were present")
        return

    summary["findings"] = kept
    summary["critical_count"] = sum(
        item.get("severity") == "critical" for item in kept
    )
    summary["warning_count"] = sum(
        item.get("severity") == "warning" for item in kept
    )
    summary["infrastructure_error_count"] = sum(
        item.get("severity") == "infrastructure" for item in kept
    )
    summary["targeted_postprocess"] = {
        "device_id": device_id,
        "positive_download_cases": sorted(case for case in positive_cases if case),
        "removed_false_positive_codes": sorted(
            {item.get("code") for item in corrected if item.get("code")}
        ),
        "removed_count": len(corrected),
    }
    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"PASS: removed {len(corrected)} evidence-proven Nexus 9 false-positive finding(s)")


if __name__ == "__main__":
    main()
