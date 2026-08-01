#!/usr/bin/env python3
"""Independent deterministic verifier for raw Quality Lab evidence bundles."""
from __future__ import annotations

from dataclasses import dataclass, asdict
from copy import deepcopy
from enum import Enum
import hashlib
import json
from pathlib import Path
import re
from typing import Any, Iterable
import xml.etree.ElementTree as ET


class Outcome(str, Enum):
    PASS = "PASS"
    FAIL_PRODUCT = "FAIL_PRODUCT"
    FAIL_LAB = "FAIL_LAB"
    FAIL_FIXTURE = "FAIL_FIXTURE"
    FAIL_INFRASTRUCTURE = "FAIL_INFRASTRUCTURE"
    BLOCKED = "BLOCKED"


class Role(str, Enum):
    PRIMARY = "PRIMARY"
    DOWNSTREAM_BLOCKED = "DOWNSTREAM_BLOCKED"


@dataclass(frozen=True)
class Finding:
    code: str
    outcome: Outcome
    message: str
    root_id: str
    role: Role = Role.PRIMARY
    evidence: tuple[str, ...] = ()


MANDATORY_FILES = (
    "ui.xml",
    "focus-events.log",
    "logcat.txt",
    "window.txt",
    "activity.txt",
    "markers.log",
    "origin.log",
    "repository.log",
    "screenshot.json",
    "PROVENANCE.json",
)

TRANSIENT_INFRA_CODES = {
    "ADB_DISCONNECTED",
    "EMULATOR_BOOT_FAILURE",
    "SYSTEM_SERVICE_UNAVAILABLE",
    "ARTIFACT_DOWNLOAD_FAILURE",
}

TV_CONTRACTS = {
    "android-tv-720p-api36": (1280, 720, 213),
    "android-tv-1080p-api36": (1920, 1080, 320),
    "android-tv-4k-api36": (3840, 2160, 640),
}

EXPECTED_DEVICE_IDS = {
    "pixel-4a-api29",
    "pixel-6-api31",
    "pixel-8-pro-api35",
    "galaxy-s24-ultra-api35",
    "pixel-tablet-api35",
    "nexus-9-api28",
    *TV_CONTRACTS.keys(),
}

PAGE_RE = re.compile(r"qa-page:([a-z-]+)")
LAUNCH_RE = re.compile(r"qa-launch-token:([A-Za-z0-9._:-]+)")
MARKER_RE = re.compile(
    r"^(?P<process>\S+)\s+(?P<launch>\S+)\s+(?P<action>[a-z-]+)\s+(?P<revision>\d+)\s*$"
)
ORIGIN_BYTES_RE = re.compile(r"bytes_served=(\d+)")
REPOSITORY_BYTES_RE = re.compile(r"bytes_persisted=(\d+)")
FOCUS_RE = re.compile(r"focused=(?P<target>[a-z0-9-]+)")


