#!/usr/bin/env python3
"""Convert Android am instrument status output into honest JUnit XML."""

from __future__ import annotations

import argparse
import html
import re
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass
class TestCase:
    class_name: str
    name: str
    status: str = "RUNNING"
    detail: str = ""


def parse_instrumentation(text: str, process_status: int) -> list[TestCase]:
    current_class = "android.instrumentation"
    current_test = "instrumentation-class"
    cases: dict[tuple[str, str], TestCase] = {}
    pending: dict[str, str] = {}

    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            pending[key.strip()] = value.strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.rsplit(":", 1)[1].strip())
            current_class = pending.get("class", current_class)
            current_test = pending.get("test", current_test)
            key = (current_class, current_test)
            case = cases.setdefault(key, TestCase(current_class, current_test))
            if code == 1:
                case.status = "RUNNING"
            elif code == 0:
                case.status = "PASS"
            elif code in (-1, -2):
                case.status = "FAIL"
                case.detail = pending.get("stack") or pending.get("stream") or f"instrumentation status code {code}"
            elif code == -3:
                case.status = "SKIPPED"
                case.detail = pending.get("stream", "ignored")
            pending = {}

    if not cases:
        detail_match = re.search(r"INSTRUMENTATION_FAILED:.*|Process crashed.*|FAILURES!!!.*", text, re.DOTALL)
        status = "FAIL" if process_status else "PASS"
        detail = detail_match.group(0)[:4000] if detail_match else ("No per-test status records" if process_status == 0 else text[-4000:])
        cases[(current_class, current_test)] = TestCase(current_class, current_test, status, detail)

    for case in cases.values():
        if case.status == "RUNNING":
            case.status = "FAIL" if process_status else "PASS"
            if process_status:
                case.detail = "Instrumentation ended before a terminal result was reported"
    return list(cases.values())


def write_junit(cases: list[TestCase], output: Path) -> None:
    failures = sum(case.status == "FAIL" for case in cases)
    skipped = sum(case.status == "SKIPPED" for case in cases)
    lines = [f'<testsuite name="compatibility-v2-instrumentation" tests="{len(cases)}" failures="{failures}" skipped="{skipped}">']
    for case in cases:
        lines.append(f'  <testcase classname="{html.escape(case.class_name, quote=True)}" name="{html.escape(case.name, quote=True)}">')
        if case.status == "FAIL":
            lines.append(f'    <failure message="instrumentation failure">{html.escape(case.detail)}</failure>')
        elif case.status == "SKIPPED":
            lines.append(f'    <skipped message="{html.escape(case.detail, quote=True)}" />')
        lines.append("  </testcase>")
    lines.append("</testsuite>")
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--process-status", type=int, required=True)
    args = parser.parse_args(argv)
    cases = parse_instrumentation(args.input.read_text(encoding="utf-8", errors="replace"), args.process_status)
    write_junit(cases, args.output)
    return 1 if any(case.status == "FAIL" for case in cases) else 0


if __name__ == "__main__":
    sys.exit(main())
