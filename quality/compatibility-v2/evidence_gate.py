#!/usr/bin/env python3
"""Verify mandatory Compatibility V2 evidence and its runtime meaning."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


APP_PACKAGE = "sa.hulksa.player.dev"


@dataclass(frozen=True)
class EvidenceCheck:
    id: str
    status: str
    message: str
    evidence: list[str]


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_text_contains(path: Path, required: list[str], check_id: str) -> EvidenceCheck:
    if not path.is_file() or path.stat().st_size == 0:
        return EvidenceCheck(check_id, "FAIL", "Semantic source file is missing or empty", [str(path)])
    text = path.read_text(encoding="utf-8", errors="replace")
    missing = [value for value in required if value not in text]
    if missing:
        return EvidenceCheck(check_id, "FAIL", f"Missing required content: {', '.join(missing)}", [str(path)])
    return EvidenceCheck(check_id, "PASS", "Required semantic content is present", [str(path)])


def check_instrumentation_junit(path: Path) -> EvidenceCheck:
    try:
        root = ET.parse(path).getroot()
        tests = int(root.attrib.get("tests", "0"))
        failures = int(root.attrib.get("failures", "0"))
        errors = int(root.attrib.get("errors", "0"))
        skipped = int(root.attrib.get("skipped", "0"))
    except (OSError, ET.ParseError, ValueError) as exc:
        return EvidenceCheck("runtime-junit", "FAIL", f"Invalid instrumentation JUnit XML: {exc}", [str(path)])
    if tests <= 0 or failures != 0 or errors != 0:
        return EvidenceCheck(
            "runtime-junit",
            "FAIL",
            f"Instrumentation results are not clean: tests={tests}, failures={failures}, errors={errors}, skipped={skipped}",
            [str(path)],
        )
    return EvidenceCheck(
        "runtime-junit",
        "PASS",
        f"Instrumentation results are clean: tests={tests}, failures=0, errors=0, skipped={skipped}",
        [str(path)],
    )


def check_png(path: Path) -> EvidenceCheck:
    try:
        header = path.read_bytes()[:24]
        if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
            raise ValueError("invalid PNG signature or IHDR")
        width, height = struct.unpack(">II", header[16:24])
    except (OSError, ValueError, struct.error) as exc:
        return EvidenceCheck("runtime-full-window-png", "FAIL", f"Invalid full-window PNG: {exc}", [str(path)])
    if width <= 0 or height <= 0:
        return EvidenceCheck("runtime-full-window-png", "FAIL", "PNG dimensions are zero", [str(path)])
    return EvidenceCheck("runtime-full-window-png", "PASS", f"Valid full-window PNG: {width}x{height}", [str(path)])


def check_checksums(root: Path, manifest: Path) -> EvidenceCheck:
    if not manifest.is_file() or manifest.stat().st_size == 0:
        return EvidenceCheck("runtime-checksums", "FAIL", "Checksum manifest is missing or empty", [str(manifest)])
    failures: list[str] = []
    checked = 0
    for line in manifest.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        digest, separator, relative = line.partition("  ")
        if not separator or len(digest) != 64:
            failures.append(f"malformed line: {line[:120]}")
            continue
        path = root / relative.lstrip("./")
        if not path.is_file():
            failures.append(f"missing: {relative}")
            continue
        checked += 1
        if file_sha256(path) != digest:
            failures.append(f"digest mismatch: {relative}")
    if checked == 0 or failures:
        detail = "; ".join(failures[:10]) if failures else "no files were checksummed"
        return EvidenceCheck("runtime-checksums", "FAIL", detail, [str(manifest)])
    return EvidenceCheck("runtime-checksums", "PASS", f"Verified {checked} evidence checksums", [str(manifest)])


def runtime_semantic_checks(evidence_root: Path) -> list[EvidenceCheck]:
    return [
        check_text_contains(
            evidence_root / "PROFILE-CONFIG.txt",
            ["result=PASS", "profile_verified=true", "locale_mode="],
            "runtime-profile-contract",
        ),
        check_text_contains(
            evidence_root / "APPLICATION-LOCALE.txt",
            ["result=PASS", "locale_verified=true", "requested_locale="],
            "runtime-effective-locale",
        ),
        check_instrumentation_junit(evidence_root / "INSTRUMENTATION.xml"),
        check_text_contains(
            evidence_root / "FOREGROUND-APP.txt",
            [f"package={APP_PACKAGE}", "Status: ok", "resolved_activity="],
            "runtime-foreground-launch",
        ),
        check_text_contains(
            evidence_root / "ACTIVITY-TOP.txt",
            [APP_PACKAGE],
            "runtime-foreground-activity",
        ),
        check_text_contains(
            evidence_root / "window.xml",
            [f'package="{APP_PACKAGE}"'],
            "runtime-window-package",
        ),
        check_png(evidence_root / "full-window.png"),
        check_checksums(evidence_root, evidence_root / "SHA256SUMS.txt"),
    ]


def gate_evidence(spec_file: Path, scope: str, evidence_root: Path) -> list[EvidenceCheck]:
    spec = json.loads(spec_file.read_text(encoding="utf-8"))
    scopes = spec.get("scopes", {})
    if scope not in scopes:
        return [EvidenceCheck("scope", "FAIL", f"Unknown evidence scope: {scope}", [str(spec_file)])]

    checks: list[EvidenceCheck] = []
    for relative in scopes[scope]:
        path = evidence_root / relative
        if not path.is_file():
            checks.append(EvidenceCheck(relative, "FAIL", "Mandatory evidence file is missing", [str(path)]))
        elif path.stat().st_size == 0:
            checks.append(EvidenceCheck(relative, "FAIL", "Mandatory evidence file is empty", [str(path)]))
        else:
            checks.append(EvidenceCheck(relative, "PASS", f"Evidence present ({path.stat().st_size} bytes)", [str(path)]))
    if scope == "runtime":
        checks.extend(runtime_semantic_checks(evidence_root))
    return checks


def write_result(checks: list[EvidenceCheck], out_dir: Path, scope: str) -> dict[str, object]:
    out_dir.mkdir(parents=True, exist_ok=True)
    summary = {status: sum(item.status == status for item in checks) for status in ("PASS", "FAIL", "BLOCKED", "SKIPPED")}
    payload = {
        "schema_version": 2,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "summary": summary,
        "checks": [asdict(item) for item in checks],
    }
    (out_dir / f"EVIDENCE-GATE-{scope}.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    lines = [f"# Evidence gate: {scope}", ""] + [f"- **{item.status}** `{item.id}` — {item.message}" for item in checks]
    (out_dir / f"EVIDENCE-GATE-{scope}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    present = sorted(path for path in out_dir.parent.rglob("*") if path.is_file() and out_dir not in path.parents)
    with (out_dir / "EVIDENCE-SHA256SUMS.txt").open("w", encoding="utf-8") as handle:
        for path in present:
            handle.write(f"{file_sha256(path)}  {path.relative_to(out_dir.parent)}\n")
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scope", required=True)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--spec", type=Path, default=Path("quality/compatibility-v2/config/evidence-spec.json"))
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)
    checks = gate_evidence(args.spec, args.scope, args.evidence_root)
    payload = write_result(checks, args.out, args.scope)
    print(json.dumps(payload["summary"], sort_keys=True))
    return 1 if payload["summary"]["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())
