#!/usr/bin/env python3
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

from verifier import canonical_bytes, gate, load_bundle, load_corpus, save_report, verify_bundle


def cmd_replay(args) -> int:
    expected_bytes = None
    final = None
    for _ in range(args.repeat):
        reports = [verify_bundle(bundle) for bundle in load_corpus(args.corpus)]
        payload = {"schema_version": 1, "reports": reports, "strict_gate": gate(reports, True), "report_only_gate": gate(reports, False)}
        encoded = canonical_bytes(payload)
        if expected_bytes is None:
            expected_bytes = encoded
        elif encoded != expected_bytes:
            raise SystemExit("non-deterministic replay output")
        final = payload
    assert final is not None
    args.out.mkdir(parents=True, exist_ok=True)
    save_report(args.out / "replay-report.json", final)
    (args.out / "replay-report.sha256").write_text(__import__("hashlib").sha256(expected_bytes).hexdigest() + "  replay-report.json\n", encoding="utf-8")
    print(f"PASS: {len(final['reports'])} corpus cases replayed {args.repeat} times byte-for-byte")
    return 0


def cmd_verify(args) -> int:
    report = verify_bundle(load_bundle(args.bundle))
    sys.stdout.buffer.write(canonical_bytes(report))
    return 0 if report["outcome"] == "PASS" else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(required=True)
    replay = sub.add_parser("replay")
    replay.add_argument("--corpus", type=Path, required=True)
    replay.add_argument("--repeat", type=int, default=5)
    replay.add_argument("--out", type=Path, required=True)
    replay.set_defaults(func=cmd_replay)
    verify = sub.add_parser("verify-bundle")
    verify.add_argument("bundle", type=Path)
    verify.set_defaults(func=cmd_verify)
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
