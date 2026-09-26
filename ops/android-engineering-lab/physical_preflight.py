#!/usr/bin/env python3
"""Read-only physical-engineering identity preflight for HULK SA Android.

This tool establishes, as applicable, the selected ADB serial, device
manufacturer/model/device identity, expected owner-approved surface, target
package, installed package presence/version/signer, candidate APK
package/version/signer/SHA-256, supplied source/worktree commit identity, the
state-preservation declaration and any temporary device-setting restore
contract. It fails closed on identity mismatch.

It never uninstalls, clears, wipes, changes settings, installs, launches or
otherwise mutates package/device state. All device access is read-only:
`adb devices`, `getprop`, `pm path` and `pull`.

Judgment (``evaluate``) is a pure function so fixture tests can prove
fail-closed behavior without a device or the Android SDK. The interactive paths
(``collect_device_facts``, ``collect_apk_facts``) only gather facts; they do not
decide anything.
"""

from __future__ import annotations

import argparse
import datetime
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
LIB_LAB = TOOLS_DIR / "lib-lab.sh"
APK_INSPECT = TOOLS_DIR / "apk-inspect.sh"

ADB_TIMEOUT_SECONDS = 25
PULL_TIMEOUT_SECONDS = 120
INSPECT_TIMEOUT_SECONDS = 120

PRODUCTION_PACKAGE = "sa.hulksa.player"
DEV_PACKAGE = "sa.hulksa.player.dev"
PERSISTENT_PACKAGES = {
    "sa.hulksa.player.preview": "persistent-preview",
    "sa.hulksa.player.benchmark": "persistent-benchmark",
}

# Owner-approved persistent physical surfaces from
# docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md.
APPROVED_SURFACES = {
    "mibox4": {"model": "MIBOX4"},
    "sm-a065f": {"model": "SM-A065F"},
    "a06": {"model": "SM-A065F", "device": "a06"},
}

EVIDENCE_KEYS = (
    "preflight_status",
    "preflight_failures",
    "failure_detail",
    "package",
    "package_role",
    "expect_package",
    "adb_serial",
    "device_manufacturer",
    "device_model",
    "device_device",
    "expect_surface",
    "installed_present",
    "installed_version_code",
    "installed_version_name",
    "installed_apk_sha256",
    "installed_signer_sha256",
    "candidate_apk_path",
    "candidate_package",
    "candidate_version_code",
    "candidate_version_name",
    "candidate_apk_sha256",
    "candidate_signer_sha256",
    "expect_signer_sha256",
    "source_commit",
    "expect_source_commit",
    "worktree_head",
    "state_preserving",
    "temporary_setting_contract",
)


def package_class(package: str) -> str:
    """Returns production | dev | persistent-preview | persistent-benchmark | disposable."""
    if package == PRODUCTION_PACKAGE:
        return "production"
    if package == DEV_PACKAGE:
        return "dev"
    if package in PERSISTENT_PACKAGES:
        return PERSISTENT_PACKAGES[package]
    return "disposable"


def sanitize(value: object) -> str:
    """Collapses control characters and bounds length for deterministic key=value evidence."""
    text = "" if value is None else str(value)
    text = text.replace("\r", " ").replace("\n", " ").replace("=", ":").replace("|", "/")
    text = re.sub(r"[^\x20-\x7e]", "?", text)
    return text[:200]


