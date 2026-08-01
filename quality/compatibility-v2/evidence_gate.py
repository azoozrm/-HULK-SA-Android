#!/usr/bin/env python3
"""Verify that mandatory Compatibility V2 evidence exists and is non-empty."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


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
