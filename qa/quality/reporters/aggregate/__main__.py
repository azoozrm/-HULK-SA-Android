"""CLI policy for scope-aware Quality Lab aggregate reporting."""

from __future__ import annotations

import json
from pathlib import Path
import sys
from typing import Any

from . import IMPLEMENTATION


def _argument_value(flag: str, default: str = "") -> str:
    try:
        index = sys.argv.index(flag)
    except ValueError:
        return default
    if index + 1 >= len(sys.argv):
        return default
    return sys.argv[index + 1]


def policy_exit_code(
    reporter_exit_code: int,
    test_variant: str,
    summary: dict[str, Any] | None,
) -> int:
    """Return the workflow exit code without changing recorded findings.

    Infrastructure BLOCKED is always fatal. Product FAIL is fatal for normal
    and product-strict variants. A variant explicitly classified as lab-only
    may return success only when the aggregate evidence is complete, contains
    no infrastructure error, and preserves at least one product-critical
    finding in the generated report.
    """

    if reporter_exit_code != 1:
        return reporter_exit_code
    if not test_variant.endswith("-lab-only") or summary is None:
        return reporter_exit_code
    if summary.get("release_recommendation") != "FAIL":
        return reporter_exit_code
    if int(summary.get("infrastructure", 0)) != 0:
        return reporter_exit_code
    if int(summary.get("product_critical", 0)) <= 0:
        return reporter_exit_code
    if summary.get("test_variant") != test_variant:
        return reporter_exit_code
    return 0


def main() -> int:
    reporter_exit_code = int(IMPLEMENTATION.main())
    test_variant = _argument_value("--test-variant", "unspecified")
    output = Path(_argument_value("--output", ""))
    summary_path = output / "SUMMARY.json" if output else Path()
    summary: dict[str, Any] | None = None
    if output and summary_path.is_file():
        summary = json.loads(summary_path.read_text(encoding="utf-8"))

    effective = policy_exit_code(reporter_exit_code, test_variant, summary)
    if reporter_exit_code == 1 and effective == 0 and summary is not None:
        print(
            "DETECTED: "
            f"{summary['product_critical']} product-critical finding(s) remain "
            "preserved in complete aggregate evidence; lab-only infrastructure "
            "qualification may continue."
        )
    return effective


if __name__ == "__main__":
    raise SystemExit(main())