def evaluate(
    facts: dict[str, str],
    temporary_settings: list[tuple[str, str]] | None = None,
) -> dict:
    """Judges collected identity facts. Pure and deterministic; fail closed on mismatch."""
    temporary_settings = list(temporary_settings or ())
    failures: list[str] = []

    def fail(message: str) -> None:
        if message not in failures:
            failures.append(message)

    package = facts.get("package", "")
    role = package_class(package)

    if package == PRODUCTION_PACKAGE:
        fail(f"refusing production package {PRODUCTION_PACKAGE}")
    if not package:
        fail("target package missing")

    expect_package = facts.get("expect_package", "")
    if expect_package and expect_package != package:
        fail(f"expected package {expect_package} does not match target {package}")

    if not facts.get("adb_serial", ""):
        fail("selected adb serial missing")

    if not any(
        facts.get(key, "")
        for key in ("device_manufacturer", "device_model", "device_device")
    ):
        fail("device manufacturer/model/device identity missing")

    expect_surface = facts.get("expect_surface", "")
    if expect_surface:
        surface = APPROVED_SURFACES.get(expect_surface)
        if surface is None:
            fail(f"unknown expected owner-approved surface {expect_surface}")
        else:
            for field, expected in surface.items():
                actual = facts.get(f"device_{field}", "")
                if actual.upper() != expected.upper():
                    fail(
                        f"surface mismatch: expected {field}={expected} "
                        f"got {actual or 'unknown'}"
                    )

    if facts.get("installed_present") != "yes":
        fail("target package is not installed")

    candidate_package = facts.get("candidate_package", "")
    if candidate_package:
        if candidate_package != package:
            fail(
                f"candidate APK package {candidate_package} does not match target {package}"
            )
        if expect_package and candidate_package != expect_package:
            fail(
                f"candidate APK package {candidate_package} does not match "
                f"expected {expect_package}"
            )
        if not facts.get("candidate_signer_sha256", ""):
            fail("candidate APK signer missing")

    if role.startswith("persistent-"):
        if not facts.get("candidate_apk_path", ""):
            fail("persistent package requires an explicit --apk candidate artifact")
        if not facts.get("expect_signer_sha256", ""):
            fail("persistent package requires --expect-signer-sha256")

    for fact_key, expect_key, label in (
        ("candidate_version_code", "expect_version_code", "candidate APK versionCode"),
        ("candidate_version_name", "expect_version_name", "candidate APK versionName"),
        ("installed_version_code", "expect_installed_version_code", "installed versionCode"),
        ("installed_version_name", "expect_installed_version_name", "installed versionName"),
    ):
        expected = facts.get(expect_key, "")
        if expected:
            actual = facts.get(fact_key, "")
            if actual != expected:
                fail(f"{label} {actual or 'unknown'} does not match expected {expected}")

    expect_signer = facts.get("expect_signer_sha256", "").lower()
    if expect_signer:
        candidate_signer = facts.get("candidate_signer_sha256", "").lower()
        installed_signer = facts.get("installed_signer_sha256", "").lower()
        if candidate_signer != expect_signer:
            fail("candidate APK signer does not match expected signer")
        if installed_signer != expect_signer:
            fail("installed signer does not match expected signer; continuity not proven")

    source_commit = facts.get("source_commit", "")
    worktree_head = facts.get("worktree_head", "")
    if source_commit and worktree_head and source_commit != worktree_head:
        fail(
            f"source commit {source_commit} does not match worktree HEAD {worktree_head}"
        )
    expect_source = facts.get("expect_source_commit", "")
    if expect_source:
        if not source_commit:
            fail("expected source commit supplied without a supplied source commit")
        elif source_commit != expect_source:
            fail(f"source commit {source_commit} does not match expected {expect_source}")

    if facts.get("state_preserving") != "yes":
        fail("state-preserving operation was not declared")

    for name, restore in temporary_settings:
        if not name:
            fail("temporary device-setting contract without a setting name")
        elif not restore:
            fail(f"temporary device-setting {name} has no restore value")

    if temporary_settings:
        contract = "; ".join(
            f"{name}=restore_supplied" if restore else f"{name}=restore_missing"
            for name, restore in temporary_settings
        )
    else:
        contract = "none"

    status = "FAIL" if failures else "PASS"
    evidence = {
        "preflight_status": status,
        "preflight_failures": str(len(failures)),
        "failure_detail": " | ".join(failures),
        "package": package,
        "package_role": role,
        "expect_package": expect_package,
        "adb_serial": facts.get("adb_serial", ""),
        "device_manufacturer": facts.get("device_manufacturer", ""),
        "device_model": facts.get("device_model", ""),
        "device_device": facts.get("device_device", ""),
        "expect_surface": expect_surface,
        "installed_present": facts.get("installed_present", ""),
        "installed_version_code": facts.get("installed_version_code", ""),
        "installed_version_name": facts.get("installed_version_name", ""),
        "installed_apk_sha256": facts.get("installed_apk_sha256", ""),
        "installed_signer_sha256": facts.get("installed_signer_sha256", ""),
        "candidate_apk_path": facts.get("candidate_apk_path", ""),
        "candidate_package": candidate_package,
        "candidate_version_code": facts.get("candidate_version_code", ""),
        "candidate_version_name": facts.get("candidate_version_name", ""),
        "candidate_apk_sha256": facts.get("candidate_apk_sha256", ""),
        "candidate_signer_sha256": facts.get("candidate_signer_sha256", ""),
        "expect_signer_sha256": expect_signer,
        "source_commit": source_commit,
        "expect_source_commit": expect_source,
        "worktree_head": worktree_head,
        "state_preserving": facts.get("state_preserving", ""),
        "temporary_setting_contract": contract,
    }
    return {
        "status": status,
        "failures": failures,
        "evidence": [(key, evidence[key]) for key in EVIDENCE_KEYS],
    }


