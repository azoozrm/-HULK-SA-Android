#!/usr/bin/env python3
"""Convert Android am instrument status output into honest JUnit XML."""

from __future__ import annotations

import argparse
import html
import re
import sys
from dataclasses import dataclass
from pathlib import Path


INFRASTRUCTURE_FAILURE_PATTERN = re.compile(
    r"INSTRUMENTATION_FAILED:.*|Process crashed.*|FAILURES!!!.*",
    re.DOTALL,
)


@dataclass
class TestCase:
    class_name: str
    name: str
    status: str = "RUNNING"
    detail: str = ""


def parse_instrumentation(
    text: str,
    process_status: int,
    *,
    timed_out: bool = False,
    timeout_seconds: int | None = None,
) -> list[TestCase]:
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
            elif code in (-3, -4):
                case.status = "SKIPPED"
                case.detail = pending.get("stack") or pending.get("stream") or (
                    "ignored" if code == -3 else "assumption not satisfied"
                )
            pending = {}

    infrastructure_failure = INFRASTRUCTURE_FAILURE_PATTERN.search(text)
    if not cases:
        if timed_out:
            detail = _timeout_detail(timeout_seconds)
        elif infrastructure_failure is not None:
            detail = infrastructure_failure.group(0)[:4000]
        elif process_status != 0:
            detail = text[-4000:] or f"Instrumentation exited with status {process_status} without a terminal test result"
        else:
            detail = "Instrumentation produced no terminal per-test status records"
        name = "instrumentation-timeout" if timed_out else current_test
        cases[(current_class, name)] = TestCase(current_class, name, "FAIL", detail)

    for case in cases.values():
        if case.status == "RUNNING":
            case.status = "FAIL"
            case.detail = "Instrumentation ended before a terminal result was reported"

    if timed_out and not any(case.name == "instrumentation-timeout" for case in cases.values()):
        key = ("android.instrumentation", "instrumentation-timeout")
        cases[key] = TestCase(key[0], key[1], "FAIL", _timeout_detail(timeout_seconds))

    if infrastructure_failure is not None and not any(case.status == "FAIL" for case in cases.values()):
        key = ("android.instrumentation", "instrumentation-run")
        cases[key] = TestCase(key[0], key[1], "FAIL", infrastructure_failure.group(0)[:4000])

    return list(cases.values())


def _timeout_detail(timeout_seconds: int | None) -> str:
    if timeout_seconds is None:
        return "Instrumentation exceeded its bounded execution timeout"
    return f"Instrumentation exceeded its bounded execution timeout of {timeout_seconds} seconds"


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
    parser.add_argument("--timed-out", action="store_true")
    parser.add_argument("--timeout-seconds", type=int)
    args = parser.parse_args(argv)
    cases = parse_instrumentation(
        args.input.read_text(encoding="utf-8", errors="replace"),
        args.process_status,
        timed_out=args.timed_out,
        timeout_seconds=args.timeout_seconds,
    )
    write_junit(cases, args.output)
    return 1 if any(case.status == "FAIL" for case in cases) else 0


if __name__ == "__main__":
    sys.exit(main())
