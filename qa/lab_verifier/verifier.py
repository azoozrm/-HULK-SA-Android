#!/usr/bin/env python3
"""Independent verifier facade with cross-source runtime evidence reconciliation."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
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


def _failure(bundle: dict[str, Any], files: dict[str, str], code: str, message: str, root: str) -> dict[str, Any]:
    return CORE.report(
        bundle,
        [CORE.Finding(code, CORE.Outcome.FAIL_LAB, message, root)],
        files,
    )


def verify_bundle(bundle: dict[str, Any]) -> dict[str, Any]:
    """Require agreement across focus, device, provenance and retry evidence."""
    result = _core_verify_bundle(bundle)
    if result.get("outcome") != CORE.Outcome.PASS.value:
        return result

    files = dict(bundle.get("files") or {})
    expected = dict(bundle.get("expected") or {})
    expected_target = str(expected.get("target") or "").strip()
    if expected_target and "ui.xml" in files and "focus-events.log" in files:
        try:
            xml = CORE.parse_ui_xml(files["ui.xml"])
        except Exception as exc:
            return _failure(bundle, files, "FOCUS_XML_PARSE_FAILURE", str(exc), "focus-evidence")
        xml_targets = [
            value for value in xml.get("focused", [])
            if isinstance(value, str) and value.strip()
        ]
        trace_targets = CORE.FOCUS_RE.findall(files["focus-events.log"])
        trace_stable = (
            len(trace_targets) >= 2
            and expected_target in trace_targets[-1]
            and expected_target in trace_targets[-2]
        )
        if not trace_stable or not any(expected_target in value for value in xml_targets):
            return _failure(
                bundle,
                files,
                "FOCUS_EVIDENCE_MISMATCH",
                f"expected {expected_target!r}; trace={trace_targets[-3:]}; xml={xml_targets}",
                "focus-evidence",
            )

    contract_raw = files.get("device-contract.json")
    if not contract_raw:
        return _failure(
            bundle,
            files,
            "DEVICE_CONTRACT_EVIDENCE_MISSING",
            "wm/screenshot contract evidence is mandatory",
            "device-contract",
        )
    try:
        contract = json.loads(contract_raw)
    except json.JSONDecodeError as exc:
        return _failure(bundle, files, "DEVICE_CONTRACT_EVIDENCE_MALFORMED", str(exc), "device-contract")
    expected_size = list(expected.get("physical_size") or [])
    expected_density = int(expected.get("density") or 0)
    if not (
        contract.get("valid") is True
        and contract.get("physical_size") == expected_size
        and contract.get("effective_size") == expected_size
        and contract.get("screenshot_size") == expected_size
        and int(contract.get("effective_density") or 0) == expected_density
    ):
        return _failure(
            bundle,
            files,
            "DEVICE_CONTRACT_EVIDENCE_MISMATCH",
            f"expected size={expected_size}, density={expected_density}; observed={contract}",
            "device-contract",
        )

    retry_raw = files.get("retry-evidence.json")
    if not retry_raw:
        return _failure(
            bundle,
            files,
            "RETRY_EVIDENCE_MISSING",
            "every fixture run must state whether a retry occurred",
            "retry-policy",
        )
    try:
        retry = json.loads(retry_raw)
    except json.JSONDecodeError as exc:
        return _failure(bundle, files, "RETRY_EVIDENCE_MALFORMED", str(exc), "retry-policy")
    retried = retry.get("retried") is True
    if retried:
        classification_raw = files.get("retry-failure-classification.json")
        if not classification_raw:
            return _failure(
                bundle,
                files,
                "RETRY_ROOT_EVIDENCE_MISSING",
                "a retry occurred without the first-attempt classification",
                "retry-policy",
            )
        try:
            classification = json.loads(classification_raw)
        except json.JSONDecodeError as exc:
            return _failure(bundle, files, "RETRY_ROOT_EVIDENCE_MALFORMED", str(exc), "retry-policy")
        permitted = (
            retry.get("attempts") == 2
            and retry.get("first_status") == 75
            and retry.get("second_status") == 0
            and retry.get("retry_reason") == "SYSTEM_SERVICE_UNAVAILABLE"
            and retry.get("retry_allowed") is True
            and retry.get("final_success") is True
            and retry.get("final_attempt") == "attempt-2"
            and classification.get("classification") == "infrastructure"
            and classification.get("code") == "SYSTEM_SERVICE_UNAVAILABLE"
            and classification.get("retry_allowed") is True
        )
        if not permitted:
            return _failure(
                bundle,
                files,
                "RETRY_POLICY_VIOLATION",
                f"retry is not a single proven external system-service recovery: retry={retry}, root={classification}",
                "retry-policy",
            )
    else:
        permitted = (
            retry.get("attempts") == 1
            and retry.get("first_status") == 0
            and retry.get("second_status") is None
            and retry.get("retry_reason") is None
            and retry.get("retry_allowed") is False
            and retry.get("final_success") is True
            and retry.get("final_attempt") == "attempt-1"
            and "retry-failure-classification.json" not in files
        )
        if not permitted:
            return _failure(
                bundle,
                files,
                "RETRY_EVIDENCE_CONTRADICTION",
                f"non-retried run has contradictory evidence: {retry}",
                "retry-policy",
            )
    return result


CORE.verify_bundle = verify_bundle