def render_evidence(result: dict) -> str:
    lines = [f"{key}={sanitize(value)}" for key, value in result["evidence"]]
    lines.append(f"preflight_utc={datetime.datetime.now(datetime.timezone.utc):%Y-%m-%dT%H:%M:%SZ}")
    return "\n".join(lines) + "\n"


def _run(command: list[str], timeout: int) -> subprocess.CompletedProcess | None:
    try:
        return subprocess.run(
            command,
            capture_output=True,
            text=True,
            check=False,
            timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None


def resolve_serial(explicit_serial: str | None) -> str:
    """Resolves the selected serial through the shared lib-lab.sh selection contract."""
    environment = dict(os.environ)
    if explicit_serial:
        environment["HULK_ADB_SERIAL"] = explicit_serial
    try:
        process = subprocess.run(
            ["bash", "-c", 'source "$1"; lab_adb_serial', "bash", str(LIB_LAB)],
            capture_output=True,
            text=True,
            check=False,
            env=environment,
            timeout=ADB_TIMEOUT_SECONDS,
        )
    except (OSError, subprocess.TimeoutExpired):
        sys.stderr.write("STOP: device selection did not complete\n")
        raise SystemExit(10)
    if process.returncode != 0:
        sys.stderr.write(process.stderr)
        raise SystemExit(10)
    serial = process.stdout.strip()
    if not serial:
        sys.stderr.write("STOP: device selection returned no serial\n")
        raise SystemExit(10)
    return serial


def adb_shell(serial: str, *arguments: str) -> str:
    process = _run(
        ["adb", "-s", serial, "shell", *arguments],
        timeout=ADB_TIMEOUT_SECONDS,
    )
    if process is None:
        return ""
    return process.stdout.replace("\r", "").strip()


def collect_apk_facts(apk_path: Path) -> dict[str, str]:
    """Runs the repository's qualified apk-inspect.sh and parses its key=value output."""
    process = _run([str(APK_INSPECT), str(apk_path)], timeout=INSPECT_TIMEOUT_SECONDS)
    if process is None or process.returncode != 0:
        return {}
    facts: dict[str, str] = {}
    for line in process.stdout.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            facts[key.strip()] = value.strip()
    return facts


def _flatten_apk_facts(prefix: str, inspected: dict[str, str]) -> dict[str, str]:
    return {
        f"{prefix}_package": inspected.get("package", ""),
        f"{prefix}_version_code": inspected.get("version_code", ""),
        f"{prefix}_version_name": inspected.get("version_name", ""),
        f"{prefix}_apk_sha256": inspected.get("apk_sha256", ""),
        f"{prefix}_signer_sha256": inspected.get("signer1_sha256", ""),
    }


def collect_device_facts(serial: str) -> dict[str, str]:
    return {
        "device_manufacturer": adb_shell(serial, "getprop", "ro.product.manufacturer"),
        "device_model": adb_shell(serial, "getprop", "ro.product.model"),
        "device_device": adb_shell(serial, "getprop", "ro.product.device"),
    }


def collect_installed_facts(serial: str, package: str) -> dict[str, str]:
    """Read-only installed-package facts from pm path plus a pulled base APK."""
    facts = {
        "installed_present": "no",
        "installed_version_code": "",
        "installed_version_name": "",
        "installed_apk_sha256": "",
        "installed_signer_sha256": "",
    }
    remote_path = ""
    for line in adb_shell(serial, "pm", "path", package).splitlines():
        if line.startswith("package:"):
            remote_path = line.split(":", 1)[1].strip()
            break
    if not remote_path:
        return facts
    facts["installed_present"] = "yes"

    with tempfile.TemporaryDirectory(prefix="hulk-physical-preflight-") as tmp:
        local_apk = Path(tmp) / "installed-base.apk"
        process = _run(
            ["adb", "-s", serial, "pull", remote_path, str(local_apk)],
            timeout=PULL_TIMEOUT_SECONDS,
        )
        if process is None or process.returncode != 0 or not local_apk.is_file():
            return facts
        inspected = collect_apk_facts(local_apk)
    facts.update(_flatten_apk_facts("installed", inspected))
    return facts


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="physical-preflight.sh",
        description="Read-only physical-engineering identity preflight (fails closed).",
    )
    parser.add_argument("--package", required=True, help="target installed package")
    parser.add_argument("--apk", help="candidate APK artifact")
    parser.add_argument("--serial", help="explicit ADB serial (overrides HULK_ADB_SERIAL)")
    parser.add_argument("--expect-package")
    parser.add_argument("--expect-version-code")
    parser.add_argument("--expect-version-name")
    parser.add_argument("--expect-installed-version-code")
    parser.add_argument("--expect-installed-version-name")
    parser.add_argument("--expect-signer-sha256")
    parser.add_argument("--expect-surface", help="owner-approved surface: " + ", ".join(sorted(APPROVED_SURFACES)))
    parser.add_argument("--expect-source-commit")
    parser.add_argument("--source-commit")
    parser.add_argument("--worktree", help="task worktree; its HEAD is the supplied source identity")
    parser.add_argument(
        "--state-preserving",
        action="store_true",
        help="declare that the intended operation preserves installed package state",
    )
    parser.add_argument(
        "--temporary-setting",
        action="append",
        default=[],
        metavar="NAME=RESTORE",
        help="temporary device-setting contract with its restore value (repeatable)",
    )
    parser.add_argument("--out", help="directory that also receives physical-preflight.txt")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if package_class(args.package) == "production":
        sys.stderr.write(
            f"STOP: refusing production package {PRODUCTION_PACKAGE}\n"
        )
        return 12
    if not args.state_preserving:
        sys.stderr.write(
            "STOP: --state-preserving declaration is required for a physical preflight\n"
        )
        return 2

    temporary_settings: list[tuple[str, str]] = []
    for item in args.temporary_setting:
        if "=" not in item:
            sys.stderr.write(
                "STOP: --temporary-setting requires NAME=RESTORE with a non-empty restore value\n"
            )
            return 2
        name, restore = item.split("=", 1)
        if not name.strip() or not restore.strip():
            sys.stderr.write(
                "STOP: --temporary-setting requires NAME=RESTORE with a non-empty restore value\n"
            )
            return 2
        temporary_settings.append((name.strip(), restore.strip()))

    candidate_facts: dict[str, str] = {}
    if args.apk:
        apk_path = Path(args.apk)
        if not apk_path.is_file():
            sys.stderr.write(f"STOP: candidate APK not found: {args.apk}\n")
            return 3
        candidate_facts = collect_apk_facts(apk_path)
        if not candidate_facts:
            sys.stderr.write(f"STOP: apk-inspect.sh failed for candidate APK: {args.apk}\n")
            return 4
        candidate_facts["candidate_apk_path"] = str(apk_path)

    worktree_head = ""
    if args.worktree:
        process = _run(
            ["git", "-C", args.worktree, "rev-parse", "HEAD"],
            timeout=ADB_TIMEOUT_SECONDS,
        )
        if process is None or process.returncode != 0:
            sys.stderr.write(f"STOP: not a readable git worktree: {args.worktree}\n")
            return 3
        worktree_head = process.stdout.strip()

    serial = resolve_serial(args.serial)

    facts: dict[str, str] = {
        "adb_serial": serial,
        "package": args.package,
        "expect_package": args.expect_package or "",
        "expect_version_code": args.expect_version_code or "",
        "expect_version_name": args.expect_version_name or "",
        "expect_installed_version_code": args.expect_installed_version_code or "",
        "expect_installed_version_name": args.expect_installed_version_name or "",
        "expect_signer_sha256": (args.expect_signer_sha256 or "").lower(),
        "expect_surface": args.expect_surface or "",
        "expect_source_commit": args.expect_source_commit or "",
        "source_commit": args.source_commit or worktree_head,
        "worktree_head": worktree_head,
        "state_preserving": "yes" if args.state_preserving else "no",
    }
    facts.update(collect_device_facts(serial))
    facts.update(collect_installed_facts(serial, args.package))
    facts.update(_flatten_apk_facts("candidate", candidate_facts))
    facts["candidate_apk_path"] = candidate_facts.get("candidate_apk_path", "")

    result = evaluate(facts, temporary_settings)
    output = render_evidence(result)

    if args.out:
        outdir = Path(args.out)
        outdir.mkdir(parents=True, exist_ok=True)
        (outdir / "physical-preflight.txt").write_text(output, encoding="utf-8")

    sys.stdout.write(output)
    if result["status"] != "PASS":
        sys.stderr.write(
            f"STOP: physical preflight FAILED ({len(result['failures'])}); "
            "identity mismatch is not bypassable\n"
        )
        return 5
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
