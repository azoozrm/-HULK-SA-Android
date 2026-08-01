#!/usr/bin/env python3
"""Independent verifier facade with cross-source runtime evidence reconciliation."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys
from typing import Any

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "independent_lab_verifier_core",
    HERE / "verifier_core.py",
)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load verifier_core.py")
CORE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CORE
SPEC.loader.exec_module(CORE)

for _name in dir(CORE):
    if not _name.startswith("__"):
        globals().setdefault(_name, getattr(CORE, _name))

_core_verify_bundle = CORE.verify_bundle


def verify_bundle(bundle: dict[str, Any]) -> dict[str, Any]:
    """Require independent focus agreement between the event trace and UI XML."""
    result = _core_verify_bundle(bundle)
    if result.get("outcome") != CORE.Outcome.PASS.value:
        return result

    files = dict(bundle.get("files") or {})
    expected = dict(bundle.get("expected") or {})
    expected_target = str(expected.get("target") or "").strip()
    if not expected_target:
        return result
    if "ui.xml" not in files or "focus-events.log" not in files:
        return result

    try:
        xml = CORE.parse_ui_xml(files["ui.xml"])
    except Exception as exc:
        finding = CORE.Finding(
            "FOCUS_XML_PARSE_FAILURE",
            CORE.Outcome.FAIL_LAB,
            str(exc),
            "focus-evidence",
        )
        return CORE.report(bundle, [finding], files)

    xml_targets = [
        label
        for label in xml.get("focused", [])
        if isinstance(label, str) and label.strip()
    ]
    trace_targets = CORE.FOCUS_RE.findall(files["focus-events.log"])
    trace_stable = (
        len(trace_targets) >= 2
        and trace_targets[-1] == trace_targets[-2] == expected_target
    )
    xml_matches = any(expected_target in label for label in xml_targets)
    if not trace_stable or not xml_matches:
        finding = CORE.Finding(
            "FOCUS_EVIDENCE_MISMATCH",
            CORE.Outcome.FAIL_LAB,
            (
                f"expected target {expected_target!r}; "
                f"trace={trace_targets[-3:]}; xml_focused={xml_targets}"
            ),
            "focus-evidence",
        )
        return CORE.report(bundle, [finding], files)
    return result


CORE.verify_bundle = verify_bundle