def canonical_bytes(payload: Any) -> bytes:
    return (json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def artifact_checksum(files: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for name in sorted(files):
        digest.update(name.encode())
        digest.update(b"\0")
        digest.update(files[name].encode())
        digest.update(b"\0")
    return digest.hexdigest()


def parse_ui_xml(text: str) -> dict[str, Any]:
    root = ET.fromstring(text)
    joined = " ".join(
        " ".join(filter(None, (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))))
        for node in root.iter("node")
    )
    page = PAGE_RE.search(joined)
    launch = LAUNCH_RE.search(joined)
    focused: list[str] = []
    for node in root.iter("node"):
        if node.attrib.get("focused") == "true":
            label = " ".join(filter(None, (node.attrib.get("text", ""), node.attrib.get("content-desc", "")))).strip()
            focused.append(label)
    return {"page": page.group(1) if page else None, "launch_token": launch.group(1) if launch else None, "focused": focused}


def parse_markers(text: str) -> list[dict[str, Any]]:
    markers = []
    for line in text.splitlines():
        if not line.strip():
            continue
        match = MARKER_RE.match(line)
        if not match:
            raise ValueError(f"malformed marker line: {line!r}")
        item = match.groupdict()
        item["revision"] = int(item["revision"])
        markers.append(item)
    return markers


def parse_counter(text: str, regex: re.Pattern[str]) -> int:
    matches = regex.findall(text)
    return int(matches[-1]) if matches else 0


def validate_matrix(devices: Iterable[dict[str, Any]]) -> list[Finding]:
    by_id = {str(item.get("id")): item for item in devices}
    findings: list[Finding] = []
    missing = sorted(EXPECTED_DEVICE_IDS - by_id.keys())
    extra = sorted(by_id.keys() - EXPECTED_DEVICE_IDS)
    if missing or extra:
        findings.append(Finding("MATRIX_DEVICE_SET_MISMATCH", Outcome.FAIL_LAB, f"missing={missing}; extra={extra}", "matrix:device-set"))
    for device_id, (width, height, density) in TV_CONTRACTS.items():
        item = by_id.get(device_id)
        if not item:
            continue
        actual = (int(item.get("physical_width", 0)), int(item.get("physical_height", 0)), int(item.get("density", 0)))
        if actual != (width, height, density):
            findings.append(Finding("DEVICE_CONTRACT_MISMATCH", Outcome.FAIL_LAB, f"{device_id}: expected {(width,height,density)}, observed {actual}", f"matrix:{device_id}"))
    return findings


def validate_provenance(bundle: dict[str, Any], provenance: dict[str, Any], xml: dict[str, Any]) -> list[Finding]:
    expected = bundle.get("expected", {})
    required = ("source_head_sha", "base_sha", "tested_commit_sha", "merge_sha", "lab_apk_sha256", "launch_token", "process_id")
    missing = [key for key in required if not str(provenance.get(key, "")).strip()]
    if missing:
        return [Finding("PROVENANCE_INCOMPLETE", Outcome.BLOCKED, f"missing provenance fields: {missing}", "provenance")]
    checks = {
        "source_head_sha": expected.get("source_head_sha"),
        "base_sha": expected.get("base_sha"),
        "tested_commit_sha": expected.get("tested_commit_sha"),
        "merge_sha": expected.get("merge_sha"),
        "lab_apk_sha256": expected.get("lab_apk_sha256"),
        "launch_token": expected.get("launch_token"),
        "process_id": expected.get("process_id"),
    }
    for key, wanted in checks.items():
        if wanted is not None and str(provenance.get(key)) != str(wanted):
            return [Finding("PROVENANCE_MISMATCH", Outcome.FAIL_LAB, f"{key}: expected {wanted!r}, observed {provenance.get(key)!r}", f"provenance:{key}")]
    if xml.get("launch_token") and xml["launch_token"] != provenance["launch_token"]:
        return [Finding("STALE_LAUNCH_TOKEN", Outcome.FAIL_LAB, "UI launch token does not match current provenance", "launch-token")]
    return []


def reconcile(findings: list[Finding]) -> list[Finding]:
    roots: dict[str, Finding] = {}
    result: list[Finding] = []
    for finding in findings:
        if finding.role == Role.PRIMARY:
            if finding.root_id in roots:
                continue
            roots[finding.root_id] = finding
            result.append(finding)
        else:
            result.append(finding)
    return result


def verify_bundle(bundle: dict[str, Any]) -> dict[str, Any]:
    files = dict(bundle.get("files") or {})
    missing = [name for name in MANDATORY_FILES if name not in files]
    findings: list[Finding] = []
    if missing:
        findings.append(Finding("MANDATORY_EVIDENCE_MISSING", Outcome.BLOCKED, f"missing evidence: {missing}", "evidence"))
        return report(bundle, findings, files)

    expected_checksum = bundle.get("artifact_checksum")
    actual_checksum = artifact_checksum(files)
    if expected_checksum and expected_checksum != actual_checksum:
        findings.append(Finding("ARTIFACT_CHECKSUM_MISMATCH", Outcome.FAIL_LAB, "artifact bundle checksum mismatch", "checksum"))
        return report(bundle, findings, files)

    try:
        screenshot = json.loads(files["screenshot.json"])
        provenance = json.loads(files["PROVENANCE.json"])
        xml = parse_ui_xml(files["ui.xml"])
        markers = parse_markers(files["markers.log"])
    except (json.JSONDecodeError, ET.ParseError, ValueError) as exc:
        findings.append(Finding("EVIDENCE_PARSE_FAILURE", Outcome.FAIL_LAB, str(exc), "parse"))
        return report(bundle, findings, files)

    expected = bundle.get("expected", {})
    expected_page = expected.get("page")
    expected_size = tuple(expected.get("physical_size") or ())
    expected_density = expected.get("density")
    expected_target = expected.get("target")
    expected_action = expected.get("action")

    findings.extend(validate_provenance(bundle, provenance, xml))
    if findings:
        return report(bundle, findings, files)

    if "device offline" in files["logcat.txt"].lower() or "adb_disconnected" in files["activity.txt"].lower():
        findings.append(Finding("ADB_DISCONNECTED", Outcome.FAIL_INFRASTRUCTURE, "ADB transport disconnected", "infrastructure:adb"))
        return report(bundle, findings, files)

    actual_size = (int(screenshot.get("width", 0)), int(screenshot.get("height", 0)))
    if expected_size and actual_size != expected_size:
        findings.append(Finding("PHYSICAL_RESOLUTION_MISMATCH", Outcome.FAIL_LAB, f"expected {expected_size}, observed {actual_size}", "device:resolution"))
        return report(bundle, findings, files)
    if expected_density is not None and int(screenshot.get("density", 0)) != int(expected_density):
        findings.append(Finding("DENSITY_MISMATCH", Outcome.FAIL_LAB, f"expected {expected_density}, observed {screenshot.get('density')}", "device:density"))
        return report(bundle, findings, files)

    if expected_page and xml.get("page") != expected_page:
        findings.append(Finding("PAGE_IDENTITY_MISMATCH", Outcome.FAIL_LAB, f"expected page {expected_page}, observed {xml.get('page')}", "page-identity"))
        return report(bundle, findings, files)

    if "fixture_server=stopped" in files["origin.log"]:
        findings.append(Finding("FIXTURE_SERVER_UNAVAILABLE", Outcome.FAIL_FIXTURE, "fixture loopback server is not running", "fixture:origin"))
        return report(bundle, findings, files)

    focus_events = FOCUS_RE.findall(files["focus-events.log"])
    stable_target = len(focus_events) >= 2 and focus_events[-1] == focus_events[-2]
    if expected_target and (not stable_target or focus_events[-1] != expected_target):
        findings.append(Finding("START_STATE_NOT_ESTABLISHED", Outcome.FAIL_LAB, f"required target {expected_target!r}; observations={focus_events[-3:]}", "start-state"))
        if expected_action:
            findings.append(Finding("CALLBACK_NOT_EXECUTED", Outcome.BLOCKED, "callback assertion blocked by invalid start state", "start-state", Role.DOWNSTREAM_BLOCKED))
        return report(bundle, findings, files)

    current_markers = [m for m in markers if m["launch"] == provenance["launch_token"] and m["process"] == provenance["process_id"]]
    foreign_markers = [m for m in markers if m not in current_markers]
    if foreign_markers and not current_markers:
        findings.append(Finding("MARKER_PROVENANCE_MISMATCH", Outcome.FAIL_LAB, "markers came from a stale process or launch", "marker-provenance"))
        return report(bundle, findings, files)

    if expected_action:
        matching = [m for m in current_markers if m["action"] == expected_action]
        wrong = [m for m in current_markers if m["action"] != expected_action]
        if not current_markers:
            findings.append(Finding("CALLBACK_NOT_EXECUTED", Outcome.FAIL_PRODUCT, f"no callback marker for {expected_action}", f"callback:{expected_action}"))
        elif not matching and wrong:
            findings.append(Finding("NAVIGATION_TARGET_MISMATCH", Outcome.FAIL_LAB, f"expected callback {expected_action}, observed {[m['action'] for m in wrong]}", "navigation-target"))
        elif not matching:
            findings.append(Finding("CALLBACK_NOT_EXECUTED", Outcome.FAIL_PRODUCT, f"callback {expected_action} not emitted", f"callback:{expected_action}"))

    origin_bytes = parse_counter(files["origin.log"], ORIGIN_BYTES_RE)
    repository_bytes = parse_counter(files["repository.log"], REPOSITORY_BYTES_RE)
    if origin_bytes > 0 and repository_bytes == 0:
        findings.append(Finding("ORIGIN_REPOSITORY_BOUNDARY_MISMATCH", Outcome.FAIL_PRODUCT, f"origin served {origin_bytes} bytes; repository persisted 0", "download-boundary"))
    elif repository_bytes > 0 and origin_bytes == 0:
        findings.append(Finding("REPOSITORY_PROGRESS_WITHOUT_ORIGIN", Outcome.FAIL_FIXTURE, f"repository persisted {repository_bytes} bytes without origin evidence", "fixture:download-boundary"))

    return report(bundle, reconcile(findings), files)


def report(bundle: dict[str, Any], findings: list[Finding], files: dict[str, str]) -> dict[str, Any]:
    outcomes = [finding.outcome for finding in findings if finding.role == Role.PRIMARY]
    order = [Outcome.FAIL_LAB, Outcome.FAIL_FIXTURE, Outcome.FAIL_INFRASTRUCTURE, Outcome.BLOCKED, Outcome.FAIL_PRODUCT]
    overall = Outcome.PASS
    for candidate in order:
        if candidate in outcomes:
            overall = candidate
            break
    return {
        "schema_version": 1,
        "case_id": bundle.get("case_id"),
        "outcome": overall.value,
        "root_count": sum(f.role == Role.PRIMARY for f in findings),
        "downstream_count": sum(f.role == Role.DOWNSTREAM_BLOCKED for f in findings),
        "artifact_checksum": artifact_checksum(files) if files else None,
        "findings": [{**asdict(f), "outcome": f.outcome.value, "role": f.role.value, "evidence": list(f.evidence)} for f in findings],
    }


def gate(reports: Iterable[dict[str, Any]], enforce_findings: bool) -> dict[str, Any]:
    reports = list(reports)
    fatal_always = {Outcome.FAIL_LAB.value, Outcome.FAIL_FIXTURE.value, Outcome.FAIL_INFRASTRUCTURE.value, Outcome.BLOCKED.value}
    fatal = [r for r in reports if r.get("outcome") in fatal_always or (enforce_findings and r.get("outcome") == Outcome.FAIL_PRODUCT.value)]
    return {"passed": not fatal, "enforce_findings": enforce_findings, "fatal_case_ids": [r.get("case_id") for r in fatal]}


def retry_allowed(code: str) -> bool:
    return code in TRANSIENT_INFRA_CODES


def load_bundle(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _expand_compact_corpus(payload: dict[str, Any]) -> list[dict[str, Any]]:
    base = payload.get("base")
    definitions = payload.get("cases")
    if not isinstance(base, dict) or not isinstance(definitions, list):
        return list(definitions or [])
    expanded: list[dict[str, Any]] = []
    for definition in definitions:
        bundle = deepcopy(base)
        bundle["case_id"] = definition["case_id"]
        bundle.setdefault("expected", {})["outcome"] = definition["outcome"]
        for key, value in (definition.get("expected_overrides") or {}).items():
            bundle["expected"][key] = value
        for name in definition.get("delete_files") or []:
            bundle.setdefault("files", {}).pop(name, None)
        for name, value in (definition.get("file_overrides") or {}).items():
            bundle.setdefault("files", {})[name] = value
        bundle["artifact_checksum"] = artifact_checksum(bundle.get("files") or {})
        expanded.append(bundle)
    return expanded


def load_corpus(root: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    paths = [root] if root.is_file() else sorted(root.glob("*.json"))
    if not paths:
        raise ValueError(f"corpus contains no JSON cases: {root}")
    for path in paths:
        payload = load_bundle(path)
        if isinstance(payload, dict) and isinstance(payload.get("cases"), list):
            cases.extend(_expand_compact_corpus(payload))
        else:
            cases.append(payload)
    if not cases:
        raise ValueError(f"corpus contains zero cases: {root}")
    return cases


def save_report(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_bytes(payload))
